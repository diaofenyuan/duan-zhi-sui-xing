# 模型上架与目录分发协议（model-onboarding）

> 版本：2026-08-23（P2 建立）。本文档定义模型进入市场前的登记、签名、分发与撤销规则；
> 客户端校验链的实现见 `app/src/main/java/com/example/localai/data/network/` 与
> `app/src/main/java/com/example/localai/feature/download/`。

## 1. 目录协议

客户端与云端控制面通过固定路径交互（P2 协议；生产可扩展为 `/v1/models/{id}` 详情接口）：

| 路径 | 内容 | 签名 |
| --- | --- | --- |
| `GET /v1/catalog.json` | 模型目录（schemaVersion/generatedAt/models[]，条目含 modelId/version/displayName/description） | `catalog.sig` |
| `GET /v1/models/{modelId}/{version}/manifest.json` | 模型 Manifest（见 `backend/manifest-schema/model-manifest.schema.json`） | `manifest.sig` |
| `GET /v1/models/{modelId}/{version}/{fileName}` | 模型文件（GGUF），支持单段 `Range`/`ETag` | 无（完整性由 SHA-256 保障） |

- 目录只作为索引：**客户端只展示 Manifest 签名验证通过的模型**；单个 Manifest 失败不影响其余条目。
- 模型版本不可变：发布后禁止覆盖同版本文件；内容更新必须发新版本号。

## 2. 签名协议（Ed25519，RFC 8032）

- 签名载荷 = 数据文件（catalog.json / manifest.json）的**原始字节流**，不做重序列化规范化。
- 签名由侧车文件承载：`{"algorithm":"Ed25519","keyId":"...","value":"<base64 R||S>"}`。
- 客户端内置可信公钥表（`TrustedKeys`，keyId -> 32 字节公钥），支持 keyId 轮换与撤销列表
  （`TrustStore.isRevoked`）；撤销后新下载被拒绝，已安装模型保留可删除状态。
- 校验顺序（S016 校验链）：**Manifest 签名 -> 字段结构 -> 文件大小 -> Range/ETag 一致性 ->
  SHA-256 -> GGUF 头部探针（magic/version/general.architecture）-> 原子安装**。
- 生产密钥必须在离线环境生成并托管；dev fixture 密钥（`release-2026-01-dev`）仅用于本地联调，
  其私钥已 gitignore（见 `backend/signing/keys/README.md`），发布前必须移除。

## 3. 上架流程（控制面）

1. 运营提交模型来源、许可证（spdx+url）、Chat Template、Tokenizer、量化方式与已知限制。
2. 服务端核验来源与格式，生成不可变版本号。
3. CI 下载文件计算 SHA-256，执行 GGUF 头部/元数据解析与短时加载生成。
4. 人工审查许可证与内容描述；服务端用签名工具（`backend/signing/ManifestSigner.java`）签发 Manifest 与目录。
5. 发布到灰度目录；客户端按硬约束（minAndroidApi/ABI/后端）过滤。
6. 安全问题或版权问题 -> 加入撤销列表，禁止新安装，保留旧版本可删除。

## 4. 下载与安装（客户端）

- 断点续传：`Range: bytes=offset-` + `If-Range: {etag}`；服务器返回 200（全量，截断重下，**绝不拼接新旧内容**）、
  206（校验 `Content-Range` 起始偏移）、416（归零重下）。
- 下载到 `files/downloads/{taskId}/model.part`，固定缓冲区流式写盘；进度节流写 Room。
- 失败语义：网络中断 -> `FAILED` 并保留 `.part`（用户重试即续传）；SHA-256/GGUF 失败 -> 删除 `.part`（非法资产不落盘）。
- 原子安装（`ModelStorageManager`）：staging 目录写入 manifest.json/manifest.sig/gguf 后**最后写 install.ok**，
  同文件系统 rename 至 `files/models/{modelId}/{version}/`；目标已存在先退避到 `.rollback`，失败自动回滚旧版本。
- 进程重启：`recoverPending()` 扫描 Room 非终态任务（QUEUED/DOWNLOADING/PAUSED/VERIFYING/INSTALLING）恢复执行；
  启动时清理孤儿 staging（含 install.ok 的回滚遗留保留，可手工恢复）。

## 5. dev Fixture 使用（P2 联调）

- 生成：`powershell -ExecutionPolicy Bypass -File backend\fixtures\generate.ps1`
  （生成确定性 GGUF 演示载荷 + 签名 catalog/manifest，输出公钥需写入 App 的 `TrustedKeys`）。
- 演示服务器：`java -cp backend/build-tools FixtureServer serve 8090 backend/fixtures 2026-08-23`
  （模拟器经 `10.0.2.2:8090` 访问，App 侧地址见 `CatalogConfig.BASE_URL`）。
- 演示载荷为合成字节（GGUF 头 + 确定性填充），**非真实权重**；真实模型接入按第 3 节上架流程执行。

## 6. 与后续阶段的边界

- WorkManager/前台服务化下载调度、多镜像测速：P4/P5。
- 已安装模型的短时加载探测（安装后冒烟）：P3（Native 推理链路）接入。
- 真实设备采集与真机验收：P6。

## 7. P3 批准模型（真实推理验收用）

- 登记文件：`qa/fixtures/approved-model.json`（代码镜像 `core/inference/ApprovedModels.java`）。
- 批准模型：SmolLM-135M-Instruct（HuggingFaceTB，Apache-2.0），Q4_K_M，约 100 MB，
  GGUF 来源 second-state/SmolLM-135M-Instruct-GGUF，SHA-256 见登记文件。
- 安装布局：`files/models/smollm-135m-instruct/2026.08.1/SmolLM-135M-Instruct-Q4_K_M.gguf`
  （与 `ModelStorageManager` 布局一致）；模拟器验证安装命令见登记文件 `installHint`。
- 聊天页引擎选择：当前模型为“已安装的批准模型”时走真实推理（`ChatEngineProvider`），
  其余模型保持演示模式；该批准清单仅用于开发验收，不属于面向用户的市场目录。
