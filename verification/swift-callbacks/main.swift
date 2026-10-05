import Foundation
import UIKit

let presenter = UIViewController()
let client = JVerificationNativeClient(appKey: "key", production: false,
    presenter: { presenter }, uiConfig: { JVUIConfig() })
func drain() { RunLoop.main.run(until: Date().addingTimeInterval(0.02)) }
var initialized = false
client.initialize(consentGranted: true) { initialized = $0.code == 8000 }
drain()
precondition(initialized)
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
client.close()
print("Swift authentication callback: blank token rejected, valid token preserved")
