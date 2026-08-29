package com.example.localai.fixtures

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 本地 HTTP Fixture 服务器（JVM 单测用，纯 java.base 的 ServerSocket 实现，
 * 避免 com.sun.net.httpserver 的 JDK 模块依赖）。
 * 能力：静态资产、动态文件载荷（可变内容/ETag）、单段 Range/If-Range 语义、
 * 故障注入（连接截断 dropAfter、分块限速 throttleMs）与请求记录（供断言）。
 */
class FixtureHttpServer : Closeable {

    class RequestRecord(
        @JvmField val method: String,
        @JvmField val path: String,
        @JvmField val range: String?,
        @JvmField val ifRange: String?,
        @JvmField val responseCode: Int
    )

    private class Entry(var content: ByteArray, var etag: String) {
        var throttleMs: Int = 0
    }

    private val serverSocket: ServerSocket
    private val acceptThread: Thread

    @Volatile
    private var closed = false

    private val assets = ConcurrentHashMap<String, ByteArray>()
    private val files = ConcurrentHashMap<String, Entry>()
    private val dropAfter = ConcurrentHashMap<String, Int>()
    private val requests = CopyOnWriteArrayList<RequestRecord>()

    init {
        serverSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        acceptThread = Thread({ acceptLoop() }, "fixture-http")
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    fun port(): Int = serverSocket.localPort

    fun baseUrl(): String = "http://127.0.0.1:" + port()

    fun asset(path: String, bytes: ByteArray) {
        assets[path] = bytes
    }

    /** 读取当前资产字节（供篡改负例使用），返回副本。 */
    fun assetBytes(path: String): ByteArray? {
        val bytes = assets[path] ?: return null
        return bytes.clone()
    }

    fun file(path: String, bytes: ByteArray) {
        setFile(path, bytes, "\"e-" + Integer.toHexString(java.util.Arrays.hashCode(bytes)) + "\"")
    }

    fun setFile(path: String, bytes: ByteArray, etag: String) {
        files[path] = Entry(bytes, etag)
    }

    /** 响应体写满 bodyBytes 字节后强制断开连接（模拟网络中断）。 */
    fun dropAfter(path: String, bodyBytes: Int) {
        dropAfter[path] = bodyBytes
    }

    fun clearDrop(path: String) {
        dropAfter.remove(path)
    }

    /** 每 chunk 写入后延迟 N 毫秒（拖慢下载以便测试暂停/恢复）。 */
    fun throttleMs(path: String, ms: Int) {
        val entry = files[path]
        if (entry != null) {
            entry.throttleMs = ms
        }
    }

    fun requests(): List<RequestRecord> = ArrayList(requests)

    fun lastRequest(path: String): RequestRecord? {
        val all = requests()
        for (i in all.size - 1 downTo 0) {
            if (all[i].path == path) {
                return all[i]
            }
        }
        return null
    }

    fun reset() {
        requests.clear()
        dropAfter.clear()
    }

    private fun acceptLoop() {
        while (!closed) {
            try {
                val socket = serverSocket.accept()
                val thread = Thread({ handle(socket) }, "fixture-conn")
                thread.isDaemon = true
                thread.start()
            } catch (e: IOException) {
                if (closed) {
                    return
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val headers = HashMap<String, String>()
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
                line = reader.readLine()
            }

            val parts = requestLine.split(" ")
            val method = parts[0]
            val path = if (parts.size > 1) parts[1] else "/"
            val range = headers["range"]
            val ifRange = headers["if-range"]

            val out = s.getOutputStream()
            val code: Int
            if (!"GET".equals(method, ignoreCase = true) && !"HEAD".equals(method, ignoreCase = true)) {
                code = respond(out, method, 405, "text/plain",
                    "method not allowed".toByteArray(StandardCharsets.UTF_8))
            } else if (assets.containsKey(path)) {
                code = serveStatic(out, method, path)
            } else if (files.containsKey(path)) {
                code = serveFileWithRange(out, method, path, range, ifRange)
            } else {
                code = respond(out, method, 404, "text/plain",
                    "not found".toByteArray(StandardCharsets.UTF_8))
            }
            requests.add(RequestRecord(method, path, range, ifRange, code))
        }
    }

    private fun serveStatic(out: OutputStream, method: String, path: String): Int {
        val body = assets[path]!!
        val etag = "\"s-" + Integer.toHexString(java.util.Arrays.hashCode(body)) + "\""
        return respond(out, method, 200, "application/json", body,
            arrayOf(arrayOf("ETag", etag)))
    }

    /** 文件服务：Range/If-Range 语义（由 handle 传入解析结果）。 */
    private fun serveFileWithRange(out: OutputStream, method: String, path: String,
                                   range: String?, ifRange: String?): Int {
        val entry = files[path]!!
        val body = entry.content
        var useRange = false
        var start = 0L
        var end = body.size - 1L
        if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            if (ifRange != null && ifRange != entry.etag && ifRange != "\"" + entry.etag + "\"") {
                useRange = false // If-Range 不匹配：按 RFC 返回 200 全量
            } else {
                val spec = range.substring("bytes=".length)
                val dash = spec.indexOf('-')
                start = java.lang.Long.parseLong(spec.substring(0, dash))
                if (dash + 1 < spec.length) {
                    end = java.lang.Long.parseLong(spec.substring(dash + 1))
                }
                if (start >= body.size || end < start) {
                    return respond(out, method, 416, "text/plain", ByteArray(0),
                        arrayOf(arrayOf("Content-Range", "bytes */" + body.size),
                            arrayOf("ETag", entry.etag)))
                }
                end = minOf(end, body.size - 1L)
                useRange = true
            }
        }

        val payload: ByteArray
        val contentLength: Long
        val prefixLength: Int
        val code: Int
        if (useRange) {
            payload = ByteArray((end - start + 1).toInt())
            System.arraycopy(body, start.toInt(), payload, 0, payload.size)
            contentLength = payload.size.toLong()
            prefixLength = start.toInt()
            code = 206
        } else {
            payload = body
            contentLength = body.size.toLong()
            prefixLength = 0
            code = 200
        }

        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(code).append(' ')
            .append(if (code == 206) "Partial Content" else "OK").append("\r\n")
        head.append("Content-Type: application/octet-stream\r\n")
        head.append("Content-Length: ").append(contentLength).append("\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        head.append("ETag: ").append(entry.etag).append("\r\n")
        if (useRange) {
            head.append("Content-Range: bytes ").append(start).append('-').append(end)
                .append('/').append(body.size).append("\r\n")
        }
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()

        if ("HEAD".equals(method, ignoreCase = true)) {
            return code
        }
        val drop = dropAfter[path]
        val chunk = 4096
        var written = 0
        while (written < payload.size) {
            val n = minOf(chunk, payload.size - written)
            out.write(payload, written, n)
            out.flush()
            written += n
            if (drop != null && prefixLength + written >= drop) {
                // 模拟网络中断：不写剩余字节，直接断开
                return code
            }
            if (entry.throttleMs > 0) {
                try {
                    Thread.sleep(entry.throttleMs.toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return code
                }
            }
        }
        return code
    }

    private fun respond(out: OutputStream, method: String, code: Int, contentType: String,
                        body: ByteArray): Int {
        return respond(out, method, code, contentType, body, null)
    }

    private fun respond(out: OutputStream, method: String, code: Int, contentType: String,
                        body: ByteArray, extraHeaders: Array<Array<String>>?): Int {
        val head = StringBuilder()
        val reason = if (code == 404) "Not Found"
        else if (code == 416) "Range Not Satisfiable" else "OK"
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        head.append("Content-Type: ").append(contentType).append("\r\n")
        head.append("Content-Length: ").append(body.size).append("\r\n")
        if (extraHeaders != null) {
            for (h in extraHeaders) {
                head.append(h[0]).append(": ").append(h[1]).append("\r\n")
            }
        }
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(StandardCharsets.ISO_8859_1))
        if (body.isNotEmpty() && !"HEAD".equals(method, ignoreCase = true)) {
            out.write(body)
        }
        out.flush()
        return code
    }

    override fun close() {
        closed = true
        try {
            serverSocket.close()
        } catch (ignored: IOException) {
        }
    }
}
