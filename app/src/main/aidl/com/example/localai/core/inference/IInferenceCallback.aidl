// 推理回调协议（P3）：跨进程流式事件。
// 全部 oneway：服务进程生成线程不等待 UI 进程消费，避免阻塞推理。
package com.example.localai.core.inference;

oneway interface IInferenceCallback {
    void onToken(String batch);

    void onFinished(int reason);

    void onError(int code, String message);
}
