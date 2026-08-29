package com.example.localai.feature.download

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * 下载后校验（S016 校验链中的 SHA-256 + GGUF 元数据探针）。
 * GGUF 头部：magic(4B "GGUF") + version(u32 LE) + tensor_count(u64) + kv_count(u64) + KV 对。
 * 探针只验证结构合法性并提取 general.architecture（字符串类型），不做完整解析；
 * 恶意/损坏头部（超限 KV 数、超长键、越界读取）一律拒绝。
 */
object ModelVerifier {

    const val GGUF_MAGIC = 0x46554747 // "GGUF" little-endian

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_KV_COUNT = 10000L
    private const val MAX_KEY_LENGTH = 4096L
    private const val MAX_STRING_LENGTH = 64L * 1024 * 1024

    /** 流式 SHA-256（固定缓冲区，不整文件载入内存）。 */
    @Throws(IOException::class)
    @JvmStatic
    fun sha256Hex(file: File): String {
        val digest: MessageDigest
        try {
            digest = MessageDigest.getInstance("SHA-256")
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { inp ->
            var n = inp.read(buffer)
            while (n > 0) {
                digest.update(buffer, 0, n)
                n = inp.read(buffer)
            }
        }
        val sb = StringBuilder(64)
        for (b in digest.digest()) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                .append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun sha256Matches(file: File, expectedHex: String?): Boolean {
        if (expectedHex == null || expectedHex.length != 64) {
            return false
        }
        return sha256Hex(file).lowercase(Locale.ROOT) == expectedHex.lowercase(Locale.ROOT)
    }

    /** GGUF 头部探针结果。 */
    class GgufProbe private constructor(
        @JvmField val ok: Boolean,
        @JvmField val architecture: String?,
        @JvmField val reason: String?
    ) {
        companion object {
            @JvmStatic
            fun ok(architecture: String): GgufProbe = GgufProbe(true, architecture, null)

            @JvmStatic
            fun fail(reason: String): GgufProbe = GgufProbe(false, null, reason)
        }
    }

    @JvmStatic
    fun probeGguf(file: File): GgufProbe {
        return try {
            CountingInputStream(FileInputStream(file)).use { inp ->
                val magic = readU32(inp)
                if (magic != GGUF_MAGIC.toLong()) {
                    return@use GgufProbe.fail("GGUF 魔数错误（非 GGUF 文件）")
                }
                val version = readU32(inp)
                if (version != 2L && version != 3L) {
                    return@use GgufProbe.fail("不支持的 GGUF 版本：$version")
                }
                readU64(inp) // tensor count（本次不校验张量区）
                val kvCount = readU64(inp)
                if (kvCount > MAX_KV_COUNT) {
                    return@use GgufProbe.fail("GGUF 元数据 KV 数量超限")
                }
                var architecture: String? = null
                var i = 0L
                while (i < kvCount) {
                    val keyLen = readU64(inp)
                    if (keyLen > MAX_KEY_LENGTH) {
                        return@use GgufProbe.fail("GGUF 元数据键超长")
                    }
                    val keyBytes = ByteArray(keyLen.toInt())
                    readFully(inp, keyBytes)
                    val key = String(keyBytes, StandardCharsets.UTF_8)
                    val type = readU32(inp)
                    if (key == "general.architecture") {
                        if (type != 8L) {
                            return@use GgufProbe.fail("general.architecture 类型非字符串")
                        }
                        val value = readString(inp)
                        if (value == null) {
                            return@use GgufProbe.fail("general.architecture 读取失败")
                        }
                        architecture = String(value, StandardCharsets.UTF_8).trim()
                    } else {
                        if (!skipValue(inp, type)) {
                            return@use GgufProbe.fail("GGUF 元数据越界（$key）")
                        }
                    }
                    i++
                }
                val arch = architecture
                if (arch == null || arch.isEmpty()) {
                    return@use GgufProbe.fail("GGUF 缺少 general.architecture 元数据")
                }
                GgufProbe.ok(arch)
            }
        } catch (e: IOException) {
            GgufProbe.fail("GGUF 头部读取失败")
        }
    }

    @Throws(IOException::class)
    private fun readString(inp: CountingInputStream): ByteArray? {
        val len = readU64(inp)
        if (len > MAX_STRING_LENGTH || len < 0) {
            return null
        }
        val bytes = ByteArray(len.toInt())
        readFully(inp, bytes)
        return bytes
    }

    @Throws(IOException::class)
    private fun skipValue(inp: CountingInputStream, type: Long): Boolean {
        when (type.toInt()) {
            0, 1, 7 -> return inp.skip(1L) == 1L // uint8/int8/bool
            2, 3 -> return inp.skip(2L) == 2L // uint16/int16
            4, 5, 6 -> return inp.skip(4L) == 4L // uint32/int32/float32
            10, 11, 12 -> return inp.skip(8L) == 8L // uint64/int64/float64
            8 -> { // string
                val s = readString(inp)
                return s != null
            }
            9 -> { // array
                val elemType = readU32(inp)
                val count = readU64(inp)
                if (count > 1_000_000) {
                    return false
                }
                var i = 0L
                while (i < count) {
                    if (!skipValue(inp, elemType)) {
                        return false
                    }
                    i++
                }
                return true
            }
            else -> return false
        }
    }

    @Throws(IOException::class)
    private fun readU32(inp: CountingInputStream): Long {
        val b = ByteArray(4)
        readFully(inp, b)
        return (b[0].toLong() and 0xFFL) or
                ((b[1].toLong() and 0xFFL) shl 8) or
                ((b[2].toLong() and 0xFFL) shl 16) or
                ((b[3].toLong() and 0xFFL) shl 24)
    }

    @Throws(IOException::class)
    private fun readU64(inp: CountingInputStream): Long {
        val b = ByteArray(8)
        readFully(inp, b)
        var v = 0L
        for (i in 0 until 8) {
            v = v or ((b[i].toLong() and 0xFFL) shl (8 * i))
        }
        return v
    }

    @Throws(IOException::class)
    private fun readFully(inp: InputStream, out: ByteArray) {
        var off = 0
        while (off < out.size) {
            val n = inp.read(out, off, out.size - off)
            if (n < 0) {
                throw IOException("unexpected EOF")
            }
            off += n
        }
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        private var count = 0L

        @Throws(IOException::class)
        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) {
                count++
            }
            return b
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) {
                count += n
            }
            return n
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }
    }
}
