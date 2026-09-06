# Native 构建基线（S005）

> 状态：`PASS`（构建证据齐备；真机加载验证按计划属于 S007，当前无设备，见 `docs/device-baseline.md`）。

## 1. 源码锁定

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/ggml-org/llama.cpp |
| 锁定 tag | `v0.2.0`（2026-08-21 发布，官方首个语义化稳定版，发布说明推荐下游锁定 vX.Y.Z 而非 b[NUM] nightly） |
| tag 对象 sha（annotated） | `8a35040e02747e136d901793604572c7ca6d0793` |
| **源码 commit** | **`bb4caa7540188872173c44d161602d9271386413`** |
| ggml 版本 | 随 v0.2.0 内嵌（ggml/ 子目录，版本 0.21.x，见 `ggml/CMakeLists.txt`） |
| 本项目 patch 列表 | Vulkan 特性查询通过现有动态 dispatcher 调用，兼容 Android API 26 链接；Vulkan 1.0 loader 缺少版本查询入口时安全禁用 GPU |
| 导入方式 | codeload tarball `refs/tags/v0.2.0` 解包去根目录至 `app/src/main/cpp/engine_llama/` |
| tarball SHA-256 | `72E6C3E70C584F84E61697E449EE388F43458D662EF8F3BD3F6B4A054C947958` |

## 2. 构建配置

- 工具链：AGP 8.13.2 + NDK r28 (28.2.13676358) + CMake 3.22.1 + Java 17（见 `docs/build-baseline.md`）。
- ABI：Release 为 `arm64-v8a`；Debug 另外包含 `x86_64`，用于模拟器验证。
- CMake 入口：`app/src/main/cpp/CMakeLists.txt` → `add_subdirectory(engine_llama)`。
- 关键开关与理由：
  | 开关 | 值 | 理由 |
  | --- | --- | --- |
  | `BUILD_SHARED_LIBS` | ON | 直接产出 .so 作为本步验证证据 |
  | `GGML_OPENMP` | OFF | Android bionic 无 OpenMP |
  | `GGML_NATIVE` | OFF | 交叉编译禁 `-march=native`；aarch64 目标默认启用 NEON |
  | `GGML_BACKEND_DL` | OFF | 后端静态并入 .so，不做运行时动态后端加载 |
  | `GGML_VULKAN` | ON | 支持 GPU 层卸载；运行时需要 Vulkan 1.2 及后端要求的设备特性，不支持时仍可使用 CPU |
  | `LLAMA_BUILD_COMMON/TESTS/TOOLS/EXAMPLES/SERVER/APP` | OFF | 只需要 libllama + ggml 核心 |
  | `LLAMA_BUILD_UI` / `LLAMA_USE_PREBUILT_UI` | OFF | 避免配置期联网拉取 WebUI 资产 |
  | `LLAMA_OPENSSL` | OFF | Android 无 OpenSSL |

2026-09-06：`vulkan-dependencies.cmake` 固定 Vulkan-Headers / SPIRV-Headers 为 SDK 1.4.341.0，并校验下载 SHA-256；首次构建需要联网，着色器编译使用 NDK 自带 `glslc`。Windows 构建需先在同一个终端运行 Visual Studio Build Tools 的 `vcvars64.bat`，让原生主机编译器能构建着色器生成工具，再运行 `gradlew.bat :app:assembleRelease`。设置中的 GPU 层数经 Parcelable、推理服务和 JNI 传给 `n_gpu_layers`：0 为 CPU，-1 为全部层。手动上下文优先于运行模式，按模型上限裁剪；修改后下一轮重新加载。

## 3. 构建产物（Debug，2026-08-23）

命令：`.\gradlew.bat :app:externalNativeBuildDebug --offline --console=plain` → BUILD SUCCESSFUL。

产物目录：`app/build/intermediates/cxx/Debug/<hash>/obj/arm64-v8a/`
（llvm-readelf 复核：`Type: DYN (Shared object file)`、`Machine: AArch64`）

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| libllama.so | 78.2 MB（Debug 含符号） | `461DDD78553A8276BA9F91D00C13E67E852E0A7856AB96ECAED640ADF5878F7D` |
| libggml.so | 1.9 MB | `FF5CF87607A79EAA435F19363BA28B11852C1EF7771E10D7AE4F697BE4C3ED65` |
| libggml-base.so | 7.5 MB | `7B9660F814782FD7B2C852DB4234BE3E3B2721782514E13627B1405769B1FBF7` |
| libggml-cpu.so | 3.2 MB | `68AC945984579F95C41C4CF76BA3816D01D0E76E6962A9245CFC0181CD9ADC54` |

## 4. 已知告警（不影响本步通过标准）

- `[CXX5304] SDK XML version 4 > supported 3`：cmdline-tools 与 AGP 的 SDK 描述文件版本差异提示，构建正常完成；后续升级 AGP 或 cmdline-tools 任一方可消除。
- Gradle 9.0 deprecation 前瞻警告：与 S002 相同，属常规提示。

## 5. 下一步接口预告

S006 将在 `app/src/main/cpp/ai_jni/` 新增 JNI 封装并链接 `llama`/`ggml` 目标；届时若改为单一大 .so 静态并入方案，需同步更新本文件的产物清单。

## 6. P3 接入：JNI 推理层（2026-08-23）

- JNI 层：\pp/src/main/cpp/ai_jni/native_session.cpp\ 重写为真实 llama.cpp 接入：
  - 加载链：\llama_backend_init\ -> \llama_model_load_from_file\（n_gpu_layers=0，CPU-only）-> \llama_init_from_model\（n_batch=2048 / n_ubatch=512，abort_callback 挂原子取消标志）-> 采样器链 top_k(40) + top_p + temp + dist。
  - 生成：单工作线程 tokenize（add_special/parse_special）-> 分批 decode 提示 -> 采样循环 -> \llama_token_to_piece\ 批量合并（8 token / 64 字符 / 60ms 窗口）回调 Java；EOS/EOT/maxNewTokens/cancel 终止。
  - 生命周期：stop 只置取消标志（decode 经 abort_callback 快速返回）；release 置取消后 join 工作线程再释放 smpl/ctx/model/backend；重复释放幂等；C++ 异常不穿 JNI。
  - 每轮生成前 \llama_memory_clear(llama_get_memory(ctx), true)\ 重置 KV（全量历史重发策略）。
- 错误码扩展：MODEL_LOAD_FAILED=1101、CONTEXT_CREATE_FAILED=1102、TOKENIZE_FAILED=1103（Java/C++ 双侧镜像）。
- ABI 说明：release 保持 arm64-v8a；debug ABI 为 x86_64（模拟器验证用，见 build-baseline.md）。
