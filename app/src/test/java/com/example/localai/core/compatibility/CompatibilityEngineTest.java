package com.example.localai.core.compatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.localai.core.compatibility.CompatibilityEngine.DeviceSnapshot;
import com.example.localai.core.compatibility.CompatibilityEngine.ModelConstraints;
import com.example.localai.core.compatibility.CompatibilityEngine.Result;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** 兼容性规则引擎测试（P4）：硬约束筛选 + 内存档位分档 + 估算峰值。 */
public class CompatibilityEngineTest {

    private static DeviceSnapshot flagship() {
        return new DeviceSnapshot(34, "arm64-v8a", 95L * 1024 * 1024 * 1024,
                12L * 1024 * 1024 * 1024);
    }

    private static DeviceSnapshot entry() {
        return new DeviceSnapshot(32, "arm64-v8a", 32L * 1024 * 1024 * 1024,
                6L * 1024 * 1024 * 1024);
    }

    private static ModelConstraints smallModel() {
        return new ModelConstraints(26, Arrays.asList("arm64-v8a"), 105_453_984L,
                2048, 135_000_000L);
    }

    private static ModelConstraints bigModel() {
        return new ModelConstraints(26, Arrays.asList("arm64-v8a"), 5_300_000_000L,
                32768, 9_000_000_000L);
    }

    @Test
    public void flagshipApprovesSmallModel() {
        Result r = CompatibilityEngine.evaluate(flagship(), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level);
        assertTrue(r.estimatedPeakBytes > 0);
    }

    @Test
    public void unsupportedApiBelowMinimum() {
        Result r = CompatibilityEngine.evaluate(
                new DeviceSnapshot(25, "arm64-v8a", 64L * 1024 * 1024 * 1024,
                        8L * 1024 * 1024 * 1024), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level);
        assertTrue(r.reasons.stream().anyMatch(s -> s.contains("系统需")));
    }

    @Test
    public void unsupportedAbiMismatch() {
        Result r = CompatibilityEngine.evaluate(
                new DeviceSnapshot(34, "armeabi-v7a", 64L * 1024 * 1024 * 1024,
                        8L * 1024 * 1024 * 1024), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level);
        assertTrue(r.reasons.stream().anyMatch(s -> s.contains("架构")));
    }

    @Test
    public void emulatorX86Permitted() {
        // 模拟器特例：x86_64 构建亦允许加载（P4 模拟器验收路径）
        Result r = CompatibilityEngine.evaluate(
                new DeviceSnapshot(34, "x86_64", 64L * 1024 * 1024 * 1024,
                        8L * 1024 * 1024 * 1024), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level);
    }

    @Test
    public void unsupportedStorageTooSmall() {
        Result r = CompatibilityEngine.evaluate(
                new DeviceSnapshot(34, "arm64-v8a", 100L * 1024 * 1024,
                        8L * 1024 * 1024 * 1024), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, r.level);
        assertTrue(r.reasons.stream().anyMatch(s -> s.contains("存储")));
    }

    @Test
    public void entryDeviceApprovesSmallModel() {
        Result r = CompatibilityEngine.evaluate(entry(), smallModel());
        assertEquals(CompatibilityEngine.LEVEL_RECOMMENDED, r.level);
    }

    @Test
    public void bigModelOnEntryUnsupportedByMemory() {
        // 9B 模型峰值估算 ≈ 10.4 GB > 6 GB RAM -> UNSUPPORTED（超内存）
        Result big = CompatibilityEngine.evaluate(entry(), bigModel());
        assertEquals(CompatibilityEngine.LEVEL_UNSUPPORTED, big.level);
        assertTrue(big.reasons.stream().anyMatch(s -> s.contains("超过设备可用内存")));
    }

    @Test
    public void estimatePeakAlwaysPositive() {
        assertTrue(CompatibilityEngine.estimatePeakBytes(smallModel()) > 0);
        assertTrue(CompatibilityEngine.estimatePeakBytes(bigModel()) > bigModel().sizeBytes);
    }
}
