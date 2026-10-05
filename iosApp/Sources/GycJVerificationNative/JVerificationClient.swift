import Foundation
import UIKit

/// 厂商回执；description 仅保留 code，token 不写日志或持久化。
public struct JVerificationReply: CustomStringConvertible {
    /// 厂商结果码或组件负值：同意 -10、忙 -11、关闭 -12、不支持 -13、超时 -14。
    public let code: Int
    /// 6000 成功时的短期敏感换票凭据；交给宿主后端，其他结果为 nil。
    public let token: String?
    /// 厂商运营商标识，可为空，不代表登录身份。
    public let carrier: String?
    /// 脱敏日志说明，不包含 token 或 carrier。
    public var description: String { "JVerificationReply(code=\(code))" }
}

/// 主线程、进程唯一实例。Token 交给宿主后端，不写日志、不持久化。
public final class JVerificationNativeClient {
    /// Main 上的唯一结果回调。
    public typealias Completion = (JVerificationReply) -> Void
    private static weak var owner: JVerificationNativeClient?
    private static var initializedKey: String?
    private static var initializing = false
    private static var initialized = false
    private static var waiters: [UUID: (Int) -> Void] = [:]
    private let appKey: String
    private let production: Bool
    private let presenter: () -> UIViewController?
    private let uiConfig: () -> JVUIConfig
    private let configureBeforeInit: () -> Void
    private var pending: Completion?
    private var timer: DispatchWorkItem?
    private var generation = 0
    private var consent = false
    private var closed = false
    private var loginActive = false
    private let waiterID = UUID()

    /// Main 使用；宿主提供可见 presenter、品牌 UI 和首次初始化前的采集开关。production 由发布环境决定。
    public init(appKey: String, production: Bool,
                presenter: @escaping () -> UIViewController?,
                uiConfig: @escaping () -> JVUIConfig,
                configureBeforeInit: @escaping () -> Void = {}) {
        precondition(!appKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        self.appKey = appKey
        self.production = production
        self.presenter = presenter
        self.uiConfig = uiConfig
        self.configureBeforeInit = configureBeforeInit
    }

    /// 明确同意后初始化并预取号；不展示授权页。
    public func prepare(consentGranted: Bool, completion: @escaping Completion) {
        initialize(consentGranted: consentGranted) { [weak self] result in
            if result.code == 0 || result.code == 8000 { self?.preLogin(completion: completion) }
            else { completion(result) }
        }
    }

    /// 同意之后初始化 SDK；拒绝同意会取消等待并清除缓存，最长等待 10 秒。
    public func initialize(consentGranted: Bool, completion: @escaping Completion) {
        precondition(Thread.isMainThread)
        if !consentGranted { revokeConsent(); completion(reply(-10)); return }
        consent = true
        guard let attempt = begin(timeout: 10, completion: completion) else { return }
        if Self.initialized { finish(reply(8000), attempt: attempt); return }
        Self.waiters[waiterID] = { [weak self] code in
            guard let self = self else { return }
            self.finish(self.reply(code), attempt: attempt)
        }
        if Self.initializing { return }
        Self.initializing = true
        Self.initializedKey = appKey
        configureBeforeInit()
        let config = JVAuthConfig()
        config.appKey = appKey
        config.isProduction = production
        config.timeout = 10_000
        config.authBlock = { result in
            let code = (result["code"] as? NSNumber)?.intValue ?? -1
            DispatchQueue.main.async {
                Self.initializing = false
                Self.initialized = code == 0 || code == 8000
                let callbacks = Array(Self.waiters.values)
                Self.waiters.removeAll()
                callbacks.forEach { $0(code) }
            }
        }
        JVERIFICATIONService.setup(with: config)
    }

    /// 同意与初始化已成功时预取号；不展示页面，最长等待 10 秒。
    public func preLogin(completion: @escaping Completion) {
        guard let attempt = begin(timeout: 10, completion: completion) else { return }
        guard consent && Self.initialized && JVERIFICATIONService.checkVerifyEnable() else {
            finish(reply(-13), attempt: attempt); return
        }
        JVERIFICATIONService.preLogin(10_000) { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.finish(self.reply((result["code"] as? NSNumber)?.intValue ?? -1), attempt: attempt)
            }
        }
    }

    /// Main 调用；先 initialize/prepare，授权最多等待 120 秒，opened 仅通知一次且不结束等待。
    public func authenticate(consentGranted: Bool, opened: @escaping () -> Void = {}, completion: @escaping Completion) {
        precondition(Thread.isMainThread)
        if !consentGranted { revokeConsent(); completion(reply(-10)); return }
        guard let attempt = begin(timeout: 120, completion: completion) else { return }
        guard consent && Self.initialized && JVERIFICATIONService.checkVerifyEnable(),
              let controller = presenter(), controller.viewIfLoaded?.window != nil else {
            finish(reply(-13), attempt: attempt); return
        }
        JVERIFICATIONService.customUI(with: uiConfig())
        loginActive = true
        var didOpen = false
        JVERIFICATIONService.getAuthorizationWith(controller, hide: true, animated: true,
            timeout: 15_000, completion: { [weak self] result in
                DispatchQueue.main.async {
                    guard let self = self, self.generation == attempt else { return }
                    self.loginActive = false
                    let code = (result["code"] as? NSNumber)?.intValue ?? -1
                    let token = result["loginToken"] as? String
                    self.finish(JVerificationReply(code: code,
                        token: code == 6000 && token?.isEmpty == false ? token : nil,
                        carrier: result["operator"] as? String), attempt: attempt)
                }
            }, actionBlock: { [weak self] type, _ in
                DispatchQueue.main.async {
                    guard let self = self, !self.closed, self.consent, self.generation == attempt,
                          self.pending != nil, self.loginActive else { return }
                    if type == 1 { self.loginActive = false }
                    if type == 2 && !didOpen { didOpen = true; opened() }
                }
            })
    }

    /// Main 上终止等待、取消定时器并关闭本实例授权页；迟到 SDK 结果不会再交付。
    public func cancel() {
        precondition(Thread.isMainThread)
        generation += 1
        Self.waiters.removeValue(forKey: waiterID)
        timer?.cancel(); timer = nil
        if Self.owner === self && loginActive {
            JVERIFICATIONService.dismissLoginController(animated: false, completion: {})
        }
        loginActive = false
        let callback = pending; pending = nil
        callback?(reply(6002))
    }

    /// Main 上仅清除本实例拥有的进程 SDK 缓存。
    public func clearPreLoginCache() {
        precondition(Thread.isMainThread)
        if Self.owner === self { JVERIFICATIONService.clearPreLoginCache() }
    }

    /// Main 撤销同意、取消等待并清空预取号缓存。
    public func revokeConsent() { consent = false; cancel(); clearPreLoginCache() }
    /// Main 永久关闭并让出进程所有权；不可重开。
    public func close() {
        precondition(Thread.isMainThread)
        closed = true; revokeConsent()
        if Self.owner === self { Self.owner = nil }
    }

    private func begin(timeout: TimeInterval, completion: @escaping Completion) -> Int? {
        precondition(Thread.isMainThread)
        if closed { completion(reply(-12)); return nil }
        if !consent { completion(reply(-10)); return nil }
        if pending != nil || (Self.owner != nil && Self.owner !== self) ||
            (Self.initializedKey != nil && Self.initializedKey != appKey) {
            completion(reply(-11)); return nil
        }
        Self.owner = self
        pending = completion
        generation += 1
        let attempt = generation
        let work = DispatchWorkItem { [weak self] in
            guard let self = self, self.generation == attempt else { return }
            let callback = self.pending; self.pending = nil
            self.cancel()
            callback?(self.reply(-14))
        }
        timer = work
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: work)
        return attempt
    }

    private func finish(_ result: JVerificationReply, attempt: Int) {
        guard !closed && consent && generation == attempt else { return }
        timer?.cancel(); timer = nil
        let callback = pending; pending = nil
        callback?(result)
    }

    private func reply(_ code: Int) -> JVerificationReply { JVerificationReply(code: code, token: nil, carrier: nil) }
}
