package com.example.localai.core.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** AIDL 参数的 Parcel 往返与字段完整性（Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class InferenceRequestTest {

    @Test
    public void request_parcelRoundTrip_preservesFields() {
        InferenceRequest original = new InferenceRequest(
                "req-1", "smollm-135m-instruct", "2026.08.1", "/data/x/model.gguf",
                2048, 4, 0.7f, 0.9f, 256);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        InferenceRequest restored = InferenceRequest.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(original.requestId, restored.requestId);
        assertEquals(original.modelId, restored.modelId);
        assertEquals(original.version, restored.version);
        assertEquals(original.modelPath, restored.modelPath);
        assertEquals(2048, restored.contextLength);
        assertEquals(4, restored.threadCount);
        assertEquals(0.7f, restored.temperature, 0.0001f);
        assertEquals(0.9f, restored.topP, 0.0001f);
        assertEquals(256, restored.maxNewTokens);
    }

    @Test
    public void stats_parcelRoundTrip_preservesFields() {
        InferenceStats original = new InferenceStats(
                InferenceStats.STATE_RUNNING, 12, 34, 1200, 5000);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        InferenceStats restored = InferenceStats.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(InferenceStats.STATE_RUNNING, restored.state);
        assertEquals(12, restored.promptTokens);
        assertEquals(34, restored.genTokens);
        assertEquals(1200, restored.ttftMs);
        assertEquals(5000, restored.elapsedMs);
    }

    @Test
    public void approvedModels_byId_unknownReturnsNull() {
        assertNull(ApprovedModels.byId("not-a-model"));
        assertNotNull(ApprovedModels.byId("smollm-135m-instruct"));
    }
}
