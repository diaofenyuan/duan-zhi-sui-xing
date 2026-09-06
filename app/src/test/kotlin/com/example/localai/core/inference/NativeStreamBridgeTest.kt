package com.example.localai.core.inference

import org.junit.Assert.*
import org.junit.Test

/** 验证任意 UTF-8 分片，不依赖模型每次碰巧输出相同 token 边界。 */
class NativeStreamBridgeTest {
    private class Recorder : NativeSession.StreamListener {
        val deltas = mutableListOf<String>()
        val finishes = mutableListOf<Int>()
        val errors = mutableListOf<Int>()
        override fun onDelta(text: String) { deltas.add(text) }
        override fun onFinished(reason: Int) { finishes.add(reason) }
        override fun onError(code: Int, message: String) { errors.add(code) }
    }

    @Test fun everyByteBoundaryPreservesChineseEmojiAndNul() {
        val original = "A\u0000中文😀𠮷é结束"
        val bytes = original.toByteArray(Charsets.UTF_8)
        for (split in 0..bytes.size) {
            val receiver = Recorder()
            val bridge = NativeSession.StreamBridge(receiver)
            bridge.onBytes(bytes.copyOfRange(0, split))
            bridge.onBytes(bytes.copyOfRange(split, bytes.size))
            bridge.onFinished(NativeSession.FINISH_END)
            assertEquals("分片位置 $split", original, receiver.deltas.joinToString(""))
            assertEquals(listOf(NativeSession.FINISH_END), receiver.finishes)
            assertTrue(receiver.errors.isEmpty())
        }
    }

    @Test fun incompleteCharacterWaitsForItsRemainingBytes() {
        val receiver = Recorder()
        val bridge = NativeSession.StreamBridge(receiver)
        val bytes = "😀".toByteArray(Charsets.UTF_8)
        for (i in 0..2) bridge.onBytes(byteArrayOf(bytes[i]))
        assertTrue(receiver.deltas.isEmpty())
        bridge.onBytes(byteArrayOf(bytes[3]))
        assertEquals(listOf("😀"), receiver.deltas)
        bridge.onBytes("中文".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("😀", "中文"), receiver.deltas)
    }

    @Test fun stoppingDropsOnlyUnfinishedCharacterAndIgnoresLateEvents() {
        val receiver = Recorder()
        val bridge = NativeSession.StreamBridge(receiver)
        val bytes = "你好😀".toByteArray(Charsets.UTF_8)
        bridge.onBytes(bytes.copyOf(bytes.size - 1))
        bridge.onFinished(NativeSession.FINISH_STOPPED)
        bridge.onBytes(byteArrayOf(bytes.last()))
        bridge.onFinished(NativeSession.FINISH_END)
        bridge.onError(NativeSession.ERR_INTERNAL, "late")
        assertEquals("你好", receiver.deltas.joinToString(""))
        assertEquals(listOf(NativeSession.FINISH_STOPPED), receiver.finishes)
        assertTrue(receiver.errors.isEmpty())
    }

    @Test fun malformedBytesDoNotDiscardFollowingValidText() {
        val receiver = Recorder()
        val bridge = NativeSession.StreamBridge(receiver)
        bridge.onBytes(byteArrayOf(0xff.toByte()) + "有效文字".toByteArray(Charsets.UTF_8))
        bridge.onFinished(NativeSession.FINISH_END)
        assertEquals("\uFFFD有效文字", receiver.deltas.joinToString(""))
    }

    @Test fun errorTerminatesDecoderWithoutLeakingBytesIntoNextRound() {
        val receiver = Recorder()
        val first = NativeSession.StreamBridge(receiver)
        first.onBytes(byteArrayOf(0xf0.toByte(), 0x9f.toByte()))
        first.onError(NativeSession.ERR_INTERNAL, "test")
        first.onFinished(NativeSession.FINISH_END)
        val second = NativeSession.StreamBridge(receiver)
        second.onBytes("下一轮😀".toByteArray(Charsets.UTF_8))
        second.onFinished(NativeSession.FINISH_END)
        assertEquals("下一轮😀", receiver.deltas.joinToString(""))
        assertEquals(listOf(NativeSession.ERR_INTERNAL), receiver.errors)
        assertEquals(listOf(NativeSession.FINISH_END), receiver.finishes)
    }
}
