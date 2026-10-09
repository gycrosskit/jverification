import Foundation
public struct JVerificationReply { public let code: Int; public let token: String?; public let carrier: String? }
public final class JVerificationNativeClient {
    public typealias Completion = (JVerificationReply) -> Void
    var reply: Completion?; var opened: (() -> Void)?; var closes = 0; var cancels = 0; var starts = 0
    public func initialize(consentGranted: Bool, completion: @escaping Completion) { precondition(Thread.isMainThread); starts += 1; reply = completion }
    public func preLogin(completion: @escaping Completion) { precondition(Thread.isMainThread); reply = completion }
    public func authenticate(consentGranted: Bool, opened: @escaping () -> Void, completion: @escaping Completion) { precondition(Thread.isMainThread); starts += 1; self.opened = opened; reply = completion }
    public func cancel() { precondition(Thread.isMainThread); cancels += 1 }
    public func revokeConsent() { cancel() }
    public func close() { precondition(Thread.isMainThread); closes += 1 }
}
