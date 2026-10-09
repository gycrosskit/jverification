package io.github.gycrosskit.jverification.kuikly

import android.os.Handler
import android.os.Looper
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import io.github.gycrosskit.jverification.JVerificationDriver
import io.github.gycrosskit.jverification.VerificationResult
import io.github.gycrosskit.jverification.VerificationStatus
import org.json.JSONObject

/** 每 Renderer 创建；factory 在 Main 提供页面 driver，SDK 的进程所有权仍由原 driver 管理。 */
class AndroidJVerificationModule(private val driverFactory: () -> JVerificationDriver) : KuiklyRenderBaseModule() {
    private val main = Handler(Looper.getMainLooper())
    private var driver: JVerificationDriver? = null
    @Volatile private var destroyed = false
    private var closed = false
    private var generation = 0L
    private var pending = false
    private var consent = false

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        onMain {
            if (destroyed || closed) return@onMain
            val args = runCatching { JSONObject(params ?: "{}") }.getOrNull()
            if (args == null) { callback?.invoke(reply(VerificationResult(VerificationStatus.FAILED, -1))); return@onMain }
            if (method in listOf("cancel", "clearCache", "close")) {
                generation++; pending = false
                driver?.cancel()
                if (method == "clearCache" || method == "close") { consent = false; driver?.clearPreLoginCache() }
                if (method == "close") { closed = true; driver?.close(); driver = null }
                return@onMain
            }
            if (method !in listOf("initialize", "preLogin", "authenticate")) {
                callback?.invoke(reply(VerificationResult(VerificationStatus.UNSUPPORTED))); return@onMain
            }
            if (args.opt("consentGranted") != true || (method == "preLogin" && !consent)) {
                generation++; pending = false; consent = false
                driver?.cancel(); driver?.clearPreLoginCache()
                callback?.invoke(reply(VerificationResult(VerificationStatus.CONSENT_REQUIRED))); return@onMain
            }
            if (pending) { callback?.invoke(reply(VerificationResult(VerificationStatus.BUSY))); return@onMain }
            consent = true
            val native = driver ?: runCatching(driverFactory).getOrNull()
            // 宿主 factory 可同步销毁 Renderer；尚未交给本页的 driver 仍必须关闭。
            if (destroyed || closed) {
                if (native !== driver) native?.close()
                return@onMain
            }
            if (native == null) { callback?.invoke(reply(VerificationResult(VerificationStatus.UNSUPPORTED))); return@onMain }
            driver = native
            pending = true
            val attempt = ++generation
            var didOpen = false
            val complete: (VerificationResult) -> Unit = { result -> onMain {
                if (!destroyed && !closed && pending && attempt == generation) {
                    pending = false
                    callback?.invoke(reply(result))
                }
            } }
            try {
                when (method) {
                    "initialize" -> native.initialize(complete)
                    "preLogin" -> native.preLogin(complete)
                    else -> native.authenticate({ onMain {
                        if (!destroyed && !closed && pending && attempt == generation && !didOpen) {
                            didOpen = true; callback?.invoke(JSONObject().put("event", "opened").toString())
                        }
                    } }, complete)
                }
            } catch (_: Exception) { complete(VerificationResult(VerificationStatus.FAILED, -1)) }
        }
        return null
    }

    override fun onDestroy() {
        destroyed = true
        onMain { generation++; pending = false; driver?.close(); driver = null }
        super.onDestroy()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }
    private fun reply(result: VerificationResult): String = JSONObject().apply {
        put("status", result.status.name); put("code", result.vendorCode)
        result.token?.let { put("token", it) }; result.carrier?.let { put("carrier", it) }
    }.toString()
    companion object { const val NAME = "GycJVerificationModule" }
}

/** 当前 Renderer 的 registerExternalModule 使用此工厂，每次返回新的页面 driver。 */
fun IKuiklyRenderExport.registerJVerificationModule(driverFactory: () -> JVerificationDriver) {
    moduleExport(AndroidJVerificationModule.NAME) { AndroidJVerificationModule(driverFactory) }
}
