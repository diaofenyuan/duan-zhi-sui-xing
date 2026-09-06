# local-ai Native 构建入口（S005）。
# 只负责把锁定的 llama.cpp 源码树（engine_llama/）以 arm64-v8a CPU/NEON
# 共享库形式编译出来；JNI 封装层在后续步骤加入，本文件不接任何业务代码。
# 锁定信息见 docs/native-baseline.md。
cmake_minimum_required(VERSION 3.22.1)
project(local_ai_native LANGUAGES C CXX)

# ---- llama.cpp / ggml 构建开关（CPU-only，Android bionic 约束）----
set(BUILD_SHARED_LIBS    ON  CACHE BOOL "" FORCE) # 直接产出 .so 作为本步验证证据
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI       OFF CACHE BOOL "" FORCE) # 必须关：避免配置期联网拉取 WebUI
set(LLAMA_USE_PREBUILT_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE) # Android 无 OpenSSL
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE) # bionic 无 OpenMP
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE) # 交叉编译禁 -march=native；aarch64 默认启用 NEON
set(GGML_BACKEND_DL      OFF CACHE BOOL "" FORCE) # 后端静态并入 .so，不拆分运行时加载
set(GGML_CCACHE          OFF CACHE BOOL "" FORCE)

add_subdirectory(engine_llama)

# 调试 APK 也要使用优化后的推理内核，否则长输入预填充严重变慢。
# 保留调试符号和断言，JNI 边界继续使用默认调试选项，方便定位生命周期问题。
foreach(inference_target llama ggml ggml-base ggml-cpu)
    target_compile_options(${inference_target} PRIVATE $<$<CONFIG:Debug>:-O2>)
endforeach()

# ---- JNI 封装层（S006 建立，P3 接入真实推理）----
# 链接 llama/ggml 目标与 Android log（logcat 诊断输出）。
add_library(ai_jni SHARED ai_jni/native_session.cpp)
target_link_libraries(ai_jni PRIVATE llama ggml log)
