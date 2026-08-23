import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 确定性演示 GGUF 载荷生成器（dev fixture 专用，非真实模型权重）。
 * 结构：合法 GGUF v3 头部（magic + version + 0 张量 + 2 个元数据 KV：general.architecture、
 * general.name）后接由 SHA-256(modelId) 种子化的确定性字节流填充至 sizeBytes。
 * 同一 (modelId, sizeBytes) 跨进程、跨平台产出完全一致的字节与 SHA-256，
 * 因此 Manifest 中的 sha256 可以离线预计算并提交。
 */
public final class DemoPayload {

    public static final int GGUF_MAGIC = 0x46554747; // "GGUF" little-endian

    private DemoPayload() {
    }

    public static byte[] of(String modelId, String architecture, String displayName, int sizeBytes) {
        byte[] body = buildBody(architecture, displayName);
        if (sizeBytes < body.length + 64) {
            throw new IllegalArgumentException("sizeBytes too small for header, need >= " + (body.length + 64));
        }
        byte[] out = new byte[sizeBytes];
        int pos = 0;

        writeIntLE(out, pos, GGUF_MAGIC);
        pos += 4;
        writeIntLE(out, pos, 3); // version
        pos += 4;
        writeLongLE(out, pos, 0L); // tensor count
        pos += 8;
        writeLongLE(out, pos, 2L); // kv count
        pos += 8;

        System.arraycopy(body, 0, out, pos, body.length);
        pos += body.length;

        // 确定性填充：SHA-256(modelId) 派生字节流
        MessageDigest digest = sha256();
        byte[] state = digest.digest(("payload-seed:" + modelId).getBytes(StandardCharsets.UTF_8));
        while (pos < out.length) {
            state = digest.digest(state);
            int n = Math.min(state.length, out.length - pos);
            System.arraycopy(state, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    public static String sha256Hex(String modelId, String architecture, String displayName, int sizeBytes) {
        byte[] payload = of(modelId, architecture, displayName, sizeBytes);
        return hex(sha256().digest(payload));
    }

    static byte[] buildBody(String architecture, String displayName) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeKv(out, "general.architecture", architecture.getBytes(StandardCharsets.UTF_8));
        writeKv(out, "general.name", displayName.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** KV 编码：key(string) + type(uint32=8 STRING) + value(string)。 */
    static void writeKv(java.io.ByteArrayOutputStream out, String key, byte[] value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        writeLongLE(out, keyBytes.length);
        out.write(keyBytes, 0, keyBytes.length);
        writeIntLE(out, 8);
        writeLongLE(out, value.length);
        out.write(value, 0, value.length);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static void writeIntLE(byte[] out, int pos, int v) {
        out[pos] = (byte) v;
        out[pos + 1] = (byte) (v >> 8);
        out[pos + 2] = (byte) (v >> 16);
        out[pos + 3] = (byte) (v >> 24);
    }

    static void writeLongLE(byte[] out, int pos, long v) {
        for (int i = 0; i < 8; i++) {
            out[pos + i] = (byte) (v >> (8 * i));
        }
    }

    static void writeLongLE(java.io.ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 8; i++) {
            out.write((byte) (v >> (8 * i)));
        }
    }

    static void writeIntLE(java.io.ByteArrayOutputStream out, int v) {
        for (int i = 0; i < 4; i++) {
            out.write((byte) (v >> (8 * i)));
        }
    }
}
