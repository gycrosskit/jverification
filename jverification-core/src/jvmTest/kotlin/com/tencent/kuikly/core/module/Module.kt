package com.tencent.kuikly.core.module

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

typealias CallbackRef = Int

/** 仅截获传输并保留迟到闭包；生命周期逻辑来自生产模块。 */
abstract class Module {
    data class ReturnValue(val callbackRef: CallbackRef?)
    data class Call(val method: String, val deliver: (JSONObject?) -> Unit)
    val calls = mutableListOf<Call>()
    val liveCallbacks = mutableSetOf<CallbackRef>()
    private var sequence = 0
    abstract fun moduleName(): String
    fun toNative(keepCallbackAlive: Boolean, method: String, params: Any?, callback: ((JSONObject?) -> Unit)?, syncCall: Boolean): ReturnValue {
        val ref = callback?.let { ++sequence }
        ref?.let(liveCallbacks::add)
        calls += Call(method) { callback?.invoke(it) }
        return ReturnValue(ref)
    }
    fun removeCallback(ref: CallbackRef) { liveCallbacks.remove(ref) }
}
