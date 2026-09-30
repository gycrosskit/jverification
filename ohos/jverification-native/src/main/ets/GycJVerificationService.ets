import { common } from '@kit.AbilityKit';
import { JVerificationInterface as SDK, JVerificationLoginSettings, JVerifyUIConfig } from '@jg/verify';

export interface GycVerificationReply {
  status: string;
  code: number;
  token?: string;
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
  constructor(context: common.ApplicationContext, appKey: string,
    uiContext: () => UIContext, stack: NavPathStack,
    uiConfig: () => JVerifyUIConfig, configureBeforeInit: () => void = () => {}) {
    if (appKey.trim().length === 0) throw new Error('AppKey required');
    this.context = context; this.appKey = appKey; this.uiContext = uiContext;
    this.stack = stack; this.uiConfig = uiConfig; this.configureBeforeInit = configureBeforeInit;
  }

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

  async prepare(consentGranted: boolean): Promise<GycVerificationReply> {
    const expected = this.generation + 1;
    const result = await this.initialize(consentGranted);
    if (result.status !== 'READY') return result;
    if (!this.current(expected)) return this.reply('CANCELED');
    return this.preLogin();
  }

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

  async authenticate(consentGranted: boolean): Promise<GycVerificationReply> {
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

  cancel(): void {
    this.generation++;
    if (this.timer !== -1) clearTimeout(this.timer);
    this.timer = -1;
    if (GycJVerificationService.owner === this && this.loginActive) SDK.dismissLoginAuthActivity(this.uiContext());
    this.loginActive = false;
    const resolve = this.pending; this.pending = null;
    resolve?.(this.reply('CANCELED'));
  }

  clearPreLoginCache(): void { if (GycJVerificationService.owner === this) SDK.clearPreLoginCache(); }
  revokeConsent(): void { this.consent = false; this.cancel(); this.clearPreLoginCache(); }
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
