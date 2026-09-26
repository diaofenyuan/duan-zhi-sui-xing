# 构建基线（S002 固定）

> 本文件记录项目固定使用的工具链版本。任何版本变更必须走新的步骤卡，禁止开发者机器自行漂移。基线确立日期：2026-08-23。

## 版本矩阵

| 项目 | 固定值 | 依据 |
| --- | --- | --- |
| Java（构建/源码） | JDK 17（Temurin 17.0.19） | AGP 8.13 要求的最低构建 JDK；本机 JAVA_HOME 已安装 |
| Gradle | 8.14（wrapper `distribution-bin`） | 满足 AGP 8.13 最低要求 Gradle 8.13；本机 wrapper 缓存已有完整发行版，可离线复现 |
| Android Gradle Plugin | 8.13.2 | Google Maven 8.13 线最新补丁（2026-08-20 元数据确认）；成熟稳定线 |
| compileSdk | 36 | 本机已装 android-36 平台；满足当期商店 targetSdk 要求 |
| targetSdk | 36 | Play 商店自 2026-08-31 起新提交要求 API 36 |
| minSdk | 26 | 方案硬性规定（Android 8.0） |
| Build Tools | 36.0.0 | 本机已安装，与 compileSdk 36 配套 |
| NDK | 28.2.13676358 | 本机唯一安装版本；NDK r28 默认对齐 16 KB page size |
| CMake | 3.22.1 | 本机 SDK 自带版本；S005 接入 externalNativeBuild 时使用 |
| ABI | arm64-v8a | 首版发布 ABI；x86_64 仅用于模拟器专项测试 |
| 语言 | Kotlin 为主 + Java 互操作保留区 | 2026-08-28 起主语言迁移为 Kotlin（KGP 2.2.20）；Room 注解类 / JNI / AIDL Parcelable 边界保持 Java |

## 选择理由摘要

1. **AGP 8.13.2 而非 9.x**：AGP 9.0 起默认启用内置 Kotlin 并引入 KGP 运行时依赖；本工程原为纯 Java 工程，故选择 8.13（8.x 成熟稳定线的最终补丁）。**2026-08-28 起引入 Kotlin（KGP 2.2.20），原"避开内置 Kotlin"的理由已因本次语言迁移而失效；但本次迁移不升级 AGP，该判断留待独立变更处理。**
2. **Gradle 与 wrapper**：项目根目录通过 Gradle Wrapper 固定发行版（`gradle/wrapper/gradle-wrapper.properties`），所有开发者与 CI 使用同一发行版，无需本机安装 Gradle。
3. **SDK 定位**：`ANDROID_HOME` 未设置系统级环境变量，机器本地路径通过 `local.properties`（已 gitignore）提供：`C:\Users\zhy23\AppData\Local\Android\Sdk`。

## 复现验证

```text
./gradlew --version            # 应输出 Gradle 8.14、JVM 17
./gradlew :app:assembleDebug   # 应 BUILD SUCCESSFUL 并生成 app-debug.apk
```

## 常见构建故障与恢复

### `undefined symbol: vtable for llama_model_*`，且 `.cxx` 中存在 0 字节 `.o`

现象：`assembleDebug` / `assembleRelease` 在 Native 链接阶段失败，例如 `ld.lld: error: undefined symbol: vtable for llama_model_exaone4`（类名随 vendored 版本而变），任务名形如 `:app:buildCMakeDebug[arm64-v8a]`。

原因：上一次 Native 构建被中断（Ctrl-C、进程被杀、磁盘写满），留下长度 0 的目标文件。Ninja 只按修改时间判断新旧，会把该 `.o` 视为最新并跳过重编，链接时便缺少整个翻译单元的全部符号（首发症状通常是该架构类的 vtable）。**源码与 CMake 配置本身没有问题**，`git status` 亦无改动，因此极易被误判为代码回归。

恢复：删除截断的目标文件后重编即可，无需清空整个 `.cxx`：

```powershell
Get-ChildItem app\.cxx -Recurse -File -Include *.o | Where-Object { $_.Length -eq 0 } | Remove-Item -Force
```

排查提示：遇到 `undefined symbol` 时，先确认「符号所属的 `.cpp` 是否已列在 `build.ninja` 与 `llama.rsp` 中，而对应 `.o` 是否为 0 字节」，再怀疑源码或 CMake 配置。

## 后续注意

- S005 引入 Native 构建时，`CMake` 版本从版本目录读取并在 `externalNativeBuild` 中显式声明。
- 升级 AGP/Gradle 前先查官方兼容表，且必须同步更新本文件与版本目录。
- 2026-08-28 起主语言为 Kotlin：新增 `org.jetbrains.kotlin.android` 插件（KGP 2.2.20，离线缓存 `kotlin-gradle-plugin-2.2.20-gradle813.jar` 命中本工程 Gradle 8.14）；`jvmTarget` 与 `compileOptions` 保持一致为 Java 17。Room 注解类（`data/room/`）、JNI 边界（`NativeSession`）、AIDL Parcelable（`InferenceRequest`/`InferenceStats`）保持 Java，详见 `docs/kotlin-migration-plan.md` 第 5 节。

## 发布签名的同机隔离验收（2026-09-06）

`releaseCheck` 继承 Release 的签名、ARM64 ABI 和优化配置，应用标识为 `com.example.localai.releasecheck`、桌面名称为“端智随行·发布验收”，保持不可调试。它用于在已有 Debug 签名应用的模拟器中共存测试；交付用户仍使用 `assembleRelease` 的正式 APK，不能把验收包当正式包分发。

构建：`gradlew.bat assembleReleaseCheck assembleReleaseCheckAndroidTest -PinstrumentedBuildType=releaseCheck -PreleaseCheckVersionCode=1 --offline`。默认仪器化目标仍是 Debug；版本号覆盖仅对 releaseCheck 生效。签名配置沿用本机忽略目录中的现有发布身份。

升级用例位于 `src/androidTestReleaseCheck`，不编译进普通 Debug 测试或应用 APK。先通过 adb 在 `/data/local/tmp/localai-release-model.gguf` 准备官方 Qwen 权重副本（测试会核对随包签名清单及完整 SHA-256），安装版本 N 的验收包与测试包，运行：

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.example.localai.ReleaseUpgradeInstrumentedTest -e upgradePhase prepare -e expectedVersionCode N com.example.localai.releasecheck.test/androidx.test.runner.AndroidJUnitRunner
```

然后以 `-PreleaseCheckVersionCode=M` 构建 M>N 的验收包，`adb -s emulator-5554 install --abi arm64-v8a -r <验收APK>` 覆盖安装，运行相同测试但将 phase 改为 `verify`、expectedVersionCode 改为 M。重复执行时使用比当前安装版本更高的 N/M，无需卸载；准备阶段仅写验收应用自己的测试会话和设置。完成后删除临时权重副本即可。

本次验证的是同一代码以版本号 1→2 的 Package Manager 覆盖升级与数据保留，不冒充历史正式版本迁移；Room 1→2 数据结构迁移另有专门用例。当前设备为 16 KB x86_64 模拟器，ARM64 经 `libndk_translation.so` 运行，此结果不等同于 ARM64 真机性能验收。

## Debug 应用的设备模型预置（2026-09-26）

Debug 应用的端到端与仪器化验收要求设备上确有**经安装流程登记**的模型，二者判定口径不同：

- `ApprovedModels.isInstalled(...)` 只校验权重文件长度与 `install.ok` 标记；
- 但 `DownloadRepository.installed()`（诊断页模型清单、兼容性统计的数据源）读的是**数据库安装记录**，其启动清理（`ModelStorageManager.cleanup()`）也只清理 staging/rollback 孤儿目录，**不会**把已有文件补登记。

因此手工复制权重只能满足前一类用例，诊断页一类用例会因「无已安装模型」而失败或跳过。要得到正规安装记录，应走应用自身的下载 → 校验 → 安装链路（目录源为 `https://huggingface.co`，签名目录随包在 assets 内，无需控制服务器）：

```text
# 先清掉非正规安装，否则用例会因“已安装”而跳过下载
adb -s emulator-5554 shell am force-stop com.example.localai
adb -s emulator-5554 shell run-as com.example.localai rm -rf files/models/qwen2.5-0.5b-instruct

# 走真实下载与安装（491 MB；本机模拟器实测 61 秒）
adb -s emulator-5554 shell am instrument -w -r -e allowModelDownload true \
  -e class com.example.localai.feature.chat.StandaloneModelInstrumentedTest \
  com.example.localai.test/androidx.test.runner.AndroidJUnitRunner
```

成功标志：`files/models/qwen2.5-0.5b-instruct/2026.09.1/` 下同时出现 `install.ok`、`manifest.json`、`manifest.sig` 与权重文件，且用例报 `OK (1 test)`（内部含完整 SHA-256 与 GGUF 架构校验）。该用例默认跳过，只有显式传 `-e allowModelDownload true` 才执行，避免常规回归联网拉取权重。

另注：`connectedDebugAndroidTest` 运行结束会卸载被测应用连同其数据（含已安装模型）。需要保留设备状态时加 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`；该参数在 PowerShell 下必须整体加引号，否则会被拆成任务名并报 `Task '.injected...' not found`。设备侧无 `sqlite3`，无法直接改写安装记录。
