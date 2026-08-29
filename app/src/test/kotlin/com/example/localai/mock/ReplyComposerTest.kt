package com.example.localai.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 流式回复生成器测试。 */
class ReplyComposerTest {

    @Test
    fun greetingReply() {
        val reply = ReplyComposer.build("你好，你是谁")
        assertTrue(reply.contains("本地"))
    }

    @Test
    fun cacheReplyExplains() {
        assertTrue(ReplyComposer.build("什么是 KV Cache？").contains("草稿"))
    }

    @Test
    fun poemReply() {
        assertTrue(ReplyComposer.build("写一首关于离线 AI 的短诗").contains("\n"))
    }

    @Test
    fun unknownPromptFallsBack() {
        val reply = ReplyComposer.build("随便说点什么")
        assertTrue(!reply.isEmpty())
        assertNotEquals(ReplyComposer.build("你好"), reply)
    }

    @Test
    fun chunksReconstructOriginal() {
        val reply = "一二三四五六七八九十ABC"
        val rebuilt = StringBuilder()
        for (part in ReplyComposer.chunk(reply)) {
            rebuilt.append(part)
        }
        assertEquals(reply, rebuilt.toString())
    }
}
