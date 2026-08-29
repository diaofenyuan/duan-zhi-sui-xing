# Java → Kotlin 迁移执行计划

> 版本：2026-08-28 · 状态：**已完成（K0-K5 全部 DONE，2026-08-29）**
> 本文件是主执行任务文档，阶段卡 `K0-K5` 为唯一执行单位，与 `docs/ai-execution-plan.md` 的 `P1-P6` 阶段卡并列。
> 每完成一个阶段，必须在 `docs/ai-work-log.md` 追加一条 `MIGRATION-K` 记录（IN_PROGRESS / DONE 各一条），规则沿用现有日志协议。

---

## 1. 目标与边界

**目标**：把 Android 应用模块（`app/`）的主开发语言由 Java 改为 Kotlin，构建、测试、UI 行为与迁移前完全一致。

**不做的事**（明确边界，避免范围蔓延）：

1. 不改动 `app/src/main/cpp/`（`native_session.cpp`、`engine_llama/`）——C++/JNI 层与语言迁移无关。
2. 不迁移 `backend/`——它是独立 JDK 17 工具（签名 CLI + Fixture 服务器），与 Android 模块零共享代码，迁移无产品收益。
3. 不升级 AGP / Gradle / compileSdk——工具链版本全部沿用 `docs/build-baseline.md`。
4. 不在本次引入 ViewBinding、Compose、协程、Flow 等新范式——先做语义等价的 1:1 迁移，范式改造另立变更。

---

## 2. 决策记录

| # | 决策 | 结论 | 理由 | 可调整性 |
| --- | --- | --- | --- | --- |
| D1 | Room 注解类是否迁移 | **保持 Java** | 缓存中缺 KSP 的 `symbol-processing-gradle-plugin`，kapt 的 `kotlin-annotation-processing-embeddable` 亦不在缓存；保持 Java 可继续用 `annotationProcessor`，零注解处理器改动 | 联网补齐 KSP 依赖后可推翻，见第 7 节技术债 |
| D2 | `backend/` 是否迁移 | **不迁移** | 独立 JDK 工具，与 app 无共享代码 | 可调整 |
| D3 | K4 是否同步引入 ViewBinding | **否** | 15 个文件用 `findViewById`，先 1:1 迁移保持 diff 可读；ViewBinding 另立变更 | 可调整 |
| D4 | 迁移粒度 | **文件级逐一替换** | 每个文件一次原子提交，可独立 review 与回退 | 可调整 |
| D5 | JNI / Parcelable 边界 | **保持 Java** | `NativeSession` 有 8 个 `native` 方法；`InferenceRequest`/`InferenceStats` 是 AIDL 对接的 Parcelable，`kotlin-parcelize-runtime` 缓存版本（1.9.22）与 KGP 2.2.20 不匹配 | 联网后可推翻 |

---

## 3. 可行性验证（已实测，2026-08-28）

迁移前做了可逆探针（临时改构建脚本 + 加 `.kt` 文件，验证后 `git checkout --` 全量还原，工作树已确认干净）。

| 验证项 | 结果 |
| --- | --- |
| Kotlin 2.2.20 离线解析 | `PASS` |
| Java ↔ Kotlin 双向互操作 | `PASS` |
| `src/test/kotlin` + Robolectric | `PASS` |
| Room `annotationProcessor` 与 Kotlin 共存 | `PASS` |
| `assembleDebug` + 全量测试 | `PASS`（104 个测试，0 失败） |

**关键机制**：Kotlin Gradle Plugin 2.2.20 声明的 Gradle 变体最高到 `gradle813`，本地缓存中正好存在 `kotlin-gradle-plugin-2.2.20-gradle813.jar`；Gradle 按 `org.gradle.plugin.api-version` 就近匹配，本项目锁定的 **Gradle 8.14 可离线命中该变体**。

**复现命令**（探针内容见第 8 节）：

```text
./gradlew :app:compileDebugKotlin --offline --console=plain
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

---

## 4. 工具链变更（K1 落地内容）

| 文件 | 变更 |
| --- | --- |
| `gradle/libs.versions.toml` | `[versions]` 增 `kotlin = "2.2.20"`；`[plugins]` 增 `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }` |
| `build.gradle`（根） | 增 `alias(libs.plugins.kotlin.android) apply false` |
| `app/build.gradle` | 增 `alias(libs.plugins.kotlin.android)`；增 `kotlinOptions { jvmTarget = "17" }` |
| 源码目录 | 新增 `src/main/kotlin`、`src/test/kotlin`、`src/androidTest/kotlin`（KGP 自动注册）；Java 文件保留在 `src/main/java`，两者共存 |

**约束**：`jvmTarget` 必须与 `compileOptions` 的 Java 17 一致，避免字节码级别错配。

---

## 5. 保留 Java 红线清单

以下文件**永久保持 Java**，任何阶段不得迁移：

| 路径 | 规模 | 保留理由 |
| --- | --- | --- |
| `data/room/` 全部 10 文件 | 409 行 | Room 注解类（D1） |
| `core/inference/NativeSession.java` | 174 行 | JNI 边界，8 个 `native` 方法（D5） |
| `core/inference/InferenceRequest.java` | 77 行 | AIDL Parcelable（D5） |
| `core/inference/InferenceStats.java` | 63 行 | AIDL Parcelable（D5） |
| `app/src/main/cpp/` | — | C++ / JNI 实现 |
| `backend/` | — | 独立 JDK 工具（D2） |

**合计保留约 723 行。**

---

## 6. 阶段卡

### 前置门禁 | G-P4 | 必须先通过

- **为什么存在**：当前 `P4` 处于"代码已落地、验收未闭环"状态——没有 `connectedDebugAndroidTest` 记录，也没有"发现→详情→下载→安装→聊天→停止→历史→删除"的端到端证据。**在未验收的代码上做语言迁移，回归会被迁移噪声掩盖，无法归因。**
- 目标：补齐 P4 验收，建立迁移前的绿色基线。
- 允许修改：`app/src/androidTest/`、`docs/ai-work-log.md`、`qa/`。
- 验证：
  - `./gradlew :app:connectedDebugAndroidTest --offline --console=plain`（需启动模拟器）
  - 模拟器端到端：发现 → 详情 → 下载 → 安装 → 聊天 → 停止 → 历史 → 删除
- 通过标准：仪器化测试全绿，端到端主流程可完成，P4 在 `docs/ai-work-log.md` 有完整 IN_PROGRESS/DONE 记录。
- **豁免**：用户可显式授权跳过，但必须在日志中记录豁免决定与承担的风险。

---

### K0 | 基线变更与决策落地

- 依赖：无。
- 目标：把语言变更写入基线文档，完成许可证登记，建立迁移日志约定。
- 允许修改：`docs/build-baseline.md`、`docs/license-policy.md`、`NOTICE`、`docs/kotlin-migration-plan.md`（本文件）、`docs/ai-work-log.md`。
- 交付结果：
  - `docs/build-baseline.md`：语言项由「纯 Java（无 Kotlin 插件）」改为「**Kotlin 为主 + Java 互操作保留区**」；补充说明——原「选 AGP 8.13.2 而非 9.x 是为了避开内置 Kotlin」的理由**因本次迁移而失效**，但本次不升级 AGP，该判断留待独立变更。
  - `docs/license-policy.md` / `NOTICE`：登记 Kotlin Stdlib（Apache-2.0，JetBrains）。
  - `docs/ai-work-log.md`：追加 `MIGRATION-K` 记录头与 K0 记录。
- 验证：`git status --short` 确认改动仅限上述文件；文档间无自相矛盾表述。
- 通过标准：基线文档与实际技术栈一致，所有新增依赖均有名称、版本、许可证、来源 URL。

---

### K1 | 工具链接入 + 叶子层迁移

- 依赖：K0 DONE。
- 目标：接通 Kotlin 工具链，迁移无外部依赖的纯逻辑类，验证互操作与测试链路。
- 规模：**534 行 / 6 文件**（主源码）
- 允许修改：
  - 构建：第 4 节列出的四个文件。
  - 源码：`model/`（2 文件）、`common/Fmt.java`、`common/widget/EmptyStateView.java`、`core/device/DeviceProfiler.java`、`core/compatibility/CompatibilityEngine.java`。
  - 测试：上述类对应的单测。
- 迁移要点：
  - `ModelInfo`、`DeviceProfile`、`ChatMessage` 等数据持有类 → `data class`（注意：需保留 Java 调用方时加 `@JvmField` / `@JvmStatic`）。
  - `Fmt`、`CompatibilityEngine` 等静态工具类 → `object` + `@JvmStatic`。
  - `DeviceProfiler.Profile` 的可变 public 字段改为 `var`，确认 Java 侧 getter/setter 调用不受影响。
- 验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
- 通过标准：构建成功，全部单测通过（数量不减少），APK 可安装启动，页面无回归。

---

### K2 | 数据层迁移

- 依赖：K1 DONE。
- 目标：迁移网络、存储与组合根；**`data/room/` 保持 Java**（D1）。
- 规模：**1008 行 / 12 文件**
- 允许修改：`data/network/`（10 文件）、`data/storage/ModelStorageManager.java`、`data/ServiceLocator.java`，及其对应测试。
- 迁移要点：
  - `Ed25519.java` 为位运算密集型实现，迁移时逐字节核对逻辑，测试必须保持全绿（`Ed25519Test` 5 项 + RFC 8032 向量交叉验证）。
  - `CatalogClient`、`ManifestVerifier` 的 Gson 反序列化数据类 → Kotlin `data class` 需给字段加默认值或 `@SerializedName`，防止 Gson 绕过构造器导致空值。
  - `ModelStorageManager` 的文件操作注意 `use {}` 资源管理替换 try-finally。
- 验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
- 通过标准：**P2 供应链测试全绿**——`Ed25519Test`、`ManifestVerifierTest`、`CatalogClientTest`、`ModelVerifierTest`、`ModelStorageManagerTest`、`DownloadPipelineTest`、`RoomPersistenceTest` 全部通过；签名校验与断点续传行为与迁移前一致。

---

### K3 | 推理层迁移

- 依赖：K2 DONE。
- 目标：迁移推理编排层；JNI 与 Parcelable 边界保持 Java（D5）。
- 规模：**656 行 / 3 文件**
- 允许修改：`core/inference/` 中的 `InferenceClient.java`、`InferenceService.java`、`ApprovedModels.java`，及其对应测试。**不得触碰** `NativeSession.java`、`InferenceRequest.java`、`InferenceStats.java`。
- 迁移要点：
  - `InferenceService` 是 AIDL 服务端实现，Stub 为 Java 生成类——Kotlin 继承 Java 生成的 `Stub` 时需注意可空性标注（AIDL 生成的参数默认不带 `@Nullable`，Kotlin 侧按平台类型处理，避免误加 `!!`）。
  - `InferenceClient` 的状态机与 `linkToDeath` 回调迁移后需保持 `ENGINE_CRASHED=1199` 恢复语义不变。
  - `ApprovedModels` 的数组常量 → Kotlin 需保持 `@JvmField` / `companion object` 暴露方式，确认 Java 侧 `ApprovedModels.SMOLLM_135M` 仍可访问。
- 验证：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - `./gradlew :app:connectedDebugAndroidTest --offline --console=plain`（模拟器）
- 通过标准：**P3 的 6 项仪器化测试全过**——错误回调、路径拒绝、独立进程、重复 stop/release、进程杀死后 `ENGINE_CRASHED` 恢复、多轮生成循环。

---

### K4 | UI 层迁移（最大工作量）

- 依赖：K3 DONE。
- 目标：迁移全部页面与宿主 Activity。
- 规模：**4787 行 / 24 文件**（占全部工作量约 47%）
- 允许修改：`feature/download/`（6）、`feature/chat/`（10）、`feature/market/`（4）、`feature/diagnostics/`（1）、`feature/settings/`（1）、`MainActivity.java`、`App.java`，及其对应测试。
- 迁移要点：
  - **Fragment 生命周期**：`onCreateView` 返回可空 View、`view.findViewById` 需处理可空，避免 `!!` 滥用。
  - **Adapter / ViewHolder**：15 个文件使用 `findViewById`，按 D3 保持现状，不做 ViewBinding 改造。
  - **匿名内部类**：监听器与 `Runnable` 用 SAM 转换简化，注意 SAM 转换每次调用会创建新对象——在 `removeCallbacks` 配对的场景必须保留具名引用，否则移除失效（P1 曾出现过同类缺陷）。
  - **`MainActivity` 底部导航**：`show/hide` 状态保持逻辑与 `setSelectedItemId` 递归缺陷的修复（P1）必须原样保留。
  - **`App.java` 进程门控**：`:inference` 进程跳过 `ServiceLocator` 初始化的逻辑不可破坏，这是"推理进程无网络栈"的实现基础。
- 验证：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - 模拟器逐页截图 + `uiautomator dump` 结构校验：市场、详情、下载、聊天、历史、诊断、设置；浅色 + 深色模式
- 通过标准：**P1 页面状态矩阵全部复现**——空态、加载态、错误态、深色模式齐全；无越界、截断、默认控件残留或不可读文本；全部交互可点击。

---

### K5 | 收尾与文档闭合

- 依赖：K4 DONE。
- 目标：迁移遗留文件，同步全部文档，确认无 Java 残留（红线区除外）。
- 规模：**513 行 / 4 文件**
- 允许修改：`mock/`（3 文件）、`app/src/androidTest/`（1 文件）、`docs/ai-work-log.md`、`docs/build-baseline.md`。
- 迁移要点：
  - `mock/` 三个文件**都仍在被引用，不可删除**：`Filters` → `MarketFragment`；`ReplyComposer` → `MockChatEngine`；`MockChatEngine` → `ChatEngineProvider`（非批准模型的回退引擎）。必须迁移而非删除。
  - 仪器化测试 `InferenceServiceInstrumentedTest` 迁移后，K3 的 6 项测试需重跑确认。
- 验证：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`
  - `./gradlew :app:connectedDebugAndroidTest --offline --console=plain`
  - 存量 Java 文件清点：除第 5 节红线清单外，`app/src/main/java` 无 `.java` 残留
- 通过标准：全部测试通过；红线区之外无 Java 源文件；基线文档与实际代码一致；日志有完整 `MIGRATION-K` 记录链。

---

## 7. 风险与回滚

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| **离线环境脆弱** | KSP / kapt / parcelize 均不可用，任何"Kotlin 侧注解处理"需求都会卡死 | 趁网络可用时预取 `symbol-processing-gradle-plugin` 与 `kotlin-annotation-processing-embeddable` 备用 |
| **Room 技术债** | Room 3.x 仅支持 KSP/Kotlin，未来升级 Room 或用 Kotlin 写 Entity 必须先解决 KSP | 已记入 D1；解除条件：联网补齐依赖并验证 |
| **回归无法归因** | 在未验收代码上迁移 | 由前置门禁 G-P4 拦截；若豁免需记录决定 |
| **Gson + data class 空值** | Kotlin 非空字段被 Gson 反射绕过构造器写入 null | K2 强制给反序列化字段加默认值或 `@SerializedName`，并用现有测试守护 |
| **SAM 转换导致回调移除失效** | 定时器/监听器无法取消 | K4 对与 `removeCallbacks` 配对的场景保留具名引用 |
| **JNI 符号错配** | Native 方法解析失败 | D5 已将 JNI 边界划为红线区，不迁移 |

**回滚方案**：每个阶段结束打一个独立提交（建议 `refactor(kotlin): migrate K<n> ...`）。任一阶段验收失败时，`git revert` 该提交即可回到上一绿色状态，不需要整体回滚。

---

## 8. 附录：可行性探针内容（供复现）

探针在 `app/src/main/kotlin/` 与 `app/src/test/kotlin/` 各放一个临时文件，验证四项能力：

1. Kotlin 调用 Java 静态方法（`Fmt.humanBytes` / `Fmt.humanEta`）
2. Kotlin 使用 `data class` 与 `object` + `@JvmStatic`
3. Kotlin 测试类使用 `@RunWith(RobolectricTestRunner::class)`
4. Room 由 Java `annotationProcessor` 生成的实现，在 Kotlin 测试中可正常构建（`Room.inMemoryDatabaseBuilder`）

验证通过后已删除临时文件并 `git checkout --` 还原构建脚本，工作树确认干净。

---

## 9. 与现有阶段卡的关系

- 本计划的 `K0-K5` 与 `docs/ai-execution-plan.md` 的 `P1-P6` **并列**，不属于任何 P 阶段。
- 迁移完成后，P5（软件候选与自动化验收）、P6（真机验收）在 Kotlin 代码库上继续执行。
- 若迁移期间 P4 回归问题暴露，优先修复 P4 再继续迁移（阶段卡与里程碑卡冲突时，以里程碑门禁为准）。
