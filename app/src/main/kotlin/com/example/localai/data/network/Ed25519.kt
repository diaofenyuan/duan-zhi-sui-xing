package com.example.localai.data.network

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Arrays

/**
 * 纯 Java Ed25519 验证实现（verify-only，RFC 8032 纯 Ed25519 模式）。
 * 算法结构参考 str4d/ed25519-java（CC0 公共领域，归属见 NOTICE），仅保留验证路径：
 * 点解码（含 y < p 规范性与 x 奇偶校验）、扩展坐标系标量乘法（4-bit 窗口）。
 * 供 Android API 26+（平台无 EdDSA 的版本）验证 Manifest/Catalog 签名使用；
 * 单元测试与 JDK 17 原生 Ed25519 交叉验证。
 *
 * Kotlin 迁移说明：Point 保持普通类（非 data class）并在身份比较处使用 `===`（引用相等），
 * 以逐字节保留与 Java 版 `==`（引用相等）完全一致的语义。
 */
object Ed25519 {

    const val PUBLIC_KEY_LENGTH = 32
    const val SIGNATURE_LENGTH = 64

    private val P: BigInteger =
        BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val L: BigInteger = BigInteger.ONE.shiftLeft(252)
        .add(BigInteger("27742317777372353535851937790883648493"))
    private val D: BigInteger = BigInteger.valueOf(-121665L)
        .multiply(BigInteger.valueOf(121666L).modInverse(P)).mod(P)
    private val D2: BigInteger = D.shiftLeft(1).mod(P)
    private val SQRT_M1: BigInteger = BigInteger(
        "19681161376707505956807079304988542015446066515923890162744021073123829784752")

    private val GY: BigInteger =
        BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
    private val GX: BigInteger = recoverX(GY, false)!!
    private val BASE_POINT: Point = Point(GX, GY, BigInteger.ONE, GX.multiply(GY).mod(P))

    /**
     * 校验签名。pubKey 为 32 字节小端编码，sig 为 R(32)||S(32)。
     * 任一点解码失败、S >= L、或等式 [S]B = R + [h]A 不成立时返回 false，不抛异常。
     */
    @JvmStatic
    fun verify(pubKey: ByteArray?, message: ByteArray?, sig: ByteArray?): Boolean {
        if (pubKey == null || message == null || sig == null) {
            return false
        }
        if (pubKey.size != PUBLIC_KEY_LENGTH || sig.size != SIGNATURE_LENGTH) {
            return false
        }
        val a = decodePoint(pubKey) ?: return false
        val rBytes = Arrays.copyOfRange(sig, 0, 32)
        val r = decodePoint(rBytes) ?: return false
        val s = toBigInteger(Arrays.copyOfRange(sig, 32, 64))
        if (s >= L) {
            return false
        }
        val h = sha512ModL(rBytes, pubKey, message)
        // [S]B + (-[h]A) == R
        val left = pointAdd(scalarMultBase(s), pointNegate(scalarMult(a, h)))
        return pointEquals(left, r)
    }

    private fun sha512ModL(vararg parts: ByteArray): BigInteger {
        return try {
            val md = MessageDigest.getInstance("SHA-512")
            for (p in parts) {
                md.update(p)
            }
            toBigInteger(md.digest()).mod(L)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    private fun toBigInteger(littleEndian: ByteArray): BigInteger {
        val copy = ByteArray(littleEndian.size)
        for (i in littleEndian.indices) {
            copy[i] = littleEndian[littleEndian.size - 1 - i]
        }
        return BigInteger(1, copy)
    }

    /** 解码 32 字节小端点编码；非法编码（y >= p、无平方根、无穷远）返回 null。 */
    private fun decodePoint(encoded: ByteArray): Point? {
        if (encoded.size != 32) {
            return null
        }
        val yBytes = Arrays.copyOf(encoded, 32)
        val odd = (yBytes[31].toInt() and 0x80) != 0
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        val y = toBigInteger(yBytes)
        if (y >= P) {
            return null
        }
        val x = recoverX(y, odd) ?: return null
        return Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    /** 由 y 坐标与目标 x 奇偶恢复 x；无解返回 null。 */
    private fun recoverX(y: BigInteger, odd: Boolean): BigInteger? {
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE)
            .multiply(D.multiply(y2).add(BigInteger.ONE).modInverse(P)).mod(P)
        var x = u.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).mod(P) != u) {
            x = x.multiply(SQRT_M1).mod(P)
            if (x.multiply(x).mod(P) != u) {
                return null
            }
        }
        if (x.testBit(0) != odd) {
            x = P.subtract(x)
        }
        return x
    }

    private fun scalarMultBase(e: BigInteger): Point = scalarMult(BASE_POINT, e)

    private fun scalarMult(p: Point, e: BigInteger): Point {
        val table = arrayOfNulls<Point>(16)
        table[0] = Point.IDENTITY
        table[1] = p
        table[2] = pointDouble(p)
        for (i in 3 until 16) {
            table[i] = pointAdd(table[i - 1]!!, p)
        }
        var result = Point.IDENTITY
        var started = false
        for (window in 63 downTo 0) {
            if (started) {
                result = pointDouble(pointDouble(pointDouble(pointDouble(result))))
            }
            val w = e.shiftRight(window * 4).and(BigInteger.valueOf(15)).toInt()
            if (w != 0) {
                result = pointAdd(result, table[w]!!)
                started = true
            }
        }
        return result
    }

    /** 扩展坐标点加（a = -1 统一公式）。 */
    private fun pointAdd(a: Point, b: Point): Point {
        val x1 = a.x
        val y1 = a.y
        val z1 = a.z
        val t1 = a.t
        val x2 = b.x
        val y2 = b.y
        val z2 = b.z
        val t2 = b.t

        val A = y1.subtract(x1).multiply(y2.subtract(x2)).mod(P)
        val B = y1.add(x1).multiply(y2.add(x2)).mod(P)
        val C = t1.multiply(D2).mod(P).multiply(t2).mod(P)
        val Dd = z1.shiftLeft(1).mod(P).multiply(z2).mod(P)
        val E = B.subtract(A).mod(P)
        val F = Dd.subtract(C).mod(P)
        val G = Dd.add(C).mod(P)
        val H = B.add(A).mod(P)

        return Point(
            E.multiply(F).mod(P),
            G.multiply(H).mod(P),
            F.multiply(G).mod(P),
            E.multiply(H).mod(P))
    }

    private fun pointDouble(p: Point): Point {
        val x = p.x
        val y = p.y
        val z = p.z

        val A = x.multiply(x).mod(P)
        val B = y.multiply(y).mod(P)
        val C = z.multiply(z).shiftLeft(1).mod(P)
        val D = A.negate().mod(P)
        val E = x.add(y).multiply(x.add(y)).mod(P)
            .subtract(A).subtract(B).mod(P)
        val G = D.add(B).mod(P)
        val F = G.subtract(C).mod(P)
        val H = D.subtract(B).mod(P)

        return Point(
            E.multiply(F).mod(P),
            G.multiply(H).mod(P),
            F.multiply(G).mod(P),
            E.multiply(H).mod(P))
    }

    private fun pointNegate(p: Point): Point =
        Point(p.x.negate().mod(P), p.y, p.z, p.t.negate().mod(P))

    private fun pointEquals(a: Point, b: Point): Boolean {
        if (a === Point.IDENTITY && b === Point.IDENTITY) {
            return true
        }
        if (a === Point.IDENTITY || b === Point.IDENTITY) {
            return false
        }
        return a.x.multiply(b.z).mod(P) == b.x.multiply(a.z).mod(P)
                && a.y.multiply(b.z).mod(P) == b.y.multiply(a.z).mod(P)
    }

    private class Point(
        val x: BigInteger,
        val y: BigInteger,
        val z: BigInteger,
        val t: BigInteger
    ) {
        companion object {
            val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
        }
    }
}
