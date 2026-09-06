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
- Kotlin 语言迁移（`docs/kotlin-migration-plan.md`）使用 `MIGRATION-K0` ～ `MIGRATION-K5` 作为阶段 ID，每个阶段 `IN_PROGRESS`/`DONE` 各一条，与 P 阶段并列、互不隶属。

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

### 2026-08-23 22:28 | S004 | DONE

- 目标：完成 S004 剩余交付——三档（旗舰/中端/入门）模拟设备画像 + 模拟 Benchmark 数据 Fixture，用于前期自动化测试；文档明确真实设备采集在候选 APK 产出后执行。
- 依赖：S002 DONE（构建基线）；S004 前半步（分档标准 + 回填流程）已于 2026-08-23 07:52 交付（当时因无真机记 BLOCKED，且经用户授权推进 S005+）；本轮为无真机可完成的 Simulation 子集收尾。
- 实际修改：
  - `qa/device-matrix/simulated-devices.yaml`: 新建，三档模拟画像（sim-flagship-sd8gen3 / sim-midrange-d8200 / sim-entry-d700），字段与 S023 DeviceProfiler 对齐（SoC/核数/RAM/memoryClass/API/ABI/页大小/存储/NEON/Vulkan/温控/电量），全 `verification: SIMULATED`，无个人标识。
  - `qa/fixtures/benchmark-results.json`: 新建，模拟 Benchmark（SmolLM-135M Q4_K_M 协议：64 入/128 出/temp 0.7/top_p 0.9/4 线程），三档各 3 样本 + P50/P95 聚合（TTFT/TPS/峰值 RSS/温度/电量）。
  - `docs/device-baseline.md`: 新增第 4.1 节"模拟 Fixture（SIMULATED）"与第 1 节状态修正，规则：模拟值不承诺性能、只增不删、真机回填后以 MEASURED 对照；真实设备采集在候选 APK 产出后（P6/S042 采集、S043/S044 验收）执行。
- 验证：
  - 命令：`python` YAML/JSON 解析 + 字段完整性断言
  - 结果：`PASS`；simulated-devices.yaml 三档 device_id/tier/ram_gb/android_api/abi/page_size_kb/storage_free_gb 全在，verification=SIMULATED；benchmark-results.json 三档各 3 样本 + aggregates（p50Tps 30.5/15.8/7.4）可解析。
  - 命令：`git status --short`（S004 前半步已在历史提交 87c5d31/...；本轮改动仅上述 3 文件 + 日志）
- 风险/阻塞：无（真机实测子集仍为 BLOCKED：无 arm64 真机；按 MIGRATION 已归入 P6/S042，解除条件见 device-baseline.md 第 6 节，不阻塞后续软件阶段）。
- 下一步依赖：`S005`（已完成，G1）与 `P4` 均不受本轮影响；本轮没有执行 P4。

### 2026-08-28 23:20 | P4 | IN_PROGRESS

- 目标：补齐 P4 验收闭环（`docs/kotlin-migration-plan.md` 前置门禁 G-P4）——在 Kotlin 语言迁移开始前建立绿色基线：全量单元测试 + `assembleDebug` + `connectedDebugAndroidTest` 仪器化测试 + 模拟器端到端主流程（发现 → 详情 → 下载 → 安装 → 聊天 → 停止 → 历史 → 删除）。
- 依赖：`P1`-`P3` 已 DONE；P4 代码（市场真实目录、设备画像、兼容性引擎、UI polish）已在提交 `a6c1ac5`/`6762f3e` 落地但未验收。
- 环境条件：模拟器 `emulator-5554`（AVD Medium_Phone，x86_64）在线；宿主 FixtureServer 供目录/下载联调。
- 允许修改：`app/src/androidTest/`、`docs/ai-work-log.md`、`qa/`。
- 计划验证：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - `./gradlew :app:connectedDebugAndroidTest --offline --console=plain`
  - 模拟器端到端主流程走查（uiautomator dump + 截图辅助）。
- 通过标准：仪器化测试全绿；端到端主流程可完成；P4 在本日志有完整 IN_PROGRESS/DONE 记录。

### 2026-08-28 23:31 | MIGRATION-K0 | IN_PROGRESS

- 目标：把语言变更写入基线文档，完成 Kotlin Stdlib 许可证登记，建立迁移日志约定。
- 依赖：无（前置门禁 G-P4 经用户显式授权豁免，见下）。
- 前置门禁豁免记录：`G-P4` 要求 P4 先完成 `connectedDebugAndroidTest` 与模拟器端到端验收再迁移。本轮用户显式要求「制定并完整执行 Java→Kotlin 重构直至全部完成」，构成对 G-P4 的授权豁免；迁移期间将**并行**在 K3/K5 补跑 `connectedDebugAndroidTest` 作为替代回归证据，P4 端到端验收仍作为独立 P 阶段待办保留。承担风险：若 P4 代码本身存在未验收缺陷，其回归可能与迁移噪声混淆，归因需依赖逐阶段提交隔离。
- 计划修改：
  - `docs/build-baseline.md`: 语言项改为「Kotlin 为主 + Java 互操作保留区」；补注原「选 AGP 8.13.2 而非 9.x 为避开内置 Kotlin」理由因迁移失效但本次不升级 AGP。
  - `docs/license-policy.md` / `NOTICE`: 登记 Kotlin Stdlib（Apache-2.0，JetBrains，2.2.20）与 Kotlin Gradle Plugin（构建工具链）。
  - `docs/ai-work-log.md`: 追加 `MIGRATION-K` 记录规则与 K0 记录。
  - `docs/kotlin-migration-plan.md`: 状态由「待批准执行」改为「执行中」。
- 计划验证：
  - `git status --short` 确认改动仅限上述文档 + `NOTICE`。

### 2026-08-28 23:35 | MIGRATION-K0 | DONE

- 目标：同上（基线文档 + 许可证登记 + 日志约定）。
- 依赖：无；G-P4 豁免已记录（见 IN_PROGRESS）。
- 实际修改：
  - `docs/build-baseline.md`: 版本矩阵「语言」行与「选择理由摘要 #1」改写；「后续注意」追加 Kotlin 工具链说明（KGP 2.2.20 离线命中 gradle813 变体、jvmTarget=17、保留区边界）。
  - `docs/license-policy.md`: 新增 §3.8 Kotlin Stdlib（2.2.20，Apache-2.0）登记；§4 构建工具链补 Kotlin Gradle Plugin 2.2.20。
  - `NOTICE`: 增补 Kotlin Standard Library（Apache-2.0，JetBrains）条目。
  - `docs/ai-work-log.md`: 记录规则新增 `MIGRATION-K0`～`K5` 约定；追加本 K0 记录。
  - `docs/kotlin-migration-plan.md`: 状态改为「执行中（K0 起）」。
- 验证：
  - 命令：`git status --short`
  - 结果：`PASS`；改动仅限 `docs/build-baseline.md`、`docs/license-policy.md`、`docs/ai-work-log.md`、`docs/kotlin-migration-plan.md`、`NOTICE`；文档间无自相矛盾表述，Kotlin Stdlib 已具备名称/版本/许可证/来源 URL。
- 风险/阻塞：G-P4 豁免风险已记录；K3/K5 以 `connectedDebugAndroidTest` 补偿回归证据。
- 下一阶段依赖：`MIGRATION-K1` 可以开始；本轮没有执行 K1。

### 2026-08-28 23:40 | MIGRATION-K1 | IN_PROGRESS

- 目标：接通 Kotlin 工具链，迁移无外部依赖的纯逻辑类（叶子层），验证互操作与测试链路。
- 依赖：`MIGRATION-K0` DONE。
- 计划修改：
  - 构建：`gradle/libs.versions.toml`（kotlin=2.2.20 + kotlin-android 插件）、根 `build.gradle`、`app/build.gradle`（插件 + `kotlinOptions.jvmTarget=17`）。
  - 源码（6 文件）：`model/ChatMessage`、`model/ModelInfo`、`common/Fmt`、`common/widget/EmptyStateView`、`core/device/DeviceProfiler`、`core/compatibility/CompatibilityEngine`。
  - 测试：`FmtTest`、`CompatibilityEngineTest` 迁移为 Kotlin。
- 计划验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`

### 2026-08-28 23:52 | MIGRATION-K1 | DONE

- 目标：同上。
- 依赖：K0 DONE。
- 实际修改：
  - `gradle/libs.versions.toml`：`[versions] kotlin=2.2.20`；`[plugins] kotlin-android`。
  - `build.gradle`（根）：`alias(libs.plugins.kotlin.android) apply false`。
  - `app/build.gradle`：应用 kotlin-android 插件；`kotlinOptions { jvmTarget = "17" }`（与 compileOptions Java 17 对齐）。
  - 新建 `app/src/main/kotlin/...`：ChatMessage.kt、ModelInfo.kt、Fmt.kt、EmptyStateView.kt、DeviceProfiler.kt、CompatibilityEngine.kt；删除对应 6 个 `.java`。
  - 新建 `app/src/test/kotlin/...`：FmtTest.kt、CompatibilityEngineTest.kt；删除对应 2 个 `.java`。
  - 迁移要点：静态工具类 Fmt/CompatibilityEngine/DeviceProfiler → `object` + `@JvmStatic`；Java 侧按字段访问的持有类 → 普通类 + `@JvmField`（保留字段访问与对象同一性语义，未用 data class）；`@JvmStatic fun langs(vararg)` 保持 `ModelInfo.langs(...)` 可用。
- 验证：
  - 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；101 个单元测试 0 失败 0 错误；`assembleDebug` 产出 APK。
- 风险/阻塞与修复：
  - 修复 1：`DeviceProfiler` 中 `Os.sysconf(_SC_PAGESIZE.toLong())` 类型错配——本 SDK `Os.sysconf` 形参为 `int`，去掉 `.toLong()`。
  - 修复 2：`CompatibilityEngine.LEVEL_*` 用 `@JvmField` 生成的静态字段非「编译期常量变量」，Java `switch(r.level)` 报「需要常量字符串表达式」——改为 `const val` 解决。经验：凡被 Java `switch case` 引用的 String 常量必须用 `const val`。
  - 备注：Kotlin 2.2.20 离线解析成功（命中 `kotlin-gradle-plugin-2.2.20-gradle813.jar`），与可行性探针一致。
- 下一阶段依赖：`MIGRATION-K2` 可以开始；本轮没有执行 K2。

### 2026-08-28 23:58 | MIGRATION-K2 | IN_PROGRESS

- 目标：迁移网络、存储与组合根；`data/room/` 保持 Java（D1）。
- 依赖：`MIGRATION-K1` DONE。
- 计划修改（12 主文件 + 6 单测 + 2 fixture）：`data/network/`(10)、`data/storage/ModelStorageManager`、`data/ServiceLocator`；测试 `Ed25519Test`、`ManifestVerifierTest`、`CatalogClientTest`、`ModelStorageManagerTest`、`ManifestParseDiagTest`、`RoomPersistenceTest`；fixture `FixtureKit`、`FixtureHttpServer`。
- 计划验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`

### 2026-08-29 00:20 | MIGRATION-K2 | DONE

- 目标：同上。
- 依赖：K1 DONE。
- 实际修改：
  - `data/network/`：Catalog/CatalogClient/CatalogConfig/CatalogException/Ed25519/Hex/ManifestVerifier/ModelManifest/TrustedKeys/TrustStore → Kotlin。
  - `data/storage/ModelStorageManager`、`data/ServiceLocator` → Kotlin。
  - 测试与 fixture：Ed25519Test、ManifestVerifierTest、CatalogClientTest、ModelStorageManagerTest、ManifestParseDiagTest、RoomPersistenceTest、FixtureKit、FixtureHttpServer → Kotlin。
  - 迁移要点：Gson 反序列化模型（Catalog/ModelManifest）用普通类 + `@JvmField var` + 默认值（Gson 按字段反射写入 + Java 侧字段访问，与原始字节码语义一致）；`Ed25519` 位运算逐字节核对，`Point` 保持普通类并在身份比较用 `===`（引用相等，避免 data class 结构相等改变语义）；`CatalogClient` 加 `@Throws(CatalogException)` 保留 Java 受检异常契约；`ModelStorageManager` 的 `persistManifest/readManifest/install` 加 `@Throws(IOException)`；`use {}` 替换 try-with-resources。
- 验证：
  - 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；101 个单元测试 0 失败 0 错误（Ed25519Test 5、ManifestVerifierTest 7、CatalogClientTest 8、ModelStorageManagerTest 7、RoomPersistenceTest 6 等全过）；`assembleDebug` 产出 APK。
- 风险/阻塞与修复：
  - 修复 1：`ByteArrayOutputStream.write(byte)` 单字节写入需 `.toByte().toInt()`（Byte 不自动宽化到 Int）；`write(byte[],int,int)` 三参形式在 android.jar 下按平台类型解析，Fixtures 改用等价的 `write(byte[])`/单字节。
  - 修复 2：Kotlin 方法默认不声明受检异常，Java 侧 `catch (CatalogException)` 报「异常永不被抛出」——对 `fetchCatalog/fetchManifest/fetchManifestBundle` 加 `@Throws(CatalogException::class)`。
  - 修复 3：Gson `fromJson` 返回平台类型，显式非空标注触发「条件恒为 false」——去除显式类型标注，保留 `== null` 防御。
  - 修复 4：可变 `var` 字段（`f.urls`、`rt.abis`）不可智能转换——先取局部 val 再判空。
- 下一阶段依赖：`MIGRATION-K3` 可以开始；本轮没有执行 K3。

### 2026-08-29 00:22 | MIGRATION-K3 | IN_PROGRESS

- 目标：迁移推理编排层；JNI 与 Parcelable 边界保持 Java（D5）。
- 依赖：`MIGRATION-K2` DONE。
- 计划修改（3 主 + 2 单测）：`core/inference/` 的 `InferenceClient`、`InferenceService`、`ApprovedModels` 及其对应单测（`InferenceRequestTest`、`NativeSessionContractTest`）。不得触碰 `NativeSession`/`InferenceRequest`/`InferenceStats`。
- 计划验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`；`connectedDebugAndroidTest`（模拟器）。

### 2026-08-29 00:27 | MIGRATION-K3 | DONE

- 目标：同上。
- 依赖：K2 DONE。
- 实际修改：
  - `InferenceClient` → Kotlin：常量用 `@JvmField`（`ERR_ENGINE_CRASHED`/`STATE_*` 供 RealChatEngine 以 `InferenceClient.STATE_READY` 字段访问）；`@Synchronized` 保留同步语义；`@Volatile` 保留 `state`/`events`；`IInferenceCallback.Stub` 匿名实现用 `object`。
  - `InferenceService` → Kotlin：继承 AIDL 生成的 `IInferenceService.Stub`，参数按平台类型处理（`request: InferenceRequest?` 保留 null 守卫、`prompt: String`/`cb: IInferenceCallback?`），未误加 `!!`；`NativeSession.NativeException` 按 `e.code` 取结构化错误码。
  - `ApprovedModels` → Kotlin：`@JvmStatic` 暴露 `byId/modelFile/isInstalled/requestFor/installedAsModelInfos`；`@JvmField` 暴露 `SMOLLM_135M` 与 `Approved` 字段。
  - 测试 `InferenceRequestTest`（Robolectric Parcel 往返 + byId）、`NativeSessionContractTest`（错误码契约 + 守卫）→ Kotlin。
  - 红线保持 Java：`NativeSession`（JNI 8 个 native）、`InferenceRequest`/`InferenceStats`（AIDL Parcelable）。
- 验证：
  - 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；101 个单元测试 0 失败 0 错误；AIDL 互操作（Kotlin 继承 Java 生成的 `IInferenceService.Stub`/`IInferenceCallback.Stub`）编译通过；`assembleDebug` 产出 APK。
  - `connectedDebugAndroidTest` 的 6 项仪器化测试留待 `MIGRATION-K5` 迁移 `InferenceServiceInstrumentedTest` 后统一重跑确认（记录为待办，不阻塞 K4）。
- 风险/阻塞：无（AIDL 可空性按平台类型处理正确；`@JvmField` 常量对 Java 字段访问有效）。
- 下一阶段依赖：`MIGRATION-K4` 可以开始；本轮没有执行 K4。

### 2026-08-29 00:30 | MIGRATION-K4 | IN_PROGRESS

- 目标：迁移全部页面与宿主 Activity（最大工作量）。
- 依赖：`MIGRATION-K3` DONE。
- 计划修改（24 主文件 + 7 单测）：`feature/download/`(6)、`feature/chat/`(10)、`feature/market/`(4)、`feature/diagnostics/`(1)、`feature/settings/`(1)、`MainActivity`、`App`；测试 `ChatHistoryTrimmerTest`、`ChatRepositoryTest`、`RealChatEngineTest`、`ChatEngineProviderTest`、`DownloadRepositoryTest`、`ModelVerifierTest`、`DownloadPipelineTest`。
- 计划验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`

### 2026-08-29 00:55 | MIGRATION-K4 | DONE

- 目标：同上。
- 依赖：K3 DONE。
- 实际修改：
  - `feature/download/`：ModelVerifier（GGUF 探针逐字节核对，`when` 替换 switch）、DownloadCoordinator（`@Throws(IOException)` 保留受检异常、`when(e.code())`）、DownloadRepository（CatalogItem/CatalogView/TaskView 用 `@JvmField`）、CatalogAdapter/DownloadAdapter/DownloadsFragment。
  - `feature/chat/`：ChatEngine（接口 + 嵌套 StreamListener）、ChatEngineProvider、RealChatEngine（`ChatEngine.StreamListener` 显式限定）、ChatHistoryTrimmer（`===` 引用相等保留最新消息语义）、ChatRepository（`fun interface` Listener/MessagesCallback）、ChatFragment/HistoryFragment/MessageAdapter/ModelPickerSheet/PickerAdapter。
  - `feature/market/`：MarketModels（`joinToString` 替换 `String.join`）、ModelAdapter（`compatLabel` 等 `@JvmStatic`）、MarketFragment/ModelDetailFragment。
  - `feature/diagnostics/`、`feature/settings/`、`MainActivity`（`when` 替换 String switch）、`App`（进程门控 `:inference` 跳过 ServiceLocator 初始化原样保留）。
  - 测试 7 文件迁移；`ChatEngineProviderTest` 一并迁移（K3 遗留）。
- 验证：
  - 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；101 个单元测试 0 失败 0 错误（含 DownloadPipelineTest 11 项、RoomPersistenceTest 6 项、ChatRepositoryTest 3 项等）；`assembleDebug` 产出 APK。
- 风险/阻塞与修复：
  - 修复 1：Kotlin 嵌套接口不随接口实现自动进入作用域（与 Java 不同）——`RealChatEngine` 中 `StreamListener` 需写 `ChatEngine.StreamListener`。
  - 修复 2：单方法回调接口需声明为 `fun interface` 才支持 lambda SAM（`OnMessageLongClick`/`Callback`/`OnPick`）。
  - 修复 3：`String.join` 在 Kotlin `String` 上不可解析——改用 `Iterable.joinToString`。
  - 修复 4：`Fragment.getView()` 与 `onViewCreated` 参数同名遮蔽——回调内 `getView()` 判空需用 `this@XxxFragment.view`（保留视图销毁后的空安全）。
  - 备注：`CatalogItem.modelId` 等 manifest 未兜底字段按 Java 保持可空，调用侧以 `!!` 断言（schema 已保证非空）。
- 下一阶段依赖：`MIGRATION-K5` 可以开始；本轮没有执行 K5。

### 2026-08-29 00:58 | MIGRATION-K5 | IN_PROGRESS

- 目标：迁移遗留文件，同步文档，确认无 Java 残留（红线区除外）。
- 依赖：`MIGRATION-K4` DONE。
- 计划修改（3 主 + 2 单测 + 1 仪器化测试）：`mock/`（Filters、MockChatEngine、ReplyComposer——仍被引用不可删除）、`FiltersTest`、`ReplyComposerTest`、`InferenceServiceInstrumentedTest`。
- 计划验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`；`connectedDebugAndroidTest`；存量 Java 清点。

### 2026-08-29 01:00 | MIGRATION-K5 | DONE

- 目标：同上。
- 依赖：K4 DONE。
- 实际修改：
  - `mock/Filters`（`const val` 常量 + `@JvmStatic apply/matchesSize`）、`mock/ReplyComposer`（`object` + `@JvmStatic`）、`mock/MockChatEngine`（实现 `ChatEngine`，`@Synchronized` 保留语义）。
  - `FiltersTest`、`ReplyComposerTest`、`InferenceServiceInstrumentedTest`（androidTest，Robolectric→AndroidJUnit4）→ Kotlin。
  - 删除全部 K1-K5 的 `.java` 源文件。
- 验证：
  - 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 结果：`PASS`；101 个单元测试 0 失败 0 错误。
  - 命令：`./gradlew :app:compileDebugAndroidTestKotlin --offline --console=plain`
  - 结果：`PASS`；仪器化测试 Kotlin 编译通过。
  - 存量 Java 清点：`app/src/main/java` 仅剩 13 个红线区文件（`data/room/` 10 + `core/inference/NativeSession/InferenceRequest/InferenceStats` 3）；`app/src/test`、`app/src/androidTest` 无 `.java` 残留。
- 风险/阻塞：`connectedDebugAndroidTest` 需模拟器 + 已安装批准模型（真实模型 4 项依赖批准模型文件，缺失时以 Assume 跳过；2 项错误路径测试不依赖模型）。见下条记录。
- 下一阶段依赖：Kotlin 迁移全部完成；P5/P6 在 Kotlin 代码库上继续。

### 2026-09-06 15:45 | 持续发布质量优化 | IN_PROGRESS

- 用户当前目标：持续优化至可交付；优先提供 APK 安装，无自有服务器或域名，需要独立可用。此持续目标取代旧计划的一阶段一对话停止约定，整体尚未验收完成。
- 已修复：`ChatRepository` 使用同一个 Room 数据库事务保存标题与消息，正确回填新会话主键、复制流式消息快照、删除最后一条消息时清理会话、拒绝复活已删除会话；`ServiceLocator` 同步注入数据库；`HistoryFragment` 移除刷新回调再次查询造成的无限循环并清理视图引用；详情页改为视图缺失时安全返回。
- 回归证据：先在原实现执行 `:app:testDebugUnitTest --tests '*ChatRepositoryTest'`，7 项中 5 项失败；修复后 7 项通过。新增 SQLite trigger 故障注入覆盖替换消息中途失败、新会话首条写入失败，验证事务回滚。
- 综合验证：`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:testReleaseUnitTest --offline --console=plain` 成功；Debug/Release 各 105 项测试，0 失败、0 错误、0 跳过；Lint 0 错误、136 警告。Lint 首次缺少本地依赖，已通过现有阿里云仓库在线补齐。
- 构建结果：`app/build/outputs/apk/release/app-release-unsigned.apk`，11,363,405 字节。`apksigner verify` 确认未签名（缺失 META-INF/MANIFEST.MF），不是可分发候选包，未提交或发布。
- 后续必须处理：目录固定为模拟器宿主 `10.0.2.2:8090`；可信密钥仍是开发密钥；市场多数模型为演示载荷，`ChatEngineProvider` 对未安装/未批准模型回退模拟回复。需要落实无自建服务的真实模型获取、校验、许可证展示和推理流程。
- 已发现待修链路：`ChatFragment.retryFrom` 删除了待重试的用户消息；新建/加载会话和切换模型未隔离旧生成回调，视图重建丢失当前消息；`RealChatEngine.stop` 未清除加载期间的 pendingPrompt；`InferenceClient.release` 同步 Binder 调用可能阻塞主线程且未清理旧回调。下载控制/工作线程间存在状态覆盖与取消竞态，异常 416/Content-Range 响应没有重试上限；设置页部分开关未作用于运行行为，清缓存按钮仅弹提示。
- 尚未完成：上述功能修复、无障碍警告筛查、独立安装签名、运行时端到端、Native 停止/崩溃恢复/低内存和设备验证。不能以本轮测试通过宣称整个软件无 bug 或达到上架标准。

### 2026-09-06 16:00 | 推理生命周期与聊天交互修复 | IN_PROGRESS

- 本轮为实际进展，整体发布目标仍未完成。用户选择仍为无自建服务器、优先独立可用 APK。
- `InferenceClient`：绑定和生成轮次分别隔离，释放后忽略旧回调，死亡/断连只报一次，加载失败解绑可重试；Native 释放移到共享串行 Binder 线程，与下一次加载有序执行，避免 UI 阻塞和反复创建线程。每次解绑清理死亡监听。
- `RealChatEngine`：加载前/加载中停止立即结束本轮，清理 pendingPrompt，READY 不再复活已停止任务；复用连接时更新事件接收者，每轮回调只交给当前 listener。
- `ChatFragment`：重试保留用户问题；新建/切换模型/打开历史时隔离旧生成；异步保存按会话隔离并合并连续写入，旧 id 不覆盖新会话；历史页返回保留同一 Fragment 的消息和草稿；删除操作重新校验消息位置，生成中禁止修改列表；空输出清除思考占位；空输入禁用发送并提供“停止生成”无障碍标签。退场动画期间先在 onPause 中停止生成。
- JVM 回归：`InferenceClientLifecycleTest` 新增 6 项先全部失败，修复后全过；`RealChatEngineLifecycleTest` 新增 4 项，先复现 2 项加载停止失败，修复后全过。Debug/Release 单测各 115 项，0 失败、0 错误、0 跳过；最终 onPause 调整后又执行完整 Debug 单测、Lint 和 Release 构建通过。
- 实际 Android 验证：在现有模拟器安装本次 Debug APK；初次 `connectedDebugAndroidTest` 由于无模型跳过 4 项，因此未作为 Native 完成证据。随后部署现有批准 GGUF（105,453,984 字节），两端 SHA-256 与批准记录一致，用 `adb shell am instrument` 执行 `InferenceServiceInstrumentedTest`，6 项全部通过、无跳过，耗时 31.737 秒，覆盖真实生成、停止、重复释放、独立进程死亡与重启。测试自身修复了读取 PID 的绑定泄漏及先杀进程后注册监听的竞态。
- Android 页面回归：`ChatFragmentInstrumentedTest` 5 项，先发现 1 项退场动画期间旧消息仍写入；修复 onPause 时机后 5 项全过（7.132 秒）。其中重试用例直接调用菜单最终处理入口，其余覆盖实际页面按钮、返回栈、消息和草稿；不是完整生产用户端到端验收。
- 验证命令：`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:testReleaseUnitTest :app:assembleRelease --offline --console=plain`；`gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`；上述两个仪器化测试类通过 `am instrument -w -r -e class ...` 执行。Release 仍未签名，尚不可分发。
- 下一步重点：实现不依赖 `10.0.2.2` 的正式真实模型目录/获取，去除产品中的模拟回复和演示载荷，签名与安装验证。还需解决 Activity 配置重建/进程死亡的会话恢复（本轮仅证明同一 Fragment 返回栈保留）、下载状态竞态/无限重试、设置空实现、运行降级和真实 ARM64 设备验收。

### 2026-09-06 | 独立模型目录、真实中文推理与签名候选包 | IN_PROGRESS

- 依据用户已明确选择：无服务器/域名，优先独立可用 APK。本轮实际修改与运行验证构成进展，整体目标继续。
- 产品目录改为读取 `app/src/main/assets/catalog/v1/` 内置资产，继续验证 Ed25519 签名；不再请求开发电脑。新增独立目录公钥，移除开发 Fixture 公钥；`.gitattributes` 禁止签名资产自动转换换行，避免 Windows 检出后签名失效。元数据限制 1 MB，验证目录模型 id/版本/唯一性、Manifest 对应关系和安全文件名。
- 接入 Qwen 官方 `Qwen/Qwen2.5-0.5B-Instruct-GGUF`，固定 revision `9217f5db79a29953eb74d5343926648285ec7e67`；Q4_K_M 文件 491,400,032 字节，SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`。官方 Hugging Face API 与实际下载哈希一致；模型页标注中文支持、Apache-2.0。完整模型许可证随 APK 附带并在设置页可读。手机运行参数限定 2K 上下文以控制内存，不把上游最大上下文当作本机已测能力。
- 下载优先国内 HTTPS 镜像，保留官方 HTTPS 地址；点击失败任务“重试”时切换到下一个签名地址并重新获取、验证和保存 Manifest；修复哈希失败删除 sidecar 后无法重试的问题。仓库的暂停/恢复/取消直接进入控制线程，不再排到长下载后面。其余下载状态竞态与异常 Range 上限仍待后续修复。
- `MockChatEngine`、`ReplyComposer` 移到 test 源集，APK 不再包含模拟回复；未安装/失效模型返回不可用状态；选择器仅展示引擎支持且完成安装的模型，检查完整长度与 install.ok，移除安装模型规格写死为 0.1B 的映射。Qwen 已接入真实 ChatML 推理，SmolLM 保留作已有 Native 验收兼容。
- 真实网络验收：`StandaloneModelInstrumentedTest` 显式参数 `allowModelDownload=true`，通过产品 `DownloadRepository.enqueue` 下载、校验、安装 Qwen，再调用真实 Native 推理并断言输出包含中文。首轮未预置 Qwen 权重，1 项通过，52.144 秒；此证据覆盖真实模型安装路径，不是 adb 拷贝模型。
- 离线验收：记录并临时关闭模拟器飞机模式相关网络/Wi-Fi/移动数据；测试确认 `ConnectivityManager.activeNetwork == null`，重新加载内置目录并真实生成中文，随后执行 5 项聊天页面回归；6 项全部通过、无跳过，28.498 秒。网络设置在 finally 中恢复，确认飞机模式恢复 disabled。
- 自动化：新增 3 项内置目录签名/运行配置/篡改与路径检查，新增 1 项损坏下载修复重试；Debug/Release 各 119 项，0 失败、0 错误、0 跳过。Lint 0 错误、135 警告；Release 构建通过。
- APK 专用签名身份已创建（RSA 3072，PKCS12），Gradle 从 `.local-signing/release.properties` 读取；目录及其口令文件被 Git 忽略，未输出私钥或口令。后续必须保留 `.local-signing/` 以保持升级签名一致，不得重建覆盖。模型目录签名 seed 在既有 ignored keys 路径，不进入 APK。
- 候选产物：`app/build/outputs/apk/release/app-release.apk`，11,378,116 字节，SHA-256 `e5095b1a7bd35618b2e092c99e108b5fe277fc4669e3bf93017eba8006ab0abe`。`apksigner verify --verbose` 通过（v2、1 signer）；`zipalign -c -P 16 4` 通过；解析 APK 中全部 5 个 ARM64 ELF 的 LOAD 段，均至少 16 KB 对齐；检查 APK 不含 MockChatEngine、ReplyComposer、seed/p12/口令文件，包含已签名目录。
- 尚未完成：专用签名 Release APK 独立安装/升级回归（当前模拟器装的是 Debug 签名，未为了换签名卸载其数据）；Activity/进程重建恢复、下载竞态/重试上限、设置空实现、隐私/第三方完整声明、低内存/热降级、实际 ARM64 设备验收。当前模拟器提供 `libndk_translation.so`，可另建隔离环境检验 ARM64 候选，但不能冒充 ARM 真机性能。签名包是待验收候选，不是正式发布通过证明。

### 2026-09-06 | 下载异常恢复与当前 Android Studio 虚拟机验收 | IN_PROGRESS

- 用户新增约束：软件必须在当前已打开的 Android Studio 虚拟机测试。已确认并始终使用 `emulator-5554`（sdk_gphone16k_x86_64），覆盖安装 Debug APK，保留已有应用数据、模型和签名；未另建虚拟机或卸载应用。
- 本轮先复现 7 项失败：零偏移 416、空响应、超长未知长度响应会重复请求；206 忽略声明总长度；快速暂停/恢复时旧请求 IOException 污染新状态；INSTALLING 带 part 恢复失败；文件已发布但数据库未提交时恢复失败。
- 修复：状态提交互斥，恢复/重试排在旧请求退出后；取消后的目录清理等待旧工作退出，校验后再次确认任务有效再安装；进度/ETag 不再覆盖暂停状态。脏 Range 仅允许一次重置，严格验证范围起止与总长度，未知长度流限制写入上限并拒绝提前结束。
- 安装恢复：重新验证本地 Manifest 签名与任务内容，处理 INSTALLING 幂等恢复，以及文件已提交后补齐数据库。staging 在提交前保留源文件，优先硬链接、不支持时复制，安装失败保留完整源供重试；新增存储提交失败保留源的回归。
- 自动化：Debug/Release 各 127 项单元测试，0 失败、0 错误、0 跳过；Lint 0 错误、135 警告；Debug、androidTest、专用签名 Release 构建成功，Release 的 apksigner v2 验证通过。`git -c core.safecrlf=false diff --check` 通过。
- 当前虚拟机：新增 DownloadDeviceInstrumentedTest 使用隔离的测试目录与内存数据库，真实 HTTPS 下载暂停、立即恢复、取消通过；用已安装 Qwen 权重验证 Android 文件提交前后恢复。首轮测试准备步骤的硬链接被 Android 拒绝（不是产品安装步骤失败），测试增加复制回退后该项补跑通过，6.71 秒；测试目录已清理，现有模型未被更改。
- 当前虚拟机：RealChatUiInstrumentedTest 从实际页面发送按钮进入真实模型推理，断言中文回答且没有生成错误；5 项既有聊天页面交错回归通过。以上首次合计 8 项中 7 通过、1 测试准备失败，失败项已补跑通过，不隐瞒首轮失败。
- 断网补充验收：同一虚拟机临时断开网络，StandaloneModelInstrumentedTest 明确断言 activeNetwork 为空、校验完整 Qwen 哈希并真实中文推理；RealChatUiInstrumentedTest 同时验证离线页面回答，2 项均通过，无跳过，18.543 秒。网络设置在 finally 中恢复。
- 后续继续：Activity/进程重建的聊天恢复、设置空实现、低内存等日常使用异常与完整第三方声明；市场/下载入队的安装判断仍仅检查文件存在，需与聊天端的长度和 install.ok 条件一致。专用签名 Release 尚未直接覆盖当前 Debug 签名应用，不能称正式发布验收完成；持续目标保持进行中。
### 2026-09-06 | 聊天重建与进程恢复 | IN_PROGRESS

- 当前 Android Studio 虚拟机 `emulator-5554` 先复现 `recreationKeepsPartialAnswerDraftAndModel` 失败：Activity 重建丢失消息/草稿；修复后该项和其余页面回归、真实中文页面推理共 7 项通过，无跳过，28.093 秒。
- 数据库增加单行 `chat_session` 保存编辑 token、会话主键、模型和草稿，正文继续保存在既有 messages 表。相同 token 的连续快照在事务内复用主键，旧页面未收到回调时不会重复创建历史；草稿和正文一起提交，生成期间定期保存，离开页面保存。正文不放进 Android 状态 Bundle，避免长会话超过系统传输限制。
- 页面初次创建异步恢复当前编辑状态；读取失败保持只读并提供重试，避免空状态覆盖旧会话。历史加载直接读取会话元数据，不再依赖尚未刷新好的缓存获取模型。删除历史/清空历史同时清除对应编辑状态。
- Room 1→2 采用显式增量迁移，不清空旧表。新增旧版导出 schema 迁移回归，验证历史标题/正文、模型和下载记录保留；测试准备对缺省 indices 的解析曾失败，修正后通过。当前模拟器覆盖升级后原 Qwen 模型仍可真实推理。
- 新增 4 项仓库回归覆盖连续快照主键复用、纯草稿恢复、草稿和正文事务回滚、删除历史同步清理编辑状态；加上迁移用例，Debug/Release 各 132 项单元测试，0 失败/错误/跳过。Debug、androidTest、签名 Release 构建通过，Lint 通过。
- 实际进程恢复：同一虚拟机分两阶段运行 `processRestartRestoresSavedChat`，prepare 进程 PID 25690 保存测试消息和草稿，结束应用后 verify 进程 PID 25784 重新打开页面，断言两条正文、草稿和真实推理引擎恢复；两个阶段分别通过，1.529/1.403 秒。此测试显式指定 processRestorePhase，普通整类执行会跳过该外部驱动场景。
- 整体仍在进行：下一步处理设置页面的空实现、安装状态判断一致性，以及删除当前历史后的页面状态同步。Release 候选尚未完成正式发布验收，继续使用用户当前虚拟机测试。
### 2026-09-06 | 设置真实清理、删除同步与安装状态 | IN_PROGRESS

- 设置的“清理模型缓存”原来把已安装模型体积当作缓存，仅提示“模拟清理”。现改为“清理临时缓存”，后台统计和清理 cacheDir，保留模型、下载断点和聊天数据库；不跟随符号链接，按实际成功删除字节反馈，失败文件明确提示。清空历史仅在数据库操作完成后显示成功，删除失败可见。
- 聊天仓库增加删除事件，返回栈中的聊天页同步清除已删除正文/草稿并使旧生成回调失效。工作线程记录删除的编辑 token，拒绝没有回填主键的迟到快照重新创建已删除历史；新增该竞态和删除事务失败回归。
- 安装状态判断统一检查 install.ok、实际文件和完整长度；目录按模型及版本匹配，损坏记录不再阻止重新下载。新增“不完整已安装记录可重装”管线回归。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装后，设置页真实按钮清理临时文件并保留 Qwen、删除测试会话后返回聊天页无残留、真实中文聊天共 3 项通过，19.423 秒，无跳过。删除用例仅删除其新建测试会话，未清空现有用户历史。
- Debug/Release 各 136 项单元测试通过，0 失败/错误/跳过；Lint 0 错误、135 警告；签名 Release 构建和 diff --check 通过。
- 后续仍需落实运行模式、常亮和后台生成设置（目前只有偏好存储）、匿名上报开关与独立使用方案的一致性，以及完整声明和发布候选验收。持续目标未完成。
### 2026-09-06 | 运行设置生效与 GitHub 提交约定 | IN_PROGRESS

- 用户新要求：每完成一个优化步骤，验证后提交并推送 GitHub，使用简短中文说明。当前分支 main，远端 origin 为 diaofenyuan/duan-zhi-sui-xing；此前连续优化尚未提交，本次一并形成可回溯基线。签名私钥/口令和目录 seed 保持 Git 忽略。
- 运行策略接入真实推理：每轮读取模式、可用 CPU、系统省电、温度和内存压力；均衡通常最多 4 线程/2K 上下文，省电最多 2 线程/1K 上下文/128 输出 token；高温或低内存时限制负载。历史裁剪采用本轮实际上下文，参数变化通过重新绑定加载生效。新增模式切换用例曾复现 connect 忽略已有连接，改为 restart 后通过。
- 常亮设置仅在聊天生成且页面可见时生效，结束/离开时解除；show/hide 导航离开聊天也停止并保存。修复点击单选圆点不写入模式的问题。移除未实现的后台生成和匿名上报开关，页面展示实际前台保存和本地隐私行为；后台持续推理未实现，也未宣称支持。
- 当前 Android Studio 虚拟机 emulator-5554：设置圆点改变实际参数、生成时常亮及离开后解除、真实中文页面推理共 3 项通过，23.793 秒，无跳过。测试后恢复原设置值。
- Debug/Release 各 139 项单元测试通过，0 失败/错误/跳过；Lint 0 错误、133 警告；Debug、androidTest、签名 Release 构建通过。
- 用户更新整体目标：模型部署功能可参考 LM Studio。已查官方文档 https://lmstudio.ai/docs/app/advanced/import-model 与 https://lmstudio.ai/docs/cli/local-models/load，后续对照模型导入、加载参数、内存估算和释放体验，结合 Android 设备能力落实；这不是宣称已支持任意 GGUF 或桌面 GPU 功能。整体发布验收继续。
### 2026-09-06 | 模型加载状态与手动释放 | IN_PROGRESS

- 聊天标题原先将下载完成显示为就绪、加载阶段显示为生成中。现按实际推理连接区分已下载、加载中、已加载、生成中、释放中和进程退出；空闲时进程退出也刷新界面。状态文字支持无障碍播报。
- 新增“释放模型”按钮，仅在模型空闲且已加载时可用。后台同步释放 Native 会话并解绑后才确认释放完成，保留模型文件、正文和草稿；下一次发送自动加载。旧释放确认不能覆盖新的加载状态，页面销毁解除状态监听。
- 参考 LM Studio 官方加载/卸载语义：https://lmstudio.ai/docs/cli/local-models/load 。当前展示运行内存是否被模型占用，不宣称已测量具体占用字节，也不等同于删除模型。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装，真实中文发送→释放→保留模型及会话→重新加载并完成第二轮回答的页面测试通过，73.936 秒，无跳过。恢复页面后检查状态行和两轮正文正常显示；流式生成期间 UI 自动化无法取得静止快照，结束后成功检查。
- 增加慢速释放确认、旧释放确认与新加载隔离、空闲进程退出状态回归。Debug/Release 各 141 项单元测试，0 失败/错误/跳过；Lint 0 错误、126 警告；Debug、androidTest、签名 Release 构建通过。按用户约定以简短中文说明提交并推送 GitHub，整体发布验收仍在继续。
### 2026-09-06 | 长对话预算与超长输入恢复 | IN_PROGRESS

- 查明原有两层静默截断：聊天按字符估算并截短最新问题，Native 再从 token 前部截断且只预留一个位置，可能破坏模板或在回复中途耗尽上下文。现通过已加载模型的实际分词器计算完整 ChatML 预算，预留本轮最大回复和余量，按完整轮次保留连续的最近历史；裁剪提示可见，数据库正文不删除。
- 最新问题超过预算时明确返回 1104，不修改原文；页面保留用户消息，并在没有新草稿时恢复输入框供修改。单次分词请求限制在 Binder 安全大小内，超长旧轮次整轮排除。Native 同时校验实际上下文和回复空间，取消原来的静默截断。
- 分词和历史选择在后台 Binder 队列执行，停止或切换后丢弃准备结果并尽快让出释放队列。输入通过标准 UTF-8 字节进入 JNI，避免 Modified UTF-8 改变表情字符。新增停止分词后不再启动生成的时序回归。
- 当前 Android Studio 虚拟机 emulator-5554：真实 Qwen 超长拒绝、51 条历史按真实词表保留完整轮次、实际提示 token 计数一致、预算内再次生成，以及页面恢复超长原文并修改重发，两项测试通过，153.375 秒，无跳过。首次长上下文测试在 90 秒超时，保持输入/断言不变延长到 3 分钟后通过；实测 456 个提示 token 的首片段耗时 142324 ms，短问题 16 个 token 为 4834 ms。功能通过不代表性能已经达到发布要求。
- Debug/Release 各 143 项单元测试通过，0 失败/错误/跳过；Lint 0 错误、126 警告；Debug、androidTest、签名 Release 构建通过。新增模拟器测试依赖当前已安装 Qwen，未跳过或创建新模拟器。
- 已验证模拟器使用 x86_64 原生库；当前 Debug ggml-cpu 编译命令没有优化选项，后续针对上述延迟优化并实测。另需继续审查原生输出的分段 UTF-8 处理及快速停止边界。按用户要求以中文提交并推送，发布目标仍未完成。
### 2026-09-06 | 调试版推理内核性能 | IN_PROGRESS

- 对照实际编译命令，Debug 的 llama/ggml 内核原本没有优化选项；当前 Android Studio 模拟器使用 x86_64 原生库。仅对这些推理目标的 Debug 配置增加 -O2，保留 -g 和断言，JNI 边界仍使用默认调试配置。Release 仍使用原有 RelWithDebInfo 的 -O2/-g/-DNDEBUG，未修改模型、线程数或上下文预算。
- 同一 emulator-5554、同一 Qwen、512 上下文/2 线程、同一 456-token 提示：优化前首片段 142324 ms，优化后 29007 ms，单次对照缩短约 79.6%。两次均生成 2 个 token，提示解码 logits 范围一致且无 NaN。该结果是此模拟器调试版的测量，不代表所有真机或 Release 的提速幅度；长输入仍有进一步优化空间。
- 既有原生服务测试改为优先使用当前已安装的 Qwen，兼容旧 SmolLM，避免实际安装模型与测试固定模型不一致导致跳过。未修改 vendor 源码。
- 当前虚拟机覆盖安装后：上下文实际分词与超长拒绝、非法路径、无效模型、独立进程、进程退出后重启、生成中停止和重复释放、连续生成、真实中文页面释放重载、超长输入恢复，共 9 项通过，71.755 秒，无跳过。未创建新模拟器，保留原模型和用户数据。
- Debug、androidTest、签名 Release 构建通过；编译命令检查确认优化范围正确，diff --check 通过。此步不涉及 JVM 业务逻辑，未重复运行上一轮已通过的 143 项单元测试。按用户约定中文提交并推送 GitHub，整体发布目标继续。

### 2026-09-06 | 流式输出字符分片 | IN_PROGRESS

- 原有 Native 输出将每批标准 UTF-8 字节直接交给 NewStringUTF，未保留跨批次的字符尾部，并受 NUL 字符串终止语义影响。Android 官方 JNI 指南要求该接口输入 Modified UTF-8：https://developer.android.com/ndk/guides/jni-tips?hl=en 。现通过 byte[] 传输标准 UTF-8，由每轮独立的 Java 解码桥接缓存不完整字符，完整字符才进入业务回调。
- 停止、输出上限或错误结束时丢弃尚未组成完整字符的尾部字节，完整正文保留；忽略该轮结束后的迟到片段和重复终止通知。非法完整字节序列使用替换字符并继续处理后续有效文本。JNI 桥接类添加 Keep，防止未来启用压缩时回调方法被改名。
- 新增 5 项确定性单元回归，覆盖中文、表情、补充平面汉字及 NUL 的每个字节分割位置、逐字节到达、停止后的迟到字节、非法字节和两轮隔离。新增真实 Qwen 中文/表情连续两轮测试；旧实现该随机分片实测也通过，未声称已复现崩溃，边界依据代码和协议检查，确定性用例覆盖随机输出不能保证触发的情况。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装，中文/表情、上下文边界、错误回调、停止释放、进程异常恢复、连续生成及真实聊天页面，共 10 项通过，68.368 秒，无跳过。模型和用户数据保留。
- Debug/Release 各 148 项单元测试，0 失败/错误/跳过；Lint 0 错误、126 警告；Debug、androidTest、签名 Release 构建通过。整体发布验收继续。

### 2026-09-06 | 快速停止与终止状态 | IN_PROGRESS

- 当前 Android Studio 虚拟机 emulator-5554 先复现两项失败：刚启动即停止却返回 FINISH_END（取消标记被工作线程重新清零）；预填充停止回调内观察到 IDLE，未达到下一轮所需 READY。原用例分别报“刚启动的停止请求被忽略”和 READY/IDLE 不符，4.115 秒完成，未修改输入或断言来规避。
- 取消标记只在启动线程前清零，工作线程先检查已到达的停止请求。停止仅设置取消请求，保持 RUNNING 直到实际清理完成；预填充取消后直接进入结束流程，避免读取尚未完成的 logits。
- 正常结束与错误结束统一先写入统计、清理共享回调并发布 READY，再向 Java 通知终止；旧回调用本地引用保持有效，新一轮等待旧线程退出后再安装监听，避免旧清理覆盖新轮次。每轮统计在启动前复位，快速停止不沿用上一轮 token 计数。
- 两项新增真实 Native 回归覆盖连续 5 次立即停止、预填充阶段停止、终止回调中的 READY 状态及后续真实中文生成。修复后与中文表情、上下文错误回调、进程异常恢复、连续生成和聊天页面一并验证，共 12 项通过，66.593 秒，无跳过。
- Debug、androidTest、签名 Release 构建通过，diff --check 通过。本次业务变化集中在 Native 生命周期，Java 仅同步注释，未重复运行上一轮已通过的 148 项 JVM 用例。保留当前模拟器的模型和用户数据，按约定中文提交并推送，整体发布验收继续。
### 2026-09-06 | 诊断数据真实展示与自动刷新 | IN_PROGRESS

- 移除诊断页的演示设备、NEON 和未执行的性能短测描述；展示真实逻辑核心、可用存储、电源状态和温控等级。存储只采用应用可用空间，避免将文件系统保留空间算入下载容量。
- 页面可见时每 5 秒刷新，切回页面、目录更新和安装清单变化时及时更新；隐藏或销毁时停止刷新并解除监听。模型运行内存明确为估算，采用当前运行模式上下文，目录加载中、失败和空清单分别说明。
- 已安装模型不再重复要求下载空间；系统版本和架构限制继续校验。市场和诊断使用一致的安装状态判断。新增低存储情况下已安装模型的兼容性回归。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装后，诊断页面返回/周期刷新、真实电源信息与模型清单变化共 2 项通过，0 失败/跳过；清单用例仅临时替换内存快照并恢复，不删除数据库或模型。实际截图确认页面数据和估算行正常展示。
- Debug/Release 各 149 项单元测试通过，0 失败/错误/跳过；Lint 0 错误、123 警告；Debug、androidTest、签名 Release 构建通过。按约定使用简要中文提交并推送 GitHub，整体发布验收仍在进行。

### 2026-09-06 | 隐私数据说明与备份排除 | IN_PROGRESS

- 复查上一轮诊断页截图的导航高亮：等待切换动画后选中背景、文字与当前页面一致，无需修改导航逻辑。
- 设置新增可离线阅读的“隐私与数据说明”，解释本机保存内容、模型下载域名和网络请求信息、系统键盘/剪贴板边界、清理/删除/释放的区别，以及换机和卸载的影响。内容随 APK 发布，使用可滚动的 Fragment 页面，支持重建后恢复阅读位置及返回设置。
- 原 Manifest 只有 allowBackup=false。依据 Android 官方说明，部分设备仍可能允许设备间迁移；补充 fullBackupContent=false 和 dataExtractionRules，对云备份、设备迁移各排除全部 9 个数据域。参考：https://developer.android.com/identity/data/autobackup?hl=en 。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装后，完整说明/滚动位置重建/返回与 Tab 切换、安装 APK Manifest 与排除规则检查共 2 项通过，2.592 秒，0 失败/跳过；实际点击设置入口并检查页面截图。仅检查系统配置，未执行云备份或跨设备迁移，不将此结果扩大为各厂商迁移实测。
- Debug、androidTest、签名 Release 构建和 Lint 通过，Lint 0 错误、124 警告，diff --check 通过。业务推理逻辑未变，本轮未重复运行此前各 149 项 JVM 用例。无模型或会话删除；按约定中文提交并推送。
- 后续明确缺项：开源许可证仍为摘要，缺少 APK 内可阅读的完整依赖归属和部分许可文本；当前说明是实际数据行为说明，不能据此宣称完整发布审查通过。整体目标继续。

### 2026-09-06 | APK 内完整许可阅读与依赖清单检查 | IN_PROGRESS

- 原设置入口仅显示手写摘要，并要求用户查看 APK 外的 docs/NOTICE。现提供 17 个离线阅读条目，展示组件版本、归属、来源和完整许可文本；长文可选择复制，正文及滚动位置随重建恢复，缺失条目显示重试而非崩溃。
- 核对当前 Gradle releaseRuntimeClasspath 的 66 个唯一运行时组件及缓存 POM/JAR/AAR 声明，补齐 Okio、协程、注解等间接依赖。移除将未采用 WorkManager 当作实际依赖的表述；测试库不列入 APK 运行时清单。签名 Release 包内已确认全部 17 个条目、66 个组件、7 个被引用文本齐全。
- Native 许可从当前编译源码核对，保留 ggml、llamafile、xxHash、rotate-bits、SHA、YaRN 与 tokenizer 改编归属；另附 Unicode 许可及 NDK 28.2 LLVM NOTICE。模型权重单独下载的范围明确，保留 Qwen 原许可和旧 SmolLM 说明。
- OkHttp 4.12.0 内附 NOTICE 明确 Public Suffix List 使用 MPL-2.0，已增加对应条目和完整文本。来源：https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix/NOTICE 。CC0 原文来源：https://raw.githubusercontent.com/str4d/ed25519-java/master/LICENSE.txt 。许可清单维护说明同步到既有 docs/license-policy.md，未生成额外报告。
- 新增 preBuild 许可检查：实际发布依赖与清单不一致或许可文件缺失时拒绝构建。通过仅限临时 Gradle init 脚本加入未登记 JUnit 的负向验收，明确拦截 JUnit/Hamcrest；正常配置检查通过，未把测试依赖加入正式工程。
- 当前 Android Studio 虚拟机 emulator-5554 覆盖安装后，两项仪器化测试通过，2.756 秒，0 失败/跳过：所有文本读取、间接/Native/模型声明、真实入口、长文滚动重建恢复、超过 100 KB 的 LLVM 文本打开、异常条目重试与返回。实际点击设置入口检查列表和正文截图，排版正常，模型与会话未删除。
- Debug、androidTest、签名 Release 构建和 Lint 通过，0 错误、126 警告。无推理业务逻辑修改，本轮不重复此前 JVM 回归。整体仍需正式签名安装/升级与发布候选验收，目标继续进行。

### 2026-09-06 | 正式签名 ARM64 安装与覆盖升级验收 | IN_PROGRESS

- 新增继承 Release 的 releaseCheck 构建，采用现有正式签名、ARM64 ABI、不可调试，使用独立应用标识和明确桌面名称。在用户当前 Android Studio 模拟器 emulator-5554 安装，与原 Debug 应用共存，未卸载或迁移原应用；仅在验收应用内写测试数据。
- 正式 APK、验收 APK 和仪器化 APK 的签名证书一致；正式与验收包的 5 个 Native 库逐字节一致。系统实测验收包 primaryCpuAbi=arm64-v8a、无 DEBUGGABLE 标志；设备通过 libndk_translation.so 运行 ARM64。
- 专用测试从原应用已有官方模型复制到临时位置，再流式复制进验收应用，核对签名目录及完整 SHA-256 后走存储安装；未重新测试网络下载链路。准备版本号 1 的模型、会话/草稿和运行设置，阶段用例通过，2.845 秒。
- 以同一代码构建版本号 2 覆盖安装，升级阶段检查模型完整哈希、安装记录、会话正文、草稿、设置全部保留，并在页面发送真实中文问题获得“您好！”；通过，7.213 秒。这里验证系统安装升级路径，不将同代码版本号变化描述成历史版本代码迁移。
- 同一正式签名 ARM64 验收应用进一步运行立即停止循环、prefill 中停止后继续生成、中文/表情流式输出 3 项 Native 回归，全部通过，8.871 秒，无跳过。最后打开升级后聊天页，截图确认原测试会话及真实回答保留。
- Debug/Release 正常构建、releaseCheck 构建、专用测试包构建通过；Debug/ReleaseCheck Lint 均 0 错误，警告分别 126/116，diff --check 通过。本轮未重复与修改无关的 JVM 回归。
- 原应用模型文件仍为 491400032 字节，原包保留；删除的仅是 shell 临时权重副本。专用验收包保留在当前模拟器便于继续测试。运行步骤补入既有 docs/build-baseline.md，按中文说明提交推送；整体发布审查仍继续。

### 2026-09-06 | 字体上限 150% 与诊断页可读性 | IN_PROGRESS

- 按用户要求，应用随系统字号变化，最大采用 150%；在唯一 Activity 的资源上下文中限制 fontScale，不修改手机系统设置。系统为 200% 时实测 Activity 为 150%，系统低于上限时保留原值。
- 大字体诊断页此前会省略内存、架构及电源状态。现按可用宽度与字号切换规格单列/双列，窄屏状态卡纵向排列，关键数值允许完整换行；设备名称也不再截断。
- 提高浅色、深色辅助文字的对比度，测试覆盖页面背景、卡片和次级表面，均达到 4.5:1。仅验证这一文字色组，不据此宣称整个应用满足全部无障碍标准。参考：https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html 。
- 当前 Android Studio 虚拟机 emulator-5554 的正式签名验收包：系统 200%/深色下字体上限与完整文字、两种主题对比度共 2 项通过（1.687 秒）；150%/深色横屏完整文字 1 项通过（1.602 秒）；恢复 100%/浅色后完整文字、诊断周期刷新与安装快照更新共 3 项通过（11.278 秒）。全部 0 失败/跳过，另检查 150% 竖屏、横屏截图和页面滚动。
- Debug、签名 Release 构建成功；Debug/ReleaseCheck Lint 0 错误，原有警告分别 126/116。最新 Debug 已覆盖安装到原应用，未卸载或清除模型、会话。虚拟机恢复原来的 font_scale=1.0、night=no、accelerometer_rotation=1、user_rotation=0。
- diff --check 通过。本轮仅字体与展示调整，不重复无关的推理/JVM 全套用例。按约定简要中文提交并推送；整体发布验收继续。
### 2026-09-06 | 聊天操作点击与跨页键盘收起 | IN_PROGRESS

- 当前虚拟机复现：聊天页切到设置后真实输入法仍保持显示，覆盖新页面；show/hide 保留输入框但未主动收起键盘。现在切换到其他 Tab、推入历史等二级页前收起当前输入法并清除焦点；重复点击当前 Tab 不打断输入，草稿仍由既有会话逻辑保存。
- 新建、历史、模型切换按钮由 42dp 扩至 48dp，发送/停止按钮由 38dp 扩至 48dp，保持原有中文操作说明与行为。标题采用均衡换行，减少大字体时末行仅剩一个字的情况。点击尺寸依据 Android 官方建议：https://developer.android.com/guide/topics/ui/accessibility/apps 。
- 先只安装新增测试包，在旧验收应用上得到 2 项明确失败：按钮区域不足 48dp、离开聊天后键盘未在限定时间隐藏。旧版共 16.142 秒，未修改断言规避问题。
- 覆盖安装修复后的正式签名验收包，在用户当前 emulator-5554 的 150% 字体下，横屏 2 项通过（12.857 秒），竖屏 2 项通过（10.187 秒），0 失败/跳过。覆盖真实触摸点击历史按钮边缘、实际输入法显示/隐藏、聊天与设置/历史之间往返，以及中文标点、多行、表情草稿完整保留；测试结束恢复原草稿，不删除模型和历史。
- 实际检查浮动输入工具条和竖屏完整 Gboard 键盘，确认完整键盘下输入栏可见。虚拟机物理键盘设置会影响 Gboard 的展示方式，不把浮动工具条当作所有软键盘遮挡情况的验证，也未将本轮草稿测试描述为中文拼音候选词输入测试。
- 临时设置已恢复：系统字号 1.0、自动旋转 1、旋转方向 0、show_ime_with_hard_keyboard=0；Gboard“Show on-screen keyboard”恢复关闭，“Show toolbar”保持原来的开启。系统主题未改变。
- Debug、签名 Release、ReleaseCheck 构建成功，Lint 0 错误、126 警告；最新 Debug 已覆盖安装原应用，未卸载或清数据，diff --check 通过。按约定中文提交推送。
- 用户补充面向中国用户、中文必须完善，纳入后续验收；下一步重点检查中文文案、错误提示与输入体验，整体发布目标仍在进行。
### 2026-09-06 | 中文历史标题与单条删除说明 | IN_PROGRESS

- 修复历史页删除单个会话却复用“删除所有对话记录”确认文案的问题。新说明明确仅删除当前选中会话及草稿，其他会话和模型不受影响；设置中的清空全部会话仍保留原文案。
- 标题原先按 UTF-16 的 24 个编码单元截断，图标直接取首个 Char，会拆开生僻汉字或表情。现按 Android ICU 字符边界截取 24 个可见字符和首个图标字符，保留组合音标、肤色、国旗及家庭表情序列；标题中的换行和多余空白合并为单空格，消息正文不改写。参考：https://developer.android.com/reference/android/icu/text/BreakIterator 。
- 历史页优先采用已验证模型目录中的中文显示名称，目录加载完成时刷新；无法匹配旧模型时保留原模型标识，不伪造名称。
- 当前 Android Studio 虚拟机 emulator-5554 的正式签名验收包 2 项测试通过，4.502 秒，0 失败/跳过。覆盖生僻字𠮷、普通/组合表情、国旗及组合音标的首字符与标题截断边界，历史页中文模型名，取消删除、确认删除及其他会话 ID 集合保持不变。
- 测试仅创建两条临时会话，最后按指定 ID 清理；原会话、草稿、模型保留。实际打开历史页截图确认中文名称正常。Debug、签名 Release、ReleaseCheck 和测试包构建通过，Lint 0 错误、126 警告，diff --check 通过；最新 Debug 已覆盖安装原应用，按约定中文提交推送。
- 检查中另发现会话数据库异常存在直接显示英文异常文本、部分读取异常未捕获的路径，下一步处理错误提示与恢复；整体发布目标继续。