package com.example.localai.feature.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 下载后校验（S016 校验链中的 SHA-256 + GGUF 元数据探针）。
 * GGUF 头部：magic(4B "GGUF") + version(u32 LE) + tensor_count(u64) + kv_count(u64) + KV 对。
 * 探针只验证结构合法性并提取 general.architecture（字符串类型），不做完整解析；
 * 恶意/损坏头部（超限 KV 数、超长键、越界读取）一律拒绝。
 */
public final class ModelVerifier {

    public static final int GGUF_MAGIC = 0x46554747; // "GGUF" little-endian

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long MAX_KV_COUNT = 10000;
    private static final long MAX_KEY_LENGTH = 4096;
    private static final long MAX_STRING_LENGTH = 64 * 1024 * 1024;

    private ModelVerifier() {
    }

    /** 流式 SHA-256（固定缓冲区，不整文件载入内存）。 */
    public static String sha256Hex(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static boolean sha256Matches(File file, String expectedHex) throws IOException {
        if (expectedHex == null || expectedHex.length() != 64) {
            return false;
        }
        return sha256Hex(file).toLowerCase(Locale.ROOT).equals(expectedHex.toLowerCase(Locale.ROOT));
    }

    /** GGUF 头部探针结果。 */
    public static final class GgufProbe {
        public final boolean ok;
        public final String architecture;
        public final String reason;

        private GgufProbe(boolean ok, String architecture, String reason) {
            this.ok = ok;
            this.architecture = architecture;
            this.reason = reason;
        }

        public static GgufProbe ok(String architecture) {
            return new GgufProbe(true, architecture, null);
        }

        public static GgufProbe fail(String reason) {
            return new GgufProbe(false, null, reason);
        }
    }

    public static GgufProbe probeGguf(File file) {
        try (CountingInputStream in = new CountingInputStream(new FileInputStream(file))) {
            long magic = readU32(in);
            if (magic != GGUF_MAGIC) {
                return GgufProbe.fail("GGUF 魔数错误（非 GGUF 文件）");
            }
            long version = readU32(in);
            if (version != 2 && version != 3) {
                return GgufProbe.fail("不支持的 GGUF 版本：" + version);
            }
            readU64(in); // tensor count（本次不校验张量区）
            long kvCount = readU64(in);
            if (kvCount > MAX_KV_COUNT) {
                return GgufProbe.fail("GGUF 元数据 KV 数量超限");
            }
            String architecture = null;
            for (long i = 0; i < kvCount; i++) {
                long keyLen = readU64(in);
                if (keyLen > MAX_KEY_LENGTH) {
                    return GgufProbe.fail("GGUF 元数据键超长");
                }
                byte[] keyBytes = new byte[(int) keyLen];
                readFully(in, keyBytes);
                String key = new String(keyBytes, StandardCharsets.UTF_8);
                long type = readU32(in);
                if (key.equals("general.architecture")) {
                    if (type != 8) {
                        return GgufProbe.fail("general.architecture 类型非字符串");
                    }
                    byte[] value = readString(in);
                    if (value == null) {
                        return GgufProbe.fail("general.architecture 读取失败");
                    }
                    architecture = new String(value, StandardCharsets.UTF_8).trim();
                } else {
                    if (!skipValue(in, type)) {
                        return GgufProbe.fail("GGUF 元数据越界（" + key + "）");
                    }
                }
            }
            if (architecture == null || architecture.isEmpty()) {
                return GgufProbe.fail("GGUF 缺少 general.architecture 元数据");
            }
            return GgufProbe.ok(architecture);
        } catch (IOException e) {
            return GgufProbe.fail("GGUF 头部读取失败");
        }
    }

    private static byte[] readString(CountingInputStream in) throws IOException {
        long len = readU64(in);
        if (len > MAX_STRING_LENGTH || len < 0) {
            return null;
        }
        byte[] bytes = new byte[(int) len];
        readFully(in, bytes);
        return bytes;
    }

    private static boolean skipValue(CountingInputStream in, long type) throws IOException {
        switch ((int) type) {
            case 0:
            case 1:
            case 7: // uint8/int8/bool
                return in.skip(1) == 1;
            case 2:
            case 3: // uint16/int16
                return in.skip(2) == 2;
            case 4:
            case 5:
            case 6: // uint32/int32/float32
                return in.skip(4) == 4;
            case 10:
            case 11:
            case 12: // uint64/int64/float64
                return in.skip(8) == 8;
            case 8: { // string
                byte[] s = readString(in);
                return s != null;
            }
            case 9: { // array
                long elemType = readU32(in);
                long count = readU64(in);
                if (count > 1_000_000) {
                    return false;
                }
                for (long i = 0; i < count; i++) {
                    if (!skipValue(in, elemType)) {
                        return false;
                    }
                }
                return true;
            }
            default:
                return false;
        }
    }

    private static long readU32(CountingInputStream in) throws IOException {
        byte[] b = new byte[4];
        readFully(in, b);
        return (b[0] & 0xFFL) | ((b[1] & 0xFFL) << 8) | ((b[2] & 0xFFL) << 16) | ((b[3] & 0xFFL) << 24);
    }

    private static long readU64(CountingInputStream in) throws IOException {
        byte[] b = new byte[8];
        readFully(in, b);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[i] & 0xFFL) << (8 * i);
        }
        return v;
    }

    private static void readFully(InputStream in, byte[] out) throws IOException {
        int off = 0;
        while (off < out.length) {
            int n = in.read(out, off, out.length - off);
            if (n < 0) {
                throw new IOException("unexpected EOF");
            }
            off += n;
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long count;

        CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
