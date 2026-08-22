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
| 语言 | 纯 Java（无 Kotlin 插件） | 方案技术栈规定；因此不使用内置 Kotlin 的 AGP 9.x |

## 选择理由摘要

1. **AGP 8.13.2 而非 9.x**：AGP 9.0 起默认启用内置 Kotlin 并引入 KGP 运行时依赖，本项目为纯 Java 工程，无此需要；8.13 是 8.x 成熟稳定线的最终补丁。
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
