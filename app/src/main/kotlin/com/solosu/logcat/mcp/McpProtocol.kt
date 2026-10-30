package com.solosu.logcat.mcp

import org.json.JSONArray
import org.json.JSONObject

/** JSON-RPC 2.0 错误码。 */
object RpcError {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

class McpException(val code: Int, message: String) : Exception(message)

fun rpcResult(id: Any?, result: Any): JSONObject = JSONObject()
    .put("jsonrpc", "2.0")
    .put("id", id)
    .put("result", result)

fun rpcError(id: Any?, code: Int, message: String): JSONObject = JSONObject()
    .put("jsonrpc", "2.0")
    .put("id", id)
    .put("error", JSONObject().put("code", code).put("message", message))

/** 解析工具调用参数,缺省返回空对象。 */
fun JSONObject.arguments(): JSONObject = optJSONObject("arguments") ?: JSONObject()

fun JSONObject.requireString(name: String): String =
    optString(name).takeIf { it.isNotBlank() } ?: throw McpException(RpcError.INVALID_PARAMS, "缺少参数 $name")

fun JSONObject.optStringOrNull(name: String): String? = optString(name).takeIf { it.isNotBlank() }

/** MCP tool 描述。 */
class ToolSpec(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: JSONObject,
    val handler: (JSONObject) -> JSONObject,
)

internal fun schema(
    properties: Map<String, JSONObject>,
    required: List<String> = emptyList(),
): JSONObject {
    val props = JSONObject()
    properties.forEach { (key, value) -> props.put(key, value) }
    return JSONObject()
        .put("type", "object")
        .put("properties", props)
        .put("required", JSONArray(required))
        .put("additionalProperties", false)
}

internal fun prop(type: String, description: String, enumValues: List<String>? = null, default: Any? = null): JSONObject =
    JSONObject().apply {
        put("type", type)
        put("description", description)
        enumValues?.let { put("enum", JSONArray(it)) }
        default?.let { put("default", it) }
    }

internal fun propInt(description: String, default: Int? = null): JSONObject =
    JSONObject().apply {
        put("type", "integer")
        put("description", description)
        default?.let { put("default", it) }
    }

internal fun propBool(description: String, default: Boolean? = null): JSONObject =
    JSONObject().apply {
        put("type", "boolean")
        put("description", description)
        default?.let { put("default", it) }
    }

internal fun textContent(text: String): JSONObject = JSONObject()
    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

internal fun textContent(json: JSONObject): JSONObject = textContent(json.toString(2))
