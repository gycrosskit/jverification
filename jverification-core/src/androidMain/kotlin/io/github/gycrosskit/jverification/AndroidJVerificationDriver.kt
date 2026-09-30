package io.github.gycrosskit.jverification

import android.app.Activity
import android.os.Handler
import android.os.Looper
import cn.jiguang.verifysdk.api.JVerificationConfig
import cn.jiguang.verifysdk.api.JVerificationInterface as SDK
import cn.jiguang.verifysdk.api.JVerifyUIConfig
import cn.jiguang.verifysdk.api.LoginSettings
import cn.jiguang.verifysdk.api.AuthPageEventListener
import cn.jiguang.verifysdk.api.RequestCallback

/** Activity 和授权页配置由宿主提供；构造函数不初始化 SDK。 */
class AndroidJVerificationDriver(
    private val activity: Activity,
    private val appKey: String,
    private val uiConfig: () -> JVerifyUIConfig,
    private val configureBeforeInit: () -> Unit = {},
) : JVerificationDriver {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var generation = 0L
    private var loginActive = false

    init { require(appKey.isNotBlank()) }

    private fun claim(callback: (VerificationResult) -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Use the main thread" }
        if (closed) { callback(VerificationResult(VerificationStatus.CLOSED)); return false }
        if ((owner != null && owner !== this) || (initializedKey != null && initializedKey != appKey)) {
            callback(VerificationResult(VerificationStatus.BUSY)); return false
        }
        owner = this
        return true
    }

    override fun initialize(callback: (VerificationResult) -> Unit) {
        if (!claim(callback)) return
        val attempt = generation
        val deliver: (VerificationResult) -> Unit = { result ->
            if (!closed && attempt == generation) callback(result)
        }
        if (SDK.isInitSuccess()) { deliver(VerificationResult(VerificationStatus.READY)); return }
        initWaiters += deliver
        if (initializing) return
        initializing = true
        initializedKey = appKey
        try {
            configureBeforeInit()
            val config = JVerificationConfig().apply { setjAppKey(appKey) }
            SDK.init(activity.applicationContext, 10_000, config, object : RequestCallback<String> {
                override fun onResult(code: Int, result: String?) {
                    main.post { finishInit(code) }
                }
            })
        } catch (_: Exception) { finishInit(-1) }
    }

    override fun preLogin(callback: (VerificationResult) -> Unit) {
        if (!claim(callback)) return
        if (!SDK.checkVerifyEnable(activity)) { callback(VerificationResult(VerificationStatus.UNSUPPORTED)); return }
        val attempt = generation
        SDK.preLogin(activity, 10_000) { code, _, _ -> main.post {
            if (!closed && attempt == generation) callback(VerificationResult(
                if (code == 7000) VerificationStatus.READY else VerificationStatus.FAILED, code))
        } }
    }

    override fun authenticate(opened: () -> Unit, callback: (VerificationResult) -> Unit) {
        if (!claim(callback)) return
        if (activity.isFinishing || activity.isDestroyed || !SDK.checkVerifyEnable(activity)) {
            callback(VerificationResult(VerificationStatus.UNSUPPORTED)); return
        }
        val attempt = generation
        SDK.setCustomUIWithConfig(uiConfig())
        loginActive = true
        var didOpen = false
        var finished = false
        val settings = LoginSettings().apply {
            setAutoFinish(true)
            setTimeout(15_000)
            setAuthPageEventListener(object : AuthPageEventListener() {
                override fun onEvent(cmd: Int, msg: String?) { main.post {
                    if (!closed && attempt == generation && !finished && loginActive) {
                        if (cmd == 1) loginActive = false
                        if (cmd == 2 && !didOpen) { didOpen = true; opened() }
                    }
                } }
            })
        }
        SDK.loginAuth(activity, settings) { code, content, carrier, _ -> main.post {
            if (!closed && attempt == generation && !finished) {
                finished = true
                loginActive = false
                val status = when {
                    code == 6000 && !content.isNullOrBlank() -> VerificationStatus.TOKEN
                    code == 6002 -> VerificationStatus.CANCELED
                    else -> VerificationStatus.FAILED
                }
                callback(VerificationResult(status, code, content.takeIf { status == VerificationStatus.TOKEN }, carrier))
            }
        } }
    }

    override fun cancel() {
        generation++
        if (owner === this) initWaiters.clear()
        if (owner === this && loginActive) SDK.dismissLoginAuthActivity()
        loginActive = false
    }

    override fun clearPreLoginCache() { if (owner === this) SDK.clearPreLoginCache() }
    override fun close() { cancel(); closed = true; if (owner === this) owner = null }

    private companion object {
        var owner: AndroidJVerificationDriver? = null
        var initializedKey: String? = null
        var initializing = false
        val initWaiters = mutableListOf<(VerificationResult) -> Unit>()
        fun finishInit(code: Int) {
            initializing = false
            val result = VerificationResult(if (code == 0 || code == 8000) VerificationStatus.READY else VerificationStatus.FAILED, code)
            val waiters = initWaiters.toList()
            initWaiters.clear()
            waiters.forEach { it(result) }
        }
    }
}
