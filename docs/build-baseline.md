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
