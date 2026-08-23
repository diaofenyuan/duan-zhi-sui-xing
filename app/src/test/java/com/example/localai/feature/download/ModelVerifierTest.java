package com.example.localai.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.localai.fixtures.FixtureKit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

/** SHA-256 与 GGUF 头部探针测试（S016 校验链）。 */
public class ModelVerifierTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(byte[] bytes) throws Exception {
        File f = tmp.newFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
        return f;
    }

    @Test
    public void sha256_matches() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        File f = write(payload);
        assertEquals(FixtureKit.sha256Hex(payload), ModelVerifier.sha256Hex(f));
        assertTrue(ModelVerifier.sha256Matches(f, FixtureKit.sha256Hex(payload)));
    }

    @Test
    public void sha256_mismatch() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        File f = write(payload);
        assertFalse(ModelVerifier.sha256Matches(f, FixtureKit.sha256Hex(new byte[]{1, 2, 3})));
    }

    @Test
    public void gguf_validHeader_architectureExtracted() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(write(payload));
        assertTrue(probe.reason, probe.ok);
        assertEquals("qwen2", probe.architecture);
    }

    @Test
    public void gguf_badMagic_rejected() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        payload[0] = 'G';
        payload[1] = 'X';
        ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(write(payload));
        assertFalse(probe.ok);
    }

    @Test
    public void gguf_badVersion_rejected() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        payload[4] = 99; // version = 99（不支持）
        ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(write(payload));
        assertFalse(probe.ok);
    }

    @Test
    public void gguf_truncatedHeader_rejected() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 8192);
        File f = write(payload);
        // 截断到 10 字节
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.getChannel().truncate(10);
        }
        ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(f);
        assertFalse(probe.ok);
    }

    @Test
    public void gguf_emptyFile_rejected() throws Exception {
        File f = write(new byte[0]);
        assertFalse(ModelVerifier.probeGguf(f).ok);
    }

    @Test
    public void gguf_missingArchitecture_rejected() throws Exception {
        // 构造一个无 general.architecture 的合法头部（kvCount=0）
        byte[] payload = new byte[4096];
        int pos = 0;
        writeIntLE(payload, pos, 0x46554747);
        pos += 4;
        writeIntLE(payload, pos, 3);
        pos += 4;
        pos += 8; // tensor count = 0
        pos += 8; // kv count = 0
        assertNotNull(write(payload));
        ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(write(payload));
        assertFalse(probe.ok);
        assertTrue(probe.reason.contains("architecture"));
    }

    private static void writeIntLE(byte[] out, int offset, int v) {
        out[offset] = (byte) v;
        out[offset + 1] = (byte) (v >> 8);
        out[offset + 2] = (byte) (v >> 16);
        out[offset + 3] = (byte) (v >> 24);
    }
}
