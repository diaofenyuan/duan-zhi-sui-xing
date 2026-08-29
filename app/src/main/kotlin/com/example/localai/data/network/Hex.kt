package com.example.localai.data.network

/** 十六进制编解码工具。 */
object Hex {

    @JvmStatic
    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                .append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    /** 严格解析：长度必须为偶数且全为十六进制字符，否则抛出 IllegalArgumentException。 */
    @JvmStatic
    fun fromHex(hex: String): ByteArray {
        if (hex.length and 1 != 0) {
            throw IllegalArgumentException("bad hex length")
        }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = digit(hex[2 * i])
            val lo = digit(hex[2 * i + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun digit(c: Char): Int {
        if (c in '0'..'9') {
            return c - '0'
        }
        if (c in 'a'..'f') {
            return c - 'a' + 10
        }
        if (c in 'A'..'F') {
            return c - 'A' + 10
        }
        throw IllegalArgumentException("bad hex char: $c")
    }
}
