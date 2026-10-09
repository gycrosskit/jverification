import GycJVerificationNative

// Compile as an independent consumer of the built optional Pod, without compiling receiver sources.
func configureNativeReceiver(_ native: JVerificationNativeClient) {
    JVerificationModule.clientFactory = { native }
    JVerificationModule.register()
    let receiver = JVerificationModule()
    _ = receiver.hrv_call(withMethod: "initialize", params: "{\"consentGranted\":false}", callback: { _ in })
    receiver.invalidate()
}
