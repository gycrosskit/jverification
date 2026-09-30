package io.github.gycrosskit.jverification.kuikly

import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.jverification.JVerificationDriver
import io.github.gycrosskit.jverification.VerificationResult
import io.github.gycrosskit.jverification.VerificationStatus

/** 与页面同生命周期；宿主销毁页面时关闭 client，再 dispose 模块回调。 */
class JVerificationModule : Module(), JVerificationDriver {
    private val callbacks = mutableListOf<CallbackRef>()
    private var disposed = false
    override fun moduleName(): String = NAME

    override fun initialize(callback: (VerificationResult) -> Unit) = invoke("initialize", callback)
    override fun preLogin(callback: (VerificationResult) -> Unit) = invoke("preLogin", callback)
    override fun authenticate(callback: (VerificationResult) -> Unit) = invoke("authenticate", callback)
    override fun cancel() { send("cancel"); disposeCallbacks() }
    override fun clearPreLoginCache() = send("clearCache")
    override fun close() { send("close"); dispose() }

    private fun invoke(method: String, callback: (VerificationResult) -> Unit) {
        if (disposed) { callback(VerificationResult(VerificationStatus.CLOSED)); return }
        var reference: CallbackRef? = null
        var completed = false
        reference = toNative(false, method, JSONObject().apply { put("consentGranted", true) }.toString(), { reply ->
            completed = true
            reference?.let { callbacks.remove(it); removeCallback(it) }
            if (!disposed) {
                var status = VerificationStatus.entries.firstOrNull { it.name == reply?.optString("status") } ?: VerificationStatus.FAILED
                val token = reply?.optString("token")
                if (status == VerificationStatus.TOKEN && token.isNullOrBlank()) status = VerificationStatus.FAILED
                callback(VerificationResult(status, reply?.optInt("code") ?: -1,
                    token.takeIf { status == VerificationStatus.TOKEN }, reply?.optString("carrier")))
            }
        }, false).callbackRef
        if (completed) reference?.let(::removeCallback) else reference?.let(callbacks::add)
    }

    private fun send(method: String) { if (!disposed) toNative(false, method, "{}", null, false) }
    private fun disposeCallbacks() { callbacks.toList().forEach(::removeCallback); callbacks.clear() }
    fun dispose() { if (!disposed) { send("close"); disposed = true; disposeCallbacks() } }
    companion object { const val NAME = "GycJVerificationModule" }
}
