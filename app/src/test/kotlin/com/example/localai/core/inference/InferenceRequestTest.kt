package com.example.localai.core.inference

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** AIDL 参数的 Parcel 往返与字段完整性（Robolectric）。 */
@RunWith(RobolectricTestRunner::class)
class InferenceRequestTest {

    @Test
    fun request_parcelRoundTrip_preservesFields() {
        val original = InferenceRequest(
            "req-1", "smollm-135m-instruct", "2026.08.1", "/data/x/model.gguf",
            8192, 4, 0.7f, 0.9f, 256, 12)

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val restored = InferenceRequest.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertEquals(original.requestId, restored.requestId)
        assertEquals(original.modelId, restored.modelId)
        assertEquals(original.version, restored.version)
        assertEquals(original.modelPath, restored.modelPath)
        assertEquals(8192, restored.contextLength)
        assertEquals(12, restored.gpuLayers)
        assertEquals(4, restored.threadCount)
        assertEquals(0.7f, restored.temperature, 0.0001f)
        assertEquals(0.9f, restored.topP, 0.0001f)
        assertEquals(256, restored.maxNewTokens)
    }

    @Test
    fun stats_parcelRoundTrip_preservesFields() {
        val original = InferenceStats(
            InferenceStats.STATE_RUNNING, 12L, 34L, 1200L, 5000L)

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val restored = InferenceStats.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertEquals(InferenceStats.STATE_RUNNING, restored.state)
        assertEquals(12L, restored.promptTokens)
        assertEquals(34L, restored.genTokens)
        assertEquals(1200L, restored.ttftMs)
        assertEquals(5000L, restored.elapsedMs)
    }

    @Test
    fun approvedModels_byId_unknownReturnsNull() {
        assertNull(ApprovedModels.byId("not-a-model"))
        assertNotNull(ApprovedModels.byId("smollm-135m-instruct"))
    }
}
