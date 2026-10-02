# 端智随行（local-ai）

Android 端侧本地 AI 应用：在手机上离线发现、下载并运行小参数语言模型，完成中文对话、图片/PDF 文字识别与离线资料问答。除模型目录与权重下载外，对话和推理全部在本机完成，不上传聊天内容。

| 项 | 值 |
| --- | --- |
| 应用标识 | `com.example.localai` |
| 版本 | `0.3.0`（versionCode 3） |
| 发布 ABI | `arm64-v8a` |
| 调试 ABI | `x86_64`（仅模拟器/专项测试） |
| 最低系统 | Android 8.0（API 26） |

## 功能

| 模块 | 说明 |
| --- | --- |
| 模型市场 | 8 个 Qwen2.5 模型（0.5B / 1.5B × Instruct / Coder × Q4_K_M / Q8_0）；目录与清单经 Ed25519 签名校验 |
| 下载安装 | HTTP 断点续传、SHA-256 与 GGUF 校验、安装回滚、任务持久化与失败重试 |
| 本地聊天 | 流式输出、随时停止、按真实分词器计算上下文预算与历史裁剪、会话增量保存 |
| 资料库 | 离线中文图片 OCR（随包 ONNX 模型）、PDF 解析、离线资料问答、可编辑任务工作区 |
| 诊断 | 真实设备画像、运行模式策略（按电量/温度/内存调整线程与上下文）、兼容性打分 |
| 设置 | 运行模式、生成时常亮、模型手动释放、字体上限 150%、APK 内离线阅读的完整第三方许可 |

## 架构要点

- **单 Activity + 底部 5 Tab**：市场 / 聊天 / 下载 / 诊断 / 设置；二级页（详情、历史）走返回栈。设计令牌与页面状态矩阵见 `docs/ui-design.md`。
- **推理独立进程**：`InferenceService` 运行于 `:inference` 进程，该进程不含网络代码路径、不访问业务数据库；UI 侧通过 AIDL 与 `InferenceClient` 通信，UI 进程不直接加载 Native 库。
- **推理内核**：vendored llama.cpp（`app/src/main/cpp/engine_llama/`，锁定版本见 `docs/native-baseline.md`）+ 自写 JNI 桥接 `app/src/main/cpp/ai_jni/`。流式输出以标准 UTF-8 字节跨批次传递并缓存不完整字符，避免多字节字符被截断。
- **语言**：Kotlin 为主；保留 Java 仅限边界：`data/room/`（Room 注解类，走 `annotationProcessor` 以避开离线不可用的 KSP/kapt）、`core/inference/` 的 JNI 边界（`NativeSession`）与 AIDL Parcelable（`InferenceRequest`/`InferenceStats`）；`app/src/main/cpp/` 与 `backend/` 不在迁移范围。

## 目录结构

```text
app/            Android 应用
  src/main/cpp/         CMake + llama.cpp + JNI
  src/main/assets/      签名目录、许可清单、OCR 模型
  src/main/aidl/        推理进程协议
  src/test/             JVM 单元测试（Robolectric）
  src/androidTest/      仪器化测试
  src/androidTestReleaseCheck/  正式签名覆盖升级验收
  schemas/              Room 导出 schema（迁移审计）
backend/        目录/清单 fixture 服务、manifest schema、签名与打包工具
qa/             E2E 脚本与截图、设备矩阵、fixture、模拟器验收
docs/           工作日志、构建/设备/原生/许可/UI/模型接入基线与发布门禁
```

## 构建与验证

工具链固定值见 `docs/build-baseline.md`（JDK 17、Gradle 8.14、AGP 8.13.2、Kotlin 2.2.20、compileSdk/targetSdk 36、NDK 28.2.13676358、CMake 3.22.1）。`local.properties` 需提供 `sdk.dir`。

```bash
./gradlew :app:testDebugUnitTest --offline        # JVM 单元测试
./gradlew :app:assembleDebug --offline            # Debug APK（x86_64）
./gradlew :app:assembleRelease --offline          # 发布 APK（arm64-v8a，需 .local-signing/release.properties）
./gradlew :app:connectedDebugAndroidTest --offline  # 仪器化测试（需模拟器或真机）
```

构建期强制校验由 `preBuild` 自动执行，不通过即拒绝构建：

- `verifyThirdPartyLicenses`：实际 `releaseRuntimeClasspath` 必须与 `app/src/main/assets/licenses/index.json` 的组件清单完全一致，且每条目的许可文本齐备。
- `verifyBundledOcrModels`：随包 OCR 模型逐字节 SHA-256 校验。

## 隐私与许可

`allowBackup=false`，云备份与设备迁移排除全部数据域；应用内可离线阅读「隐私与数据说明」。网络仅用于模型目录与权重下载。第三方组件归属见 `NOTICE`，清单维护规则见 `docs/license-policy.md`（发布依赖变更必须同步更新随包清单）。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/ai-work-log.md` | 追加式工作日志：每轮目标、改动与验证证据 |
| `docs/build-baseline.md` | 工具链基线与发布签名覆盖升级验收流程 |
| `docs/native-baseline.md` | llama.cpp 版本锁定与 Native 构建 |
| `docs/model-onboarding.md` | 模型接入与目录清单规范 |
| `docs/license-policy.md` | 第三方许可登记与随包清单规则 |
| `docs/ui-design.md` | 设计令牌、页面清单与状态矩阵 |
| `docs/device-baseline.md` | 设备分档标准与真机回填流程 |

## 已知限制

- **真机验收尚未执行**：现有性能结论均来自 x86_64 模拟器（ARM64 经 `libndk_translation.so` 运行），不等同于 arm64 真机性能。解除条件见 `docs/device-baseline.md` 第 6 节。
- **商店路径未验证**：候选 APK 与 AAB、哈希与软件验收报告见 `artifacts/manifest.json`、`docs/gates/g5-software-acceptance.md`、`docs/release/candidate-0.3.0.md`；应用商店上传路径尚未验证。
- 模型权重不入库，需由目录地址下载；`qa/fixtures/models/*.gguf` 与 `backend/fixtures/media/` 已被忽略。
