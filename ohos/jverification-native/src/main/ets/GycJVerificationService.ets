import { common } from '@kit.AbilityKit';
import { JVerificationInterface as SDK, JVerificationLoginSettings, JVerifyUIConfig } from '@jg/verify';

/** 归一 SDK 回执；只在 TOKEN 携带敏感凭据，不记录日志或持久化。 */
export interface GycVerificationReply {
  /** READY 是准备成功；TOKEN 才能向宿主后端换票。 */
  status: string;
  /** 厂商整数码；组件自产结果默认 0。 */
  code: number;
  /** 短期敏感换票凭据，失败或取消时省略。 */
  token?: string;
  /** 可选运营商标识，不证明业务身份。 */
  carrier?: string;
}

/** 进程唯一实例；宿主提供 NavPathStack、UIContext、品牌配置和同意状态。 */
export class GycJVerificationService {
  private static owner: GycJVerificationService | null = null;
  private static initializedKey: string = '';
  private static initializing: Promise<number> | null = null;
  private consent: boolean = false;
  private closed: boolean = false;
  private generation: number = 0;
  private pending: ((value: GycVerificationReply) => void) | null = null;
  private timer: number = -1;
  private loginActive: boolean = false;

  private context: common.ApplicationContext;
  private appKey: string;
  private uiContext: () => UIContext;
  private stack: NavPathStack;
  private uiConfig: () => JVerifyUIConfig;
  private configureBeforeInit: () => void;
  /** UI 线程使用；AppKey 非空，宿主提供导航栈、可用 UIContext、品牌 UI 和 SDK 初始化前配置。 */
  constructor(context: common.ApplicationContext, appKey: string,
    uiContext: () => UIContext, stack: NavPathStack,
    uiConfig: () => JVerifyUIConfig, configureBeforeInit: () => void = () => {}) {
    if (appKey.trim().length === 0) throw new Error('AppKey required');
    this.context = context; this.appKey = appKey; this.uiContext = uiContext;
    this.stack = stack; this.uiConfig = uiConfig; this.configureBeforeInit = configureBeforeInit;
  }

  /** 明确同意后初始化，等待最多 10000 毫秒；底层 init 未结束前不会重复初始化。 */
  async initialize(consentGranted: boolean): Promise<GycVerificationReply> {
    if (!consentGranted) { this.revokeConsent(); return this.reply('CONSENT_REQUIRED'); }
    this.consent = true;
    return this.perform(10_000, async (attempt: number): Promise<GycVerificationReply> => {
      if (SDK.isInitSuccess()) return this.reply('READY', 8000);
      if (!GycJVerificationService.initializing) {
        GycJVerificationService.initializedKey = this.appKey;
        this.configureBeforeInit();
        GycJVerificationService.initializing = new Promise<number>((resolve, reject) => {
          try { SDK.init(this.context, this.appKey, (code: number) => resolve(code)); }
          catch (error) { reject(error); }
        });
        // 页面等待超时不能代表底层 init 已结束，继续保留同一 Promise 防止重复 init。
        GycJVerificationService.initializing.finally(() => {
          GycJVerificationService.initializing = null;
        }).catch(() => {});
      }
      const code = await GycJVerificationService.initializing;
      return this.reply(code === 8000 ? 'READY' : 'FAILED', code);
    });
  }

  /** 初始化并预取号，不展示授权页；撤销发生在两个阶段之间也取消后续操作。 */
  async prepare(consentGranted: boolean): Promise<GycVerificationReply> {
    const expected = this.generation + 1;
    const result = await this.initialize(consentGranted);
    if (result.status !== 'READY') return result;
    if (!this.current(expected)) return this.reply('CANCELED');
    return this.preLogin();
  }

  /** 同意之后预取号，等待最多 10000 毫秒。 */
  async preLogin(): Promise<GycVerificationReply> {
    if (!this.consent) return this.reply('CONSENT_REQUIRED');
    return this.perform(10_000, async (attempt: number): Promise<GycVerificationReply> => {
      if (!this.consent || !SDK.isInitSuccess() || !await SDK.checkVerifyEnable()) return this.reply('UNSUPPORTED');
      if (!this.current(attempt)) return this.reply('CANCELED');
      return new Promise<GycVerificationReply>((resolve) => {
        SDK.prelogin((code: number) => resolve(this.reply(code === 7000 ? 'READY' : 'FAILED', code)), 10_000);
      });
    });
  }

  /** 初始化后打开授权页；opened 仅通知一次，最长等待 120000 毫秒，Token 为终态。 */
  async authenticate(consentGranted: boolean, opened: () => void = () => {}): Promise<GycVerificationReply> {
    if (!consentGranted) { this.revokeConsent(); return this.reply('CONSENT_REQUIRED'); }
    const expected = this.generation + 1;
    const result = await this.initialize(true);
    if (result.status !== 'READY') return result;
    if (!this.current(expected)) return this.reply('CANCELED');
    return this.perform(120_000, async (attempt: number): Promise<GycVerificationReply> => {
      if (!this.consent || !await SDK.checkVerifyEnable()) return this.reply('UNSUPPORTED');
      if (!this.current(attempt)) return this.reply('CANCELED');
      SDK.setCustomUIWithConfig(this.uiConfig());
      const settings = new JVerificationLoginSettings(this.uiContext());
      settings.navPathStack = this.stack;
      settings.isRouter = false;
      settings.autoFinish = true;
      settings.timeout = 15_000;
      this.loginActive = true;
      let didOpen: boolean = false;
      settings.authPageEventListener = (event: number): void => {
        if (!this.current(attempt) || !this.loginActive) return;
        if (event === 1) this.loginActive = false;
        if (event === 2 && !didOpen) { didOpen = true; opened(); }
      };
      return new Promise<GycVerificationReply>((resolve) => {
        SDK.loginAuth(settings, (code: number, content: string, carrier: string) => {
          if (!this.current(attempt)) return;
          this.loginActive = false;
          if (code === 6000 && content.trim().length > 0) {
            resolve({ status: 'TOKEN', code, token: content, carrier });
          } else resolve(this.reply(code === 6002 ? 'CANCELED' : 'FAILED', code));
        });
      });
    });
  }

  /** 结束本地等待并撤销迟到事件；仅关闭本实例拥有的授权页。 */
  cancel(): void {
    this.generation++;
    if (this.timer !== -1) clearTimeout(this.timer);
    this.timer = -1;
    if (GycJVerificationService.owner === this && this.loginActive) SDK.dismissLoginAuthActivity(this.uiContext());
    this.loginActive = false;
    const resolve = this.pending; this.pending = null;
    resolve?.(this.reply('CANCELED'));
  }

  /** 仅清除当前进程 owner 的预取号缓存。 */
  clearPreLoginCache(): void { if (GycJVerificationService.owner === this) SDK.clearPreLoginCache(); }
  /** 撤销同意，取消等待并清空预取号缓存。 */
  revokeConsent(): void { this.consent = false; this.cancel(); this.clearPreLoginCache(); }
  /** 永久关闭并释放进程所有权；不可重开。 */
  close(): void {
    this.closed = true; this.revokeConsent();
    if (GycJVerificationService.owner === this) GycJVerificationService.owner = null;
  }

  private perform(timeout: number, action: (attempt: number) => Promise<GycVerificationReply>): Promise<GycVerificationReply> {
    if (this.closed) return Promise.resolve(this.reply('CLOSED'));
    if (!this.consent) return Promise.resolve(this.reply('CONSENT_REQUIRED'));
    if (this.pending || (GycJVerificationService.owner && GycJVerificationService.owner !== this) ||
      (GycJVerificationService.initializedKey && GycJVerificationService.initializedKey !== this.appKey)) {
      return Promise.resolve(this.reply('BUSY'));
    }
    GycJVerificationService.owner = this;
    const attempt = ++this.generation;
    return new Promise<GycVerificationReply>((resolve) => {
      this.pending = resolve;
      this.timer = setTimeout(() => {
        if (!this.current(attempt)) return;
        this.pending = null;
        this.cancel();
        resolve(this.reply('TIMEOUT'));
      }, timeout);
      action(attempt).then((result: GycVerificationReply) => {
        if (!this.current(attempt)) return;
        clearTimeout(this.timer); this.timer = -1; this.pending = null;
        resolve(result);
      }).catch(() => {
        if (!this.current(attempt)) return;
        this.pending = null; this.cancel(); resolve(this.reply('FAILED'));
      });
    });
  }

  private current(attempt: number): boolean { return !this.closed && this.consent && this.generation === attempt; }
  private reply(status: string, code: number = 0): GycVerificationReply { return { status, code }; }
}
