package com.example.localai.feature.settings

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemporaryCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun clearsOnlyTemporaryFilesAndReportsActualBytes() {
        val cache = tmp.newFolder("cache")
        File(cache, "nested").mkdirs()
        File(cache, "nested/part.tmp").writeBytes(ByteArray(4096))
        File(cache, "other.tmp").writeBytes(ByteArray(1024))
        val model = File(tmp.newFolder("files"), "model.gguf").apply { writeText("installed model") }
        val storage = TemporaryCache(cache)
        assertEquals(5120L, storage.measure().bytes)
        assertEquals(TemporaryCache.Result(5120, 0), storage.clear())
        assertTrue(cache.isDirectory)
        assertTrue(cache.listFiles()!!.isEmpty())
        assertEquals("installed model", model.readText())
        assertEquals(TemporaryCache.Result(0, 0), storage.clear())
    }
}
