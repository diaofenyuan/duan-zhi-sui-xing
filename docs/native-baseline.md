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
| 本项目 patch 列表 | 空——未修改 engine_llama/ 内任何文件 |
| 导入方式 | codeload tarball `refs/tags/v0.2.0` 解包去根目录至 `app/src/main/cpp/engine_llama/` |
| tarball SHA-256 | `72E6C3E70C584F84E61697E449EE388F43458D662EF8F3BD3F6B4A054C947958` |

## 2. 构建配置

- 工具链：AGP 8.13.2 + NDK r28 (28.2.13676358) + CMake 3.22.1 + Java 17（见 `docs/build-baseline.md`）。
- ABI：仅 `arm64-v8a`（`app/build.gradle` abiFilters）。
- CMake 入口：`app/src/main/cpp/CMakeLists.txt` → `add_subdirectory(engine_llama)`。
- 关键开关与理由：
  | 开关 | 值 | 理由 |
  | --- | --- | --- |
  | `BUILD_SHARED_LIBS` | ON | 直接产出 .so 作为本步验证证据 |
  | `GGML_OPENMP` | OFF | Android bionic 无 OpenMP |
  | `GGML_NATIVE` | OFF | 交叉编译禁 `-march=native`；aarch64 目标默认启用 NEON |
  | `GGML_BACKEND_DL` | OFF | 后端静态并入 .so，不做运行时动态后端加载 |
  | `LLAMA_BUILD_COMMON/TESTS/TOOLS/EXAMPLES/SERVER/APP` | OFF | 只需要 libllama + ggml 核心 |
  | `LLAMA_BUILD_UI` / `LLAMA_USE_PREBUILT_UI` | OFF | 避免配置期联网拉取 WebUI 资产 |
  | `LLAMA_OPENSSL` | OFF | Android 无 OpenSSL |

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
