import Foundation
import OpenKuiklyIOSRender
let native = JVerificationNativeClient()
var created = 0
JVerificationModule.clientFactory = { precondition(Thread.isMainThread); created += 1; return native }
JVerificationModule.register()
precondition(JVerificationModule.moduleName() == "GycJVerificationModule")
let module = JVerificationModule()
var replies = [[String: Any]]()
_ = module.hrv_call(withMethod: "initialize", params: "{\"consentGranted\":1}", callback: { replies.append($0 as! [String: Any]) })
precondition(created == 0 && replies.last?["status"] as? String == "CONSENT_REQUIRED")
replies.removeAll()
_ = module.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: { replies.append($0 as! [String: Any]) })
precondition(replies.isEmpty && native.starts == 1)
native.opened?(); native.opened?()
precondition(replies.count == 1 && replies[0]["event"] as? String == "opened")
native.reply?(JVerificationReply(code: 6000, token: "token", carrier: nil))
native.reply?(JVerificationReply(code: 6000, token: "late", carrier: nil)); native.opened?()
precondition(replies.count == 2 && replies[1]["token"] as? String == "token")
_ = module.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: { replies.append($0 as! [String: Any]) })
let late = native.reply!
let semaphore = DispatchSemaphore(value: 0)
DispatchQueue.global().async { module.invalidate(); semaphore.signal() }
semaphore.wait()
late(JVerificationReply(code: 6000, token: "destroyed", carrier: nil)); native.opened?()
precondition(replies.count == 2)
RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.03))
precondition(native.closes == 1)
// Repeated invalidation does not close the page client twice.
module.invalidate(); precondition(native.closes == 1)
let queued = JVerificationModule()
let queueDone = DispatchSemaphore(value: 0)
DispatchQueue.global().async {
    _ = queued.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: { _ in preconditionFailure("queued call survived invalidate") })
    queued.invalidate(); queueDone.signal()
}
queueDone.wait(); RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.03))
precondition(created == 1)
let orphan = JVerificationNativeClient(), racing = JVerificationModule()
JVerificationModule.clientFactory = { racing.invalidate(); return orphan }
_ = racing.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: { _ in preconditionFailure("factory race callback") })
precondition(orphan.starts == 0 && orphan.closes == 1)
let dyingNative = JVerificationNativeClient()
JVerificationModule.clientFactory = { dyingNative }
var dying: JVerificationModule? = JVerificationModule()
weak var weakDying = dying
_ = dying?.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: nil)
let deallocDone = DispatchSemaphore(value: 0)
DispatchQueue.global().async { dying = nil; deallocDone.signal() }
deallocDone.wait()
precondition(weakDying == nil)
RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.03))
precondition(dyingNative.closes == 1)
// ContextQueue invalidation boundary.
let contextNative = JVerificationNativeClient(), contextModule = JVerificationModule()
JVerificationModule.clientFactory = { contextNative }
var contextReplies = 0
KuiklyRenderThreadManager.paused = true
_ = contextModule.hrv_call(withMethod: "authenticate", params: "{\"consentGranted\":true}", callback: { _ in contextReplies += 1 })
contextNative.reply?(JVerificationReply(code: 6000, token: "queued", carrier: nil))
contextModule.invalidate()
KuiklyRenderThreadManager.paused = false; KuiklyRenderThreadManager.drain()
precondition(contextReplies == 0 && contextNative.closes == 1)
// Accepted READY must survive a subsequent legal request while ContextQueue is blocked.
let consecutiveNative = JVerificationNativeClient(), consecutive = JVerificationModule()
JVerificationModule.clientFactory = { consecutiveNative }
var firstReady = 0, secondReady = 0
KuiklyRenderThreadManager.paused = true
_ = consecutive.hrv_call(withMethod: "initialize", params: "{\"consentGranted\":true}", callback: { _ in firstReady += 1 })
consecutiveNative.reply?(JVerificationReply(code: 8000, token: nil, carrier: nil))
_ = consecutive.hrv_call(withMethod: "preLogin", params: "{\"consentGranted\":true}", callback: { _ in secondReady += 1 })
consecutiveNative.reply?(JVerificationReply(code: 7000, token: nil, carrier: nil))
KuiklyRenderThreadManager.paused = false; KuiklyRenderThreadManager.drain()
precondition(firstReady == 1 && secondReady == 1)
// Explicit cancel still rejects an accepted result queued for ContextQueue.
KuiklyRenderThreadManager.paused = true
_ = consecutive.hrv_call(withMethod: "initialize", params: "{\"consentGranted\":true}", callback: { _ in firstReady += 1 })
consecutiveNative.reply?(JVerificationReply(code: 8000, token: nil, carrier: nil))
_ = consecutive.hrv_call(withMethod: "cancel", params: "{}", callback: nil)
KuiklyRenderThreadManager.paused = false; KuiklyRenderThreadManager.drain()
precondition(firstReady == 1)
consecutive.invalidate()
// End ContextQueue boundary.
print("Production JVerification Swift receiver: consent, opened/terminal, repeated invalidate, queued/factory races and dealloc cleanup passed (SDK/render boundaries stubbed)")
