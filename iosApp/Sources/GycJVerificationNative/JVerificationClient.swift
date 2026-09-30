import Foundation
import UIKit

public struct JVerificationReply: CustomStringConvertible {
    public let code: Int
    public let token: String?
    public let carrier: String?
    public var description: String { "JVerificationReply(code=\(code))" }
}

/** 主线程、进程唯一实例。Token 交给宿主后端，不写日志、不持久化。 */
public final class JVerificationNativeClient {
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

    public func prepare(consentGranted: Bool, completion: @escaping Completion) {
        initialize(consentGranted: consentGranted) { [weak self] result in
            if result.code == 0 || result.code == 8000 { self?.preLogin(completion: completion) }
            else { completion(result) }
        }
    }

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

    /** 原生直接使用时先 initialize/prepare；KMP client 自动先调用 initialize。 */
    public func authenticate(consentGranted: Bool, completion: @escaping Completion) {
        precondition(Thread.isMainThread)
        if !consentGranted { revokeConsent(); completion(reply(-10)); return }
        guard let attempt = begin(timeout: 120, completion: completion) else { return }
        guard consent && Self.initialized && JVERIFICATIONService.checkVerifyEnable(),
              let controller = presenter(), controller.viewIfLoaded?.window != nil else {
            finish(reply(-13), attempt: attempt); return
        }
        JVERIFICATIONService.customUI(with: uiConfig())
        loginActive = true
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
            }, actionBlock: { _, _ in })
    }

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

    public func clearPreLoginCache() {
        precondition(Thread.isMainThread)
        if Self.owner === self { JVERIFICATIONService.clearPreLoginCache() }
    }

    public func revokeConsent() { consent = false; cancel(); clearPreLoginCache() }
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
