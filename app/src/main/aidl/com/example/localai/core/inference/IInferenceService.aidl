// 推理服务协议（P3）：UI 进程经 Binder 驱动独立推理进程。
// load 为同步耗时调用（客户端应在工作线程调用）；start/stop/getStats 为快路径。
package com.example.localai.core.inference;

import com.example.localai.core.inference.InferenceRequest;
import com.example.localai.core.inference.InferenceStats;
import com.example.localai.core.inference.IInferenceCallback;

interface IInferenceService {
    int load(in InferenceRequest request);

    int start(String prompt, IInferenceCallback cb);

    int countTokens(String prompt);

    int stop();

    InferenceStats getStats();

    int getPid();

    void releaseSession();
}
