# 发布候选 0.3.0（arm64-v8a）

> 归档日期：2026-09-26。本文件说明候选包的内容、校验方式与已知限制；门禁结论见
> `docs/gates/g5-software-acceptance.md`，机器可读清单见 `artifacts/manifest.json`。

## 1. 获取与校验

候选包不入库（96 MB 二进制），归档于本机 `artifacts/candidate/`。校验方式：

```powershell
# 重新构建
./gradlew :app:assembleRelease --offline

# 校验归档副本与构建产物一致
(Get-FileHash artifacts/candidate/local-ai-0.3.0-arm64-release.apk -Algorithm SHA256).Hash
# 期望 c4937ee2c036c50a73c972e84217623d4928654c6918c25c452abeffee69c52e

# 校验签名
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  artifacts/candidate/local-ai-0.3.0-arm64-release.apk
```

## 2. 内容

- 版本 `0.3.0`（versionCode 3），仅 `arm64-v8a`，签名方案 v2。
- 模型权重**不随包分发**：应用内置签名目录与清单，权重经 HTTPS 从 `https://huggingface.co` 下载，下载后校验 SHA-256 与 GGUF 结构再登记安装。
- 随包包含：签名目录（8 个 Qwen2.5 模型条目）、17 组第三方许可条目及其完整文本、离线中文 OCR 的 ONNX 模型（构建期逐字节校验）。
- 其余依赖与归属见 `NOTICE` 与 `docs/license-policy.md`。

## 3. 安装（验收用）

```powershell
adb install -r artifacts/candidate/local-ai-0.3.0-arm64-release.apk
```

首次使用需在「模型」页选择模型并下载；也可按 `docs/build-baseline.md` 的「Debug 应用的设备模型预置」一节走真实下载链路预置后再验证。

## 4. 已知限制

- **未经真机验收**：现有结论均来自 16 KB x86_64 模拟器（ARM64 经 `libndk_translation.so` 运行），不代表 arm64 真机性能与温度表现。
- 输出速度与首字延迟在应用内显示「待真机实测」，未提供基准数字（口径与回填条件见 `docs/device-baseline.md` 第 7 节）。
- 已产出 AAB（`artifacts/candidate/local-ai-0.3.0-arm64-release.aab`，64,225,674 字节，SHA-256 `f2f097ab…d37bc`，`jarsigner -verify` 通过），但未验证应用商店上传路径，也未做 AAB→APK 拆分校验。
- 未执行长时间稳定性循环与故障矩阵全量跑测。
- 「停止生成」的 UI 侧人工复现受 uiautomator 限制（流式期间取不到静止快照），该路径以自动化用例为准。

## 5. 下一步

`P6`：在三档真实 arm64 设备上安装本候选包，执行端到端主流程与性能回归，记录 TTFT/TPS、峰值内存、温度与电量，并回流任何问题。验收记录写入 `docs/gates/g6-device-acceptance.md`。
