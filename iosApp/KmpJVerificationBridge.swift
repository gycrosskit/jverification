// 消费示例；将 JVerificationConsumer 改为宿主导出的 framework 名称。
import JVerificationConsumer
import GycJVerificationNative

func makeJVerificationDriver(_ native: JVerificationNativeClient) -> IosJVerificationDriver {
    IosJVerificationDriver(
        initializeNative: { callback in
            native.initialize(consentGranted: true) { result in
                _ = callback(KotlinInt(int: Int32(result.code)), result.token, result.carrier)
            }
        },
        preLoginNative: { callback in
            native.preLogin { result in
                _ = callback(KotlinInt(int: Int32(result.code)), result.token, result.carrier)
            }
        },
        authenticateNative: { opened, callback in
            native.authenticate(consentGranted: true, opened: { _ = opened() }) { result in
                _ = callback(KotlinInt(int: Int32(result.code)), result.token, result.carrier)
            }
        },
        cancelNative: { native.cancel() },
        clearCacheNative: { native.revokeConsent() },
        closeNative: { native.close() }
    )
}
