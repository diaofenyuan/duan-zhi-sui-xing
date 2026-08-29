package com.example.localai.data.network

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 临时诊断：真实 fixture manifest 用 App 侧 Gson + ModelManifest 解析（P4 问题排查）。 */
class ManifestParseDiagTest {

    companion object {
        private val ROOT = System.getProperty("user.dir")!!.replace('\\', '/')
    }

    private fun parse(rel: String): ModelManifest {
        val json = Files.readAllBytes(Paths.get("$ROOT/$rel"))
        val s = String(json, StandardCharsets.UTF_8)
        return Gson().fromJson(s, ModelManifest::class.java)
    }

    @Test
    fun parseQwenManifest() {
        val m = parse("../backend/fixtures/models/qwen3-4b/2026.08.1/manifest.json")
        assertNotNull(m)
        assertNotNull(m.tasks)
        assertTrue(m.tasks!!.contains("text"))
        assertNotNull(m.languages)
    }

    @Test
    fun parseApprovedManifest() {
        val m = parse("../backend/fixtures/models/smollm-135m-instruct/2026.08.1/manifest.json")
        assertNotNull(m)
        assertTrue(m.isApproved())
        assertTrue(m.primaryFile()!!.sizeBytes > 0)
    }
}
