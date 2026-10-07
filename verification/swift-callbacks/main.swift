import Foundation
import UIKit

let presenter = UIViewController()
let client = JVerificationNativeClient(appKey: "key", production: false,
    presenter: { presenter }, uiConfig: { JVUIConfig() })
func drain() { RunLoop.main.run(until: Date().addingTimeInterval(0.02)) }
var denied = false
client.authenticate(consentGranted: false) { denied = $0.code == -10 }
precondition(denied && JVERIFICATIONService.setupCalls == 0)
JVERIFICATIONService.reply = ["code": 6000, "loginToken": "direct-token"]
var authenticated = false
client.authenticate(consentGranted: true) { authenticated = $0.token == "direct-token" }
drain()
precondition(authenticated && JVERIFICATIONService.setupCalls == 1,
    "Direct authenticate must initialize before authorization without a host wrapper")
client.revokeConsent()
authenticated = false
client.authenticate(consentGranted: true) { authenticated = $0.token == "direct-token" }
drain()
precondition(authenticated && JVERIFICATIONService.setupCalls == 1,
    "Explicit consent must allow retry without another SDK setup")
for token in ["", " \t\n", "\u{00a0}\u{2003}", " token "] {
    JVERIFICATIONService.reply = ["code": 6000, "loginToken": token]
    var received = false
    client.authenticate(consentGranted: true) {
        received = true
        precondition($0.token == (token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : token))
        precondition(!$0.description.contains("token"))
    }
    drain()
    precondition(received)
}
JVERIFICATIONService.reply = ["code": 6001, "loginToken": "secret"]
client.authenticate(consentGranted: true) { precondition($0.token == nil) }
drain()
JVERIFICATIONService.reply = ["code": 6000, "loginToken": "late-token"]
var canceledResults: [Int] = []
client.authenticate(consentGranted: true) { canceledResults.append($0.code) }
var busyCode: Int?
client.authenticate(consentGranted: true) { busyCode = $0.code }
precondition(busyCode == -11)
client.cancel()
drain()
precondition(canceledResults == [6002], "Cancellation must finish once and reject the queued token")
client.close()
var closedCode: Int?
client.authenticate(consentGranted: true) { closedCode = $0.code }
precondition(closedCode == -12)
print("Swift authentication initialization, consent retry, BUSY, cancel, late token, close and token policy passed")
