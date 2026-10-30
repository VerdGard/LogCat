package com.solosu.logcat.mcp

import com.solosu.logcat.LogCatApplication
import com.solosu.logcat.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简 MCP (Model Context Protocol) 服务器:JSON-RPC 2.0 over HTTP。
 *
 * 只用 JDK 的 ServerSocket 手写 HTTP/1.1,避免引入 Ktor/NanoHTTPD 等依赖。
 * 支持:
 *  - POST /            单条 JSON-RPC 请求
 *  - POST /mcp         SSE 之外的普通 JSON 响应(与 / 等价)
 *  - GET  /health      健康检查
 *  - 可选 Bearer Token 鉴权(settings.mcpToken)
 *
 * 线程模型:每个连接一个协程,读超时 5s,请求体上限 1MB。
 */
class McpServer(private val app: LogCatApplication) {

    private val tools = McpTools(app)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var boundPort: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean get() = running.get() && serverSocket?.isClosed == false

    fun start(port: Int): Result<Unit> {
        if (isRunning) return Result.success(Unit)
        return runCatching {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", port))
            serverSocket = socket
            boundPort = socket.localPort
            running.set(true)
            lastError = null
            acceptJob = scope.launch { acceptLoop(socket) }
            AppLog.info("MCP 服务已启动:http://127.0.0.1:$boundPort/")
        }.onFailure { throwable ->
            lastError = throwable.message ?: throwable.javaClass.simpleName
            AppLog.error("MCP 启动失败:$lastError")
            runCatching { serverSocket?.close() }
            serverSocket = null
            running.set(false)
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        AppLog.info("MCP 服务已停止")
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (io: IOException) {
                break
            }
            scope.launch { handleClient(client) }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = HttpRequest.read(input) ?: return
                val response = route(request)
                writeResponse(output, response)
                output.flush()
            }.onFailure { throwable ->
                AppLog.warn("MCP 处理连接异常:${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }
    }

    private fun route(request: HttpRequest): HttpResponse {
        if (request.method == "GET" && request.path.startsWith("/health")) {
            return HttpResponse.json(
                200,
                JSONObject()
                    .put("ok", true)
                    .put("name", "logcat-mcp")
                    .put("port", boundPort)
                    .put("tools", tools.all().size)
                    .put("running", app.logRepository.running)
                    .toString()
            )
        }

        if (request.method == "OPTIONS") {
            return HttpResponse(204, "No Content", emptyMap(), ByteArray(0))
        }

        if (request.path != "/" && request.path != "/mcp") {
            return HttpResponse.json(404, rpcError(null, RpcError.INVALID_REQUEST, "未知路径 ${request.path}").toString())
        }

        if (request.method != "POST") {
            return HttpResponse.json(405, rpcError(null, RpcError.INVALID_REQUEST, "仅支持 POST").toString())
        }

        val token = app.settingsState.value.mcpToken
        if (token.isNotEmpty()) {
            val header = request.headers["authorization"].orEmpty()
            val provided = header.removePrefix("Bearer ").removePrefix("bearer ").trim()
            if (provided != token) {
                return HttpResponse.json(
                    401,
                    rpcError(null, RpcError.INVALID_REQUEST, "Token 校验失败").toString()
                )
            }
        }

        val body = request.body.toString(Charsets.UTF_8)
        if (body.isBlank()) {
            return HttpResponse.json(400, rpcError(null, RpcError.PARSE_ERROR, "空请求体").toString())
        }

        val payload = runCatching { JSONObject(body) }.getOrElse {
            return HttpResponse.json(200, rpcError(null, RpcError.PARSE_ERROR, "JSON 解析失败").toString())
        }
        val response = dispatch(payload)
        // 通知类请求(无 id)按规范返回 202 空体
        if (response == null) return HttpResponse(202, "Accepted", emptyMap(), ByteArray(0))
        return HttpResponse.json(200, response.toString())
    }

    /** 返回 null 表示无需响应(notification)。 */
    private fun dispatch(request: JSONObject): JSONObject? {
        val id = request.opt("id")
        val hasId = request.has("id")
        val method = request.optString("method")

        if (!hasId) return null

        return try {
            when (method) {
                "initialize" -> rpcResult(id, initializeResult())
                "ping" -> rpcResult(id, JSONObject())
                "tools/list" -> rpcResult(id, JSONObject().put("tools", toolsJson()))
                "tools/call" -> rpcResult(id, callTool(request.optJSONObject("params") ?: JSONObject()))
                "resources/list" -> rpcResult(id, JSONObject().put("resources", JSONArray()))
                "prompts/list" -> rpcResult(id, JSONObject().put("prompts", JSONArray()))
                else -> rpcError(id, RpcError.METHOD_NOT_FOUND, "未实现的方法:$method")
            }
        } catch (mcp: McpException) {
            rpcError(id, mcp.code, mcp.message ?: "参数错误")
        } catch (throwable: Throwable) {
            AppLog.error("MCP 工具调用异常:${throwable.message}")
            rpcError(id, RpcError.INTERNAL_ERROR, throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    private fun initializeResult(): JSONObject = JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION)
        .put(
            "capabilities",
            JSONObject().put("tools", JSONObject().put("listChanged", false))
        )
        .put(
            "serverInfo",
            JSONObject()
                .put("name", "logcat-mcp")
                .put("version", "1.0.0")
        )
        .put(
            "instructions",
            "LogCat 的 MCP 服务。用 logcat_query 检索日志,crash_list/crash_detail 查看崩溃," +
                "settings_set 调整采集参数。需要完整系统日志时,先用 settings_set 把 mode 设为 root 或 shizuku。"
        )

    private fun toolsJson(): JSONArray {
        val array = JSONArray()
        tools.all().forEach { spec ->
            array.put(
                JSONObject()
                    .put("name", spec.name)
                    .put("title", spec.title)
                    .put("description", spec.description)
                    .put("inputSchema", spec.inputSchema)
            )
        }
        return array
    }

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.optString("name").takeIf { it.isNotBlank() }
            ?: throw McpException(RpcError.INVALID_PARAMS, "缺少工具名 name")
        val spec = tools.all().firstOrNull { it.name == name }
            ?: throw McpException(RpcError.INVALID_PARAMS, "未知工具:$name")
        return spec.handler(params.optJSONObject("arguments") ?: JSONObject())
    }

    // ---------- HTTP ----------

    private class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        companion object {
            fun read(input: InputStream): HttpRequest? {
                val headerBuffer = StringBuilder()
                while (!headerBuffer.endsWith("\r\n\r\n")) {
                    val b = input.read()
                    if (b == -1) return null
                    headerBuffer.append(b.toChar())
                    if (headerBuffer.length > MAX_HEADER_BYTES) return null
                }
                val lines = headerBuffer.toString().split("\r\n").filter { it.isNotEmpty() }
                if (lines.isEmpty()) return null
                val requestLine = lines.first().split(" ")
                if (requestLine.size < 2) return null
                val headers = lines.drop(1).mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) null
                    else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
                }.toMap()
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length > MAX_BODY_BYTES) return null
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = input.read(body, read, length - read)
                    if (count == -1) break
                    read += count
                }
                return HttpRequest(
                    method = requestLine[0].uppercase(),
                    path = requestLine[1].substringBefore('?'),
                    headers = headers,
                    body = if (read == length) body else body.copyOf(read),
                )
            }
        }
    }

    private class HttpResponse(
        val status: Int,
        val reason: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        companion object {
            fun json(status: Int, text: String): HttpResponse {
                val bytes = text.toByteArray(Charsets.UTF_8)
                return HttpResponse(
                    status = status,
                    reason = if (status == 200) "OK" else "Error",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Headers" to "content-type, authorization",
                        "Access-Control-Allow-Methods" to "POST, GET, OPTIONS",
                    ),
                    body = bytes,
                )
            }
        }
    }

    private fun writeResponse(output: BufferedOutputStream, response: HttpResponse) {
        val head = StringBuilder()
        head.append("HTTP/1.1 ${response.status} ${response.reason}\r\n")
        response.headers.forEach { (key, value) -> head.append("$key: $value\r\n") }
        head.append("Content-Length: ${response.body.size}\r\n")
        head.append("Connection: close\r\n\r\n")
        output.write(head.toString().toByteArray(Charsets.UTF_8))
        output.write(response.body)
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        const val SOCKET_TIMEOUT_MS = 5000
        const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}
