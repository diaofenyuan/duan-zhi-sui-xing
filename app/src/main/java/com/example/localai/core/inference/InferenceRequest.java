package com.example.localai.core.inference;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 跨进程推理请求（AIDL）：一次模型加载所需的全部参数。
 * requestId 用于会话追踪；模型路径由 UI 进程解析后传入，服务侧校验白名单。
 */
public final class InferenceRequest implements Parcelable {

    public final String requestId;
    public final String modelId;
    public final String version;
    public final String modelPath;
    public final int contextLength;
    public final int threadCount;
    public final float temperature;
    public final float topP;
    public final int maxNewTokens;

    public InferenceRequest(String requestId, String modelId, String version, String modelPath,
                            int contextLength, int threadCount, float temperature, float topP,
                            int maxNewTokens) {
        this.requestId = requestId;
        this.modelId = modelId;
        this.version = version;
        this.modelPath = modelPath;
        this.contextLength = contextLength;
        this.threadCount = threadCount;
        this.temperature = temperature;
        this.topP = topP;
        this.maxNewTokens = maxNewTokens;
    }

    protected InferenceRequest(Parcel in) {
        requestId = in.readString();
        modelId = in.readString();
        version = in.readString();
        modelPath = in.readString();
        contextLength = in.readInt();
        threadCount = in.readInt();
        temperature = in.readFloat();
        topP = in.readFloat();
        maxNewTokens = in.readInt();
    }

    public static final Creator<InferenceRequest> CREATOR = new Creator<InferenceRequest>() {
        @Override
        public InferenceRequest createFromParcel(Parcel in) {
            return new InferenceRequest(in);
        }

        @Override
        public InferenceRequest[] newArray(int size) {
            return new InferenceRequest[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(requestId);
        dest.writeString(modelId);
        dest.writeString(version);
        dest.writeString(modelPath);
        dest.writeInt(contextLength);
        dest.writeInt(threadCount);
        dest.writeFloat(temperature);
        dest.writeFloat(topP);
        dest.writeInt(maxNewTokens);
    }
}
