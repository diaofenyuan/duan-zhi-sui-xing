package com.example.localai.feature.download

import com.example.localai.fixtures.FixtureKit
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** SHA-256 与 GGUF 头部探针测试（S016 校验链）。 */
class ModelVerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(bytes: ByteArray): File {
        val f = tmp.newFile()
        FileOutputStream(f).use { out -> out.write(bytes) }
        return f
    }

    @Test
    fun sha256_matches() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        val f = write(payload)
        assertEquals(FixtureKit.sha256Hex(payload), ModelVerifier.sha256Hex(f))
        assertTrue(ModelVerifier.sha256Matches(f, FixtureKit.sha256Hex(payload)))
    }

    @Test
    fun sha256_mismatch() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        val f = write(payload)
        assertFalse(ModelVerifier.sha256Matches(f, FixtureKit.sha256Hex(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun gguf_validHeader_architectureExtracted() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        val probe = ModelVerifier.probeGguf(write(payload))
        assertTrue(probe.reason, probe.ok)
        assertEquals("qwen2", probe.architecture)
    }

    @Test
    fun gguf_badMagic_rejected() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        payload[0] = 'G'.code.toByte()
        payload[1] = 'X'.code.toByte()
        val probe = ModelVerifier.probeGguf(write(payload))
        assertFalse(probe.ok)
    }

    @Test
    fun gguf_badVersion_rejected() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        payload[4] = 99 // version = 99（不支持）
        val probe = ModelVerifier.probeGguf(write(payload))
        assertFalse(probe.ok)
    }

    @Test
    fun gguf_truncatedHeader_rejected() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 8192)
        val f = write(payload)
        // 截断到 10 字节
        FileOutputStream(f, true).use { out ->
            out.channel.truncate(10)
        }
        val probe = ModelVerifier.probeGguf(f)
        assertFalse(probe.ok)
    }

    @Test
    fun gguf_emptyFile_rejected() {
        val f = write(ByteArray(0))
        assertFalse(ModelVerifier.probeGguf(f).ok)
    }

    @Test
    fun gguf_missingArchitecture_rejected() {
        // 构造一个无 general.architecture 的合法头部（kvCount=0）
        val payload = ByteArray(4096)
        var pos = 0
        writeIntLE(payload, pos, 0x46554747)
        pos += 4
        writeIntLE(payload, pos, 3)
        pos += 4
        pos += 8 // tensor count = 0
        pos += 8 // kv count = 0
        assertNotNull(write(payload))
        val probe = ModelVerifier.probeGguf(write(payload))
        assertFalse(probe.ok)
        assertTrue(probe.reason!!.contains("architecture"))
    }

    private fun writeIntLE(out: ByteArray, offset: Int, v: Int) {
        out[offset] = v.toByte()
        out[offset + 1] = (v shr 8).toByte()
        out[offset + 2] = (v shr 16).toByte()
        out[offset + 3] = (v shr 24).toByte()
    }
}
