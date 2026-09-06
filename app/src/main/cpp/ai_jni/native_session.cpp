// local-ai JNI inference layer (P3).
// Real llama.cpp integration: load a GGUF model, generate tokens on a worker
// thread, stream batched text pieces to Java, support fast cancel/release.
// Error codes mirror com.example.localai.core.inference.NativeSession.
//
// Threading model:
//   - load/stop/getStats/release run on the caller (Java) thread
//   - generation runs on one worker thread owned by the Session
//   - release() sets cancel, joins the worker, then frees llama resources
//   - callbacks to Java use a global ref held by the Session and are only
//     touched by the worker thread (except start(), which installs it first)

#include <jni.h>

#include <android/log.h>

#include <llama.h>

#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

// ---- error codes (mirror NativeSession.java) ----
constexpr jint EC_OK = 0;
constexpr jint EC_INVALID_HANDLE = 1001;
constexpr jint EC_WRONG_STATE = 1002;
constexpr jint EC_NULL_ARGUMENT = 1003;
constexpr jint EC_ILLEGAL_ARGUMENT = 1004;
constexpr jint EC_INTERNAL = 1099;
constexpr jint EC_MODEL_LOAD_FAILED = 1101;
constexpr jint EC_CONTEXT_CREATE_FAILED = 1102;
constexpr jint EC_TOKENIZE_FAILED = 1103;
constexpr jint EC_INPUT_TOO_LONG = 1104;

// ---- finish reasons (mirror NativeSession.java) ----
constexpr jint FINISH_END = 0;
constexpr jint FINISH_STOPPED = 1;

// ---- session states ----
enum SessionState : int {
    STATE_IDLE = 0,
    STATE_READY = 1,
    STATE_RUNNING = 2,
};

constexpr int FLUSH_TOKEN_COUNT = 8;
constexpr int FLUSH_CHAR_COUNT = 64;
constexpr int FLUSH_INTERVAL_MS = 60;

constexpr int MAX_PROMPT_BYTES = 256 * 1024;

void logInfo(const char * fmt, ...) {
    char buf[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_INFO, "local-ai-jni", "%s", buf);
}

void llamaLogHandler(ggml_log_level level, const char * text, void * /*user_data*/) {
    int prio = ANDROID_LOG_VERBOSE;
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) {
        prio = ANDROID_LOG_WARN;
    }
    __android_log_print(prio, "local-ai-llama", "%s", text);
}

struct JniListener {
    jobject object = nullptr;      // global ref
    jclass cls = nullptr;          // global ref
    jmethodID on_delta = nullptr;
    jmethodID on_finished = nullptr;
    jmethodID on_error = nullptr;
};

struct Session {
    explicit Session(JavaVM * java_vm) : vm(java_vm) {}

    std::atomic<int> state{STATE_IDLE};
    std::atomic<bool> cancel{false};

    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * smpl = nullptr;

    int max_new_tokens = 256;
    float temperature = 0.7f;
    float top_p = 0.9f;

    JavaVM * vm;
    std::thread worker;
    JniListener listener;

    std::atomic<int64_t> prompt_tokens{0};
    std::atomic<int64_t> gen_tokens{0};
    std::atomic<int64_t> ttft_ms{-1};
    std::atomic<int64_t> elapsed_ms{0};
};

class HandleTable {
public:
    jlong create(JavaVM * vm) {
        std::lock_guard<std::mutex> lock(mutex_);
        const jlong id = next_id_++;
        sessions_.emplace(id, std::make_shared<Session>(vm));
        return id;
    }

    std::shared_ptr<Session> acquire(jlong id) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = sessions_.find(id);
        if (it == sessions_.end()) {
            return nullptr;
        }
        return it->second;
    }

    bool release(jlong id) {
        std::lock_guard<std::mutex> lock(mutex_);
        return sessions_.erase(id) > 0;
    }

private:
    std::mutex mutex_;
    std::unordered_map<jlong, std::shared_ptr<Session>> sessions_;
    jlong next_id_ = 1;
};

HandleTable & table() {
    static HandleTable instance;
    return instance;
}

const char * stateName(int state) {
    switch (state) {
        case STATE_READY:
            return "READY";
        case STATE_RUNNING:
            return "RUNNING";
        default:
            return "IDLE";
    }
}

jint throwNativeException(JNIEnv * env, jint code, const char * message) {
    if (env->ExceptionCheck() == JNI_TRUE) {
        return EC_INTERNAL;
    }
    jclass cls = env->FindClass("com/example/localai/core/inference/NativeSession$NativeException");
    if (cls == nullptr) {
        return EC_INTERNAL;
    }
    std::string text = message;
    text += " (code=" + std::to_string(code) + ")";
    if (env->ThrowNew(cls, text.c_str()) != JNI_OK) {
        return EC_INTERNAL;
    }
    return code;
}

void clearListener(JNIEnv * env, Session * s) {
    if (s->listener.object != nullptr) {
        env->DeleteGlobalRef(s->listener.object);
        s->listener.object = nullptr;
    }
    if (s->listener.cls != nullptr) {
        env->DeleteGlobalRef(s->listener.cls);
        s->listener.cls = nullptr;
    }
    s->listener.on_delta = nullptr;
    s->listener.on_finished = nullptr;
    s->listener.on_error = nullptr;
}

bool installListener(JNIEnv * env, const std::shared_ptr<Session> & s, jobject listener) {
    if (listener == nullptr) {
        return false;
    }
    jclass local = env->FindClass("com/example/localai/core/inference/NativeSession$StreamListener");
    if (local == nullptr) {
        return false;
    }
    s->listener.cls = static_cast<jclass>(env->NewGlobalRef(local));
    s->listener.object = env->NewGlobalRef(listener);
    s->listener.on_delta = env->GetMethodID(local, "onDelta", "(Ljava/lang/String;)V");
    s->listener.on_finished = env->GetMethodID(local, "onFinished", "(I)V");
    s->listener.on_error = env->GetMethodID(local, "onError", "(ILjava/lang/String;)V");
    env->DeleteLocalRef(local);
    if (s->listener.on_delta == nullptr || s->listener.on_finished == nullptr
            || s->listener.on_error == nullptr) {
        clearListener(env, s.get());
        return false;
    }
    return true;
}

bool abortCallback(void * data) {
    return *static_cast<std::atomic<bool> *>(data);
}

std::string pieceToText(const llama_vocab * vocab, llama_token token) {
    char buf[512];
    const int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        return "";
    }
    return std::string(buf, static_cast<size_t>(n));
}

// Decode a chunked prompt; returns EC_OK or an error code.
// v0.2.0 API: llama_batch_get_one(tokens, n) tracks positions automatically.
jint decodePrompt(const std::shared_ptr<Session> & s, const std::vector<llama_token> & tokens) {
    const int n_batch = static_cast<int>(llama_n_batch(s->ctx));
    size_t off = 0;
    while (off < tokens.size()) {
        if (s->cancel.load()) {
            return EC_OK; // cancelled before first sample; worker reports stopped
        }
        const size_t n = std::min<size_t>(static_cast<size_t>(n_batch), tokens.size() - off);
        llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + off),
                static_cast<int32_t>(n));
        const int ret = llama_decode(s->ctx, batch);
        if (ret == 2) {
            return EC_OK; // aborted via cancel flag
        }
        if (ret != 0) {
            return EC_INTERNAL;
        }
        off += n;
    }
    return EC_OK;
}

void generationWorker(std::shared_ptr<Session> s, std::string prompt) {
    if (s->vm == nullptr) {
        s->state.store(STATE_READY);
        return;
    }
    JNIEnv * env = nullptr;
    bool attached = false;
    if (s->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (s->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            s->state.store(STATE_READY);
            return;
        }
        attached = true;
    }

    const auto t_start = std::chrono::steady_clock::now();
    int64_t t_last_flush_ms = 0;
    bool first_flush = true;
    std::string batch_text;
    s->ttft_ms.store(-1);
    s->elapsed_ms.store(0);

    auto flush = [&]() {
        if (batch_text.empty()) {
            return;
        }
        jstring jtext = env->NewStringUTF(batch_text.c_str());
        if (jtext != nullptr) {
            env->CallVoidMethod(s->listener.object, s->listener.on_delta, jtext);
            env->DeleteLocalRef(jtext);
        }
        if (first_flush) {
            first_flush = false;
            const auto now = std::chrono::steady_clock::now();
            s->ttft_ms.store(static_cast<int64_t>(std::chrono::duration_cast<
                    std::chrono::milliseconds>(now - t_start).count()));
        }
        batch_text.clear();
    };

    auto fail = [&](jint code, const char * message) {
        flush();
        jstring jmsg = env->NewStringUTF(message);
        if (jmsg != nullptr) {
            env->CallVoidMethod(s->listener.object, s->listener.on_error, code, jmsg);
            env->DeleteLocalRef(jmsg);
        }
    };

    const llama_vocab * vocab = llama_model_get_vocab(s->model);
    s->cancel.store(false);
    logInfo("generation start: promptLen=%zu eos=%d eot=%d",
            prompt.size(), static_cast<int>(llama_vocab_eos(vocab)),
            static_cast<int>(llama_vocab_eot(vocab)));

    std::vector<llama_token> tokens(1024);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                  tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (n_tokens < 0) {
        tokens.resize(static_cast<size_t>(-n_tokens));
        n_tokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                  tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n_tokens < 0) {
        fail(EC_TOKENIZE_FAILED, "prompt tokenization failed");
        goto done;
    }
    tokens.resize(static_cast<size_t>(n_tokens));

    {
        const int ctx_size = static_cast<int>(llama_n_ctx(s->ctx));
        // 保留完整输入和回复空间，不能静默截断 ChatML 或在解码中途耗尽上下文。
        if (static_cast<int64_t>(tokens.size()) + s->max_new_tokens > ctx_size) {
            fail(EC_INPUT_TOO_LONG, "input exceeds context budget");
            goto done;
        }
    }

    llama_memory_clear(llama_get_memory(s->ctx), true);
    llama_synchronize(s->ctx);
    s->prompt_tokens.store(static_cast<int64_t>(tokens.size()));
    s->gen_tokens.store(0);

    if (decodePrompt(s, tokens) != EC_OK) {
        fail(EC_INTERNAL, "prompt decode failed");
        goto done;
    }

    {
        const float * logits = llama_get_logits_ith(s->ctx, -1);
        float lo = logits[0];
        float hi = logits[0];
        int nan_count = 0;
        const int n_vocab = llama_vocab_n_tokens(vocab);
        for (int i = 0; i < n_vocab; ++i) {
            const float v = logits[i];
            if (v != v) {
                ++nan_count;
                continue;
            }
            if (v < lo) {
                lo = v;
            }
            if (v > hi) {
                hi = v;
            }
        }
        logInfo("post-decode logits: n_vocab=%d min=%f max=%f nan=%d", n_vocab, lo, hi, nan_count);
    }

    {
        const llama_token eos = llama_vocab_eos(vocab);
        const llama_token eot = llama_vocab_eot(vocab);

        for (;;) {
            if (s->cancel.load()) {
                break;
            }
            const llama_token token = llama_sampler_sample(s->smpl, s->ctx, -1);
            llama_sampler_accept(s->smpl, token);
            if (s->gen_tokens.load() == 0) {
                logInfo("first sampled token id=%d", static_cast<int>(token));
            }
            if (token == eos || token == eot) {
                break;
            }
            const std::string piece = pieceToText(vocab, token);
            if (!piece.empty()) {
                batch_text += piece;
            }
            s->gen_tokens.fetch_add(1);

            const auto now = std::chrono::steady_clock::now();
            const int64_t now_ms = static_cast<int64_t>(std::chrono::duration_cast<
                    std::chrono::milliseconds>(now - t_start).count());
            if (batch_text.size() >= FLUSH_CHAR_COUNT
                    || s->gen_tokens.load() % FLUSH_TOKEN_COUNT == 0
                    || now_ms - t_last_flush_ms >= FLUSH_INTERVAL_MS) {
                t_last_flush_ms = now_ms;
                flush();
            }
            if (s->gen_tokens.load() >= s->max_new_tokens) {
                break;
            }
            llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
            const int ret = llama_decode(s->ctx, batch);
            if (ret == 2) {
                break; // aborted via cancel flag
            }
            if (ret != 0) {
                fail(EC_INTERNAL, "generation decode failed");
                goto done;
            }
        }
    }

    flush();
    {
        const auto now = std::chrono::steady_clock::now();
        s->elapsed_ms.store(static_cast<int64_t>(std::chrono::duration_cast<
                std::chrono::milliseconds>(now - t_start).count()));
    }
    logInfo("generation done: prompt=%lld tokens, gen=%lld tokens, ttft=%lld ms, cancelled=%d",
            static_cast<long long>(s->prompt_tokens.load()),
            static_cast<long long>(s->gen_tokens.load()),
            static_cast<long long>(s->ttft_ms.load()),
            s->cancel.load() ? 1 : 0);
    env->CallVoidMethod(s->listener.object, s->listener.on_finished,
                        s->cancel.load() ? FINISH_STOPPED : FINISH_END);

done:
    clearListener(env, s.get());
    // 生成结束回到 READY：同一会话可继续下一轮 start（模型已加载）
    s->state.store(STATE_READY);
    if (attached) {
        s->vm->DetachCurrentThread();
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeCreate(JNIEnv * env, jclass /*clazz*/) {
    try {
        JavaVM * vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK || vm == nullptr) {
            throwNativeException(env, EC_INTERNAL, "GetJavaVM failed");
            return 0;
        }
        return table().create(vm);
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return 0;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeCreate unknown error");
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeLoad(
        JNIEnv * env, jclass /*clazz*/, jlong handle, jstring model_path,
        jint context_length, jint thread_count, jfloat temperature, jfloat top_p,
        jint max_new_tokens) {
    try {
        auto session = table().acquire(handle);
        if (!session) {
            return EC_INVALID_HANDLE;
        }
        if (model_path == nullptr) {
            return EC_NULL_ARGUMENT;
        }
        const char * path_chars = env->GetStringUTFChars(model_path, nullptr);
        if (path_chars == nullptr) {
            return EC_INTERNAL;
        }
        const std::string path(path_chars);
        env->ReleaseStringUTFChars(model_path, path_chars);
        if (path.empty()) {
            return EC_ILLEGAL_ARGUMENT;
        }
        if (context_length <= 0 || thread_count <= 0
                || max_new_tokens <= 0 || temperature < 0.0f || top_p <= 0.0f || top_p > 1.0f) {
            return EC_ILLEGAL_ARGUMENT;
        }
        int expected = STATE_IDLE;
        if (!session->state.compare_exchange_strong(expected, STATE_READY)) {
            return EC_WRONG_STATE;
        }

        llama_backend_init();
        llama_log_set(llamaLogHandler, nullptr);

        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0; // CPU-only MVP

        session->model = llama_model_load_from_file(path.c_str(), mparams);
        if (session->model == nullptr) {
            llama_backend_free();
            session->state.store(STATE_IDLE);
            return EC_MODEL_LOAD_FAILED;
        }

        const int32_t train_ctx = llama_model_n_ctx_train(session->model);
        int32_t n_ctx = context_length;
        if (train_ctx > 0 && n_ctx > train_ctx) {
            n_ctx = train_ctx;
        }

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = static_cast<uint32_t>(n_ctx);
        cparams.n_batch = 2048;
        cparams.n_ubatch = 512;
        cparams.n_threads = thread_count;
        cparams.n_threads_batch = thread_count;
        cparams.abort_callback = abortCallback;
        cparams.abort_callback_data = &session->cancel;

        session->ctx = llama_init_from_model(session->model, cparams);
        if (session->ctx == nullptr) {
            llama_model_free(session->model);
            session->model = nullptr;
            llama_backend_free();
            session->state.store(STATE_IDLE);
            return EC_CONTEXT_CREATE_FAILED;
        }

        session->smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(session->smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(session->smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(session->smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(session->smpl, llama_sampler_init_dist(
                static_cast<uint32_t>(std::chrono::steady_clock::now()
                                              .time_since_epoch().count())));

        session->temperature = temperature;
        session->top_p = top_p;
        session->max_new_tokens = max_new_tokens;

        logInfo("model loaded: %s (ctx=%d, threads=%d)", path.c_str(), n_ctx, thread_count);
        return EC_OK;
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return EC_INTERNAL;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeLoad unknown error");
        return EC_INTERNAL;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeStart(
        JNIEnv * env, jclass /*clazz*/, jlong handle, jbyteArray prompt, jobject listener) {
    try {
        auto session = table().acquire(handle);
        if (!session) {
            return EC_INVALID_HANDLE;
        }
        if (prompt == nullptr || listener == nullptr) {
            return EC_NULL_ARGUMENT;
        }
        const jsize length = env->GetArrayLength(prompt);
        if (length > MAX_PROMPT_BYTES) return EC_INPUT_TOO_LONG;
        int expected = STATE_READY;
        if (!session->state.compare_exchange_strong(expected, STATE_RUNNING)) {
            return EC_WRONG_STATE;
        }
        // Java 明确传标准 UTF-8，避免 Modified UTF-8 将表情等补充平面字符拆坏。
        std::string prompt_text(static_cast<size_t>(length), '\0');
        env->GetByteArrayRegion(prompt, 0, length, reinterpret_cast<jbyte *>(prompt_text.data()));
        if (env->ExceptionCheck()) {
            session->state.store(STATE_READY);
            return EC_INTERNAL;
        }

        if (!installListener(env, session, listener)) {
            session->state.store(STATE_READY);
            return EC_INTERNAL;
        }

        if (session->worker.joinable()) {
            session->worker.join();
        }

        session->cancel.store(false);
        session->worker = std::thread(generationWorker, session, prompt_text);
        return EC_OK;
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return EC_INTERNAL;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeStart unknown error");
        return EC_INTERNAL;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeCountTokens(
        JNIEnv * env, jclass /*clazz*/, jlong handle, jbyteArray prompt) {
    try {
        auto session = table().acquire(handle);
        if (!session) return -EC_INVALID_HANDLE;
        if (session->state.load() != STATE_READY) return -EC_WRONG_STATE;
        if (prompt == nullptr) return -EC_NULL_ARGUMENT;
        const jsize length = env->GetArrayLength(prompt);
        if (length > MAX_PROMPT_BYTES) return -EC_INPUT_TOO_LONG;
        std::string text(static_cast<size_t>(length), '\0');
        env->GetByteArrayRegion(prompt, 0, length, reinterpret_cast<jbyte *>(text.data()));
        if (env->ExceptionCheck()) return -EC_INTERNAL;
        const int count = llama_tokenize(llama_model_get_vocab(session->model), text.data(),
                                        length, nullptr, 0, true, true);
        return count < 0 ? -count : count;
    } catch (...) {
        return -EC_TOKENIZE_FAILED;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeStop(JNIEnv * env, jclass /*clazz*/, jlong handle) {
    try {
        auto session = table().acquire(handle);
        if (!session) {
            return EC_INVALID_HANDLE;
        }
        int expected = STATE_RUNNING;
        if (!session->state.compare_exchange_strong(expected, STATE_IDLE)) {
            return EC_WRONG_STATE;
        }
        session->cancel.store(true);
        return EC_OK;
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return EC_INTERNAL;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeStop unknown error");
        return EC_INTERNAL;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeGetStats(JNIEnv * env, jclass /*clazz*/, jlong handle) {
    try {
        auto session = table().acquire(handle);
        if (!session) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "invalid handle");
            return nullptr;
        }
        // stats snapshot only; never contains prompt or reply text
        std::string json = "{\"state\":\"";
        json += stateName(session->state.load());
        json += "\",\"promptTokens\":";
        json += std::to_string(session->prompt_tokens.load());
        json += ",\"genTokens\":";
        json += std::to_string(session->gen_tokens.load());
        json += ",\"ttftMs\":";
        json += std::to_string(session->ttft_ms.load());
        json += ",\"elapsedMs\":";
        json += std::to_string(session->elapsed_ms.load());
        json += "}";
        return env->NewStringUTF(json.c_str());
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return nullptr;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeGetStats unknown error");
        return nullptr;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_localai_core_inference_NativeSession_nativeRelease(JNIEnv * env, jclass /*clazz*/, jlong handle) {
    try {
        auto session = table().acquire(handle);
        if (!session) {
            return EC_INVALID_HANDLE;
        }
        table().release(handle);
        session->cancel.store(true);
        if (session->worker.joinable()) {
            session->worker.join();
        }
        if (session->smpl != nullptr) {
            llama_sampler_free(session->smpl);
            session->smpl = nullptr;
        }
        if (session->ctx != nullptr) {
            llama_free(session->ctx);
            session->ctx = nullptr;
        }
        if (session->model != nullptr) {
            llama_model_free(session->model);
            session->model = nullptr;
        }
        llama_backend_free();
        session->state.store(STATE_IDLE);
        return EC_OK;
    } catch (const std::exception & e) {
        throwNativeException(env, EC_INTERNAL, e.what());
        return EC_INTERNAL;
    } catch (...) {
        throwNativeException(env, EC_INTERNAL, "nativeRelease unknown error");
        return EC_INTERNAL;
    }
}

} // extern "C"
