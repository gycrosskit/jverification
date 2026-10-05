package com.tencent.kuikly.core.nvi.serialization.json

/** 仅替代容器，不复制模块结果转换。 */
class JSONObject {
    private val values = mutableMapOf<String, Any>()
    fun put(key: String, value: Any) { values[key] = value }
    fun optString(key: String) = values[key] as? String ?: ""
    fun optInt(key: String) = values[key] as? Int ?: 0
    override fun toString() = values.toString()
}
