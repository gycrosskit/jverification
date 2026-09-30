import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { GycJVerificationService, GycVerificationReply } from './GycJVerificationService';

interface VerificationParams { consentGranted?: boolean; }

/** 不传 AppKey、UIContext 或 Token 到日志；原生配置通过宿主模块工厂注入。 */
export class GycJVerificationModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME: string = 'GycJVerificationModule';
  private destroyed: boolean = false;
  private service: GycJVerificationService;
  constructor(service: GycJVerificationService) { super(); this.service = service; }
  syncMode(): boolean { return false; }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    if (this.destroyed) return null;
    this.perform(method, params).then((reply: GycVerificationReply) => {
      if (!this.destroyed) callback?.(reply);
    }).catch(() => { if (!this.destroyed) callback?.({ status: 'FAILED', code: -1 }); });
    return null;
  }

  private async perform(method: string, params: KRAny): Promise<GycVerificationReply> {
    const args = JSON.parse(typeof params === 'string' ? params : '{}') as VerificationParams;
    if (method === 'initialize') return this.service.initialize(args.consentGranted === true);
    if (method === 'preLogin') return this.service.preLogin();
    if (method === 'authenticate') return this.service.authenticate(args.consentGranted === true);
    if (method === 'cancel') this.service.cancel();
    else if (method === 'clearCache') this.service.revokeConsent();
    else if (method === 'close') this.service.close();
    else return { status: 'FAILED', code: -1 };
    return { status: 'READY', code: 0 };
  }

  onDestroy(): void { this.destroyed = true; this.service.close(); super.onDestroy(); }
}
