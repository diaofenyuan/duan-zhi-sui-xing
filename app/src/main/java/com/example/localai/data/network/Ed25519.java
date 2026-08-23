package com.example.localai.data.network;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 纯 Java Ed25519 验证实现（verify-only，RFC 8032 纯 Ed25519 模式）。
 * 算法结构参考 str4d/ed25519-java（CC0 公共领域，归属见 NOTICE），仅保留验证路径：
 * 点解码（含 y &lt; p 规范性与 x 奇偶校验）、扩展坐标系标量乘法（4-bit 窗口）。
 * 供 Android API 26+（平台无 EdDSA 的版本）验证 Manifest/Catalog 签名使用；
 * 单元测试与 JDK 17 原生 Ed25519 交叉验证。
 */
public final class Ed25519 {

    public static final int PUBLIC_KEY_LENGTH = 32;
    public static final int SIGNATURE_LENGTH = 64;

    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger D = BigInteger.valueOf(-121665L)
            .multiply(BigInteger.valueOf(121666L).modInverse(P)).mod(P);
    private static final BigInteger D2 = D.shiftLeft(1).mod(P);
    private static final BigInteger SQRT_M1 = new BigInteger(
            "19681161376707505956807079304988542015446066515923890162744021073123829784752");

    private static final BigInteger GY = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);
    private static final BigInteger GX = recoverX(GY, false);
    private static final Point BASE_POINT = new Point(GX, GY, BigInteger.ONE, GX.multiply(GY).mod(P));

    private Ed25519() {
    }

    /**
     * 校验签名。pubKey 为 32 字节小端编码，sig 为 R(32)||S(32)。
     * 任一点解码失败、S &ge; L、或等式 [S]B = R + [h]A 不成立时返回 false，不抛异常。
     */
    public static boolean verify(byte[] pubKey, byte[] message, byte[] sig) {
        if (pubKey == null || message == null || sig == null) {
            return false;
        }
        if (pubKey.length != PUBLIC_KEY_LENGTH || sig.length != SIGNATURE_LENGTH) {
            return false;
        }
        Point a = decodePoint(pubKey);
        if (a == null) {
            return false;
        }
        byte[] rBytes = Arrays.copyOfRange(sig, 0, 32);
        Point r = decodePoint(rBytes);
        if (r == null) {
            return false;
        }
        BigInteger s = toBigInteger(Arrays.copyOfRange(sig, 32, 64));
        if (s.compareTo(L) >= 0) {
            return false;
        }
        BigInteger h = sha512ModL(rBytes, pubKey, message);
        // [S]B + (-[h]A) == R
        Point left = pointAdd(scalarMultBase(s), pointNegate(scalarMult(a, h)));
        return pointEquals(left, r);
    }

    static BigInteger sha512ModL(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            for (byte[] p : parts) {
                md.update(p);
            }
            return toBigInteger(md.digest()).mod(L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static BigInteger toBigInteger(byte[] littleEndian) {
        byte[] copy = new byte[littleEndian.length];
        for (int i = 0; i < littleEndian.length; i++) {
            copy[i] = littleEndian[littleEndian.length - 1 - i];
        }
        return new BigInteger(1, copy);
    }

    /** 解码 32 字节小端点编码；非法编码（y &ge; p、无平方根、无穷远）返回 null。 */
    static Point decodePoint(byte[] encoded) {
        if (encoded.length != 32) {
            return null;
        }
        byte[] yBytes = Arrays.copyOf(encoded, 32);
        boolean odd = (yBytes[31] & 0x80) != 0;
        yBytes[31] &= 0x7F;
        BigInteger y = toBigInteger(yBytes);
        if (y.compareTo(P) >= 0) {
            return null;
        }
        BigInteger x = recoverX(y, odd);
        if (x == null) {
            return null;
        }
        return new Point(x, y, BigInteger.ONE, x.multiply(y).mod(P));
    }

    /** 由 y 坐标与目标 x 奇偶恢复 x；无解返回 null。 */
    static BigInteger recoverX(BigInteger y, boolean odd) {
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u = y2.subtract(BigInteger.ONE).multiply(D.multiply(y2).add(BigInteger.ONE).modInverse(P)).mod(P);
        BigInteger x = u.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).mod(P).equals(u)) {
            x = x.multiply(SQRT_M1).mod(P);
            if (!x.multiply(x).mod(P).equals(u)) {
                return null;
            }
        }
        if (x.testBit(0) != odd) {
            x = P.subtract(x);
        }
        return x;
    }

    static Point scalarMultBase(BigInteger e) {
        return scalarMult(BASE_POINT, e);
    }

    static Point scalarMult(Point p, BigInteger e) {
        Point[] table = new Point[16];
        table[0] = Point.IDENTITY;
        table[1] = p;
        table[2] = pointDouble(p);
        for (int i = 3; i < 16; i++) {
            table[i] = pointAdd(table[i - 1], p);
        }
        Point result = Point.IDENTITY;
        boolean started = false;
        for (int window = 63; window >= 0; window--) {
            if (started) {
                result = pointDouble(pointDouble(pointDouble(pointDouble(result))));
            }
            int w = e.shiftRight(window * 4).and(BigInteger.valueOf(15)).intValue();
            if (w != 0) {
                result = pointAdd(result, table[w]);
                started = true;
            }
        }
        return result;
    }

    /** 扩展坐标点加（a = -1 统一公式）。 */
    static Point pointAdd(Point a, Point b) {
        BigInteger x1 = a.x;
        BigInteger y1 = a.y;
        BigInteger z1 = a.z;
        BigInteger t1 = a.t;
        BigInteger x2 = b.x;
        BigInteger y2 = b.y;
        BigInteger z2 = b.z;
        BigInteger t2 = b.t;

        BigInteger A = y1.subtract(x1).multiply(y2.subtract(x2)).mod(P);
        BigInteger B = y1.add(x1).multiply(y2.add(x2)).mod(P);
        BigInteger C = t1.multiply(D2).mod(P).multiply(t2).mod(P);
        BigInteger Dd = z1.shiftLeft(1).mod(P).multiply(z2).mod(P);
        BigInteger E = B.subtract(A).mod(P);
        BigInteger F = Dd.subtract(C).mod(P);
        BigInteger G = Dd.add(C).mod(P);
        BigInteger H = B.add(A).mod(P);

        return new Point(
                E.multiply(F).mod(P),
                G.multiply(H).mod(P),
                F.multiply(G).mod(P),
                E.multiply(H).mod(P));
    }

    static Point pointDouble(Point p) {
        BigInteger x = p.x;
        BigInteger y = p.y;
        BigInteger z = p.z;

        BigInteger A = x.multiply(x).mod(P);
        BigInteger B = y.multiply(y).mod(P);
        BigInteger C = z.multiply(z).shiftLeft(1).mod(P);
        BigInteger D = A.negate().mod(P);
        BigInteger E = x.add(y).multiply(x.add(y)).mod(P)
                .subtract(A).subtract(B).mod(P);
        BigInteger G = D.add(B).mod(P);
        BigInteger F = G.subtract(C).mod(P);
        BigInteger H = D.subtract(B).mod(P);

        return new Point(
                E.multiply(F).mod(P),
                G.multiply(H).mod(P),
                F.multiply(G).mod(P),
                E.multiply(H).mod(P));
    }

    static Point pointNegate(Point p) {
        return new Point(p.x.negate().mod(P), p.y, p.z, p.t.negate().mod(P));
    }

    static boolean pointEquals(Point a, Point b) {
        if (a == Point.IDENTITY && b == Point.IDENTITY) {
            return true;
        }
        if (a == Point.IDENTITY || b == Point.IDENTITY) {
            return false;
        }
        return a.x.multiply(b.z).mod(P).equals(b.x.multiply(a.z).mod(P))
                && a.y.multiply(b.z).mod(P).equals(b.y.multiply(a.z).mod(P));
    }

    static final class Point {
        static final Point IDENTITY = new Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);
        final BigInteger x;
        final BigInteger y;
        final BigInteger z;
        final BigInteger t;

        Point(BigInteger x, BigInteger y, BigInteger z, BigInteger t) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
        }
    }
}
