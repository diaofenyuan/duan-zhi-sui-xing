package com.example.localai.feature.settings

import com.example.localai.core.inference.ApprovedModels
import org.junit.Assert.*
import org.junit.Test

class InferencePolicyTest {
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
