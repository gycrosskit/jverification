package cn.jiguang.verifysdk.api

import android.app.Activity

class JVerificationConfig { fun setjAppKey(@Suppress("UNUSED_PARAMETER") key: String) {} }
class JVerifyUIConfig
interface RequestCallback<T> { fun onResult(code: Int, result: T?) }
abstract class AuthPageEventListener { abstract fun onEvent(cmd: Int, msg: String?) }
class LoginSettings {
    var listener: AuthPageEventListener? = null
    fun setAutoFinish(@Suppress("UNUSED_PARAMETER") value: Boolean) {}
    fun setTimeout(@Suppress("UNUSED_PARAMETER") value: Int) {}
    fun setAuthPageEventListener(value: AuthPageEventListener) { listener = value }
}

/** 仅替代 SDK I/O；generation、owner、waiter、终态逻辑均来自生产 driver。 */
object JVerificationInterface {
    var initialized = false
    var enabled = true
    var initCalls = 0
    var loginCalls = 0
    var dismissCalls = 0
    var clearCalls = 0
    var initCallback: RequestCallback<String>? = null
    var preLoginCallback: ((Int, String?, String?) -> Unit)? = null
    var loginCallback: ((Int, String?, String?, String?) -> Unit)? = null
    var settings: LoginSettings? = null
    fun reset() {
        initialized = false; enabled = true
        initCalls = 0; loginCalls = 0; dismissCalls = 0; clearCalls = 0
        initCallback = null; preLoginCallback = null; loginCallback = null; settings = null
    }
    fun isInitSuccess() = initialized
    fun checkVerifyEnable(@Suppress("UNUSED_PARAMETER") activity: Activity) = enabled
    fun init(@Suppress("UNUSED_PARAMETER") context: Activity, @Suppress("UNUSED_PARAMETER") timeout: Int,
             @Suppress("UNUSED_PARAMETER") config: JVerificationConfig, callback: RequestCallback<String>) {
        initCalls++; initCallback = callback
    }
    fun preLogin(@Suppress("UNUSED_PARAMETER") activity: Activity, @Suppress("UNUSED_PARAMETER") timeout: Int,
                 callback: (Int, String?, String?) -> Unit) { preLoginCallback = callback }
    fun setCustomUIWithConfig(@Suppress("UNUSED_PARAMETER") config: JVerifyUIConfig) {}
    fun loginAuth(@Suppress("UNUSED_PARAMETER") activity: Activity, value: LoginSettings,
                  callback: (Int, String?, String?, String?) -> Unit) {
        loginCalls++; settings = value; loginCallback = callback
    }
    fun dismissLoginAuthActivity() { dismissCalls++ }
    fun clearPreLoginCache() { clearCalls++ }
}
