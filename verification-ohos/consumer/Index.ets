import { GycJVerificationService, GycJVerificationModule, JVerifyUIConfig } from '@gycrosskit/jverification-native';
import { common } from '@kit.AbilityKit';
export function service(context: common.ApplicationContext, key: string, ui: UIContext, stack: NavPathStack, config: JVerifyUIConfig): GycJVerificationService {
 return new GycJVerificationService(context, key, () => ui, stack, () => config);
}
export function module(service: GycJVerificationService): GycJVerificationModule { return new GycJVerificationModule(service); }
export async function authenticate(service: GycJVerificationService, opened: () => void): Promise<string> {
 return (await service.authenticate(true, opened)).status;
}
