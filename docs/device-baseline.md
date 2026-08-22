# 设备基线与分档标准（S004 改造版）

> 状态：**无真机改造版**（2026-08-23，经项目所有者授权）。原步骤卡要求登记三台真实设备并使 G0 门禁可复现；因当前无可用的真实 Android 设备，本步交付"三档分档标准 + 回填流程"，真机实测记 `BLOCKED`。机器可读数据源：`qa/device-matrix/devices.yaml`。

## 1. 范围变更声明

| 项 | 原步骤卡 | 本轮实际交付 |
| --- | --- | --- |
| 设备来源 | 三台真实设备 | 公开规格参考档位（`REFERENCE_UNVERIFIED`） |
| 动作 | 只填写设备基线 | 同左 + 明确回填流程与硬约束 |
| 验证 | YAML 可解析、字段完整、无个人信息 | 同左 |
| 未完成部分 | —— | 真机实测条目（`MEASURED`），解除条件见第 6 节 |

风险：在真机回填前，一切基于本表的性能假设（内存预算系数、速度预期、温控行为）都是**未校准值**；不得写入任何对外承诺。

## 2. 三档分档标准

| 档位 | SoC 参考 | RAM | Android(API) | ABI | 页大小 | 可用存储下限 | 模型策略 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 旗舰 | 骁龙 8 Gen2/Gen3、天玑 9300 | ≥12 GB | 14+ (34+) | arm64-v8a | 4KB | 60 GB | 1.5B/3B，CPU 与 GPU 对比 |
| 中端 | 骁龙 7+ Gen2、天玑 8200 | 8 GB | 13+ (33+) | arm64-v8a | 4KB | 30 GB | 1.5B，自动上下文 |
| 入门 | 骁龙 695、天玑 700 | 6 GB | 12+ (32+) | arm64-v8a | 4KB | 20 GB | 1.3B-1.5B，CPU 优先 |

特殊档（不入基线）：非 arm64 / API<26 / 16KB 页设备——按硬约束禁用或单独验证安装与提示文案。

## 3. 字段字典与采集方式

真机到位后，用以下命令采集（无需 root；结果不含序列号）：

| 字段 | 含义 | adb 采集命令 |
| --- | --- | --- |
| brand/model | 品牌/型号 | `adb shell getprop ro.product.brand; ro.product.model` |
| soc | SoC 型号 | `adb shell getprop ro.soc.model; ro.board.platform` |
| ram_gb | 运行内存 | `adb shell cat /proc/meminfo`（MemTotal 换算 GiB，向下取整） |
| android_version/api | 系统/API 级别 | `adb shell getprop ro.build.version.release; ro.build.version.sdk` |
| abi | CPU 架构 | `adb shell getprop ro.product.cpu.abi` |
| page_size_bytes | 内存页大小 | `adb shell getconf PAGESIZE` |
| storage_free_gb | 数据分区可用空间 | `adb shell df -h /data` |

禁止登记：序列号（`ro.serialno`）、IMEI、账号、完整路径中的用户名等个人标识信息。

## 4. 与运行时自动打分的衔接

本表是静态分档标准；运行时链路为后续步骤，不在 S004 实现：

```text
S023 DeviceProfiler   运行时采集画像（字段同第 3 节，另加热状态/电量）
S024 MemoryBudget     按 RAM 分档给出候选模型内存上限（初始系数取自本表，实测后覆盖）
S025 CompatibilityEngine  硬约束过滤(第 2 节) -> 短 Benchmark -> 输出 推荐/可运行/高负载/不支持
```

即用户设备无需人工查表：App 自身采集画像后按硬约束 + 规则引擎自动打分匹配。本表的硬约束阈值（`hard_constraints`）就是规则引擎的第一道过滤器。

## 5. 回填流程

1. 真机连接后按第 3 节命令采集全部字段。
2. 在 `devices.yaml` 的 `measured_devices:` 列表追加条目：`verification: MEASURED`、记录采集日期与所用 adb 命令输出摘要。
3. 归档档位时以实测 SoC/RAM 对照第 2 节；不匹配参考规格时按实测值归档并在 notes 说明。
4. 参考档位（`REFERENCE_UNVERIFIED` 条目）保留作对照，不得删除。
5. 全部三档有 `MEASURED` 条目后，G0 门禁的设备项视为恢复可复现，原步骤卡验收补全。

## 6. 解除条件（BLOCKED 部分）

- 需要：至少 1 台 arm64-v8a 真机（USB 调试可用）即可开始 S007 前置验证；三档齐备才能完整通过 G0。
- 当前持有：0 台。
- 已尝试：检查本机 `adb devices`（无设备连接）；确认 Windows x86_64 模拟器不能作为 arm64 Native 推理的可信基线。
