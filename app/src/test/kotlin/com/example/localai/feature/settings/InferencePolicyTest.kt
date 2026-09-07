package com.example.localai.feature.settings

import com.example.localai.core.inference.ApprovedModels
import org.junit.Assert.*
import org.junit.Test

class InferencePolicyTest {
    @Test fun qualityModeRetainsMoreContextWithoutIgnoringPressure() {
        val fast = InferencePolicy.select(ApprovedModels.QWEN_05B, "auto", 8, false, false)
        val quality = InferencePolicy.select(ApprovedModels.QWEN_05B, "balanced", 8, false, false)
        assertEquals(4096, quality.contextLength)
        assertTrue(quality.contextLength > fast.contextLength)
        assertEquals(1024, InferencePolicy.select(ApprovedModels.QWEN_05B, "balanced", 8, false, true).contextLength)
    }
    @Test fun manualContextAndGpuSurviveModeChangesAndRespectModelLimit() {
        val manual = InferencePolicy.select(ApprovedModels.QWEN_05B, "saver", 8, true, true, 8192, 12)
        assertEquals(8192, manual.contextLength)
        assertEquals(12, manual.gpuLayers)
        assertEquals(2, manual.threads)
        val small = InferencePolicy.select(ApprovedModels.SMOLLM_135M, "auto", 8, false, false, 32768, -1)
        assertEquals(2048, small.contextLength)
        assertEquals(-1, small.gpuLayers)
    }

    @Test fun invalidStoredValuesAreBoundedAndZeroRestoresAutomaticContext() {
        val bounded = InferencePolicy.select(ApprovedModels.QWEN_05B, "balanced", 8, false, false, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(32768, bounded.contextLength)
        assertEquals(256, bounded.gpuLayers)
        val automatic = InferencePolicy.select(ApprovedModels.QWEN_05B, "saver", 8, false, false, 0, 0)
        assertEquals(1024, automatic.contextLength)
        assertEquals(0, automatic.gpuLayers)
    }

    @Test fun automaticModeRespectsSystemPowerSavingAndAvailableCpu() {
        val model = ApprovedModels.QWEN_05B
        val normal = InferencePolicy.select(model, "auto", 8, false, false)
        val saving = InferencePolicy.select(model, "auto", 1, true, false)
        assertEquals(2048, normal.contextLength)
        assertEquals(1024, saving.contextLength)
        assertEquals(1, saving.threads)
        assertTrue(saving.maxNewTokens < normal.maxNewTokens)
    }

    @Test fun heatOrMemoryPressureLimitsEvenBalancedMode() {
        val limited = InferencePolicy.select(ApprovedModels.QWEN_05B, "balanced", 8, false, true)
        assertEquals(1024, limited.contextLength)
        assertEquals(2, limited.threads)
        assertEquals(128, limited.maxNewTokens)
    }
}
