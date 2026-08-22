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
3. 按 `llama.cpp/examples/llama.android` 的官方文档完成一次独立真机加载和生成。
4. 将模型文件访问、会话状态和错误码重新封装为自己的 `InferenceSession` 接口。
5. 在 UI 还没有开始之前，先完成 3 台真机的加载、停止、释放、重启和峰值内存基线。

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
| TTFT/TPS | 完成三档设备基线记录 | 不能比基线回退超过约定阈值；持续 30 分钟记录温控 |
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
| 0. 技术验证 | W1～W2 | 锁定 llama.cpp、模型、ABI、最低 API、三档设备；完成官方 Android 示例验证 | 真机完成加载、生成、停止、释放；记录内存和速度 |
| 1. 推理服务 | W3～W5 | JNI 句柄表、状态机、AIDL、独立进程、CPU 基线 | Native 崩溃不拖垮 UI；重复释放和停止有测试 |
| 2. Manifest/下载 | W4～W8 | Schema、签名、CDN、Range、ETag、校验、Room、安装/删除 | 弱网、断网、强杀、镜像切换均可恢复或安全失败 |
| 3. 设备适配 | W6～W9 | DeviceProfiler、规则引擎、短 Benchmark、模型详情适配信息 | 每个推荐/不支持结果都有可解释原因 |
| 4. 市场与会话 | W8～W12 | 列表、详情、搜索、下载管理、对话、历史和上下文裁剪 | 端到端完成“发现-下载-安装-聊天-删除”闭环 |
| 5. 稳定性/安全 | W10～W14 | 熔断、温控、进程重启、日志脱敏、签名和许可证审计 | 故障注入、抓包、压力和供应链检查通过 |
| 6. 机型回归/发布 | W15～W18 | 性能回归、灰度、崩溃修复、上架材料、模型回滚演练 | 发布门禁达标，能撤销模型和回滚客户端 |

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
- 三档设备完成固定条件下的 TTFT、TPS、峰值内存和温控基线。
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
