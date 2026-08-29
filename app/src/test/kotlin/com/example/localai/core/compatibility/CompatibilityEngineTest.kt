package com.example.localai.core.compatibility

import com.example.localai.core.compatibility.CompatibilityEngine.DeviceSnapshot
import com.example.localai.core.compatibility.CompatibilityEngine.ModelConstraints
import com.example.localai.core.compatibility.CompatibilityEngine.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 兼容性规则引擎测试（P4）：硬约束筛选 + 内存档位分档 + 估算峰值。 */
class CompatibilityEngineTest {

    private fun flagship(): DeviceSnapshot =
        DeviceSnapshot(34, "arm64-v8a", 95L * 1024 * 1024 * 1024, 12L * 1024 * 1024 * 1024)

    private fun entry(): DeviceSnapshot =
        DeviceSnapshot(32, "arm64-v8a", 32L * 1024 * 1024 * 1024, 6L * 1024 * 1024 * 1024)

    private fun smallModel(): ModelConstraints =
        ModelConstraints(26, listOf("arm64-v8a"), 105_453_984L, 2048L, 135_000_000L)

    private fun bigModel(): ModelConstraints =
        ModelConstraints(26, listOf("arm64-v8a"), 5_300_000_000L, 32768L, 9_000_000_000L)

    @Test
    fun flagshipApprovesSmallModel() {
        val r = CompatibilityEngine.evaluate(flagship(), smallModel())
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level)
        assertTrue(r.estimatedPeakBytes > 0L)
    }

    @Test
    fun unsupportedApiBelowMinimum() {
        val r = CompatibilityEngine.evaluate(
            DeviceSnapshot(25, "arm64-v8a", 64L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024),
            smallModel())
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level)
        assertTrue(r.reasons.any { it.contains("系统需") })
    }

    @Test
    fun unsupportedAbiMismatch() {
        val r = CompatibilityEngine.evaluate(
            DeviceSnapshot(34, "armeabi-v7a", 64L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024),
            smallModel())
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level)
        assertTrue(r.reasons.any { it.contains("架构") })
    }

    @Test
    fun emulatorX86Permitted() {
        // 模拟器特例：x86_64 构建亦允许加载（P4 模拟器验收路径）
        val r = CompatibilityEngine.evaluate(
            DeviceSnapshot(34, "x86_64", 64L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024),
            smallModel())
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level)
    }

    @Test
    fun unsupportedStorageTooSmall() {
        val r = CompatibilityEngine.evaluate(
            DeviceSnapshot(34, "arm64-v8a", 100L * 1024 * 1024, 8L * 1024 * 1024 * 1024),
            smallModel())
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level)
        assertTrue(r.reasons.any { it.contains("存储") })
    }

    @Test
    fun entryDeviceApprovesSmallModel() {
        val r = CompatibilityEngine.evaluate(entry(), smallModel())
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level)
    }

    @Test
    fun bigModelOnEntryUnsupportedByMemory() {
        // 9B 模型峰值估算 ≈ 10.4 GB > 6 GB RAM -> UNSUPPORTED（超内存）
        val big = CompatibilityEngine.evaluate(entry(), bigModel())
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, big.level)
        assertTrue(big.reasons.any { it.contains("超过设备可用内存") })
    }

    @Test
    fun estimatePeakAlwaysPositive() {
        assertTrue(CompatibilityEngine.estimatePeakBytes(smallModel()) > 0L)
        assertTrue(CompatibilityEngine.estimatePeakBytes(bigModel()) > bigModel().sizeBytes)
    }
}
