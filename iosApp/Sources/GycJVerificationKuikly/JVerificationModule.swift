import Foundation
import OpenKuiklyIOSRender

/// 每 Renderer 一个页面 client；SDK 的进程 owner/初始化继续由 JVerificationNativeClient 管理。
@objc(GycJVerificationModule)
public final class JVerificationModule: KRBaseModule {
    /// Main 配置 factory；返回配置了品牌、presenter、UI 和采集策略的页面 client，不在 JSON 传这些对象。
    public static var clientFactory: (() -> JVerificationNativeClient)?
    private var client: JVerificationNativeClient?
    private let lifecycleLock = NSLock()
    private var invalidated = false
    private var destroyed: Bool { lifecycleLock.lock(); defer { lifecycleLock.unlock() }; return invalidated }
    private var currentClient: JVerificationNativeClient? {
        lifecycleLock.lock(); defer { lifecycleLock.unlock() }; return client
    }
    private func resolveClient() -> JVerificationNativeClient? {
        if let currentClient { return currentClient }
        let created = Self.clientFactory?()
        lifecycleLock.lock()
        if invalidated { lifecycleLock.unlock(); created?.close(); return nil }
        client = created
        lifecycleLock.unlock()
        return created
    }
    private func releaseClient() {
        lifecycleLock.lock()
        invalidated = true
        let native = client
        client = nil
        lifecycleLock.unlock()
        // SDK dealloc 也调用 invalidate；清理只捕获资源，不能重新 retain 正在释放的 module。
        let cleanup: () -> Void = { native?.close() }
        if Thread.isMainThread { cleanup() } else { DispatchQueue.main.async(execute: cleanup) }
    }
    private var generation = 0
    private var deliveryEpoch = 0
    private func rejectQueuedDeliveries() {
        lifecycleLock.lock(); deliveryEpoch += 1; lifecycleLock.unlock()
    }
    private var pending = false
    private var consent = false

    public override class func moduleName() -> String { "GycJVerificationModule" }
    public override func hrv_call(withMethod method: String, params: Any?, callback: KuiklyRenderCallback?) -> Any? {
        onMain { [weak self] in self?.perform(method, params: params, callback: callback) }
        return nil
    }
    private func perform(_ method: String, params: Any?, callback: KuiklyRenderCallback?) {
        guard !destroyed else { return }
        guard let text = params as? String, let data = text.data(using: .utf8),
              let args = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            deliver(["status": "FAILED", "code": -1], callback: callback); return
        }
        if ["cancel", "clearCache", "close"].contains(method) {
            rejectQueuedDeliveries()
            generation += 1; pending = false
            currentClient?.cancel()
            if method == "clearCache" || method == "close" { consent = false; currentClient?.revokeConsent() }
            if method == "close" { releaseClient() }
            return
        }
        guard ["initialize", "preLogin", "authenticate"].contains(method) else { deliver(["status": "UNSUPPORTED", "code": -13], callback: callback); return }
        guard let granted = args["consentGranted"] as? NSNumber, CFGetTypeID(granted) == CFBooleanGetTypeID(), granted.boolValue,
              method != "preLogin" || consent else {
            rejectQueuedDeliveries()
            generation += 1; pending = false; consent = false; currentClient?.revokeConsent()
            deliver(["status": "CONSENT_REQUIRED", "code": -10], callback: callback); return
        }
        guard !pending else { deliver(["status": "BUSY", "code": -11], callback: callback); return }
        guard let client = resolveClient() else { if !destroyed { deliver(["status": "UNSUPPORTED", "code": -13], callback: callback) }; return }
        guard !destroyed else { return }
        consent = true; pending = true; generation += 1
        let attempt = generation
        var didOpen = false
        let completion: JVerificationNativeClient.Completion = { [weak self] result in
            guard let self, !self.destroyed, self.pending, self.generation == attempt else { return }
            self.pending = false
            var value: [String: Any] = ["status": Self.status(result, login: method == "authenticate"), "code": result.code]
            if value["status"] as? String == "TOKEN" { value["token"] = result.token }
            value["carrier"] = result.carrier
            self.deliver(value, callback: callback)
        }
        switch method {
        case "initialize": client.initialize(consentGranted: true, completion: completion)
        case "preLogin": client.preLogin(completion: completion)
        default: client.authenticate(consentGranted: true, opened: { [weak self] in
            guard let self, !self.destroyed, self.pending, self.generation == attempt, !didOpen else { return }
            didOpen = true; self.deliver(["event": "opened"], callback: callback)
        }, completion: completion)
        }
    }
    private func deliver(_ value: [String: Any], callback: KuiklyRenderCallback?) {
        lifecycleLock.lock(); let expected = deliveryEpoch; lifecycleLock.unlock()
        KuiklyRenderThreadManager.performOnContextQueue { [weak self] in
            guard let self else { return }
            self.lifecycleLock.lock()
            let active = !self.invalidated && self.deliveryEpoch == expected
            self.lifecycleLock.unlock()
            // 下一合法请求不能吞掉已接受的结果；撤销/取消/关闭仍抑制排队交付。
            if active, self.hr_rootView != nil { callback?(value) }
        }
    }
    public override func invalidate() {
        releaseClient()
        super.invalidate()
    }
    public static func register() { precondition(NSClassFromString("GycJVerificationModule") == JVerificationModule.self) }
    private func onMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread { action() } else { DispatchQueue.main.async(execute: action) }
    }
    private static func status(_ result: JVerificationReply, login: Bool) -> String {
        if login && result.code == 6000 && result.token?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false { return "TOKEN" }
        if !login && [0, 8000, 7000].contains(result.code) { return "READY" }
        switch result.code {
        case 2000, 6001, 6002: return "CANCELED"
        case -10: return "CONSENT_REQUIRED"
        case -11: return "BUSY"
        case -12: return "CLOSED"
        case -13: return "UNSUPPORTED"
        case -14: return "TIMEOUT"
        default: return "FAILED"
        }
    }
}
