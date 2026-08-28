# Android 端侧本地 AI 与智能模型市场 AI 单步执行制作方案

> **给 AI 执行者的强制规则：** 本文件不是一次性生成全部代码的提示词，而是项目执行控制协议。每次运行对话只处理一个交付阶段；完成该阶段后必须验证并追加 `docs/ai-work-log.md`，然后停止。阶段内部的技术检查项不得再拆成独立对话任务。

**Goal:** 在 Java/Android 原生体系下，按可审计、可回滚、可验证的单步任务，交付 ARM64、GGUF、llama.cpp CPU/NEON、本地对话和模型市场 MVP，再用真机数据决定 GPU、多模态和 NPU 是否进入后续阶段。

> **交付顺序优先级：** 先完成软件实现、本机构建和自动化测试，产出可安装的候选 APK；之后才收集真实设备信息并执行真机安装、端到端和性能验证。APK 候选版产出前，不得要求真实设备、采集真实设备信息或以真机结果阻塞软件步骤。

**Architecture:** Android UI/业务进程负责市场、下载、会话和本地数据库；独立 `InferenceService` 进程负责 JNI 和 llama.cpp 推理；云端负责签名 Manifest、目录、对象存储和撤销列表。UI 不直接接触 JNI，推理进程不访问网络，Native 层不持有业务数据库。

**Tech Stack:** Java、XML、AndroidX、Room、OkHttp、WorkManager、AIDL/Binder、NDK/CMake、固定版本 llama.cpp、GGUF、Ed25519、SHA-256、`arm64-v8a`、CPU/NEON。

---

## 1. 六阶段运行协议

### 1.1 每次对话的唯一工作单位

一次 AI 对话只能有一个当前阶段，例如 `P1`。当前阶段必须来自第 4 节六阶段计划，并且前一阶段已经在日志中标记为 `DONE`。如果无法满足依赖，AI 必须只记录 `BLOCKED`，不得自行跳到别的阶段。

每个阶段必须产生一个可运行的软件结果：前端可操作版本、后端可联调版本、本地推理版本、功能闭环 APK、候选 APK 或真机验收报告。阶段内部可同时修改实现、资源、测试和文档，不再为了单个接口或文件暂停。

### 1.2 AI 每次运行的固定顺序

1. 定位项目根目录，确认存在 `.git`。
2. 读取 `docs/ai-execution-plan.md` 和 `docs/ai-work-log.md` 的最新内容。
3. 执行 `git status --short`，识别已有未提交改动；不得覆盖不属于当前阶段的改动。
4. 从六阶段计划中确定唯一的 `PHASE_ID`。若日志中已有 `IN_PROGRESS`，只能继续该 ID。
5. 在日志追加一条 `IN_PROGRESS`，写明计划修改的文件和验证命令。
6. 只完成当前阶段的交付范围，允许同时修改该阶段涉及的实现、资源、测试和文档。
7. 执行当前阶段的构建和验收命令；失败时修复当前阶段，仍不得开始下一阶段。
8. 在日志追加 `DONE` 或 `BLOCKED`，记录实际文件、命令输出摘要、风险和下一阶段依赖。
9. 向用户返回本轮结果，然后结束本轮对话。

### 1.3 严格禁止事项

- 不得一次对话完成两个或更多 `PHASE_ID`。
- 不得在当前阶段之外扩展产品范围；额外问题写入“后续风险”。
- 不得把“代码已写入”当作完成；没有可运行结果和验证命令只能标记 `IN_PROGRESS`。
- 不得删除或改写历史日志；日志只允许追加。
- 不得把 Prompt、模型回复原文、Authorization、设备序列号、完整路径中的个人信息写入日志。
- 不得把任意 Hugging Face URL、未签名 Manifest 或未校验的 GGUF 接入产品市场。
- 不得把 GPU、NPU、多模态、RAG、账号同步提前加入 CPU/NEON MVP。

### 1.4 每次对话的启动提示词

将下面提示词与本文件、项目日志一起交给 AI。只替换尖括号中的值：

```text
你是 local-ai 项目的执行 AI。

强制边界：本轮只执行一个阶段 <PHASE_ID>，禁止执行其他阶段或无关重构。
项目根目录：<PROJECT_ROOT>
阶段计划：docs/ai-execution-plan.md
工作日志：docs/ai-work-log.md

开始前：
1. 读取阶段计划中 <PHASE_ID> 的完整阶段卡。
2. 读取工作日志最后 120 行和最近一次状态。
3. 执行 git status --short，保护已有未提交改动。

执行中：只修改当前阶段允许的范围；遇到额外问题先记录，不扩张范围。

结束前：执行当前阶段的构建和验收命令，并向 docs/ai-work-log.md 追加一条日志。验证未通过时只能写 IN_PROGRESS 或 BLOCKED，不能写 DONE。

回复格式必须是：
- PHASE_ID / 状态：DONE、IN_PROGRESS 或 BLOCKED
- 本轮实际改动：文件路径 + 一句话
- 验证：命令 + 结果摘要
- 日志：已追加的日志标题
- 下一阶段：只写依赖关系，不执行下一阶段
```

### 1.5 日志追加格式

每次运行追加以下结构，时间使用项目统一时区 `Asia/Shanghai`，状态只能使用 `IN_PROGRESS`、`DONE`、`BLOCKED`：

```markdown
### 2026-08-22 09:30 | P1 | DONE

- 目标：用一句话说明本阶段要交付什么。
- 依赖：无，或列出已经 DONE 的阶段 ID。
- 实际修改：
  - `path/to/file`: 变更摘要。
- 验证：
  - 命令：`具体命令`
  - 结果：`PASS`，并记录关键数字或错误摘要。
- 风险/阻塞：无；若有，必须写清复现条件。
- 下一阶段依赖：`P2` 可以开始；本轮没有执行 P2。
```

`DONE` 的含义是本阶段的交付结果和验收标准全部通过，不代表整个项目完成。`BLOCKED` 必须包含阻塞原因、已尝试命令、需要的外部输入和解除条件。日志中的“下一阶段依赖”只用于导航，不表示 AI 已经执行该阶段。

### 1.6 项目日志文件约定

项目初始化后必须存在：

```text
local-ai/
  docs/
    ai-execution-plan.md
    ai-work-log.md
```

把本文件复制为 `docs/ai-execution-plan.md`，把第 5 节模板复制为 `docs/ai-work-log.md`。每一轮 AI 都先读日志再工作；多人协作时用 Git 合并日志冲突，禁止静默覆盖他人的记录。

---

## 2. 步骤卡执行规范

每张阶段卡都包含：依赖、目标、允许修改范围、交付结果、验证命令和通过标准。AI 必须严格按卡执行。阶段卡中的路径以项目根目录为基准；不存在的目录由对应阶段创建。

若阶段卡要求“创建文件”，只能创建与阶段交付直接相关的文件。若实现过程中发现需要新增的产品模块，先将该阶段记为 `BLOCKED`，把新增模块理由写入日志，等待下一轮重新确认范围。

---

## 3. 里程碑和停止条件

| 里程碑 | 通过条件 | 未通过时的动作 |
| --- | --- | --- |
| P1 前端体验 | 美观、可操作的 Android 前端骨架和完整页面流转 | 停在 P1，禁止提前接入真实推理 |
| P2 后端与数据 | Manifest 目录、下载、Room 数据和校验服务可联调 | 停在 P2，先修复数据链路 |
| P3 本地推理 | llama.cpp、JNI、独立推理服务和流式对话可运行 | 停在 P3，先修复推理链路 |
| P4 功能闭环 | 前端、后端和推理真正接通，Debug APK 可完成核心流程 | 停在 P4，先修复主流程 |
| P5 软件候选 | 自动化测试、故障注入、隐私/许可证检查通过，产出候选 APK | 停在 P5，不进入真机 |
| P6 真机验收 | 候选 APK 完成设备采集、安装、端到端、性能和稳定性验证 | 停在 P6，问题回流 P4/P5 修复 |

任何里程碑未通过，都只能修复当前失败项或新增明确的阻塞记录；不得以“先做后面功能”绕过门禁。

---

## 4. 六阶段可执行计划

以下六张阶段卡是唯一的主执行任务。原有 `S001-S045` 内容保留在本节后半作为技术检查清单，不能再作为独立对话任务或阻塞软件交付。

### P1 | 构建美观的 Android 前端

- 依赖：无；允许使用模拟数据。
- 目标：完成模型市场、模型详情、下载管理、本地聊天、会话历史、诊断和设置页面，建立统一视觉系统和导航。
- 参考：Google AI Edge Gallery 的模型发现与体验结构、PocketPal AI 的本地聊天和模型管理流程、Material 3 的组件/无障碍规范；SmolChat-Android 仅参考 Android 本地模型交互，不复制实现。
- 允许修改：`app/src/main/java/`、`app/src/main/res/`、`app/src/test/`、`docs/ui-design.md`、必要的 Gradle UI 依赖。
- 交付结果：在模拟数据下可运行的前端 APK，用户能完成“浏览模型 -> 查看详情 -> 打开聊天 -> 查看历史 -> 查看诊断 -> 修改设置”。
- 美观硬性要求：统一色彩、字体层级、间距、图标和圆角；首页首屏有清晰视觉焦点；空态、加载态、错误态和深色模式完整；小屏无溢出；按钮、列表、输入框和卡片不能使用默认粗糙样式。
- 验证：`./gradlew :app:assembleDebug :app:testDebugUnitTest`；在模拟器或 UI 自动化中逐页截图并检查核心流程可点击。
- 通过标准：APK 构建成功，核心页面可操作，视觉验收无明显错位、截断、默认控件残留或不可读文本。

### P2 | 实现后端与本地数据层

- 依赖：P1 DONE。
- 目标：实现签名 Manifest、模型目录、下载任务、断点续传、SHA-256/GGUF 校验、Room 模型/会话/消息数据和安装回滚。
- 允许修改：`backend/`、`app/src/main/java/com/example/localai/data/`、`app/src/main/java/com/example/localai/feature/download/`、对应测试和文档。
- 交付结果：使用本地 HTTP Fixture 即可完成“拉取目录 -> 下载 -> 校验 -> 安装 -> 删除/回滚”，前端显示真实任务状态。
- 验证：`./gradlew :app:testDebugUnitTest`；运行覆盖 200/206/416、断网、ETag 变化、哈希错误、签名错误和进程重启的 Fixture 测试。
- 通过标准：非法资产被拒绝，下载可恢复，任务和会话可持久化，前端不再依赖写死的下载状态。

### P3 | 实现本地推理能力

- 依赖：P1 DONE；P2 可提供模型安装接口。
- 目标：接入固定版本 llama.cpp，完成 Java/JNI、`InferenceService` 独立进程、流式 Token、停止、释放、重启和结构化错误码。
- 允许修改：`app/src/main/cpp/`、`app/src/main/java/com/example/localai/core/inference/`、AIDL、Native 测试和构建文档。
- 交付结果：使用一个批准的 GGUF 模型，在本机 runner/模拟环境完成加载、生成、停止和释放，聊天页可接收流式事件。
- 验证：`./gradlew :app:externalNativeBuildDebug :app:testDebugUnitTest :app:assembleDebug`；执行重复 stop/release、服务重启和错误回调测试。
- 通过标准：UI 进程不直接加载 Native；流式消息不乱序；重复释放不崩溃；推理进程无网络权限。

### P4 | 接通核心功能闭环

- 依赖：P1、P2、P3 DONE。
- 目标：把市场、详情、下载、模型安装、聊天、历史、设备画像、兼容性提示和诊断页面接通为真实本地流程。
- 允许修改：`app/src/main/java/com/example/localai/feature/`、ViewModel、Room 映射、布局、资源和端到端测试。
- 交付结果：Debug APK 可完成“发现 -> 详情 -> 下载 -> 安装 -> 聊天 -> 停止 -> 历史 -> 删除”，异常有可见原因和恢复入口。
- 验证：`./gradlew :app:assembleDebug :app:testDebugUnitTest`；模拟器端到端测试覆盖空态、错误态、停止生成、进程重启和上下文裁剪。
- 通过标准：核心流程无需手工改数据库或文件；页面状态与真实任务/会话一致；视觉样式在接入真实数据后仍保持稳定。

### P5 | 软件候选与自动化验收

- 依赖：P4 DONE。
- 目标：完成故障注入、Native 稳定性、隐私/许可证审查、Release 构建和候选 APK 归档。
- 允许修改：`qa/`、`docs/gates/`、`docs/release/`、`artifacts/manifest.json`、测试配置。
- 交付结果：可供真机验收的候选 APK/AAB、哈希、符号表和软件验收报告。
- 验证：`./gradlew :app:assembleRelease :app:testReleaseUnitTest`；运行下载/安装故障矩阵、Native 循环和日志脱敏扫描。
- 通过标准：自动化测试通过，候选 APK 可校验，软件门禁无未解释失败；此阶段结束前不连接真实设备。

### P6 | 候选 APK 真机验收

- 依赖：P5 DONE。
- 目标：收集三档真实设备非个人信息，安装同一候选 APK，执行端到端、性能和稳定性回归。
- 允许修改：`qa/device-matrix/`、`qa/e2e/`、`qa/benchmarks/`、`docs/device-baseline.md`、`docs/gates/g6-device-acceptance.md`。
- 交付结果：真机验收报告，包含安装结果、脱敏设备画像、TTFT/TPS、峰值内存、温度、电量、崩溃和恢复证据。
- 验证：三档设备各执行主流程至少一次、Benchmark 至少三次，记录候选 APK SHA-256；不得记录序列号、账号或完整个人路径。
- 通过标准：候选 APK 在目标设备可安装启动，主流程可完成；性能/稳定性问题明确回流 P4 或 P5 修复后重新打包。

### 4.1 技术检查清单（非主执行任务）

以下原 `S001-S045` 检查项仅用于阶段内核对、日志和验收证据，不再要求每项单独开一轮对话：

### G0 工程基线

#### S001 | 建立项目目录和 AI 日志

- 依赖：无。
- 目标：建立 Android、后端、QA、文档目录，并创建追加式 AI 工作日志。
- 允许修改：`settings.gradle`、`build.gradle`、`app/`、`backend/`、`qa/`、`docs/ai-execution-plan.md`、`docs/ai-work-log.md`。
- 唯一动作：初始化空 Git 工程和日志文件，不引入推理代码。
- 验证：`git status --short`；确认两个文档存在，且日志包含“当前阶段”和“追加规则”。
- 通过标准：目录和日志可提交，工作树状态可解释。

#### S002 | 固定 Android 和 NDK 工具链

- 依赖：S001 DONE。
- 目标：固定 Gradle、Android Gradle Plugin、compileSdk、minSdk 26、NDK、CMake 和 Java 版本。
- 允许修改：`gradle/libs.versions.toml`、`gradle.properties`、`build.gradle`、`app/build.gradle`、`docs/build-baseline.md`。
- 唯一动作：写入版本目录和构建基线，禁止添加业务功能。
- 验证：`./gradlew --version`；`./gradlew :app:assembleDebug`。
- 通过标准：版本输出符合基线，Debug APK 构建成功。

#### S003 | 建立第三方许可证和 NOTICE 清单

- 依赖：S002 DONE。
- 目标：记录 llama.cpp、ggml、AndroidX、OkHttp、Room、WorkManager 等代码依赖的许可证和版本来源。
- 允许修改：`docs/license-policy.md`、`NOTICE`、`gradle/libs.versions.toml`。
- 唯一动作：完成代码依赖清单，不审查模型权重许可证。
- 验证：`./gradlew :app:dependencies`；检查清单覆盖所有直接依赖。
- 通过标准：每个直接依赖都有名称、版本、许可证、来源 URL。

#### S004 | 建立模拟设备画像和 Benchmark Fixture

- 依赖：S002 DONE。
- 目标：定义旗舰、中端、入门三档的模拟 SoC、RAM、Android、ABI、页大小、可用存储和 Benchmark 数据，用于前期自动化测试。
- 允许修改：`qa/device-matrix/simulated-devices.yaml`、`qa/fixtures/benchmark-results.json`、`docs/device-baseline.md`。
- 唯一动作：只创建不包含个人信息的模拟 Fixture，不连接真实设备、不运行真机 Benchmark。
- 验证：YAML/JSON 解析命令，例如 `python -c "import yaml; yaml.safe_load(open('qa/device-matrix/simulated-devices.yaml'))"`，并校验 `qa/fixtures/benchmark-results.json`。
- 通过标准：三档 Fixture 字段完整、JSON/YAML 可解析，且文档明确真实设备采集在候选 APK 产出后执行。

### G1 Native 推理基线

#### S005 | 锁定 llama.cpp 提交并导入 Native 构建

- 依赖：S002、S003 DONE。
- 目标：固定 llama.cpp commit、ggml 依赖和本项目 patch 列表，完成 `arm64-v8a` CPU/NEON 编译。
- 允许修改：`app/src/main/cpp/engine_llama/`、`app/src/main/cpp/CMakeLists.txt`、`docs/native-baseline.md`。
- 唯一动作：只完成源码固定和 Native 构建，不接 Java UI。
- 验证：`./gradlew :app:externalNativeBuildDebug`。
- 通过标准：生成 `arm64-v8a` `.so`，记录 commit、编译参数和构建产物哈希。

#### S006 | 定义 JNI 最小生命周期接口

- 依赖：S005 DONE。
- 目标：定义 `load/start/stop/release/getStats` 的 Java/JNI 边界和结构化错误码。
- 允许修改：`app/src/main/java/com/example/localai/core/inference/NativeSession.java`、`app/src/main/cpp/ai_jni/native_session.cpp`、对应测试。
- 唯一动作：只实现接口声明、句柄表和错误码映射，不加载真实模型。
- 验证：`./gradlew :app:testDebugUnitTest :app:externalNativeBuildDebug`。
- 通过标准：Java 可创建和释放句柄；C++ 异常不会穿过 JNI。

#### S007 | 完成单模型加载和首 token 生成

- 依赖：S006 DONE。
- 目标：使用一个已批准的 1.5B-3B GGUF 指令模型完成 CPU/NEON 加载和一次生成。
- 允许修改：`app/src/main/cpp/ai_jni/`、`app/src/main/cpp/benchmark/`、`qa/fixtures/approved-model.json`。
- 唯一动作：只实现单模型 load/generate 冒烟，不接流式 UI。
- 验证：本机 Native runner、单元测试和 `./gradlew :app:assembleDebug`；记录构建结果、模拟生成输出和错误码。
- 通过标准：Debug APK 构建成功，模拟加载与生成路径可测试，失败有结构化错误码；真机加载留待 G6 验收。

#### S008 | 实现流式 Token 回调

- 依赖：S007 DONE。
- 目标：以批量或固定时间窗口把 Token 片段从 Native 传到 Java，避免每 Token 一次 JNI/IPC。
- 允许修改：`app/src/main/cpp/ai_jni/`、`app/src/main/java/com/example/localai/core/inference/`、对应测试。
- 唯一动作：只实现流式事件通道，不做聊天页面。
- 验证：Native 单元测试或本机 runner，确认顺序、结束事件和异常事件。
- 通过标准：Token 顺序稳定，结束/错误只回调一次，长输出无明显丢段。

#### S009 | 实现停止、释放和重复调用保护

- 依赖：S008 DONE。
- 目标：让 `stop()` 可取消生成，`release()` 等待工作线程退出，并防止重复释放和回调悬空。
- 允许修改：`app/src/main/cpp/ai_jni/`、`app/src/main/java/com/example/localai/core/inference/`、对应测试。
- 唯一动作：只处理生命周期和并发，不新增参数。
- 验证：重复 `stop/stop/release/release` 测试；运行 30 次加载-生成-释放循环。
- 通过标准：无 UAF、死锁、崩溃；每轮句柄都进入已释放状态。

#### S010 | 建立短 Benchmark 协议和模拟结果校验

- 依赖：S009 DONE。
- 目标：固定未来真机 Benchmark 的输入、输出上限、线程数和采样参数，并校验模拟 TTFT、TPS、峰值内存、温度和电量结果格式。
- 允许修改：`app/src/main/cpp/benchmark/`、`qa/benchmarks/`、`docs/benchmark-protocol.md`。
- 唯一动作：只建立协议、JSON Schema 和模拟结果校验，不连接真实设备、不做推荐算法。
- 验证：运行 JSON Schema 校验和 P50/P95 计算测试。
- 通过标准：模拟结果字段一致，P50/P95 可计算，异常样本保留原因；真机采样留待 G6。

#### S011 | 完成 G1 门禁复核

- 依赖：S007、S008、S009、S010 DONE。
- 目标：汇总 Native 基线的构建、自动化加载/生成模拟、停止、释放和重启证据。
- 允许修改：`docs/gates/g1-native.md`。
- 唯一动作：只写门禁报告，不修改实现代码。
- 验证：逐项引用 S005-S010 日志和产物路径。
- 通过标准：G1 报告明确 PASS 或 BLOCKED，包含 Debug APK 构建产物；未 PASS 不得开始 S012 之后的供应链任务。真实设备数据不是本门禁前置条件。

### G2 模型资产和下载供应链

#### S012 | 定义 Manifest Schema

- 依赖：S011 DONE。
- 目标：定义模型 ID、版本、来源、许可证、GGUF 文件、SHA-256、运行约束、签名字段。
- 允许修改：`backend/manifest-schema/model-manifest.schema.json`、`docs/model-onboarding.md`。
- 唯一动作：只定义 Schema 和示例，不接 CDN。
- 验证：JSON Schema validator 校验有效和无效样例。
- 通过标准：缺少哈希、许可证、来源或运行约束的 Manifest 被拒绝。

#### S013 | 实现 Manifest Ed25519 签名验证

- 依赖：S012 DONE。
- 目标：服务端签名、客户端内置公钥验证、`keyId` 轮换和撤销列表接口。
- 允许修改：`backend/signing/`、`app/src/main/java/com/example/localai/data/network/ManifestVerifier.java`、测试。
- 唯一动作：只实现签名和验证，不实现下载。
- 验证：有效签名、篡改字段、错误 keyId、撤销 key 四组测试。
- 通过标准：四组测试结果符合预期，失败错误码可显示给 UI。

#### S014 | 建立 Room 下载任务状态机

- 依赖：S012 DONE。
- 目标：持久化 `taskId/modelId/version/fileName/bytesDownloaded/totalBytes/etag/sha256/state/retryCount/lastError`。
- 允许修改：`app/src/main/java/com/example/localai/data/room/DownloadEntity.java`、`DownloadDao.java`、数据库迁移测试。
- 唯一动作：只建立状态和事务，不发 HTTP 请求。
- 验证：Room migration test；非法状态跳转测试。
- 通过标准：进程被杀后可从数据库恢复，状态跳转不可越权。

#### S015 | 实现 HTTP Range 和 ETag 断点下载

- 依赖：S013、S014 DONE。
- 目标：实现暂停、恢复、206 校验、ETag 变化重启、镜像切换。
- 允许修改：`app/src/main/java/com/example/localai/feature/download/DownloadCoordinator.java`、网络 Fixture、测试。
- 唯一动作：只实现下载传输，不解压和安装。
- 验证：本地 HTTP Fixture 覆盖 200、206、416、超时、ETag 变化和断网重试。
- 通过标准：不会把旧版本内容拼接到新版本；断网后可安全恢复或重启。

#### S016 | 实现 SHA-256、GGUF 元数据和 Manifest 校验

- 依赖：S013、S015 DONE。
- 目标：按“签名 -> 大小 -> Range/ETag -> SHA-256 -> GGUF 元数据”顺序校验文件。
- 允许修改：`app/src/main/java/com/example/localai/feature/download/ModelVerifier.java`、`app/src/main/cpp/ai_jni/gguf_probe.cpp`、测试。
- 唯一动作：只实现校验链，不执行正式安装。
- 验证：篡改字节、错误大小、错误架构、超限上下文、损坏头部的测试。
- 通过标准：所有非法资产被拒绝，并产生可追踪错误码。

#### S017 | 实现原子安装和回滚

- 依赖：S016 DONE。
- 目标：使用临时目录、固定缓冲区、同文件系统原子重命名和 `install.ok` 完成安装；失败保留旧版本。
- 允许修改：`app/src/main/java/com/example/localai/data/storage/ModelStorageManager.java`、安装测试。
- 唯一动作：只实现安装、删除和回滚，不做市场页面。
- 验证：安装中断、空间不足、重启恢复、旧版本回滚测试。
- 通过标准：任何中断都不会留下“已安装但未校验”的模型。

#### S018 | 建立 WorkManager 可恢复任务

- 依赖：S015、S017 DONE。
- 目标：将下载、校验、安装串成可恢复的后台任务，处理网络类型、Doze、通知和重试上限。
- 允许修改：`app/src/main/java/com/example/localai/feature/download/ModelDownloadWorker.java`、约束测试。
- 唯一动作：只接后台调度，不实现 UI。
- 验证：杀进程、断网、Doze 模拟、达到重试上限的测试。
- 通过标准：任务可恢复，失败原因持久化，不承诺“退后台绝不停止”。

#### S019 | 完成 G2 供应链门禁复核

- 依赖：S012-S018 DONE。
- 目标：汇总 Manifest、签名、下载、校验、安装和回滚证据。
- 允许修改：`docs/gates/g2-supply-chain.md`。
- 唯一动作：只写门禁报告。
- 验证：逐项引用测试报告和 SHA-256 产物。
- 通过标准：G2 PASS 后才允许开发市场和聊天 UI。

### G3 推理进程隔离与设备适配

#### S020 | 定义 AIDL 推理协议

- 依赖：S011、S019 DONE。
- 目标：定义 `start/stop/getStats` 和 Token/结束/错误事件的 AIDL 接口，批量传输文本片段。
- 允许修改：`app/src/main/aidl/com/example/localai/IInferenceService.aidl`、协议文档和测试。
- 唯一动作：只定义协议，不接真实 Service。
- 验证：AIDL 编译和 Binder 参数序列化测试。
- 通过标准：协议包含 requestId、modelId、版本、上下文、生成参数和错误码。

#### S021 | 将 InferenceService 放入独立进程

- 依赖：S020 DONE。
- 目标：让 `InferenceService` 在独立进程加载 Native，UI 进程通过 Binder 调用。
- 允许修改：`AndroidManifest.xml`、`InferenceService.java`、`InferenceClient.java`、相关测试。
- 唯一动作：只完成跨进程调用，不做崩溃恢复策略。
- 验证：模拟器或自动化 Instrumentation 测试覆盖启动、绑定、生成、解绑和重连。
- 通过标准：推理进程不申请网络权限，UI 进程不直接加载 llama.cpp。

#### S022 | 实现推理进程崩溃恢复

- 依赖：S021 DONE。
- 目标：处理 Binder death、`ENGINE_CRASHED`、会话清理、服务重启和模型切换。
- 允许修改：`InferenceClient.java`、`InferenceService.java`、`ConversationViewModel.java`、测试。
- 唯一动作：只实现恢复状态机，不新增聊天 UI。
- 验证：主动杀死推理进程并观察 UI、日志和会话恢复。
- 通过标准：UI 不崩溃，当前生成明确失败，允许一键重启服务或切换已安装模型。

#### S023 | 建立 DeviceProfile 采集

- 依赖：S004、S021 DONE。
- 目标：采集 Android API、ABI、RAM、memoryClass、可用存储、CPU/NEON、Vulkan、热状态和电量状态。
- 允许修改：`app/src/main/java/com/example/localai/core/device/DeviceProfiler.java`、实体和测试。
- 唯一动作：只采集和持久化画像，不决定推荐。
- 验证：三档模拟 Fixture 字段完整性测试，确认不记录序列号。
- 通过标准：画像字段有来源、时间和“未知”处理；真实采集在 G6 复用同一接口验证。

#### S024 | 实现内存预算计算

- 依赖：S023 DONE。
- 目标：按权重映射、KV Cache、计算图、后端 workspace 和 App 增量估算候选上限。
- 允许修改：`app/src/main/java/com/example/localai/core/compatibility/MemoryBudget.java`、单元测试、文档。
- 唯一动作：只实现预算模型，不接实测推荐。
- 验证：边界输入、低内存设备和超长上下文测试。
- 通过标准：输出预算和降级建议，不把单一 `(availMem-threshold)*0.8` 当作精确结论。

#### S025 | 实现短 Benchmark 驱动的兼容性规则

- 依赖：S010、S024 DONE。
- 目标：根据硬约束、短 Benchmark、内存余量和热状态输出 `RECOMMENDED/RUNNABLE/HIGH_LOAD/UNSUPPORTED`。
- 允许修改：`app/src/main/java/com/example/localai/core/compatibility/CompatibilityEngine.java`、测试。
- 唯一动作：只实现规则引擎，不做个性化机器学习。
- 验证：覆盖 ABI/API/存储/内存/热状态/Benchmark 成功和失败的规则测试。
- 通过标准：每个结果包含原因、建议配置和缺少的能力字段。

#### S026 | 实现 CPU 降级和上下文裁剪

- 依赖：S025 DONE。
- 目标：在高负载或高温时关闭 GPU、减少上下文、降低并发或切换小模型。
- 允许修改：`CompatibilityEngine.java`、`InferenceRequest.java`、设置页配置实体、测试。
- 唯一动作：只实现降级配置生成，不改变后端实现。
- 验证：模拟高温、低内存和 Benchmark 超时。
- 通过标准：降级原因可解释，配置变化可记录，用户可看到限制。

#### S027 | 完成 G3 隔离与适配门禁复核

- 依赖：S020-S026 DONE。
- 目标：汇总 AIDL、独立进程、恢复、画像、预算和降级证据。
- 允许修改：`docs/gates/g3-isolation-compatibility.md`。
- 唯一动作：只写门禁报告。
- 验证：引用对应日志、自动化崩溃测试和模拟设备结果。
- 通过标准：G3 PASS 后才允许接入完整 UI；不以真机结果作为前置条件。

### G4 市场、聊天和诊断闭环

#### S028 | 建立 Room 模型、会话和诊断实体

- 依赖：S019、S027 DONE。
- 目标：建立模型、下载、会话、消息、Benchmark 和错误诊断实体及迁移。
- 允许修改：`app/src/main/java/com/example/localai/data/room/`、数据库测试。
- 唯一动作：只完成数据层，不实现页面。
- 验证：迁移、事务、删除级联和敏感字段脱敏测试。
- 通过标准：模型和会话数据可恢复，日志不保存 Prompt/回复原文。

#### S029 | 实现模型市场列表和筛选

- 依赖：S028 DONE。
- 目标：展示目录列表，支持任务、语言、参数量、体积和适配状态筛选。
- 允许修改：`feature/market/`、目录 Repository、列表测试。
- 唯一动作：只完成列表和筛选，不做详情下载。
- 验证：分页、空态、错误态、筛选组合测试。
- 通过标准：列表只显示签名 Manifest 中的模型，适配原因可进入详情。

#### S030 | 实现模型详情和许可证展示

- 依赖：S029 DONE。
- 目标：展示来源、许可证、Tokenizer、Chat Template、上下文、体积、实测性能和限制。
- 允许修改：`feature/market/ModelDetailFragment.java`、布局和 UI 测试。
- 唯一动作：只实现详情页，不启动下载。
- 验证：缺失字段、长文本、撤销模型和许可证链接测试。
- 通过标准：用户能在安装前判断来源、许可和设备适配。

#### S031 | 实现下载管理页面

- 依赖：S018、S030 DONE。
- 目标：展示进度、速度、剩余大小、暂停、恢复、取消、校验失败和重试。
- 允许修改：`feature/download/`、布局、WorkManager UI 测试。
- 唯一动作：只连接已完成的下载任务，不改下载器核心。
- 验证：Fixture 断网、杀进程、重试和安装完成事件。
- 通过标准：状态与 Room 一致，错误信息可操作。

#### S032 | 实现单模型本地聊天页

- 依赖：S021、S026、S030 DONE。
- 目标：连接 AIDL 推理服务，实现流式消息、停止、重试、复制和当前模型显示。
- 允许修改：`feature/chat/`、`ConversationViewModel.java`、布局和测试。
- 唯一动作：只支持一个已安装模型的文本对话，不做多模型常驻。
- 验证：生成、停止、服务崩溃、空输入、超长输入和重试测试。
- 通过标准：流式消息不重复、不乱序；停止后不会继续追加 Token。

#### S033 | 实现会话历史和上下文裁剪

- 依赖：S028、S032 DONE。
- 目标：支持新建、历史、删除、模型切换、上下文上限和摘要/裁剪提示。
- 允许修改：`feature/chat/history/`、Room DAO、裁剪策略测试。
- 唯一动作：只处理会话数据和上下文策略，不做云同步。
- 验证：大消息、超长会话、删除和模型切换测试。
- 通过标准：上下文裁剪有可解释的提示，删除后不残留消息。

#### S034 | 实现设备诊断和性能页

- 依赖：S010、S023-S026、S032 DONE。
- 目标：展示当前内存、热状态、后端、Benchmark、TTFT/TPS、峰值内存和“为什么不支持”。
- 允许修改：`feature/diagnostics/`、布局和数据映射测试。
- 唯一动作：只读展示诊断，不上传匿名指标。
- 验证：三档模拟设备和四种兼容性状态的 UI 测试。
- 通过标准：所有降级和不支持状态都有具体原因。

#### S035 | 完成 G4 产品闭环复核

- 依赖：S029-S034 DONE。
- 目标：验证“发现 -> 详情 -> 下载 -> 安装 -> 聊天 -> 历史 -> 删除”的完整本地流程。
- 允许修改：`qa/e2e/`、`docs/gates/g4-product-loop.md`。
- 唯一动作：只执行端到端复核并写报告。
- 验证：模拟器或自动化端到端测试，执行 `assembleRelease` 并保留候选 APK 路径、截图、日志摘要和失败步骤。
- 通过标准：主流程可完成，候选 APK 构建成功，任意失败都有可见原因和恢复入口；真机端到端验证留待 G6。

### G5 软件候选、测试、隐私和发布门禁

#### S036 | 建立下载和安装故障注入矩阵

- 依赖：S035 DONE。
- 目标：覆盖 200/206/416、断网、ETag 变化、磁盘不足、哈希错误、签名错误和安装中断。
- 允许修改：`qa/fault-injection/`、`docs/fault-matrix.md`。
- 唯一动作：只建立自动化故障场景，不改业务代码。
- 验证：运行全部 Fixture，输出每个场景 PASS/FAIL。
- 通过标准：失败不会破坏已安装版本，错误码和日志可定位。

#### S037 | 建立 Native 并发和崩溃稳定性测试

- 依赖：S035 DONE。
- 目标：覆盖重复停止、重复释放、服务杀死、切换模型、后台恢复和长对话。
- 允许修改：`qa/native-stability/`、`docs/stability-report.md`。
- 唯一动作：只执行稳定性测试，不新增功能。
- 验证：固定循环次数并收集 crash-free session、错误码和峰值内存。
- 通过标准：达到发布门禁阈值，所有失败样本有可复现记录。

#### S038 | 构建候选 APK/AAB 并执行自动化预检

- 依赖：S010、S035 DONE。
- 目标：构建可供真机验收的候选 APK/AAB，并对安装包、签名配置和自动化测试结果执行预检。
- 允许修改：`docs/release/candidate-build.md`、`artifacts/manifest.json`。
- 唯一动作：只生成候选安装包与预检报告，不连接真实设备。
- 验证：`./gradlew :app:assembleRelease :app:testReleaseUnitTest`；计算 APK/AAB 的 SHA-256 并检查 `artifacts/manifest.json`。
- 通过标准：候选 APK/AAB 存在且可校验，自动化测试通过；该产物是 G6 真机工作的唯一输入。

#### S039 | 完成隐私、日志和许可证审查

- 依赖：S035 DONE。
- 目标：确保日志、抓包、数据库和崩溃报告不含 Prompt/回复原文、Authorization、设备序列号或完整个人路径。
- 允许修改：`docs/privacy-review.md`、`docs/license-review.md`、日志脱敏测试。
- 唯一动作：只执行审查和修复发现的脱敏问题。
- 验证：静态扫描、抓包样本、数据库样本和 NOTICE 检查。
- 通过标准：隐私和许可证审查均为 PASS。

#### S040 | 完成发布门禁和回滚演练

- 依赖：S036-S039 DONE。
- 目标：验证灰度目录、模型撤销、Manifest key 轮换、客户端回滚和版本不可变规则。
- 允许修改：`docs/release-gate.md`、`qa/release/`、后端灰度 Fixture。
- 唯一动作：只执行发布演练，不发布生产内容。
- 验证：模拟撤销、密钥轮换、CDN 镜像不一致和旧版本回滚。
- 通过标准：可以阻止新安装、保留旧版本可删除状态，并能回滚客户端配置。

#### S041 | 生成软件候选归档和预验收报告

- 依赖：S040 DONE。
- 目标：归档候选 APK/AAB、SO、符号表、Manifest Schema、NOTICE、SBOM、模拟 Benchmark 和软件门禁报告。
- 允许修改：`docs/release/`、`artifacts/manifest.json`。
- 唯一动作：只生成归档索引和最终报告，不再修改功能代码。
- 验证：按索引逐个计算哈希并确认文件存在；`git status --short` 只包含预期归档变更。
- 通过标准：归档可由另一名工程师按索引复核，G0-G5 全部 PASS；归档明确列出待执行的 G6 真机验收项。

### G6 候选 APK 真机信息采集与验收

#### S042 | 收集三档真机信息并安装候选 APK

- 依赖：S041 DONE。
- 目标：在旗舰、中端、入门三档真实设备上采集 SoC、RAM、Android、ABI、页大小、可用存储等非个人信息，并安装 S038 归档的候选 APK。
- 允许修改：`qa/device-matrix/devices.yaml`、`docs/device-baseline.md`、`qa/device-installation/`。
- 唯一动作：只采集设备信息和验证候选 APK 的安装/启动，不运行完整业务流程。
- 验证：每台设备记录 APK SHA-256、安装结果、启动结果和字段完整性；不得记录设备序列号、账号或完整个人路径。
- 通过标准：三档设备均安装并启动候选 APK，所有字段完整且无个人标识。

#### S043 | 执行候选 APK 真机端到端验证

- 依赖：S042 DONE。
- 目标：在已安装候选 APK 上验证“发现 -> 详情 -> 下载 -> 安装 -> 聊天 -> 历史 -> 删除”流程及可见错误恢复。
- 允许修改：`qa/e2e/`、`docs/gates/g6-device-e2e.md`。
- 唯一动作：只执行真机端到端测试并记录证据，不改功能代码。
- 验证：三档设备各运行一次主流程，保留脱敏截图、日志摘要、错误码和失败复现步骤。
- 通过标准：主流程可完成；失败项具有可复现记录并返回对应软件步骤修复。

#### S044 | 完成三档真机性能与稳定性回归

- 依赖：S042、S043 DONE。
- 目标：按 S010 协议在三档设备上收集 TTFT、TPS、峰值 RSS/PSS、温度、电量和稳定性结果，比较模拟门槛与真实基线。
- 允许修改：`qa/benchmarks/`、`qa/native-stability/`、`docs/performance-regression.md`、`docs/stability-report.md`。
- 唯一动作：只执行性能和稳定性回归，不为追逐单一峰值临时调参。
- 验证：每档设备至少运行 3 次并计算 P50/P95；记录异常样本、崩溃和恢复结果。
- 通过标准：结果字段完整、异常样本保留原因；未达门禁时返回对应软件步骤修复后重新构建候选 APK。

#### S045 | 生成最终真机验收归档和报告

- 依赖：S040、S043、S044 DONE。
- 目标：将候选 APK/AAB、SO、符号表、SBOM、软件门禁和真机验收结果归档为最终发布报告。
- 允许修改：`docs/release/`、`artifacts/manifest.json`、`docs/gates/g6-device-acceptance.md`。
- 唯一动作：只生成最终归档索引和验收报告，不再修改功能代码。
- 验证：按索引逐个计算哈希并确认文件存在；核对三档设备记录、端到端证据和性能结果。
- 通过标准：归档可由另一名工程师复核，G0-G6 全部 PASS。

---

## 5. AI 回复和日志验收清单

每次 AI 回复前必须逐项确认：

- [ ] 本轮只有一个 `PHASE_ID`。
- [ ] 已读取步骤文件和日志末尾。
- [ ] 已检查 `git status --short`。
- [ ] 修改文件都在当前阶段允许范围内。
- [ ] 验证命令已经真实执行并记录结果。
- [ ] 日志已经追加 `DONE`、`IN_PROGRESS` 或 `BLOCKED`。
- [ ] 没有执行下一阶段。
- [ ] 回复中明确写出下一阶段依赖，但没有声称下一阶段已完成。

---

---

# 附录：技术参考正文

以下正文只作为步骤实现时的技术参考，不改变单步执行边界。

# Android 端侧本地 AI 与智能模型市场完整制作方案
Java / Android 原生体系 | GitHub 调研更新版 | 版本 2.0 | 2026-08-22

> 本方案根据 GitHub 上 Google AI Edge Gallery、MNN Chat、PocketPal AI、SmolChat-Android 和 llama.cpp Android 示例的实际能力重新收敛。目标是先交付稳定的 ARM64 本地对话和模型市场 MVP，再用实测数据逐步打开 GPU、更多模型和多模态能力。

## 0. 先给结论

建议采用“成熟项目参考 + 自研产品层 + 单一稳定推理底座”的组合，而不是直接 Fork 一个项目：

| 决策项 | 首版选择 | 选择理由 |
| --- | --- | --- |
| 产品交互 | 参考 Google AI Edge Gallery、MNN Chat、PocketPal AI | 这三个项目已经验证模型浏览、下载、管理、Benchmark 和本地聊天的用户流程 |
| Android 工程 | Java + XML + AndroidX + Room | 保留原定 Java 原生方向；可以阅读 Kotlin 示例，但不把 Kotlin 作为业务强制依赖 |
| 推理底座 | llama.cpp，固定提交版本 | GGUF 生态成熟，官方有 Android 示例，CPU/NEON 可作为稳定基线 |
| 模型格式 | GGUF | 与 llama.cpp、SmolChat、PocketPal 的模型生态一致，首版不做多格式适配 |
| 首版硬件 | `arm64-v8a`，CPU/NEON 必须可用 | 先保证跨品牌可运行，不把 Vulkan、OpenCL 或 NPU 当作硬依赖 |
| GPU 策略 | 通过后端适配器延后接入 Vulkan/MLC/MNN | 能力探测不等于性能收益，必须先通过真机 Benchmark |
| 模型市场 | Manifest、签名、版本、下载、校验、回滚自研 | GitHub 项目通常只解决其中一部分，不能直接当生产供应链 |
| 目标周期 | 18 周生产版；6～8 周单模型 MVP | 以 2 Android + 1 NDK + 1 后端 + 1 QA 的团队规模估算 |

这意味着第一版不承诺“全机型、全 GPU、所有模型、零 OOM”。第一版的成功标准是：模型来源可信、下载可恢复、对话能停止、Native 崩溃可隔离、设备不适配时能解释原因。

## 1. GitHub 参考项目与使用边界

### 1.1 最接近的项目

| 项目 | 主要能力 | 可直接借鉴 | 不应直接照搬 |
| --- | --- | --- | --- |
| [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) | Android 端侧模型体验、模型管理、Benchmark、Hugging Face 集成、离线对话 | 模型市场信息架构、下载/模型管理入口、Benchmark 展示和用户流程 | 其 Kotlin、LiteRT/Google AI Edge 模型路径；本地构建还需要 Hugging Face OAuth 配置 |
| [MNN Chat](https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat) | Android 本地聊天、模型浏览下载、ModelScope、多模态 | 中文模型分发、模型切换、聊天历史、下载入口 | README 说明主要在高端机测试，不能直接当作全机型稳定性证明；其 MNN 后端要单独评估 |
| [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) | GGUF、Hugging Face 下载、Benchmark、CPU/GPU/NPU 回退、工具和语音 | 模型详情、硬件基准、性能展示、离线产品体验 | React Native + Native Bridge，不是 Java 工程模板；模型/助手社区服务也不等于你的模型市场 |
| [SmolChat-Android](https://github.com/shubham0204/SmolChat-Android) | Android、GGUF、llama.cpp 子模块、JNI、模型增删、参数配置 | Android/JNI 最小骨架、模型文件管理、C++ 调用方式 | 规模较小，缺少签名 Manifest、多镜像下载、回滚和生产级隔离 |
| [llama.cpp Android 示例](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android) | 官方 Android 绑定、GGUF 元数据、流式 Token、CPU 优化 | Native 构建、GGUF 解析、推理 API 适配、Android 真机验证 | 它是底层示例，不提供完整市场、用户账户、供应链和运营能力 |

### 1.2 可选的后端参考

| 项目 | 用途 | 当前定位 | 首版处理 |
| --- | --- | --- | --- |
| [MLC LLM](https://github.com/mlc-ai/mlc-llm) | 编译型高性能运行时、Android OpenCL | 适合后续追求 GPU 性能，但有模型转换和编译流程 | 保留 `InferenceBackend` 接口，第二阶段用 Benchmark 决定是否接入 |
| [MNN](https://github.com/alibaba/MNN) | 移动端优化推理和多模态 | Android 产品化程度较高，但引擎接入和模型适配边界更复杂 | 只作为性能对照和多模态后续候选 |
| [ExecuTorch](https://github.com/pytorch/executorch) | PyTorch 端侧运行时，提供 Java/Kotlin API | LLM Android API 仍带 experimental 属性，模型通常为 `.pte` | 不作为 GGUF MVP 底座 |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe) | Google 端侧 AI API | 适合 Google 支持的模型和任务，不是通用 GGUF 市场 | 仅在未来做 Gemma/特定任务时评估 |
| [ChatterUI](https://github.com/Vali-98/ChatterUI) | 本地/远程聊天前端、GGUF 导入 | 产品交互有参考价值 | AGPL-3.0；闭源商业产品不得直接复制其核心代码 |

### 1.3 许可证和来源规则

- GitHub 仓库许可证只约束仓库代码，不能替代模型本身的许可证。
- llama.cpp、PocketPal、Google Gallery、SmolChat、MLC 等代码许可证不同，合并代码前要保留 NOTICE 和许可证文件。
- 模型的权重、Tokenizer、Chat Template、数据集和图片/音频组件可能分别有许可证，必须在模型详情页展示。
- 对 AGPL-3.0 项目只能参考交互和公开文档，不直接复制实现到闭源商业版本。
- 每一个进入市场的模型都要有来源 URL、版本、哈希、许可证和下架/撤销记录。

## 2. 产品定义与首版范围

### 2.1 用户价值

用户不需要理解 NDK、量化、GPU offload 或上下文窗口，就可以在手机上完成：发现模型、判断是否适配、下载、离线对话、查看实际速度和删除模型。

### 2.2 MVP 功能清单

| 模块 | MVP 必须有 | 生产版再增强 |
| --- | --- | --- |
| 模型市场 | 列表、搜索、任务/语言/参数量筛选、详情页 | 个性化推荐、评论、灰度、专题和社区模型 |
| 模型下载 | 单文件 GGUF、暂停、恢复、断点、进度、哈希校验 | 多镜像测速、并发分片、带宽策略、差分更新 |
| 本地推理 | 1～3 个已验证的 1.5B～3B 指令模型、流式输出、停止 | 多模型常驻、工具调用、多模态、RAG |
| 设备适配 | 内存、存储、ABI、CPU、热状态、短 Benchmark | 更完整的 SoC 数据库、个性化功耗策略 |
| 会话 | 新建、历史、删除、上下文裁剪、模型切换 | 云端同步、导入导出、角色模板 |
| 诊断 | TTFT、输出 TPS、峰值内存、错误码 | 匿名聚合排行、远程配置和 A/B 测试 |

### 2.3 明确不承诺

- Vulkan/OpenCL/NPU 在所有设备上都能成功初始化或一定更快。
- 固定的 TTFT/TPS 数字可以适用于所有输入、温度和设备状态。
- “内存溢出率 0%”或“退后台绝不停止”等无法由应用完全控制的绝对承诺。
- 任意 Hugging Face 文件都能安全加载；只允许签名 Manifest 中声明的资产。

## 3. 总体架构

```text
Android UI 进程
  +-- 市场页 / 模型详情 / 下载管理 / 对话页 / 设置页
  +-- ViewModel + Repository + Room
  +-- DeviceProfiler + CompatibilityEngine
  +-- DownloadCoordinator + StorageManager
  +-- InferenceClient (AIDL/Binder)
          |
          v
独立推理进程 InferenceService
  +-- SessionStateMachine
  +-- JNI 安全包装层
  +-- llama.cpp 固定版本适配器
  +-- CPU/NEON 后端；可选 Vulkan/其他 Backend

云端控制面
  +-- Catalog API / Manifest Schema
  +-- Ed25519 签名和密钥轮换
  +-- 对象存储 + CDN + 镜像
  +-- 撤销列表 / 灰度配置 / 可选匿名指标
```

### 3.1 Android 模块拆分

| 模块 | 输入 | 输出 | 关键接口 |
| --- | --- | --- | --- |
| `feature-market` | 目录 API、适配结果 | 列表和详情 UI | `ModelCatalogRepository` |
| `feature-download` | Manifest、下载 URL | 任务状态和安装模型 | `DownloadCoordinator` |
| `feature-chat` | 会话、推理事件 | 流式消息 UI | `ConversationViewModel` |
| `core-device` | Android Framework、Native 探针 | `DeviceProfile` | `DeviceProfiler` |
| `core-compatibility` | DeviceProfile、ModelManifest、Benchmark | `CompatibilityResult` | `CompatibilityEngine` |
| `core-inference` | 推理请求、Binder 事件 | 句柄状态和 Token 批次 | `InferenceClient` |
| `data-room` | 本地实体 | 事务读写 | `ModelDao`、`DownloadDao`、`ChatDao` |
| `native/ai_jni` | 文件路径/FD、推理配置 | Token、统计、错误码 | `NativeSession` |

模块边界的原则是：UI 不直接接触 JNI，下载器不决定模型质量，推理进程不访问网络，Native 层不持有业务数据库。

## 4. 工程初始化和可复现构建

### 4.1 环境固定

| 项目 | 首版固定值 |
| --- | --- |
| Android | Android Studio、Gradle Wrapper、最低 API 26；发布目标按当期商店要求 |
| ABI | `arm64-v8a`；x86_64 仅用于模拟器或专项测试，不作为首版发布 ABI |
| NDK/CMake | 通过 `gradle.properties` 或版本目录固定；禁止开发者机器自行漂移 |
| Native 引擎 | 记录 llama.cpp commit、ggml 依赖、编译参数和 patch 列表 |
| 构建产物 | AAR/SO、符号表、Manifest Schema、NOTICE、SBOM 一起归档 |
| 页面大小 | 验证 16 KB page size 设备安装和 Native 加载 |

### 4.2 推荐项目目录

```text
local-ai/
  app/
    src/main/java/com/example/localai/
      feature/market/
      feature/download/
      feature/chat/
      core/device/
      core/compatibility/
      core/inference/
      data/room/
      data/network/
      data/storage/
    src/main/aidl/com/example/localai/IInferenceService.aidl
    src/main/cpp/ai_jni/
    src/main/cpp/engine_llama/
    src/main/cpp/benchmark/
  backend/
    catalog-api/
    manifest-schema/
    signing/
  qa/
    device-matrix/
    network-fixtures/
    fault-injection/
  docs/
    model-onboarding.md
    release-gate.md
    license-policy.md
```

### 4.3 参考项目落地方式

1. 新建自己的 Android 空工程。
2. 阅读并记录 `SmolChat-Android` 的 JNI 和 C++ 调用边界，不直接复制其 UI 数据层。
3. 按 `llama.cpp/examples/llama.android` 的官方文档完成一次本机构建和自动化加载/生成冒烟。
4. 将模型文件访问、会话状态和错误码重新封装为自己的 `InferenceSession` 接口。
5. 完成软件闭环并产出候选 APK 后，再收集 3 台真机的加载、停止、释放、重启和峰值内存基线。

## 5. 模型资产、Manifest 和供应链

### 5.1 Manifest 最小结构

```json
{
  "modelId": "qwen2.5-1.5b-instruct",
  "version": "2026.08.1",
  "displayName": "Qwen 2.5 1.5B Instruct",
  "source": {"publisher": "Qwen", "url": "https://huggingface.co/..."},
  "license": {"spdx": "Apache-2.0", "url": "https://..."},
  "format": "gguf",
  "architecture": "qwen2",
  "parameterCount": 1540000000,
  "quantization": "Q4_K_M",
  "tokenizer": "embedded-or-declared",
  "chatTemplate": "chatml",
  "contextLength": 32768,
  "files": [{
    "name": "model-q4_k_m.gguf",
    "sizeBytes": 1100000000,
    "sha256": "...",
    "urls": ["https://cdn.example.com/model"],
    "etag": "..."
  }],
  "runtime": {
    "minAndroidApi": 26,
    "abis": ["arm64-v8a"],
    "backends": ["cpu", "vulkan"],
    "kvCacheTypes": ["f16", "q8_0"]
  },
  "signature": {"algorithm": "Ed25519", "keyId": "release-2026-01", "value": "..."}
}
```

### 5.2 上架流程

1. 运营或模型工程师提交模型来源、许可证、Chat Template、Tokenizer、量化方式和已知限制。
2. 服务端检查文件来源、格式、大小和安全范围，生成不可变版本号。
3. CI 下载文件并计算 SHA-256，执行 GGUF 头部和元数据解析，短时加载和生成。
4. 人工审查许可证和内容描述，服务端签名 Manifest。
5. 发布到灰度目录，客户端按 ABI、最低 API 和硬约束过滤。
6. 出现安全、版权或运行问题时，加入撤销列表，客户端禁止新安装并保留旧版本可删除状态。

### 5.3 真实性和完整性

- SHA-256 只能证明下载内容没有被意外破坏，不能证明来源可信。
- 客户端内置可信公钥，验证 Manifest 的 Ed25519 签名和 `keyId`。
- 下载地址使用短期签名 URL，不能由客户端拼接任意远程路径。
- 校验顺序：Manifest 签名 -> 文件大小 -> `Content-Range`/ETag -> SHA-256 -> GGUF 元数据 -> 短时加载。

## 6. 下载、安装和本地文件管理

### 6.1 状态机

```text
CREATED -> PROBING -> DOWNLOADING -> VERIFYING -> INSTALLING -> READY
                         |              |
                    PAUSED/FAILED   VERIFY_FAILED
```

Room 的下载任务至少保存：`taskId、modelId、version、fileName、bytesDownloaded、totalBytes、etag、sha256、state、retryCount、lastError`。

### 6.2 断点续传规则

- 恢复时发送 `Range: bytes=offset-` 和 `If-Range: ETag`。
- 必须验证服务端返回 `206`、正确的 `Content-Range`、总长度和 ETag。
- 如果返回 `200`、ETag 变化、版本变化或本地临时文件大小异常，安全地重新开始，不能直接拼接新旧内容。
- 切换镜像前必须确认镜像返回同一版本和同一 SHA-256。
- 下载、校验、解压和安装都使用固定大小缓冲区，不把 GB 级文件读入 JVM 数组。
- 校验通过后在同一文件系统内原子重命名，保留 `install.ok` 和已验证的 Manifest。

### 6.3 Android 后台任务

使用 WorkManager 负责可恢复任务；按目标 API 版本和用户是否主动发起选择前台服务或用户发起的数据传输任务。正确处理通知权限、Doze、网络类型变化、系统任务配额和应用进程被杀。产品文案应说“可恢复”，不应承诺“退后台绝不中断”。

### 6.4 文件布局

```text
files/models/{modelId}/{version}/
  manifest.json
  manifest.sig
  model-q4_k_m.gguf
  install.ok
files/downloads/{taskId}/
  model.part
  state.json
```

更新时先安装新版本并执行短时加载/生成探测，成功后再标记旧版本可删除；失败则保留旧版本继续使用。

## 7. 推理服务、JNI 和进程隔离

### 7.1 为什么使用独立推理进程

llama.cpp 和模型解析属于 Native 代码，单次崩溃可能结束整个进程。将 `InferenceService` 放在独立进程，可以把 Native 崩溃的影响限制在推理服务，UI、下载状态和历史数据库仍然可恢复。

推理进程不申请网络权限；模型市场和下载模块仍在 UI/业务进程联网，所以产品文案应表述为“推理不主动联网”，而不是“应用完全断网”。

### 7.2 Java/AIDL 接口

```java
interface InferenceSession extends AutoCloseable {
    void start(InferenceRequest request, InferenceListener listener);
    void stop();
    InferenceStats getStats();
    @Override void close();
}
```

`InferenceRequest` 需要包含：`modelId、version、contextLength、threadCount、gpuLayers、temperature、topP、maxNewTokens、requestId`。Binder/JNI 回调按小批量或固定时间窗口发送文本片段，避免每个 Token 都跨一次 JNI 和 IPC。

### 7.3 Native 生命周期

```text
CREATED -> LOADING -> READY -> RUNNING -> STOPPING -> RELEASED
```

- Native 句柄通过句柄表管理，不让 Java 直接操作裸指针语义。
- `stop()` 只设置原子取消标志；调用方必须等待 Native 工作线程确认退出。
- `release()` 前必须解除回调、等待线程退出，防止回调访问已销毁对象。
- 一个会话首版只允许一个并发生成任务。
- C++ 异常不能穿过 JNI；统一转换为结构化错误码和脱敏消息。
- UI 进程收到 `ENGINE_CRASHED` 后清理失效会话，允许重启服务或切换模型。

## 8. 设备画像、内存和适配算法

### 8.1 设备画像字段

| 类别 | 采集内容 | 采集方式 | 注意事项 |
| --- | --- | --- | --- |
| 内存 | 总内存、可用内存、memoryClass、进程 PSS/RSS | ActivityManager、Debug.MemoryInfo、Native allocator | `availMem` 不是当前进程可直接申请的精确上限 |
| CPU | ABI、核心数、NEON、可用线程数 | Build、Runtime、系统节点 | 大核数量不能直接等于最佳线程数 |
| GPU | Vulkan 特性、扩展、初始化结果 | PackageManager + Native 查询 | 版本支持不等于模型后端可用 |
| 温控 | Thermal Status、电量、充电、省电状态 | PowerManager、BatteryManager | 只能作为系统等级信号 |
| 存储 | 可用空间、临时空间、文件系统 | StatFs 和原子替换探测 | 要同时容纳 `.part`、最终文件和旧版本 |

### 8.2 内存预算方法

不能使用单一的 `(availMem - threshold) * 0.8` 作为精确结论。建议使用：

1. 记录 UI/业务进程的 `baselinePss`。
2. 用应用 `memoryClass`、设备可用内存和后台状态生成候选上限。
3. 按模型权重、KV Cache、计算图、后端 workspace 和 App 增量估算峰值。
4. 首次加载执行短 Benchmark，记录峰值 RSS/PSS，后续优先使用本机实测值。
5. 推理期间监控 Native allocator、RSS/PSS 和引擎错误码，分级降级或熔断。

KV Cache 估算可以从下面的近似开始，但必须以具体引擎和模型元数据校准：

```text
KV 字节数 ≈ 2 × 层数 × KV 头数 × 头维度 × 上下文长度 × 每元素字节数
峰值估算 = 权重映射 + KV Cache + 计算图/临时张量 + 后端 workspace + App 增量
```

### 8.3 适配输出

| 状态 | 进入条件 | 默认配置 |
| --- | --- | --- |
| 推荐 | 硬约束满足，短 Benchmark 成功，内存和温控余量充足 | 自动配置；展示实测 TTFT/TPS |
| 可运行 | 需要缩短上下文、降低输出或关闭部分后端 | 自动平衡配置，并解释降级原因 |
| 高负载 | 可加载但压力测试或实时资源进入风险区间 | 限制上下文和并发，明确提示可能变慢/发热 |
| 不支持 | ABI/API/存储/最低内存等硬约束不满足 | 禁止加载，显示可操作的缺失项 |

建议先用可解释规则引擎，不急于称为机器学习推荐：

```text
硬约束过滤 -> 读取本机 Benchmark -> 计算速度/余量/温控/语言匹配分数
            -> 生成推理配置 -> 运行时观测 -> 更新本机 profile
```

## 9. 模型市场和客户端页面

### 9.1 页面结构

1. **市场首页**：推荐模型、最近更新、按任务/语言/参数量/体积筛选。
2. **模型详情**：用途、样例、模型来源、许可证、量化方式、Chat Template、上下文上限、文件大小、适配等级和实测性能。
3. **下载管理**：任务状态、暂停/恢复、校验中、失败原因、重试和删除。
4. **对话页**：当前模型、上下文/输出控制、流式 Token、停止、重试、复制、历史会话。
5. **设备诊断**：当前内存、温控、后端、Benchmark 结果和“为什么不支持”。
6. **设置页**：自动/均衡/省电模式，模型缓存，隐私、日志、会话删除和许可证入口。

### 9.2 关键状态必须可见

- 下载中：速度、剩余大小、暂停和取消。
- 校验失败：网络重试、版本变化或文件损坏的具体原因。
- 模型不可用：显示缺少的 ABI、磁盘、内存或 Android API，而不是只显示“失败”。
- 推理降级：告诉用户是降低上下文、关闭 GPU 还是进入省电模式。
- 推理进程崩溃：允许一键重启和切换已安装模型。

## 10. 云端控制面

### 10.1 API

| API | 用途 | 要求 |
| --- | --- | --- |
| `GET /v1/models` | 分页目录 | 支持版本、语言、任务、参数量和最低配置筛选 |
| `GET /v1/models/{id}` | 详情和 Manifest | 返回签名、许可证、Chat Template、文件和运行约束 |
| `GET /v1/models/{id}/download-ticket` | 短期下载地址 | 按文件、版本、区域签发；客户端不自由拼 URL |
| `GET /v1/revocations` | 撤销列表 | 处理安全漏洞、版权和运行问题 |
| `POST /v1/telemetry` | 可选匿名指标 | 默认关闭或明确同意；不上传 Prompt/回复原文 |

### 10.2 最小后端能力

- 模型版本不可变，禁止覆盖同一版本的文件。
- Manifest 签名和密钥轮换可审计。
- CDN/镜像返回一致的版本、长度、ETag 和 SHA-256。
- 有模型下架、撤销、灰度和回滚机制。
- 记录下载失败率、校验失败率、加载失败率和设备档位，但脱敏并可按用户选择关闭。

## 11. 测试与性能验收

### 11.1 测试矩阵

| 档位 | 设备样本 | 模型策略 | 必测内容 |
| --- | --- | --- | --- |
| 旗舰 | Snapdragon 8 Gen 2/3、Dimensity 9300，12 GB 以上 | 1.5B/3B，CPU 与后端对比 | GPU 初始化、持续温控、冷启动、长上下文 |
| 中端 | Snapdragon 7+ Gen 2、Dimensity 8200，8 GB | 1.5B，自动上下文 | 内存余量、后台恢复、UI 掉帧、模型切换 |
| 入门 | Snapdragon 695、Dimensity 700，6 GB | 1.3B～1.5B，CPU 优先 | 低内存、低电量、长对话、熔断和重启 |
| 特殊 | 非 arm64、低 API、16 KB page size 设备 | 按硬约束禁用或单独适配 | 安装、Native 加载、提示文案 |

### 11.2 性能口径

每条性能数据都必须记录：机型、Android 版本、模型版本、量化、上下文长度、输入/输出 Token 数、采样参数、温度、电量、是否充电、冷/热启动和 P50/P95。

| 指标 | MVP 门槛 | 生产版门槛 |
| --- | --- | --- |
| 加载 | 目标设备至少完成 1.5B 模型加载和单轮生成 | 失败有明确错误和回退，不阻塞 UI |
| TTFT/TPS | 候选 APK 产出后完成三档设备基线记录 | 不能比基线回退超过约定阈值；持续 30 分钟记录温控 |
| 内存 | 压测中不出现不可恢复 OOM | 峰值内存、熔断和重启结果进入发布门禁 |
| 下载 | 断网/强杀后可恢复，哈希正确 | ETag、206、镜像切换、旧版本回滚全部通过 |
| 稳定性 | 长对话、停止、切换和进程重启通过 | crash-free session >= 99.9%，问题可追踪到错误码 |
| 隐私 | 抓包/日志不出现对话原文 | 通过隐私、开源组件、模型许可证和供应链审查 |

### 11.3 故障注入

- HTTP 返回 `200`、错误 `206`、错误 `Content-Range`、ETag 变化、半文件和 CDN 超时。
- App 在下载、校验、安装、加载、生成和释放的每个状态被系统强杀。
- Native 线程生成中调用停止、重复停止、重复释放和进程崩溃。
- 人为降低可用内存、提高温控等级、切换充电状态和省电模式。
- Manifest 签名错误、哈希错误、许可证缺失、模型头部异常和超限元数据。

## 12. 研发排期与交付物

| 阶段 | 周期 | 主要任务 | 退出标准 |
| --- | --- | --- | --- |
| 0. 软件技术验证 | W1～W2 | 锁定 llama.cpp、模型、ABI、最低 API；完成本机构建、自动化 Native 冒烟和 Debug APK | 构建成功，自动化加载、生成、停止、释放通过；不要求真机 |
| 1. 推理服务 | W3～W5 | JNI 句柄表、状态机、AIDL、独立进程、CPU 基线 | Native 崩溃不拖垮 UI；重复释放和停止有测试 |
| 2. Manifest/下载 | W4～W8 | Schema、签名、CDN、Range、ETag、校验、Room、安装/删除 | 弱网、断网、强杀、镜像切换均可恢复或安全失败 |
| 3. 设备适配 | W6～W9 | DeviceProfiler、规则引擎、短 Benchmark、模型详情适配信息 | 每个推荐/不支持结果都有可解释原因 |
| 4. 市场与会话 | W8～W12 | 列表、详情、搜索、下载管理、对话、历史和上下文裁剪 | 端到端完成“发现-下载-安装-聊天-删除”闭环 |
| 5. 稳定性/安全 | W10～W14 | 熔断、温控、进程重启、日志脱敏、签名和许可证审计 | 故障注入、抓包、压力和供应链检查通过 |
| 6. 机型回归/发布 | W15～W18 | 产出候选 APK 后进行真机信息采集、安装、性能回归、灰度、崩溃修复、上架材料和模型回滚演练 | 三档真机验收和发布门禁达标，能撤销模型和回滚客户端 |

### 12.1 团队配置

| 角色 | 人数 | 责任 |
| --- | ---: | --- |
| Android 工程师 | 2 | UI、Room、下载、后台任务、Binder 客户端 |
| NDK/推理工程师 | 1 | llama.cpp、JNI、内存、后端、benchmark、崩溃隔离 |
| 后端工程师 | 1 | Manifest、签名、CDN、目录、撤销和指标 |
| QA/兼容性 | 1 | 设备矩阵、弱网、低内存、热控、故障注入和回归 |
| 产品/设计/合规 | 兼职 | 范围、模型清单、隐私、许可证和发布审查 |

## 13. 前十天启动清单

### 第 1 天

- 建立项目仓库、许可证清单和第三方依赖清单。
- 固定 Android、NDK、CMake、llama.cpp commit 和首批模型。
- 准备旗舰、中端、入门三台真实设备。

### 第 2～3 天

- 运行官方 `llama.android` 或 SmolChat 参考工程。
- 完成 Java 可调用的 `load/generate/stop/release` 最小接口。
- 记录加载时间、TTFT、output tokens/s、峰值 RSS/PSS。

### 第 4～5 天

- 定义 Manifest JSON Schema、签名格式、模型状态和错误码。
- 建立 Room 下载任务表和状态机。
- 用本地 HTTP Fixture 模拟 Range、ETag 变化、断网和错误重试。

### 第 6～7 天

- 完成单模型下载、SHA-256、签名校验、原子安装和删除。
- 实现进程被杀后从数据库恢复任务。

### 第 8～10 天

- 完成市场列表、详情和单轮对话。
- 加入设备适配原因、停止生成和推理进程重启。
- 召开 PoC 评审；只要 Native 构建、内存、许可证或模型模板任一项失败，立即缩小范围，不进入大规模 UI 开发。

## 14. 发布门禁

- 代码依赖、模型权重、Tokenizer 和模板许可证已登记并可展示。
- Manifest 签名、SHA-256、GGUF 解析和撤销列表测试通过。
- 下载在断网、退后台、强杀和 ETag 变化后不会产生错误拼接文件。
- 推理支持停止、释放、重启、切换模型，Native 崩溃不会拖垮 UI。
- 低内存和高温状态有可观察的降级或熔断结果。
- 候选 APK 产出后，三档真实设备完成固定条件下的 TTFT、TPS、峰值内存和温控基线。
- 日志、抓包和数据库检查不包含 Prompt、回复原文、Authorization 或完整设备标识。
- Android 目标 API、前台任务、通知权限、隐私政策、开源许可证和模型许可证审查通过。
- 客户端和模型都具备灰度、回滚、撤销和问题定位机制。

## 15. 主要风险和处理

| 风险 | 影响 | 处理方案 |
| --- | --- | --- |
| Vulkan 驱动不稳定 | 加速失败、崩溃或速度倒退 | Benchmark 后启用；失败回退 CPU；按设备/驱动缓存黑名单 |
| 内存估算偏差 | OOM 或系统杀进程 | 实测峰值、分级降级、独立进程和小模型回退 |
| 后台任务被系统限制 | 下载中断 | 状态机可恢复，按 API 版本选择任务机制，不承诺永不停止 |
| 模型被替换或来源不明 | 供应链/版权风险 | Manifest 签名、短期 URL、许可证审查和撤销列表 |
| JNI 竞态 | UAF、死锁、重复释放 | 句柄表、状态机、回调解除和释放前等待线程 |
| Chat Template 不匹配 | 输出质量差、Token 浪费 | Manifest 强制声明模板和 Tokenizer，加载前校验 |
| 项目范围过大 | 延期和质量下降 | 首版砍掉多模态、NPU、社区市场、个性化推荐和多格式 |
| 参考项目许可证冲突 | 商业发布受限 | 只借鉴协议允许的代码；保留 NOTICE；模型许可证单独审查 |

## 16. 最终技术路线

```text
第一阶段：Java/XML + Room + OkHttp + WorkManager
          + arm64-v8a + GGUF + llama.cpp CPU/NEON
          + 独立推理进程 + 签名 Manifest + 可恢复下载

第二阶段：真实设备 Benchmark 驱动 Vulkan/MLC/MNN 后端适配
          + 多镜像 + 设备策略缓存 + 模型灰度和撤销

第三阶段：多模态、工具调用、RAG、NPU 和个性化推荐
          + 仅在每一项都有独立性能/稳定性/许可证验证后加入
```

最终建议是：**先把“模型可信、能下载、能加载、能停止、能恢复、能解释失败”做好，再追求更快的 TPS 和更多模型。** GitHub 上的成熟项目已经证明了产品形态，但没有一个项目可以直接替你解决 Java 原生、模型供应链、全机型适配和生产级稳定性；这些仍然需要在自己的产品层完成。

## 附录 A：参考仓库清单

1. Google AI Edge Gallery：<https://github.com/google-ai-edge/gallery>
2. MNN Chat：<https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat>
3. PocketPal AI：<https://github.com/a-ghorbani/pocketpal-ai>
4. SmolChat-Android：<https://github.com/shubham0204/SmolChat-Android>
5. llama.cpp Android：<https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android>
6. llama.cpp Android 文档：<https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md>
7. MLC LLM：<https://github.com/mlc-ai/mlc-llm>
8. ExecuTorch：<https://github.com/pytorch/executorch>
9. MediaPipe：<https://github.com/google-ai-edge/mediapipe>
10. ChatterUI：<https://github.com/Vali-98/ChatterUI>
