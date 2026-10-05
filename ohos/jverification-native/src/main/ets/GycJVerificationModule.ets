import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { GycJVerificationService, GycVerificationReply } from './GycJVerificationService';

interface VerificationParams { consentGranted?: boolean; }

/** 不传 AppKey、UIContext 或 Token 到日志；原生配置通过宿主模块工厂注入。 */
export class GycJVerificationModule extends KuiklyRenderBaseModule {
  /** Kuikly 注册名，与 Kotlin 侧常量一致。 */
  static readonly MODULE_NAME: string = 'GycJVerificationModule';
  private destroyed: boolean = false;
  private generation: number = 0;
  private service: GycJVerificationService;
  /** 宿主注入品牌配置后的服务；页面销毁将关闭此服务。 */
  constructor(service: GycJVerificationService) { super(); this.service = service; }
  syncMode(): boolean { return false; }

  /** 页面线程异步入口；销毁后忽略请求与迟到回调，参数 JSON 属于输入边界。 */
  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    if (this.destroyed) return null;
    if (method === 'cancel' || method === 'clearCache' || method === 'close') this.generation++;
    const attempt = this.generation;
    const deliver = (reply: GycVerificationReply): void => {
      if (!this.destroyed && attempt === this.generation) callback?.(reply);
    };
    this.perform(method, params, () => {
      if (!this.destroyed && attempt === this.generation) callback?.({ event: 'opened' });
    }).then(deliver)
      .catch(() => deliver({ status: 'FAILED', code: -1 }));
    return null;
  }

  private async perform(method: string, params: KRAny, opened: () => void): Promise<GycVerificationReply> {
    const args = JSON.parse(typeof params === 'string' ? params : '{}') as VerificationParams;
    if (method === 'initialize') return this.service.initialize(args.consentGranted === true);
    if (method === 'preLogin') return this.service.preLogin();
    if (method === 'authenticate') return this.service.authenticate(args.consentGranted === true, opened);
    if (method === 'cancel') this.service.cancel();
    else if (method === 'clearCache') this.service.revokeConsent();
    else if (method === 'close') this.service.close();
    else return { status: 'FAILED', code: -1 };
    return { status: 'READY', code: 0 };
  }

  onDestroy(): void { this.destroyed = true; this.service.close(); super.onDestroy(); }
}
