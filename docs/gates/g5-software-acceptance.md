# G5 软件验收门禁（P5）

> 记录日期：2026-09-26。本门禁在**不连接真实设备**的前提下完成，构成 `P6` 真机验收的输入。
> 机器可读清单：`artifacts/manifest.json`。

## 1. 候选标识

| 项 | 值 |
| --- | --- |
| 应用 | 端智随行（`com.example.localai`） |
| 版本 | `0.3.0`（versionCode 3） |
| 构建类型 | `release`（R8 未开启压缩，与既有基线一致） |
| ABI | `arm64-v8a` |
| 候选包 | `artifacts/candidate/local-ai-0.3.0-arm64-release.apk` |
| 大小 | 96,071,389 字节 |
| SHA-256 | `c4937ee2c036c50a73c972e84217623d4928654c6918c25c452abeffee69c52e` |
| SHA-1 | `5db6dc7dcc00d8f845c44abd91eecab11fb0b42b` |

签名（`apksigner verify` 退出码 0）：

- 方案：APK Signature Scheme **v2** 通过；v1 未启用（minSdk 26 无需），v3/v3.1/v4 未启用。
- 签名者 1 个，证书 `CN=duan-zhi-sui-xing`，证书 SHA-256 `ede51c40…a4c5e`。
- 签名材料位于本机忽略目录 `.local-signing/`，不入库。

## 2. 验证矩阵

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 发布构建 | `./gradlew :app:assembleRelease --offline` | `PASS`（44 秒） |
| 发布单测 | `./gradlew :app:testReleaseUnitTest --offline` | `PASS`，164 项 0 失败 |
| 调试单测 | `./gradlew :app:testDebugUnitTest --offline` | `PASS`，164 项 0 失败 |
| Lint | `./gradlew :app:lintDebug --offline` | `PASS`，0 错误、139 警告 |
| 仪器化回归 | `./gradlew :app:connectedDebugAndroidTest --offline "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` | `PASS`，52 项完成、0 失败、3 项设计内跳过 |
| 真实下载链路 | `adb shell am instrument -w -r -e allowModelDownload true -e class …StandaloneModelInstrumentedTest …` | `PASS`，`OK (1 test)`，61.0 秒，491,400,032 字节，SHA-256 与 GGUF 架构校验通过 |
| 签名校验 | `apksigner verify --print-certs` | `PASS`，退出码 0 |
| 脱敏扫描 | `python qa/release/log_redaction_scan.py` | `PASS`，312 个受跟踪文件 0 命中 |
| 构建期门禁 | `verifyThirdPartyLicenses`、`verifyBundledOcrModels`（`preBuild` 自动执行） | `PASS` |

**未解释的失败：无。**

> 2026-09-30 更新（软件优化轮）：单测 **167 项 0 失败**、lint **0 错误 135 警告**；候选包归档副本复核仍为 96,071,389 字节、SHA-256 `c4937ee2…9c52e`，`apksigner verify` 退出码 0、证书指纹一致。警告按「正确性/隐私/安全」与「纯风格」分类处置，详见 `docs/ai-work-log.md` 的 `OPT-6` 记录。

跳过 3 项均为设计使然，不计入失败：进程重启恢复需外部两阶段 `-e phase prepare|verify` 驱动；下载验收需显式 `-e allowModelDownload true`；离线连续负载需先断网（见 `qa/build/run_emulator_acceptance.py`）。

## 3. 本轮顺带修复的门禁问题

- `qa/release/log_redaction_scan.py` 首次运行命中 2180 处，逐条核实后收紧规则：排除 Kotlin `this@Label` 造成的邮箱误报、排除内置上游源码与随包许可正文（其作者署名是许可要求的一部分）。修正后 0 命中。
- 扫描发现并修复 1 处真实泄露：`docs/build-baseline.md` 曾记录本机 SDK 绝对路径（含用户名），已改为不含用户名的形式说明。

## 4. 明确未覆盖（不得据本门禁推断）

- **真机验收（P6）未执行**：无 arm64-v8a 真机。全部性能与稳定性结论来自 16 KB x86_64 模拟器（ARM64 经 `libndk_translation.so` 运行），不等同于真机表现。
- **未产出 AAB**：仅 APK，未验证 Play 上传路径。
- **未做性能基准**：详情页「输出速度」「首字延迟」显示「待真机实测」，本轮未补测，也未以模拟器数字冒充真机指标；该口径与回填条件见 `docs/device-baseline.md` 第 7 节。
- **未执行崩溃/长时间稳定性循环**：仅覆盖仪器化回归与一次真实下载+生成。
- 许可审查以构建期清单一致性与随包文本可读性为准（`LicensesInstrumentedTest` 通过），不构成法律意见。

## 5. 结论

软件侧门禁通过，候选 APK 可校验；`P5` 可判定为完成，`P6` 真机验收在其之上进行。
