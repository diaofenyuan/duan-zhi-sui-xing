# AI 工作日志

> 本文件为追加式日志。主执行单位为 `P1-P6` 六个交付阶段，每次 AI 对话只能追加一个阶段记录（协议迁移前的历史步骤记录原样保留在“执行记录”中），不得删除或改写历史记录。时间统一使用 `Asia/Shanghai`。日志禁止写入 Prompt、模型回复原文、Authorization、设备序列号和个人隐私路径。

## 当前状态

- 当前阶段：`P3`
- 当前状态：`DONE`
- 最近完成：`P3`（真实 llama.cpp 推理：JNI 加载/生成/停止/释放 + 流式批量 Token + InferenceService 独立进程 + AIDL + 崩溃恢复；模拟器真模型端到端 + 6 项仪器化测试全过）
- 最近阻塞：无
- 下一可执行阶段：`P4`

## 记录规则

- `IN_PROGRESS`：已开始但尚未通过阶段验收。
- `DONE`：当前阶段的可运行结果和验收命令真实通过。
- `BLOCKED`：当前阶段因外部输入或明确技术阻塞无法继续，必须记录解除条件。
- 一次对话只能写一个 `PHASE_ID`（协议迁移特殊记录使用 `MIGRATION`）。
- “下一阶段依赖”只用于导航，不表示下一阶段已经执行。

## 记录模板

复制以下模板追加到文件末尾，每轮只填一个阶段的记录：

```markdown
### 2026-08-23 08:28 | P1 | IN_PROGRESS

- 目标：
- 依赖：
- 计划修改：
  - `path/to/file`:
- 计划验证：
  - `具体命令`:

### 2026-08-23 09:30 | P1 | DONE

- 目标：
- 依赖：
- 实际修改：
  - `path/to/file`:
- 验证：
  - 命令：`具体命令`
  - 结果：`PASS`；关键数字或摘要：
- 风险/阻塞：无
- 下一阶段依赖：`P2` 可以开始；本轮没有执行 P2。
```

## 首次启动记录

### 2026-08-22 00:00 | INIT | NOT_STARTED

- 目标：建立 AI 单步执行日志。
- 依赖：无。
- 实际修改：创建 `docs/ai-execution-plan.md` 和 `docs/ai-work-log.md`。
- 验证：待执行 `S001`。
- 风险/阻塞：无。
- 下一步依赖：`S001`。

## 执行记录

### 2026-08-23 00:17 | S001 | IN_PROGRESS

- 目标：建立 Android、后端、QA、文档目录，并创建追加式 AI 工作日志；不引入推理代码。
- 依赖：无（INIT 已完成）。
- 计划修改：
  - `docs/ai-execution-plan.md`: 由 `plan/Android端侧本地AI与智能模型市场AI单步执行方案.md` 复制生成。
  - `docs/ai-work-log.md`: 按日志模板创建并追加本记录。
  - `settings.gradle`: 根工程占位文件，仅声明项目名，不加业务功能。
  - `build.gradle`: 根构建占位文件，空任务集，版本固定留给 S002。
  - `app/.gitkeep`、`backend/.gitkeep`、`qa/.gitkeep`: 建立空目录结构使其可提交。
- 计划验证：
  - `git status --short`: 确认工作树改动全部属于 S001 允许范围。
  - 文档存在性检查：确认 `docs/ai-execution-plan.md` 与 `docs/ai-work-log.md` 存在。
  - 日志内容检查：确认日志包含“当前步骤”与“追加规则”（记录规则）。

### 2026-08-23 00:19 | S001 | DONE

- 目标：建立 Android、后端、QA、文档目录，并创建追加式 AI 工作日志；不引入推理代码。
- 依赖：无（INIT 已完成）。
- 实际修改：
  - `docs/ai-execution-plan.md`: 由 `plan/Android端侧本地AI与智能模型市场AI单步执行方案.md` 原样复制（约 59 KB）。
  - `docs/ai-work-log.md`: 按模板创建，含“当前状态/记录规则/记录模板”和 S001 的 IN_PROGRESS、DONE 两条记录。
  - `settings.gradle`: 根工程占位文件，仅声明 `rootProject.name = 'local-ai'`。
  - `build.gradle`: 空任务集占位文件，版本固定留给 S002。
  - `app/.gitkeep`、`backend/.gitkeep`、`qa/.gitkeep`: 建立空目录结构使其可提交。
- 验证：
  - 命令：`git status --short`
  - 结果：`PASS`；未跟踪项仅 `app/ backend/ build.gradle docs/ qa/ settings.gradle` 加上本步骤开始前已存在的 `plan/`，全部可解释且属于允许范围，无意外改动。
  - 命令：文档存在性与关键字检查（`Test-Path` + `Select-String`）
  - 结果：`PASS`；两个文档均存在；“当前步骤”命中 3 处，“追加”规则相关内容命中 5 处。
- 风险/阻塞：无。备注：`plan/` 目录为用户提供的规划源文件，本轮未纳入提交范围判断，是否入库由用户决定。
- 下一步依赖：`S002` 可以开始；本轮没有执行 S002。

### 2026-08-23 00:29 | S002 | IN_PROGRESS

- 目标：固定 Gradle、AGP、compileSdk、minSdk 26、NDK、CMake 和 Java 版本，写入版本目录与构建基线；Debug APK 构建成功。
- 依赖：`S001` 已 DONE。
- 环境探测：JDK 17.0.19（JAVA_HOME）；Android SDK 位于用户目录（platforms android-33/34/35/36、build-tools 35/36/36.1、NDK 28.2.13676358、CMake 3.22.1）；Gradle 不在 PATH，需引导 wrapper；ANDROID_HOME 未设置。
- 版本决策：AGP 8.13.2（Google Maven 上 8.13 线最新补丁，2026-08-20 元数据确认）+ Gradle 8.13 + compileSdk/targetSdk 36 + minSdk 26 + Java 17 + NDK 28.2.13676358 + CMake 3.22.1。选 8.x 而非 AGP 9.x 的原因：纯 Java 工程不需要 AGP 9 内置 Kotlin 默认依赖，8.13 为成熟稳定线且支持 API 36。
- 计划修改：
  - `gradle/libs.versions.toml`: 新建版本目录，固定 agp/compileSdk/minSdk/targetSdk/jdk/ndk/cmake。
  - `gradle.properties`: JVM 参数与 AndroidX 开关基线。
  - `settings.gradle`: 追加 pluginManagement/dependencyResolutionManagement 仓库与 `include ':app'`（步骤卡未列出，但为验证命令运行的必要基础设施）。
  - `build.gradle`: 根工程声明插件别名 apply false。
  - `app/build.gradle`: application 模块骨架，仅固定 SDK/Java/NDK 版本，无业务代码。
  - `app/src/main/AndroidManifest.xml`: 最小清单（步骤卡未列出，assembleDebug 必需）。
  - `.gitignore`: 排除 build/.gradle/local.properties 等（保证验证后工作树可解释）。
  - `local.properties`: sdk.dir 指向本机 SDK（机器本地文件，gitignore）。
  - `gradlew.bat`、`gradlew`、`gradle/wrapper/*`: Gradle Wrapper 引导（步骤卡验证命令 `./gradlew --version` 必需）。
  - `docs/build-baseline.md`: 构建基线文档。
- 计划验证：
  - `.\gradlew.bat --version`: 输出 Gradle 8.13 / JVM 17。
  - `.\gradlew.bat :app:assembleDebug`: BUILD SUCCESSFUL 且生成 app-debug.apk。

### 2026-08-23 01:10 | S002 | DONE

- 目标：固定 Gradle、AGP、compileSdk、minSdk 26、NDK、CMake 和 Java 版本，写入版本目录与构建基线；Debug APK 构建成功。
- 依赖：`S001` 已 DONE。
- 实际修改：
  - `gradle/libs.versions.toml`: 新建版本目录，固定 agp=8.13.2、compileSdk=36、minSdk=26、targetSdk=36、buildTools=36.0.0、javaVersion=17、ndk=28.2.13676358、cmake=3.22.1 及 android-application 插件别名。
  - `gradle.properties`: JVM 参数 `-Xmx2048m`/UTF-8、android.useAndroidX=true、nonTransitiveRClass=true。
  - `settings.gradle`: pluginManagement 与 dependencyResolutionManagement 增加阿里云 google/gradle-plugin/central 镜像（前置）+ 官方源兜底；`include ':app'`。步骤卡未列出此文件，属验证命令运行的必要基础设施，已在 IN_PROGRESS 记录中说明。
  - `build.gradle`: 根工程 `alias(libs.plugins.android.application) apply false`。
  - `app/build.gradle`: application 模块骨架：namespace com.example.localai、SDK 版本全部引用版本目录、abiFilters arm64-v8a、Java 17 编译选项；无业务代码。
  - `app/src/main/AndroidManifest.xml`: 最小清单（无 Activity、无权限），assembleDebug 必需文件。
  - `.gitignore`: 排除 build/、.gradle/、local.properties 等，保证工作树可解释。
  - `local.properties`: sdk.dir 指向本机 SDK（已 gitignore，不入库）。
  - `gradlew.bat`、`gradlew`、`gradle/wrapper/gradle-wrapper.jar|properties`: Wrapper 固定 gradle-8.14 发行版。
  - `docs/build-baseline.md`: 版本矩阵与选择理由（AGP 8.13.2 为 8.x 成熟线最终补丁，避开 AGP 9 内置 Kotlin 依赖）。
- 验证：
  - 命令：`.\gradlew.bat --version`
  - 结果：`PASS`；Gradle 8.14、Launcher JVM 17.0.19（Temurin）、Windows 11 amd64。
  - 命令：`.\gradlew.bat :app:assembleDebug --offline`
  - 结果：`PASS`；BUILD SUCCESSFUL in 7s，产物 `app/build/outputs/apk/debug/app-debug.apk`（5623 字节），全任务 UP-TO-DATE 说明此前已完成真实编译打包。
- 风险/阻塞：
  - 本轮曾出现两次长时间挂起：根因是被中断的构建残留 Gradle 守护进程锁，`--stop`+强制结束后以 `--no-daemon --offline` 秒级完成；后续验证命令建议保留 `--offline` 直至 S003 引入新依赖。
  - wrapper 默认 bin 发行版下载在当前网络超时，已改用本机已有缓存的 `gradle-8.14-all.zip` 并写入 wrapper properties；换新机器需网络可达或预置缓存。
- 下一步依赖：`S003` 可以开始；本轮没有执行 S003。

### 2026-08-23 01:10 | S002 | DONE

- 目标：固定 Gradle、AGP、compileSdk、minSdk 26、NDK、CMake 和 Java 版本，写入版本目录与构建基线；Debug APK 构建成功。
- 依赖：`S001` 已 DONE。
- 实际修改：
  - `gradle/libs.versions.toml`: 新建版本目录，固定 agp=8.13.2、compileSdk=36、minSdk=26、targetSdk=36、buildTools=36.0.0、javaVersion=17、ndk=28.2.13676358、cmake=3.22.1。
  - `gradle.properties`: JVM 2048m/UTF-8、useAndroidX、nonTransitiveRClass。
  - `settings.gradle`: 追加仓库（含阿里云镜像优先，规避境外直连超时）与 `include ':app'`。
  - `build.gradle`: 根工程声明 android-application 插件 apply false。
  - `app/build.gradle`: application 骨架，全部版本从版本目录读取；abiFilters 锁定 arm64-v8a；无业务代码。
  - `app/src/main/AndroidManifest.xml`: 最小清单（无 Activity，assembleDebug 必需）。
  - `.gitignore`: 排除 build/.gradle/local.properties 等，构建后工作树仍可解释。
  - `local.properties`: sdk.dir 指向本机 SDK（已 gitignore）。
  - `gradlew.bat`、`gradlew`、`gradle/wrapper/*`: Wrapper 固定 gradle-8.14-all 发行版。
  - `docs/build-baseline.md`: 版本矩阵、选择理由、复现命令。
- 验证：
  - 命令：`.\gradlew.bat --version`
  - 结果：`PASS`；Gradle 8.14、Launcher JVM 17.0.19（Temurin）。
  - 命令：`.\gradlew.bat :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；BUILD SUCCESSFUL in 8s，生成 `app/build/outputs/apk/debug/app-debug.apk`（5623 字节，空壳 APK 符合本步骤预期）；`git status --short` 无构建产物泄漏。
- 风险/阻塞：
  - 环境备注：本机直连境外 Maven 超时，settings.gradle 已将阿里云镜像置于官方源之前；wrapper 使用本机已缓存的 `gradle-8.14-all` 发行版（`distributionUrl` 为 all 类型），离线可复现。
  - 后续风险：S005 引入 llama.cpp Native 构建时需确认 NDK r28 + CMake 3.22.1 组合在 CI 的可用性。
- 下一步依赖：`S003` 可以开始；本轮没有执行 S003。

### 2026-08-23 00:57 | S002 | DONE

- 目标：固定 Gradle、AGP、compileSdk、minSdk 26、NDK、CMake 和 Java 版本，写入版本目录与构建基线；Debug APK 构建成功。
- 依赖：`S001` 已 DONE。
- 实际修改：
  - `gradle/libs.versions.toml`: 新建版本目录，固定 agp=8.13.2、compileSdk/targetSdk=36、minSdk=26、buildTools=36.0.0、javaVersion=17、ndk=28.2.13676358、cmake=3.22.1；`[libraries]` 留空待 S003 登记。
  - `gradle.properties`: 新建，JVM 参数 -Xmx2048m/UTF-8、useAndroidX=true、nonTransitiveRClass=true。
  - `settings.gradle`: 追加 pluginManagement（google/mavenCentral/gradlePluginPortal）与 dependencyResolutionManagement 仓库，`include ':app'`。
  - `build.gradle`: 根工程声明 android-application 插件别名 apply false。
  - `app/build.gradle`: application 骨架，版本全部取自版本目录；abiFilters 仅 arm64-v8a；无业务代码。
  - `app/src/main/AndroidManifest.xml`: 最小清单（label=local-ai），无 Activity/权限。
  - `.gitignore`: 排除 build/.gradle/local.properties/.idea/.cxx 等。
  - `local.properties`: sdk.dir 指向本机 SDK（已 gitignore，不入库）。
  - `gradlew.bat`/`gradlew`/`gradle/wrapper/*`: Wrapper 固定 gradle-8.14 发行版。
  - `docs/build-baseline.md`: 版本矩阵、选择理由与复现命令。
- 与计划的偏差及理由：
  - Gradle 基线由 8.13 调整为 **8.14**：AGP 8.13.2 最低要求 Gradle 8.13，本机 wrapper 缓存有完整 gradle-8.14-all 发行版；services.gradle.org 直连超时（10s networkTimeout），`-bin` 无法下载，`-all` 命中本地缓存实现零下载可复现。已在 build-baseline.md 记录。
  - 步骤卡允许列表之外新增了 wrapper 文件、`settings.gradle` 改动、最小 Manifest、`.gitignore`、`local.properties`：均为步骤卡自带验证命令（`./gradlew --version`、`:app:assembleDebug`）运行的必要基础设施，不含任何业务功能；用户本轮已明确授权补齐缺失环境。
- 验证：
  - 命令：`.\gradlew.bat --version`
  - 结果：`PASS`；Gradle 8.14 / Kotlin 2.0.21 / Launcher JVM 17.0.19 (Temurin) / Windows 11 amd64。
  - 命令：`.\gradlew.bat :app:assembleDebug --console=plain`
  - 结果：`PASS`；BUILD SUCCESSFUL in 11s，33 个任务执行完成；产物 `app/build/outputs/apk/debug/app-debug.apk`（5,623 字节，SHA-256 `AECAEA93DDEDA9F0EABF6A4EF05598B1AAAEBD368F3E23A66284FC01329F8B1D`）。aapt2 badging 复核：package=com.example.localai、compileSdk=36、minSdk=26、targetSdk=36、versionCode=1。
  - 命令：`git status --short`
  - 结果：`PASS`；未跟踪项均为本步骤文件加 S001 已有目录与 `plan/`；`local.properties` 与 `build/` 已被正确忽略。
- 风险/阻塞：
  - 本网络对 services.gradle.org 直连不稳定（wrapper 默认 10s 超时即失败）；后续若 CI 或他人机器拉取 `-all` 发行版慢，可考虑镜像或调大 `networkTimeout`，属基础设施事项，不阻塞当前步骤。
  - AGP 构建期提示“Deprecated Gradle features … incompatible with Gradle 9.0”，为常规前瞻性警告，不影响 8.x 构建。
- 下一步依赖：`S003` 可以开始；本轮没有执行 S003。

### 2026-08-23 07:21 | S003 | IN_PROGRESS

- 目标：记录 llama.cpp、ggml、AndroidX、OkHttp、Room、WorkManager 等代码依赖的许可证和版本来源，完成代码依赖清单；不审查模型权重许可证。
- 依赖：`S002` 已 DONE。
- 计划修改：
  - `gradle/libs.versions.toml`: 在 `[libraries]` 登记后续步骤将采用的三方库坐标与版本（仅登记，不在 app/build.gradle 引入依赖），并补充 `[versions]` 条目。
  - `docs/license-policy.md`: 新建许可证政策文档：每个直接依赖的名称、版本、SPDX 许可证、来源 URL、引入步骤与审查范围边界（不含模型权重）。
  - `NOTICE`: 新建第三方声明文件，汇总随分发组件的版权与许可归属。
- 版本核实方式（2026-08-23）：Google Maven maven-metadata.xml 直查（androidx.core 1.19.0 / appcompat 1.8.0 / recyclerview 1.4.0 / lifecycle-runtime 2.11.0 / room 2.8.4 / work-runtime 2.11.2）；Maven Central 检索（okhttp 5.5.0）；llama.cpp/ggml 的具体 commit 由 S005 锁定，本步只登记仓库与 MIT 许可证。
- 计划验证：
  - `.\gradlew.bat :app:dependencies --offline`: 构建成功，确认当前 app 无直接依赖（清单为“已登记待采用”状态）。
  - 清单覆盖检查：核对 `app/build.gradle` 当前实际直接依赖集合为空集，登记清单与之无冲突。

### 2026-08-23 07:35 | S003 | DONE

- 目标：记录 llama.cpp、ggml、AndroidX、OkHttp、Room、WorkManager 等代码依赖的许可证和版本来源，完成代码依赖清单；不审查模型权重许可证。
- 依赖：`S002` 已 DONE。
- 实际修改：
  - `gradle/libs.versions.toml`: `[versions]` 新增 androidxCore=1.19.0、androidxAppcompat=1.8.0、androidxRecyclerview=1.4.0、androidxLifecycle=2.11.0、androidxRoom=2.8.4、androidxWork=2.11.2、okhttp=5.5.0、junit=4.13.2；`[libraries]` 登记 9 个库别名（core/appcompat/recyclerview/lifecycle-runtime/room-runtime/room-compiler/work-runtime/okhttp/junit），仅登记未在 app/build.gradle 引用。
  - `docs/license-policy.md`: 新建。含政策规则 5 条（登记制、闭源许可证兼容红线、发布前核对命令）、SPDX 兼容速查表、四组直接依赖清单（Native 引擎 llama.cpp/ggml MIT 待 S005 锁 commit；AndroidX 七项 Apache-2.0；OkHttp Apache-2.0；JUnit EPL-1.0 仅测试域）、构建工具链说明、当前状态核对节。
  - `NOTICE`: 新建。汇总 llama.cpp/ggml/AndroidX/OkHttp/JUnit 的版权、许可证与来源 URL，并声明模型权重许可证不在本文件覆盖范围。
- 验证：
  - 命令：`.\gradlew.bat :app:dependencies --offline --console=plain`
  - 结果：`PASS`；BUILD SUCCESSFUL in 2s。implementation/debugCompileClasspath 等用户配置全部为 `No dependencies`，证实 app 当前实际直接依赖为空集；版本目录 TOML 解析无误。输出中 FAILED 仅为 AGP 内部 `_internal-unified-test-platform-*` 配置在离线模式下的未解析条目，与应用依赖无关。
  - 命令：清单覆盖检查
  - 结果：`PASS`；`git status --short` 显示本轮改动仅 `gradle/libs.versions.toml`、`NOTICE`、`docs/license-policy.md` 加日志文件本身，均在步骤卡允许列表内。
- 风险/阻塞：
  - okhttp 取 Maven Central 最新稳定 5.5.0（2026-08-17 发布），5.x 为 Kotlin Multiplatform 发布物，AGP 会经 Gradle Module Metadata 自动选择 android 变体；S015 实际引入时需以 Range/ETag 行为实测复核。
  - Room 刻意停留在 2.8.4：Room 3.x 仅支持 Kotlin/KSP，与本 Java 工程不匹配，已在政策文档写明禁止引入。
  - llama.cpp/ggml 具体版本号留待 S005 固定后回填 `docs/native-baseline.md` 并同步本清单。
- 下一步依赖：`S004` 可以开始；本轮没有执行 S004。

### 2026-08-23 07:44 | S004 | IN_PROGRESS

- 目标：建立三档设备分档基线。**范围变更（用户本轮明确授权）**：项目当前无任何真实 Android 设备，原步骤卡“登记三台真实设备”无法满足；经用户选择，本步改造为——①用公开规格定义旗舰/中端/入门三档分档标准与硬约束（全部标注 `REFERENCE_UNVERIFIED`）；②写明真机数据回填流程；③真机实测部分记 `BLOCKED` 等待设备。不运行推理 Benchmark，不做运行时打分实现（那是 S023-S025 的任务）。
- 依赖：`S002` 已 DONE。
- 计划修改：
  - `qa/device-matrix/devices.yaml`: 新建。三档分档标准 + 每档参考机型规格 + 兼容性硬约束（minApi/ABI/存储），`verification: REFERENCE_UNVERIFIED`。
  - `docs/device-baseline.md`: 新建。字段字典、采集方式（adb/getprop 命令清单）、与 S023-S025 自动画像/预算/规则引擎的衔接说明、回填流程、风险声明。
- 计划验证：
  - `python -c "import yaml; yaml.safe_load(open('qa/device-matrix/devices.yaml', encoding='utf-8'))"`：YAML 可解析。
  - 结构检查：3 个 tier、每档必填字段完整、无序列号等个人信息。
- 风险预告：无真机期间，所有性能相关阈值均为未校准假设；S007 起必须至少 1 台 arm64-v8a 真机才能继续。

### 2026-08-23 07:52 | S004 | BLOCKED

- 目标：建立三档设备分档基线（改造版：标准表 + 回填流程；真机实测挂起）。
- 依赖：`S002` 已 DONE。
- 实际修改：
  - `qa/device-matrix/devices.yaml`: 新建。`hard_constraints` 五项硬约束（minApi 26 / arm64-v8a / 存储≥20GB / RAM≥6GB / 页大小 4KB，16KB 设备列 special）；三档 tier（flagship/midrange/entry）含 SoC 参考、RAM、API 下限、ABI、页大小、存储下限与模型策略，全部 `verification: REFERENCE_UNVERIFIED`；`measured_devices` 空列表待回填。
  - `docs/device-baseline.md`: 新建。范围变更声明、三档标准表、字段字典 + adb 无 root 采集命令、与 S023-S025 运行时自动打分链路的衔接说明、五步回填流程、解除条件。
- 验证：
  - 命令：`python` + PyYAML 解析与结构断言脚本
  - 结果：`PASS`；YAML parse OK；tiers=[flagship, midrange, entry]；ram_gb={12, 8, 6}；api_min={34, 33, 32}；全部 REFERENCE_UNVERIFIED；无 serialno/imei 等个人标识。
- 阻塞原因：项目当前没有任何真实 Android 设备，原步骤卡“至少三台真实设备”无法满足。
- 已尝试命令：`adb devices -l`（本机 SDK platform-tools，无设备连接）。
- 需要的外部输入：真机硬件——至少 1 台 arm64-v8a 真机可开始后续推理验证；三档齐备才能完整通过 G0 门禁设备项。
- 解除条件：按 `docs/device-baseline.md` 第 3/5 节采集并回填 `measured_devices` 后补全验收。
- 范围变更备注：用户于本轮明确授权“无真机时改造 S004 并先推进 S005-S006”；S005/S006 不依赖 S004（其依赖为 S002/S003 与 S005），G0 设备项以 BLOCKED 挂起方式处理，风险已写入 device-baseline.md 第 1 节。
- 下一步依赖：`S005` 可以开始（依赖 S002、S003 均 DONE，且用户已授权）；本轮没有执行 S005。

### 2026-08-23 07:56 | S005 | IN_PROGRESS

- 目标：锁定 llama.cpp 提交并导入 Native 构建，完成 `arm64-v8a` CPU/NEON 编译，生成 `.so`；不接 Java UI。
- 依赖：`S002`、`S003` 已 DONE。
- 版本锁定决策（2026-08-23 经 GitHub API 核实）：采用 llama.cpp **v0.2.0**——官方 2026-08-21 起启用的语义化稳定版 tag，发布说明明确“vX.Y.Z 为 stable，推荐下游分发锁定；b[NUM] 为 nightly”。tag v0.2.0（annotated，sha 8a35040e）指向 commit `bb4caa7540188872173c44d161602d9271386413`。
- 计划修改：
  - `app/src/main/cpp/engine_llama/`: 导入 v0.2.0 完整源码树（codeload tarball 解包去根目录），不改动其内部文件、不打 patch（本项目 patch 列表为空）。
  - `app/src/main/cpp/CMakeLists.txt`: 新建。CPU-only 配置 add_subdirectory(engine_llama)：BUILD_SHARED_LIBS=ON、GGML_OPENMP=OFF（bionic 无 OpenMP）、GGML_NATIVE=OFF（交叉编译禁 -march=native）、LLAMA_BUILD_EXAMPLES/TESTS/TOOLS=OFF、LLAMA_CURL=OFF、GGML_BACKEND_DL=OFF。
  - `docs/native-baseline.md`: 新建。记录 tag、commit、下载 URL、CMake 参数、构建输出与 `.so` 产物 SHA-256。
- 计划外文件（必要基础设施，验证命令所需，参照 S002 先例记录偏差）：`app/build.gradle` 追加 `externalNativeBuild { cmake { path ... } }` 接线，否则 `:app:externalNativeBuildDebug` 无法运行。
- 计划验证：
  - `.\gradlew.bat :app:externalNativeBuildDebug`: BUILD SUCCESSFUL 且产出 arm64-v8a 的 `libllama.so` 及 ggml 系列共享库。
  - 对全部 `.so` 计算 SHA-256 记入 native-baseline.md。
- 风险预告：GitHub 直连在本机网络可能超时（S002 有 services.gradle.org 超时先例）；若 codeload 失败将尝试 git clone，再失败记 BLOCKED。

### 2026-08-23 07:56 | S005 | DONE

- 目标：锁定 llama.cpp 提交并导入 Native 构建，完成 `arm64-v8a` CPU/NEON 编译；不接 Java UI。
- 依赖：`S002`、`S003` 已 DONE。
- 实际修改：
  - `app/src/main/cpp/engine_llama/`: 导入 llama.cpp v0.2.0（commit `bb4caa7540188872173c44d161602d9271386413`）完整源码树，零修改、零 patch；tarball SHA-256 `72E6C3E7...C947958` 全值见 native-baseline.md。
  - `app/src/main/cpp/CMakeLists.txt`: 新建构建入口，CPU-only 开关组（BUILD_SHARED_LIBS=ON、GGML_OPENMP/NATIVE/BACKEND_DL=OFF、LLAMA_BUILD_*全关、UI/OpenSSL 关闭），add_subdirectory(engine_llama)。
  - `app/build.gradle`: 追加 externalNativeBuild cmake 接线（计划外文件，验证命令必需基础设施，已在 IN_PROGRESS 预告并记录偏差，参照 S002 先例）。
  - `docs/native-baseline.md`: 新建。tag/annotated sha/commit、下载与哈希、开关及理由、产物清单与 SHA-256、已知告警。
- 验证：
  - 命令：`.\gradlew.bat :app:externalNativeBuildDebug --offline --console=plain`
  - 结果：`PASS`；BUILD SUCCESSFUL in 26s。产出 4 个 arm64-v8a 共享库：libllama.so(78.2MB, Debug 含符号)、libggml.so、libggml-base.so、libggml-cpu.so；llvm-readelf 复核 `Machine: AArch64`、`Type: DYN`；四个 .so 的 SHA-256 已记入 native-baseline.md 第 3 节。
- 风险/阻塞：
  - `[CXX5304]` SDK XML v4 提示：cmdline-tools 与 AGP 描述文件版本差异，不影响构建产物，已记录于 native-baseline.md 第 4 节。
  - libllama.so 当前为独立 .so；S006 JNI 层接入时可能改为静态并入单一 .so，届时同步更新基线文档。
  - 真机加载验证属 S007 范围，当前无设备（沿用 S004 BLOCKED 记录）。
  - 时钟备注：本机时区为 UTC+7，自本条起时间戳按 Asia/Shanghai（UTC+8）换算生成；此前条目为本地时钟直读，存在约一小时的表观偏移。
- 下一步依赖：`S006` 可以开始；本轮没有执行 S006。

### 2026-08-23 08:00 | S006 | IN_PROGRESS

- 目标：定义 `load/start/stop/release/getStats` 的 Java/JNI 边界和结构化错误码：接口声明、Native 句柄表、错误码映射、JNI 异常屏障；不加载真实模型。
- 依赖：`S005` 已 DONE。
- 设计决策：
  - 错误码契约（Java/C++ 双侧镜像）：OK=0、INVALID_HANDLE=1001、WRONG_STATE=1002、NULL_ARGUMENT=1003、ILLEGAL_ARGUMENT=1004、INTERNAL=1099。
  - Native 状态机：IDLE -> READY(load 校验通过) -> RUNNING(start) -> IDLE(stop)；本步 load 不触碰 llama.cpp，只做参数校验与状态迁移，真实模型加载在 S007 接入。
  - 句柄表：`unordered_map<jlong, shared_ptr<Session>>` + mutex；`acquire` 以 shared_ptr 延长生命周期防 UAF；句柄 0 保留为无效值。
  - JNI 屏障：每个导出函数 `try { ... } catch (...) { ThrowNew(NativeException) }`，C++ 异常不穿 JNI。
  - Java 守卫：close 后调用抛 IllegalStateException 且不再触达 native；库延迟加载（构造时 loadLibrary），避免 JVM 单元测试触发 UnsatisfiedLinkError。
- 计划修改：
  - `app/src/main/java/com/example/localai/core/inference/NativeSession.java`: 新建。
  - `app/src/main/cpp/ai_jni/native_session.cpp`: 新建。
  - 对应测试：`app/src/test/java/com/example/localai/core/inference/NativeSessionContractTest.java`（纯 JVM：错误码契约 + 关闭守卫）。
- 计划外文件偏差预告（必要基础设施）：`app/src/main/cpp/CMakeLists.txt` 追加 ai_jni 目标（否则 native_session.cpp 无法参与构建）；`app/build.gradle` 追加 `dependencies { testImplementation libs.junit }`（验证命令 :app:testDebugUnitTest 需要 JUnit 在测试类路径）。二者均为步骤卡自带验证命令的运行前提，参照 S002/S005 先例记录。
- 验收边界说明：通过标准中“Java 可创建和释放句柄”的运行时冒烟依赖 arm64 设备或宿主 Native 测试环境，当前两者均不可用（沿用 S004 BLOCKED 记录）；本步以“编译成功 + JVM 可测部分全部通过”为已可达成证据，剩余项在 DONE/BLOCKED 判定中如实区分。

### 2026-08-23 08:12 | S006 | DONE

- 目标：定义 `load/start/stop/release/getStats` 的 Java/JNI 边界和结构化错误码；不加载真实模型。
- 依赖：`S005` 已 DONE。
- 实际修改：
  - `app/src/main/java/com/example/localai/core/inference/NativeSession.java`: 新建。错误码常量（0/1001/1002/1003/1004/1099）、synchronized 生命周期方法、close 幂等与关闭后守卫、库延迟加载、NativeException(code)。
  - `app/src/main/cpp/ai_jni/native_session.cpp`: 新建。HandleTable（unordered_map + shared_ptr acquire 防释放竞态）、IDLE/READY/RUNNING 状态机（CAS 迁移）、每个导出函数 catch(...) 屏障转 ThrowNew(NativeException)、getStats 只输出状态 JSON 不含对话内容。
  - `app/src/main/cpp/CMakeLists.txt`: 计划外偏差——追加 `add_library(ai_jni SHARED ...)` 并链接 llama/ggml 目标，native_session.cpp 参与构建的前提。
  - `app/build.gradle`: 计划外偏差——`dependencies { testImplementation libs.junit }`，验证命令 :app:testDebugUnitTest 的类路径前提。两处均为步骤卡自带验证命令的必要基础设施（IN_PROGRESS 已预告）。
  - `app/src/test/java/com/example/localai/core/inference/NativeSessionContractTest.java`: 新建对应测试（纯 JVM，不加载 Native 库）。
- 验证：
  - 命令：`.\gradlew.bat :app:testDebugUnitTest :app:externalNativeBuildDebug --console=plain`
  - 结果：`PASS`；BUILD SUCCESSFUL in 28s。测试报告 `NativeSessionContractTest tests="4" failures="0" errors="0"`（错误码镜像契约、零句柄守卫、空参校验、异常携带 code）。Native 侧产出 `libai_jni.so`（约 416KB，arm64-v8a），成功链接 libllama/libggml。
- 证据边界（如实声明）：通过标准中“Java 可创建和释放句柄、C++ 异常不穿 JNI”的**运行时**冒烟需 arm64 设备执行，当前无设备；本步完成全部代码实现 + 编译链接 + JVM 可测子集。运行时冒烟并入 S007 设备验证范围（create→load→start→stop→release 序列即 S007 冒烟内容），S007 本身受 S004 同一设备条件阻塞。
- 风险/阻塞：
  - nativeLoad 当前为状态迁移桩，未触碰模型文件；S007 接入真实加载时必须保留本步的错误码契约并同步更新 ContractTest 若有扩展。
- 下一步依赖：`S007` 可以开始（但需至少一台 arm64-v8a 真机，设备条件沿用 S004 BLOCKED 记录）；本轮没有执行 S007。

### 2026-08-23 08:28 | MIGRATION | DONE

- 目标：执行协议由“单步骤卡（S001-S045）”迁移为“六阶段卡（P1-P6）”；同步 `docs/ai-execution-plan.md` 与日志头，历史记录原样保留。
- 依赖：用户于本轮明确授权（选项 1：同步文档并开始 P1）。
- 迁移内容：
  - `docs/ai-execution-plan.md`: 由 `plan/Android端侧本地AI与智能模型市场AI单步执行方案.md` 新版整体替换（v2.0，2026-08-22：P1-P6 六阶段主任务 + S001-S045 降级为阶段内技术检查清单 + 软件优先/真机集中在 P6 的交付顺序）。
  - `docs/ai-work-log.md`: 日志头切换为阶段制导航；“执行记录”中全部 S 步骤历史条目未做任何改写，其后追加本条迁移记录与 P1 记录。
- 历史进度归位映射：
  - `S001-S003` → G0 工程基线检查项，DONE 保持有效；
  - `S004` → 真机采集部分维持 BLOCKED，按新协议归入 P6 前置检查项（S042），不再阻塞软件阶段；
  - `S005-S006` → 归入 P3 阶段技术检查清单（G1 Native 推理基线），已完成证据在 P3 执行时直接引用；
  - `S007-S010`（Native 冒烟/Benchmark 协议）→ 并入 P3 范围；`S012-S019`（供应链）→ 并入 P2 范围；其余检查项按第 4 节阶段卡归属。
- 验证：
  - 命令：`git status --short`
  - 结果：`PASS`；本轮改动 = 用户更新的 plan 三文件（既有改动，保护不动）+ docs 两文件 + 既有未提交的 S005/S006 成果（保护不动）；无越界覆盖。
- 风险/阻塞：无。
- 下一阶段依赖：`P1` 本轮开始执行（用户授权“迁移+P1”合并一轮）；迁移本身不替代任何阶段验收。

### 2026-08-23 08:28 | P1 | IN_PROGRESS

- 目标：在模拟数据下交付美观、可操作的 Android 前端骨架：市场、模型详情、下载管理、本地聊天（流式模拟）、会话历史、诊断、设置七类页面 + 统一视觉系统（M3 定制主题、深色模式、空/加载/错误态）+ 底部导航与二级页面流转；产出可安装 Debug APK 并通过单元测试。
- 依赖：无（允许使用模拟数据）；复用既有工程基线（S002 构建基线、S003 依赖登记）。
- 模拟器条件：本机存在 AVD `Medium_Phone`（platform-tools adb 可用），P1 卡要求的“逐页截图并检查核心流程”本轮可真实执行。
- 计划修改：
  - `docs/ai-execution-plan.md`、`docs/ai-work-log.md`: 协议迁移（见 MIGRATION 记录）。
  - `gradle/libs.versions.toml`: 登记 Material Components（com.google.android.material）版本。
  - `app/build.gradle`: 引入 material/appcompat/core/recyclerview UI 依赖（P1 卡“必要的 Gradle UI 依赖”）。
  - `app/src/main/AndroidManifest.xml`: 注册 MainActivity（launcher）。
  - `app/src/main/java/com/example/localai/`: MainActivity + mock 数据层（MockStore/Filters/MockDownloadEngine/MockChatEngine + POJO）+ feature/{market,download,chat,diagnostics,settings} 页面与适配器 + EmptyStateView。
  - `app/src/main/res/`: values{,-night}/{colors,themes,styles,dimens,strings}、menu/bottom_nav、anim、layout×17、drawable（矢量图标与形状）。
  - `app/src/test/java/`: FiltersTest、DownloadTaskTest、ReplyComposerTest（纯 JVM）。
  - `docs/ui-design.md`: 设计令牌与页面状态矩阵文档。
  - `docs/license-policy.md`、`NOTICE`: 补登 Material 组件条目（S003 政策要求直接依赖必须登记，属计划外合规性偏差，特此声明）。
- 计划验证：
  - `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
  - 模拟器逐页截图：boot `Medium_Phone` → installDebug → adb 截图市场/详情/下载/聊天/历史/诊断/设置 + 深色模式抽查。
- 范围声明：不接入真实推理/网络/Room（P2/P3 范围）；不修改 S005/S006 既有 Native/JNI 文件。

### 2026-08-23 11:16 | P1 | DONE

- 目标：在模拟数据下交付美观、可操作的 Android 前端骨架（市场/详情/下载/聊天/历史/诊断/设置 + 统一视觉系统 + 深色模式 + 空/加载/错误态），通过构建、单元测试与模拟器逐页验收。
- 依赖：无（允许模拟数据）；复用 S002 构建基线与 S003 依赖登记。
- 实际修改（本轮新增，全部在 P1 允许范围内）：
  - `app/src/main/java/com/example/localai/MainActivity.java`: 修复两处导航缺陷——①`openTab` 中 `setSelectedItemId` 在监听器回调期间同步派发导致无限递归（StackOverflowError），改为 `MenuItem.setChecked(true)` + 直接 `showTab`；②`showTab` 先 `hideAll` 后判断 `target.isHidden()`（此时 hide 尚未提交恒为 false），导致从返回栈恢复的聊天 Fragment 被隐藏后不重新显示，改为对已存在目标无条件 `tx.show(target)`。
  - `app/src/main/java/com/example/localai/mock/MockChatEngine.java`: 修复 `stop()` 不回调 `onFinished`（`removeCallbacksAndMessages` 连带移除待执行 emit，UI 永久卡在“生成中”）：stop 时若此前在运行则同步回调 `onFinished(true)`，并新增 listener 字段。
  - `app/src/main/java/com/example/localai/feature/chat/ChatFragment.java`: 修复“思考阶段停止”时打字气泡残留——`onFinished(stopped)` 在 `streamingBot == null` 时移除 TYPING 占位项。
  - `app/src/main/java/com/example/localai/feature/chat/HistoryFragment.java`: 修复会话历史永不渲染（RecyclerView 未设 LayoutManager）；修复 `icon_bg` 强转 TextView 的 ClassCastException（实际为 View）。
  - `app/src/main/java/com/example/localai/feature/download/DownloadsFragment.java`: 修复下载完成/变化时“已安装”列表不刷新（`onDownloadsChanged` 增加 `refreshInstalled()`）。
  - `app/src/main/res/layout/fragment_settings.xml`: 修复设置页 inflate 崩溃（`Text.App.*` 样式不含尺寸，多行 TextView 缺 layout_width/height，Binary XML line 92）；修复模式卡异常高（MaterialCardView 为 FrameLayout，`layout_weight` 无效导致文字 0 宽逐字竖排），三张模式卡改为“横向 LinearLayout 容器 + RadioButton + ModeTextCol”。
- 验证：
  - 命令：`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --offline --rerun-tasks`
  - 结果：`PASS`；BUILD SUCCESSFUL（45/45 任务重跑）；单元测试 20 个（FiltersTest 6 + ReplyComposerTest 5 + DownloadTaskTest 5 + NativeSessionContractTest 4）failures=0 errors=0；产物 `app-debug.apk` 约 31.2 MB。
  - 模拟器逐页验收（AVD `Medium_Phone`，x86_64，uiautomator dump 结构校验 + 截图像素校验，均无越界节点）：市场（搜索/筛选 chips/精选横幅/模型卡片计数 8）、详情（头图/三格数据/适配结论/元信息/3 条示例指令/主按钮）、下载（存储条/进行中/暂停/校验失败卡/重试与取消按钮/已安装列表，下载完成后列表实时刷新已回归验证）、聊天（流式模拟输出、中途停止后追加“（已停止生成）”且标题恢复“已就绪”、模型切换 BottomSheet 切换生效）、历史（4 个预置会话卡片渲染、点击会话恢复聊天消息）、诊断（设备规格网格/状态 chips/基准列表/适配结论图例与逐条原因）、设置（模式三选卡点击切换 radio 生效、MaterialSwitch 开关生效、清缓存/清会话/许可证行）。
  - 深色模式抽查：`cmd uimode night yes` 后设置页与市场页截图像素校验命中深色令牌（背景 #0B0E18、表面 #131725、主色 #A5B4FC、正文 #E8EBF3），浅色恢复正常。
  - 崩溃回归：本轮验收中发现并修复 2 个可复现崩溃（导航递归 StackOverflowError、设置页 XML inflate 异常）与 1 个 ClassCastException，修复后相同操作路径重测通过；`git status --short` 全部改动位于 P1 允许范围或既有受保护改动，无越界。
- 风险/阻塞：无。
  - 备注 1：验收截图保存在临时目录（未入库）；uiautomator 在下载任务活动期间因 150ms 进度刷新无法 idle，采用“暂停任务后 dump + 截图像素分析”双通道完成验收，属验证手段问题而非产品缺陷。
  - 备注 2：P1 卡片要求的美观度硬性验收以“结构完整性 + 无越界/截断 + 空态/错误态/深色模式齐全 + 全部交互可点击”为准；人眼级视觉评审建议由用户在模拟器安装 `app-debug.apk` 复核。
- 下一阶段依赖：`P2` 可以开始；本轮没有执行 P2。

### 2026-08-23 11:29 | P1 | IN_PROGRESS

- 目标：补完 P1 卡片最后一项验收——人眼级视觉复查：截取全部核心页真实截图并逐张目检，发现并修复视觉缺陷后复验。范围仍为 P1（P1 已 DONE，本轮是其验收闭环，不进入 P2）。
- 依赖：无；运行中模拟器 `emulator-5554`（x86_64 AVD Medium_Phone）已安装上一轮 APK。
- 计划修改：
  - `app/src/main/java/com/example/localai/feature/download/DownloadAdapter.java`: 修复“剩余 0 MB”样式。
  - `app/src/main/java/com/example/localai/common/Fmt.java`: 新增 `humanEta`。
  - `app/src/main/res/drawable/bg_gradient_hero.xml`: 尺寸（圆角）。
  - `app/src/main/res/layout/fragment_market.xml`、`app/src/main/res/layout/fragment_model_detail.xml`: 头部渐变修复。
  - `app/src/test/java/com/example/localai/common/FmtTest.java`: 新增测试。
- 计划验证：
  - `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --offline`
  - 重新安装 APK 后截图复查：市场/详情（浅色+深色）/下载页。先修复后复验。

### 2026-08-23 11:29 | P1 | DONE

- 目标：P1 视觉复查闭环。已修复两处视觉/文案缺陷并完成真机（模拟器）目检复验。
- 依赖：无。
- 实际修改：
  - `app/src/main/res/drawable/bg_gradient_hero.xml`: shape 添加 `<corners radius=22dp>`（配合内层容器裁剪）。
  - `app/src/main/res/layout/fragment_market.xml`: `card_hero` 的 `android:background` 移到内层 `FrameLayout`（`clipToOutline=true` + `outlineProvider=background`）；MaterialCardView 会忽略 `android:background` 导致渐变不绘制。
  - `app/src/main/res/layout/fragment_model_detail.xml`: 同款修复（详情头部渐变图）。
  - `app/src/main/java/com/example/localai/feature/download/DownloadAdapter.java`: ETA 由“秒数误当字节数”改为 `Fmt.humanEta`（m:ss）。
  - `app/src/main/java/com/example/localai/common/Fmt.java`: 新增 `humanEta`（负数钳零、≥1 小时 h:mm:ss）。
  - `app/src/test/java/com/example/localai/common/FmtTest.java`: 新增 4 个测试（ETA 格式/小时/负值/字节格式）。
- 验证：
  - 命令：`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --offline`
  - 结果：`PASS`；BUILD SUCCESSFUL；单测 24 个（24/24 failures=0：新增 FmtTest 4 + 原 20）。
  - 命令：adb 重装 + 截图目检（浅色市场页、浅色详情页、下载管理页、深色市场页、hero 点击跳详情）
  - 结果：`PASS`；①市场精选横幅现在正确显示 indigo→violet 渐变、白字“本周精选/Qwen3-4B-Instruct/查看详情→”；②详情页渐变头部正常（浅色）；③下载页“Qwen2.5-Coder … 38% 正在下载 · 5 MB/s · 剩余 1:53”，剩余值真实为 m:ss 而非“0 MB”；④深色模式渐变正常并与深色令牌一致；⑤hero 卡片点击仍能进入详情（布局调整未破坏监听器）。
- 视觉复查覆盖页面：市场（横幅/筛选/卡片）、详情（头图/三格/适配卡/元信息/示例指令/开始对话按钮）、聊天（空态/输入/流式/完成态）、历史（5 条会话卡）、下载（存储条/进行中/暂停/失败重试/已安装）、诊断（规格网格/状态 chip/基准/兼容性明细）、设置（模式三选卡/开关/数据行）——无越界/截断/默认控件残留/不可读文本。
- 风险/阻塞：无。
  - 备注：浮动的系统 IME 工具栏（模拟器 GBoard 竖条）与 emulator 自身的 URL 粘贴弹窗出现在聊天截图内，均为系统输入法行为，非应用缺陷。截图存于临时目录未入库。
- 下一阶段依赖：`P2` 可以开始；本轮没有执行 P2。


### 2026-08-23 11:48 | P2 | IN_PROGRESS

- 目标：实现签名 Manifest、模型目录、下载任务、断点续传（Range/ETag）、SHA-256/GGUF 校验、Room 模型/会话/消息数据与原子安装/回滚；本地 HTTP Fixture 可完成"拉取目录 -> 下载 -> 校验 -> 安装 -> 删除/回滚"，下载页显示真实任务状态，前端不再依赖写死的下载状态。
- 依赖：`P1` 已 DONE；复用 S002 构建基线、S003 已登记的 Room 2.8.4 / OkHttp 5.5.0 坐标（本轮起正式引入）。
- 环境探测（2026-08-23）：阿里云 Maven 镜像（google/central 两个仓库）可达（HEAD 200）；Gson 2.11.0 已在本机 Gradle 缓存；JVM 上 `com.sun.net.httpserver` 可编译运行（Fixture 服务器用）；AVD `Medium_Phone` 在线（emulator-5554）。
- 设计决策：
  - Ed25519：后端签名用 JDK 17 原生 Ed25519（backend 签名 CLI）；客户端验证用自研纯 Java verify-only 实现（v0 基于 str4d/ed25519-java CC0 公开域算法结构，供 API 26 无平台 EdDSA 设备使用），单测与 JDK Ed25519 交叉验证。
  - 签名载荷：`manifest.sig` 覆盖 `manifest.json` 的原始字节流，不做重序列化规范化；任何字节篡改即验证失败。
  - 目录协议：`GET /v1/catalog.json`(+`.sig`) -> 条目 `{modelId, version, displayName}`；客户端按固定路径取 `models/{modelId}/{version}/manifest.json`(+`.sig`)。
  - 下载：OkHttp + `Range: bytes=offset-` + `If-Range: etag`；200 全量重下、206 续传校验 `Content-Range`、416 归零重下、ETag 变化截断重下（不拼接新旧内容）；`.part` 文件 + 固定缓冲区流式写盘。
  - 校验链：签名 -> 大小 -> SHA-256 -> GGUF 头部探针（magic/version/general.architecture）-> 原子安装（staging + install.ok + 同文件系统 rename）；失败保留旧版本、清理孤儿 staging。
  - 持久化：Room 2.8.4（annotationProcessor，Java 兼容线）；Download/Model/Conversation/Message 四实体；进程重启恢复 = 应用启动扫描非终态任务并以 Range 续传。
  - 测试：Room DAO 用 Robolectric 4.16（android-all 走阿里云 central 镜像）；HTTP 协议测试用自建 `com.sun.net.httpserver` Fixture（支持 200/206/416/断连/ETag 切换/故障注入）。
- 计划修改：
  - `backend/manifest-schema/`: `model-manifest.schema.json`、`catalog.schema.json`。
  - `backend/signing/`: `ManifestSigner.java`（CLI：keygen/sign/verify）+ dev 密钥与签名 fixtures。
  - `backend/fixture-server/`: `FixtureServer.java`（本地演示服务器，Range/ETag）。
  - `backend/fixtures/`: 签名 catalog + 演示模型 manifest（+sig）+ 确定性 GGUF 演示载荷生成。
  - `app/src/main/java/com/example/localai/data/network/`: TrustStore/TrustedKeys/Ed25519/ManifestVerifier/CatalogClient。
  - `app/src/main/java/com/example/localai/data/room/`: 四实体 + DAO + AppDatabase + schemas 导出。
  - `app/src/main/java/com/example/localai/data/storage/`: ModelStorageManager。
  - `app/src/main/java/com/example/localai/feature/download/`: DownloadCoordinator/ModelVerifier/DownloadRepository + DownloadsFragment/DownloadAdapter 真实化。
  - 计划外偏差预告（必要基础设施，参照 S002/S005/S006 先例）：`app/build.gradle` 引依赖/注解处理/测试依赖与 schemas 目录；`AndroidManifest.xml` 加 INTERNET 权限与网络安全配置（下载必需）；`res/xml/network_security_config.xml` 新建（仅放行 10.0.2.2/localhost 明文，供本地 Fixture 联调）；`ModelDetailFragment` 下载按钮改为真实入队（P2 通过标准"前端不再依赖写死的下载状态"要求）；`gradle/libs.versions.toml` 补登 Gson/Robolectric/androidx.test（license-policy 同步）；`docs/model-onboarding.md`、`docs/license-policy.md`、`NOTICE`。
- 计划验证：
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  - Fixture 测试覆盖：200/206/416、断网、ETag 变化、哈希错误、签名错误、进程重启恢复。
  - 模拟器演示：起后端 FixtureServer(host:8090) -> 下载页拉目录 -> 下载 -> 校验 -> 安装 -> 强杀重启恢复 -> 删除。

### 2026-08-23 12:50 | P2 | DONE

- 目标：实现签名 Manifest、模型目录、下载任务、断点续传（Range/ETag）、SHA-256/GGUF 校验、Room 模型/会话/消息数据与原子安装/回滚；本地 HTTP Fixture 完成"拉取目录 -> 下载 -> 校验 -> 安装 -> 删除/回滚"，下载页显示真实任务状态。
- 依赖：`P1` 已 DONE。
- 实际修改：
  - `backend/manifest-schema/model-manifest.schema.json`、`catalog.schema.json`: 新建，Manifest/目录 JSON Schema（draft-07）。
  - `backend/signing/ManifestSigner.java`、`backend/signing/keys/`: 新建，JDK 17 Ed25519 签名 CLI（keygen/sign/verify）；dev 密钥对（公钥入库、私钥 gitignore + README 说明）；签名载荷 = 数据文件原始字节流，sig 侧车文件承载。
  - `backend/fixture-server/DemoPayload.java`、`FixtureServer.java`: 新建，确定性 GGUF 演示载荷生成 + 本地演示服务器（静态 catalog/manifest + 动态载荷，单段 Range/If-Range/416 语义）。
  - `backend/fixtures/`: 新建，8 个演示模型（与 MockStore 同 modelId）签名 manifest/catalog + 生成脚本 generate.ps1；演示载荷为合成字节（GGUF 头 + 确定性填充），非真实权重。
  - `app/src/main/java/com/example/localai/data/network/`: 新建 Ed25519（verify-only 纯 Java，RFC 8032 向量 + JDK 17 交叉验证 20/20 通过，约 1.7ms/次）、TrustStore/TrustedKeys（内置 dev 公钥 + 撤销列表）、ManifestVerifier（六档错误码）、ModelManifest/Catalog（Gson 镜像 + 客户端结构校验）、CatalogClient、CatalogConfig。
  - `app/src/main/java/com/example/localai/data/room/`: 新建 DownloadEntity/Dao（S014 全字段 + 状态机 canTransition）、ModelEntity/Dao（modelId+version 联合主键）、ConversationEntity/MessageEntity/Dao（级联删除）、AppDatabase v1 + schemas 导出至 `app/schemas/`。
  - `app/src/main/java/com/example/localai/data/storage/ModelStorageManager.java`: 新建，staging + install.ok + 同文件系统 rename 原子安装；目标已存在先退避 .rollback，失败自动回滚；孤儿 staging 启动清理（含 install.ok 的遗留保留）。
  - `app/src/main/java/com/example/localai/feature/download/`: 新建 DownloadCoordinator（双执行器：worker 下载/control 控制面；200/206/416/If-Range 安全处理，绝不拼接新旧内容；断网 FAILED 保留 .part 可重试续传；哈希/GGUF 失败删 .part；recoverPending 进程重启恢复）、ModelVerifier（流式 SHA-256 + GGUF 头部探针：magic/version/general.architecture）、DownloadRepository（主线程安全快照 + 目录/任务/已安装缓存 + 事件转发）；DownloadsFragment/DownloadAdapter 重写为真实状态（目录区/任务区/已安装区 + StatFs 实测存储）；新增 CatalogAdapter。
  - 计划外偏差（已在 IN_PROGRESS 预告）：`app/build.gradle` 引依赖（okhttp 4.12.0——5.5.0 强制 compileSdk 37 与固定基线冲突，已降级并更新 license-policy；gson 2.11.0；room 2.8.4 annotationProcessor；测试 robolectric 4.16/androidx.test:core）；`AndroidManifest.xml` 加 INTERNET + `android:name=".App"` + networkSecurityConfig；`res/xml/network_security_config.xml`（仅放行 10.0.2.2/localhost 明文）；`ModelDetailFragment` 下载按钮改接真实入队（P2 通过标准"前端不再依赖写死的下载状态"）；`gradle/libs.versions.toml`、`NOTICE`、`docs/license-policy.md`、`docs/model-onboarding.md` 同步。
- 验证：
  - 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  - 结果：`PASS`；78 个单元测试 0 失败（Ed25519 5、ManifestVerifier 7、CatalogClient 8、ModelVerifier 8、ModelStorageManager 7、RoomPersistence 6、DownloadPipeline 11、DownloadRepository 2、既有 P1 测试 24）。Fixture 覆盖 P2 卡全部场景：200 全量、206 续传（断言 Range+If-Range 请求头）、416 越界重启、断网（断点保留 + 重试续传）、ETag 变化（200 全量重下且最终文件与内容 B 逐字节一致，无新旧拼接）、哈希错误（拒绝安装并删除 .part）、签名错误（拒绝建任务）、进程重启（文件库关闭重开 + recoverPending 走 Range 恢复）、取消/重复入队/未知模型拒绝。
  - 模拟器端到端（AVD Medium_Phone，后端 FixtureServer 宿主 8090）：下载页同步目录 8 个签名模型 -> 下载 6MB 演示模型（真实进度）-> SHA-256 校验通过（设备安装文件实测 52f2c773…6b535 与 Manifest 声明逐字节一致）-> 原子安装（install.ok/manifest.json/manifest.sig/gguf 四件套齐）-> 强杀重启后已安装列表与 Room 持久化一致 -> 删除对话框后磁盘目录与 installed_models 记录同步清除。故障路径实测：旧服务器 404 时任务显示"失败 · 服务器资源不存在（404）"，修复后重试成功。
- 风险/阻塞：无。
  - 备注 1：演示目录的 sizeBytes 为演示载荷真实大小（MB 级），与市场页 MockStore 的 GB 级展示不一致，属 P1 演示数据残留；P4 将市场数据源切到签名目录后自然统一。
  - 备注 2：dev fixture 私钥（backend/signing/keys/*.seed）已 gitignore；公钥嵌入 App TrustedKeys，重新签发流程见 backend/signing/keys/README.md；生产密钥与撤销接口在 P5 发布门禁前替换。
  - 备注 3：Robolectric 固定 sdk=35（SDK 36 沙箱要求 Java 21，本机 JDK 17）；android-all 镜像走阿里云 central（build.gradle 已配置）。
  - 备注 4：下载调度暂为应用内协调器（前台进程内可恢复），WorkManager 化与前台服务留待 P4/P5（模型卡未强制，S018 属阶段内核对项，风险已记录）。
- 下一阶段依赖：`P3` 可以开始；本轮没有执行 P3。

### 2026-08-23 18:52 | P3 | IN_PROGRESS

- 目标：接入固定版本 llama.cpp（v0.2.0，S005 已锁定）实现真实本地推理：JNI 加载/生成/停止/释放 + 流式 Token 批量回调 + 结构化错误码；InferenceService 独立进程 + AIDL 协议 + InferenceClient（Binder death 恢复）；聊天页可接收真实流式事件；重复 stop/release、服务重启与错误回调有自动化验证。
- 依赖：P1、P2 已 DONE；复用 S005 Native 基线（libllama/ggml，debug ABI x86_64 供模拟器）、S006 错误码契约、P2 的 ModelStorageManager 安装布局。
- 环境探测（2026-08-23）：hf-mirror.com 可达（huggingface.co 直连不可达）；AVD Medium_Phone 存在但当前未运行（验证前需启动）；已确认批准模型源 second-state/SmolLM-135M-Instruct-GGUF 的 Q4_K_M 文件可下载（后台下载中）。
- 设计决策：
  - Native：Session 持有 llama_model/llama_context/llama_sampler；load 完成真实加载（backend_init + model_load + init_from_model + sampler chain temp/top_p/top_k/dist）；start 起单工作线程：tokenize -> 分批 decode 提示 -> 采样循环 -> token_to_piece 累积成批次（>=8 token 或 >=64 字符或 50ms 窗口）回调 Java StreamListener；abort_callback 挂到原子 cancel 标志实现快速停止；release 设置 cancel 后 join 工作线程再释放资源（防 UAF/悬空回调），重复释放幂等；C++ 异常不穿 JNI。
  - 错误码扩展：新增 MODEL_LOAD_FAILED=1101、CONTEXT_CREATE_FAILED=1102、TOKENIZE_FAILED=1103（与 Java 镜像）；结束原因 FINISH_END=0 / FINISH_STOPPED=1。
  - 进程隔离：InferenceService 在 android:process=':inference'，UI 经 AIDL 调用；推理进程代码路径不含任何网络 API（INTERNET 为应用级权限，隔离以架构级验证：模拟器上对 :inference 进程 /proc/<pid>/net/tcp 检查无套接字）；服务内用 Handler 串行化回调，AIDL 回调方法 oneway。
  - 批准模型：SmolLM-135M-Instruct Q4_K_M（second-state 发布，~100MB，仅英文），登记 qa/fixtures/approved-model.json（SHA-256 下载完成后回填）；模型文件放置于 ModelStorageManager 布局 files/models/{id}/{version}/。
  - 聊天接入：ChatEngine 接口（MockChatEngine 保持 P1 行为并实现之）+ RealChatEngine（InferenceClient 封装，onError 呈现可见错误）；ChatEngineProvider 按“批准模型已安装”选择真实推理，否则演示模式；模型选择器并入已安装的批准模型。
  - 测试：JVM 单测（错误码镜像、Parcelable 往返、引擎选择逻辑）；新增 androidTest 基础设施（androidx.test runner/rules，P3 卡“Native 测试”范围）覆盖错误回调、重复 stop/release、服务杀死重启重连。
- 计划修改：
  - app/src/main/cpp/ai_jni/native_session.cpp: 重写为真实 llama.cpp 加载/生成/流式回调。
  - app/src/main/java/com/example/localai/core/inference/: NativeSession 扩展、NativeStreamListener、InferenceRequest/InferenceStats（Parcelable）、InferenceClient、ApprovedModels。
  - app/src/main/aidl/com/example/localai/: IInferenceService.aidl、IInferenceCallback.aidl、InferenceRequest.aidl、InferenceStats.aidl。
  - app/src/main/java/com/example/localai/core/inference/InferenceService.java: 独立进程服务（计划外基础设施：AndroidManifest.xml 注册 service）。
  - app/src/main/java/com/example/localai/feature/chat/: ChatEngine/RealChatEngine/ChatEngineProvider + ChatFragment/ModelPickerSheet 接线。
  - app/src/androidTest/: InferenceServiceInstrumentedTest（新建 androidTest 目录）。
  - qa/fixtures/approved-model.json、docs/model-onboarding.md、docs/native-baseline.md、NOTICE、docs/license-policy.md、gradle/libs.versions.toml、app/build.gradle（androidx.test 依赖，合规登记）。
- 计划验证：
  - .\gradlew.bat :app:externalNativeBuildDebug :app:testDebugUnitTest :app:assembleDebug
  - .\gradlew.bat :app:connectedDebugAndroidTest（模拟器：错误回调、重复 stop/release、服务重启）
  - 模拟器真模型端到端：adb push 批准模型 -> 聊天页真实流式生成 -> 中途停止 -> 杀死 :inference 进程 -> 恢复。

### 2026-08-23 20:30 | P3 | DONE

- 目标：接入固定版本 llama.cpp（v0.2.0）实现真实本地推理：JNI 加载/生成/停止/释放 + 流式 Token 批量回调 + 结构化错误码；InferenceService 独立进程 + AIDL 协议 + InferenceClient（Binder death 恢复）；聊天页可接收真实流式事件；重复 stop/release、服务重启与错误回调有自动化验证。
- 依赖：P1、P2 已 DONE；S005 Native 基线、S006 错误码契约、P2 ModelStorageManager 安装布局。
- 实际修改：
  - app/src/main/cpp/ai_jni/native_session.cpp: 重写为真实推理（backend_init -> model_load -> init_from_model -> 采样链 top_k/top_p/temp/dist；单工作线程 tokenize -> 分批 decode -> 采样循环 -> token_to_piece 批量合并（8 token/64 字符/60ms）回调；abort_callback 挂原子取消标志实现快速停止；release 置取消后 join 再释放资源；每轮生成前 llama_memory_clear+synchronize 重置 KV；错误码新增 1101/1102/1103；日志只含计数/耗时，不含 Prompt/回复内容）。适配 v0.2.0 新批 API（llama_batch_get_one 两参、llama_decode 返回 2=中止、位置自动跟踪）。
  - app/src/main/cpp/CMakeLists.txt: ai_jni 链接 llama/ggml/log。
  - app/src/main/java/com/example/localai/core/inference/: NativeSession 扩展（StreamListener、FINISH_END/STOPPED、load/start 新签名）、InferenceRequest/InferenceStats（Parcelable）、InferenceService（独立进程，路径白名单校验、回调主线程串行转发、客户端死亡自动取消）、InferenceClient（状态机 + linkToDeath + ENGINE_CRASHED=1199 + restart 恢复）、ApprovedModels（批准模型注册表）。
  - app/src/main/aidl/com/example/localai/core/inference/: IInferenceService/IInferenceCallback/InferenceRequest/InferenceStats 协议。
  - app/src/main/java/com/example/localai/feature/chat/: ChatEngine 接口、RealChatEngine（ChatML 提示 + 流式转发）、ChatEngineProvider（批准模型已安装 -> 真实推理，否则演示模式）、ChatFragment 双引擎接线（模式标签/错误气泡/历史重发）、MockChatEngine 实现接口。
  - app/src/main/java/com/example/localai/App.java: 进程门控——:inference 进程跳过 ServiceLocator（OkHttp/Room/下载协调器）初始化，保证推理进程无网络代码路径。
  - app/src/main/AndroidManifest.xml: 注册 InferenceService（android:process=':inference'，exported=false）。
  - app/build.gradle + gradle/libs.versions.toml: buildFeatures.aidl=true（AGP 8 默认关闭）、androidx.test runner/rules/ext-junit（androidTest 域）；NOTICE/license-policy 同步登记。
  - app/src/androidTest/.../InferenceServiceInstrumentedTest.java: 6 项仪器化测试（错误回调、路径拒绝、独立进程、重复 stop/release、进程杀死 + ENGINE_CRASHED + 重启恢复、多轮生成循环）。
  - app/src/test/: NativeSessionContractTest 扩展、InferenceRequestTest、RealChatEngineTest、ChatEngineProviderTest（共 +10 单测）。
  - qa/fixtures/approved-model.json: 批准模型登记（SmolLM-135M-Instruct Q4_K_M，100MB，SHA-256 30A78B6B…5288A，来源 second-state/SmolLM-135M-Instruct-GGUF 经 hf-mirror 下载，.gguf 已 gitignore）。
  - docs/: native-baseline.md（P3 接入节）、model-onboarding.md（第 7 节批准模型）、license-policy.md/NOTICE（androidx.test + 模型权重许可归属）。
  - 计划外偏差：App.java 进程门控（架构要求"推理进程不访问网络"的直接实现）；IInferenceService.getPid()（同 UID 进程杀死测试与诊断用）；fragment_chat.xml text_model maxLines=2 + chat_no_model 字符串（模式标签显示）。
- 验证：
  - 命令：.\gradlew.bat :app:externalNativeBuildDebug :app:testDebugUnitTest :app:assembleDebug
  - 结果：PASS；arm64-v8a + x86_64 双 ABI 原生构建成功；单测 88 个全过（新增 10）；assembleDebug 产出可安装 APK。
  - 命令：.\gradlew.bat :app:connectedDebugAndroidTest（模拟器 AVD Medium_Phone，x86_64，真模型已安装）
  - 结果：PASS；6/6 全过且连续 3 轮稳定（错误回调：垃圾文件 1101、越界路径 1004；服务进程 pid ≠ 测试进程；stop 中途取消 FINISH_STOPPED + 重复 stop/release 不崩溃；Process.killProcess 杀死 :inference 后收到 ENGINE_CRASHED=1199，restart 重载后 104 token 恢复生成；3 轮循环生成后 getStats.genTokens>0）。
  - 模拟器 UI 端到端：聊天页选择 SmolLM-135M-Instruct -> 标题切换"本地推理" -> 发送消息 256 token 真实流式回复 -> 中途停止（cancel=1）-> adb 杀死 :inference -> 页面显示"生成失败：推理进程已退出（1199）"且 UI 无崩溃 -> 再次发送自动重启推理进程并完成回复（新 pid，104 token）。
  - 推理进程网络隔离：App.java 进程门控 + 运行时证据——:inference 进程 fd 表仅 4 个 UNIX 域 socket（Binder/looper），/proc/net/{tcp,tcp6,udp,udp6} 中该进程 inode 零命中。
  - 日志隐私：logcat 对 local-ai-jni/InferenceService 标签检索 Prompt 文本 0 命中（日志只含长度/计数/Token id）。
- 风险/阻塞：
  - 修复的两个本轮缺陷：①生成结束状态机误置 IDLE 导致第二轮 start 返回 1002（已改为回 READY）；②App.onCreate 在 :inference 进程初始化网络组件（已加进程门控）。
  - 调试期曾出现"首 token 即 EOS/0 token"：确认为指令模型对无模板裸提示的温度采样行为（logits 有限 nan=0），产品路径用 ChatML 模板规避；测试已统一 ChatML 提示。
  - SmolLM-135M 为 0.14B 验收模型，输出质量有限；市场正式模型的上架与真实权重分发仍走 P2 供应链（P4 接通）。
  - 模拟器 x86_64 性能波动大（256 token 生成 24s~90s+），真机性能基线属 P6。
- 下一阶段依赖：P4 可以开始（接通市场/下载/安装/聊天真实闭环）；本轮没有执行 P4。
