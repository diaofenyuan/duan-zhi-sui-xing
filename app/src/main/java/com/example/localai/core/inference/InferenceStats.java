package com.example.localai.core.inference;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 推理统计快照（AIDL 返回）：只含状态与计数/耗时，不含任何 Prompt 或回复原文。
 */
public final class InferenceStats implements Parcelable {

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_READY = "READY";
    public static final String STATE_RUNNING = "RUNNING";

    public final String state;
    public final long promptTokens;
    public final long genTokens;
    public final long ttftMs;
    public final long elapsedMs;

    public InferenceStats(String state, long promptTokens, long genTokens,
                          long ttftMs, long elapsedMs) {
        this.state = state;
        this.promptTokens = promptTokens;
        this.genTokens = genTokens;
        this.ttftMs = ttftMs;
        this.elapsedMs = elapsedMs;
    }

    protected InferenceStats(Parcel in) {
        state = in.readString();
        promptTokens = in.readLong();
        genTokens = in.readLong();
        ttftMs = in.readLong();
        elapsedMs = in.readLong();
    }

    public static final Creator<InferenceStats> CREATOR = new Creator<InferenceStats>() {
        @Override
        public InferenceStats createFromParcel(Parcel in) {
            return new InferenceStats(in);
        }

        @Override
        public InferenceStats[] newArray(int size) {
            return new InferenceStats[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(state);
        dest.writeLong(promptTokens);
        dest.writeLong(genTokens);
        dest.writeLong(ttftMs);
        dest.writeLong(elapsedMs);
    }
}
