package com.example.localai.fixtures;

import com.example.localai.data.network.Hex;
import com.example.localai.data.network.TrustStore;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;

/**
 * 测试夹具工具：JDK 原生 Ed25519 动态签名（与 App 内纯 Java 验证器互操作）、
 * 确定性 GGUF 演示载荷、Manifest/Catalog JSON 构造。
 */
public final class FixtureKit {

    public static final Gson GSON = new Gson();

    private FixtureKit() {
    }

    /** 测试密钥对 + 对应 TrustStore（验证器经此信任测试公钥）。 */
    public static final class TestKeys {
        public final String keyId;
        public final byte[] publicKeyBytes; // 32 字节原始公钥
        public final TrustStore trustStore;
        private final KeyPair keyPair;

        TestKeys(String keyId, byte[] publicKeyBytes, TrustStore trustStore, KeyPair keyPair) {
            this.keyId = keyId;
            this.publicKeyBytes = publicKeyBytes;
            this.trustStore = trustStore;
            this.keyPair = keyPair;
        }

        public byte[] sign(byte[] data) throws Exception {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            return sig.sign();
        }
    }

    public static TestKeys newTestKeys(String keyId) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = gen.generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        byte[] rawPub = new byte[32];
        System.arraycopy(spki, spki.length - 32, rawPub, 0, 32);
        TrustStore store = new TrustStore() {
            @Override
            public byte[] publicKey(String id) {
                return keyId.equals(id) ? rawPub : null;
            }

            @Override
            public boolean isRevoked(String id) {
                return false;
            }
        };
        return new TestKeys(keyId, rawPub, store, pair);
    }

    /** 用 JDK Ed25519 签名数据并生成 sig 侧车文件字节。 */
    public static byte[] sigFile(TestKeys keys, byte[] data) throws Exception {
        byte[] sig = keys.sign(data);
        JsonObject json = new JsonObject();
        json.addProperty("algorithm", "Ed25519");
        json.addProperty("keyId", keys.keyId);
        json.addProperty("value", Base64.getEncoder().encodeToString(sig));
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 生成确定性 GGUF 演示载荷（合法 v3 头部 + general.architecture + 模式填充）。 */
    public static byte[] ggufPayload(String modelId, String architecture, int sizeBytes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeIntLE(out, 0x46554747); // "GGUF"
        writeIntLE(out, 3); // version
        writeLongLE(out, 0L); // tensor count
        writeLongLE(out, 1L); // kv count
        writeKvString(out, "general.architecture", architecture);
        byte[] header = out.toByteArray();
        if (sizeBytes < header.length + 32) {
            throw new IllegalArgumentException("payload too small");
        }
        byte[] result = new byte[sizeBytes];
        System.arraycopy(header, 0, result, 0, header.length);
        byte[] state = sha256(("payload:" + modelId).getBytes(StandardCharsets.UTF_8));
        int pos = header.length;
        while (pos < result.length) {
            state = sha256(state);
            int n = Math.min(state.length, result.length - pos);
            System.arraycopy(state, 0, result, pos, n);
            pos += n;
        }
        return result;
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return Hex.toHex(sha256(data));
    }

    public static byte[] catalogJson(String[][] models) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("generatedAt", "2026-08-23T00:00:00Z");
        JsonArray array = new JsonArray();
        for (String[] m : models) {
            JsonObject entry = new JsonObject();
            entry.addProperty("modelId", m[0]);
            entry.addProperty("version", m[1]);
            entry.addProperty("displayName", m[2]);
            entry.addProperty("description", "fixture model");
            array.add(entry);
        }
        json.add("models", array);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 生成 manifest.json 字节（未签名；签名由 sigFile 侧车承载）。 */
    public static byte[] manifestJson(String modelId, String version, String displayName,
                                      String architecture, String publisher, String licenseSpdx,
                                      String fileName, int sizeBytes, String sha256, String urlPath) {
        JsonObject json = new JsonObject();
        json.addProperty("modelId", modelId);
        json.addProperty("version", version);
        json.addProperty("displayName", displayName);
        JsonObject source = new JsonObject();
        source.addProperty("publisher", publisher);
        source.addProperty("url", "https://example.com/" + publisher);
        json.add("source", source);
        JsonObject license = new JsonObject();
        license.addProperty("spdx", licenseSpdx);
        license.addProperty("url", "https://example.com/license");
        json.add("license", license);
        json.addProperty("format", "gguf");
        json.addProperty("architecture", architecture);
        json.addProperty("parameterCount", 1500000000L);
        json.addProperty("quantization", "Q4_K_M");
        json.addProperty("contextLength", 32768);
        JsonArray files = new JsonArray();
        JsonObject file = new JsonObject();
        file.addProperty("name", fileName);
        file.addProperty("sizeBytes", sizeBytes);
        file.addProperty("sha256", sha256);
        JsonArray urls = new JsonArray();
        urls.add(urlPath);
        file.add("urls", urls);
        files.add(file);
        json.add("files", files);
        JsonObject runtime = new JsonObject();
        runtime.addProperty("minAndroidApi", 26);
        JsonArray abis = new JsonArray();
        abis.add("arm64-v8a");
        runtime.add("abis", abis);
        JsonArray backends = new JsonArray();
        backends.add("cpu");
        runtime.add("backends", backends);
        json.add("runtime", runtime);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String manifestPath(String modelId, String version) {
        return "/v1/models/" + modelId + "/" + version + "/manifest.json";
    }

    public static String manifestSigPath(String modelId, String version) {
        return "/v1/models/" + modelId + "/" + version + "/manifest.sig";
    }

    public static String filePath(String modelId, String version, String fileName) {
        return "/v1/models/" + modelId + "/" + version + "/" + fileName;
    }

    private static void writeKvString(java.io.ByteArrayOutputStream out, String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        writeLongLE(out, keyBytes.length);
        out.write(keyBytes, 0, keyBytes.length);
        writeIntLE(out, 8); // STRING
        writeLongLE(out, valueBytes.length);
        out.write(valueBytes, 0, valueBytes.length);
    }

    private static void writeIntLE(java.io.ByteArrayOutputStream out, int v) {
        for (int i = 0; i < 4; i++) {
            out.write((byte) (v >> (8 * i)));
        }
    }

    private static void writeLongLE(java.io.ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 8; i++) {
            out.write((byte) (v >> (8 * i)));
        }
    }
}
