package com.example.localai.core.inference;

/**
 * JNI 推理会话（P3）：真实 llama.cpp 加载/生成/停止/释放边界。
 * 线程约定：实例方法 synchronized，同一会话串行访问；句柄 0 恒为无效值。
 * 流式回调由 Native 生成线程直接进入 StreamListener（顺序由单工作线程保证），
 * 调用方如需主线程分发，请在实现 StreamListener 时自行 post。
 */
public final class NativeSession implements AutoCloseable {

    // ---- 结构化错误码（必须与 ai_jni/native_session.cpp 保持镜像）----
    public static final int OK = 0;
    public static final int ERR_INVALID_HANDLE = 1001;
    public static final int ERR_WRONG_STATE = 1002;
    public static final int ERR_NULL_ARGUMENT = 1003;
    public static final int ERR_ILLEGAL_ARGUMENT = 1004;
    public static final int ERR_INTERNAL = 1099;
    public static final int ERR_MODEL_LOAD_FAILED = 1101;
    public static final int ERR_CONTEXT_CREATE_FAILED = 1102;
    public static final int ERR_TOKENIZE_FAILED = 1103;
    public static final int ERR_INPUT_TOO_LONG = 1104;

    /** 结束原因：自然结束（EOS/上限）。 */
    public static final int FINISH_END = 0;
    /** 结束原因：被 stop() 取消。 */
    public static final int FINISH_STOPPED = 1;

    /** 流式回调：onDelta/onFinished/onError 每个生成周期各最多一次，顺序稳定。 */
    public interface StreamListener {
        /** 增量文本片段（批量合并后回调，非逐 Token）。 */
        void onDelta(String text);

        /** 生成结束；reason 为 FINISH_END 或 FINISH_STOPPED。 */
        void onFinished(int reason);

        /** 生成失败；message 已脱敏。 */
        void onError(int code, String message);
    }

    private static volatile boolean libraryLoaded;

    /** 不透明 Native 句柄；0 表示已释放或创建失败。 */
    private long handle;

    public NativeSession() {
        ensureLibrary();
        this.handle = nativeCreate();
        if (this.handle == 0) {
            throw new NativeException(ERR_INTERNAL, "nativeCreate failed");
        }
    }

    /**
     * 加载 GGUF 模型（IDLE -> READY）。真实读取模型文件并创建上下文与采样器。
     * 该调用可能耗时数秒，禁止在 UI 主线程执行。
     */
    public synchronized void load(String modelPath, int contextLength, int threadCount,
                                  float temperature, float topP, int maxNewTokens) {
        ensureOpen(handle);
        requireNonEmpty(modelPath, "modelPath");
        if (contextLength <= 0) {
            throw new IllegalArgumentException("contextLength must be positive");
        }
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be positive");
        }
        if (maxNewTokens <= 0) {
            throw new IllegalArgumentException("maxNewTokens must be positive");
        }
        if (temperature < 0f) {
            throw new IllegalArgumentException("temperature must be >= 0");
        }
        if (topP <= 0f || topP > 1f) {
            throw new IllegalArgumentException("topP must be in (0, 1]");
        }
        check(nativeLoad(handle, modelPath, contextLength, threadCount,
                temperature, topP, maxNewTokens), "load");
    }

    /** 开始生成（READY -> RUNNING）；prompt 为完整格式化提示。 */
    public synchronized void start(String prompt, StreamListener listener) {
        ensureOpen(handle);
        requireNonEmpty(prompt, "prompt");
        if (listener == null) {
            throw new NullPointerException("listener must not be null");
        }
        check(nativeStart(handle, prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8), listener), "start");
    }

    /** 使用已加载模型的真实词表，包含与生成相同的模板特殊 token。 */
    public synchronized int countTokens(String prompt) {
        ensureOpen(handle);
        requireNonEmpty(prompt, "prompt");
        int count = nativeCountTokens(handle, prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (count < 0) check(-count, "countTokens");
        return count;
    }

    /** 请求取消生成（RUNNING -> IDLE）；未在运行时返回 WRONG_STATE。 */
    public synchronized void stop() {
        ensureOpen(handle);
        check(nativeStop(handle), "stop");
    }

    /** 返回 JSON 格式统计快照（状态、Token 计数、耗时；不含任何 Prompt/回复原文）。 */
    public synchronized String getStats() {
        ensureOpen(handle);
        return nativeGetStats(handle);
    }

    /** 幂等释放；等待工作线程退出后释放模型资源。重复调用为无操作。 */
    @Override
    public synchronized void close() {
        long h = handle;
        if (h != 0) {
            handle = 0;
            nativeRelease(h);
        }
    }

    // ---- 守卫（包级私有以便 JVM 单元测试直接覆盖）----

    static void ensureOpen(long handleValue) {
        if (handleValue == 0) {
            throw new IllegalStateException("session closed");
        }
    }

    static void requireNonEmpty(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    private static void check(int code, String op) {
        if (code != OK) {
            throw new NativeException(code, "native " + op + " failed");
        }
    }

    private static void ensureLibrary() {
        if (!libraryLoaded) {
            synchronized (NativeSession.class) {
                if (!libraryLoaded) {
                    System.loadLibrary("ai_jni");
                    libraryLoaded = true;
                }
            }
        }
    }

    /** 承载结构化错误码的运行时异常；message 已脱敏，不含路径等个人信息。 */
    public static final class NativeException extends RuntimeException {
        private final int code;

        public NativeException(int code, String message) {
            super(message + " (code=" + code + ")");
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    // ---- JNI 声明（符号名与 native_session.cpp 一一对应）----

    private static native long nativeCreate();

    private static native int nativeLoad(long handle, String modelPath, int contextLength,
                                         int threadCount, float temperature, float topP,
                                         int maxNewTokens);

    private static native int nativeStart(long handle, byte[] prompt, StreamListener listener);

    private static native int nativeCountTokens(long handle, byte[] prompt);

    private static native int nativeStop(long handle);

    private static native String nativeGetStats(long handle);

    private static native int nativeRelease(long handle);
}
