import Foundation
import UIKit

public final class JVUIConfig {}
final class JVAuthConfig {
    var appKey = ""
    var isProduction = false
    var timeout = 0
    var authBlock: (([String: Any]) -> Void)?
}
enum JVERIFICATIONService {
    static var reply: [String: Any] = [:]
    static var setupCalls = 0
    static func setup(with config: JVAuthConfig) { setupCalls += 1; config.authBlock?(["code": 8000]) }
    static func checkVerifyEnable() -> Bool { true }
    static func preLogin(_ timeout: Int, completion: ([String: Any]) -> Void) { completion(["code": 7000]) }
    static func customUI(with config: JVUIConfig) {}
    static func getAuthorizationWith(_ controller: UIViewController, hide: Bool, animated: Bool,
                                    timeout: Int, completion: ([String: Any]) -> Void,
                                    actionBlock: (Int, [String: Any]) -> Void) { completion(reply) }
    static func dismissLoginController(animated: Bool, completion: () -> Void) { completion() }
    static func clearPreLoginCache() {}
}
