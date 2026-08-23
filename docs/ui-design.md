# UI 设计系统与页面状态矩阵（P1）

> 版本 2026-08-23 · P1 阶段产出。前端为 Java + XML + Material 3（material 1.12.0）定制主题，全部页面基于模拟数据。

## 1. 设计令牌

### 1.1 色彩

| 令牌 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `md_primary` | #4F46E5 | #A5B4FC | 主操作、选中态、发送按钮 |
| `md_primary_container` | #E0E7FF | #3730A3 | 导航指示器、次级强调 |
| `md_secondary` | #0D9488 | #5EEAD4 | 辅助强调（诊断后端等） |
| `md_tertiary` | #D97706 | #FBBF24 | 第三强调色 |
| `app_background` | #F6F7FB | #0B0E18 | 窗口背景 |
| `surface` / `surface_variant` | #FFFFFF / #EDF0F8 | #131725 / #1C2233 | 卡片、气泡、图标底 |
| `text_primary/secondary/tertiary` | #111827 / #5A6473 / #98A1B0 | #E8EBF3 / #9BA6BB / #5F6980 | 三级文字 |
| `status_success/info/warn/danger` (+container) | 绿/蓝/琥珀/红 | 同系深色变体 | 兼容性徽标、状态点、错误文案 |

渐变：`bg_gradient_hero`（indigo→violet，头图）、`grad_a..d`（模型图标底，按 gradIndex 循环）。

### 1.2 字体层级

| 样式 | 规格 |
| --- | --- |
| Text.App.Headline | 24sp medium bold（市场页品牌标题） |
| Text.App.PageTitle | 20sp medium bold（各 Tab 页标题） |
| Text.App.Section | 16sp medium（区块标题） |
| Text.App.ItemTitle | 14sp medium 单行省略（列表标题） |
| Text.App.Body / BodySec | 14sp / 13sp，行距 +3~4sp |
| Text.App.Label / Caption | 12sp / 11sp（元数据、注释） |
| Text.App.Badge | 10sp bold（兼容性徽标） |

### 1.3 几何令牌

- 圆角：卡片 18dp、头图 22dp、按钮 14dp、徽标 8dp、图标盒 14dp。
- 屏幕边距 `pad_screen` = 18dp；卡片内边距 16dp；间距梯度 4/8/12/16/22dp。
- 按钮 `h_button` = 46dp；消息气泡最大宽 270dp；用户气泡右下角 6dp、机器人气泡左下角 6dp 的“方向角”。

## 2. 页面清单与导航

- 宿主：单 Activity + 底部导航 5 Tab（市场/聊天/下载/诊断/设置），Tab 使用 show/hide 保留状态；二级页（详情、历史）走返回栈并带左右滑入动画。
- 市场 → 详情（点击卡片或精选横幅）；详情示例指令 → 预填聊天输入框。
- 聊天 → 历史（顶栏时钟按钮）；聊天 → 模型切换 BottomSheet（顶栏调音按钮）。
- 下载空态 → “去市场看看”；详情下载成功 → Snackbar“查看”。

## 3. 状态矩阵（美观硬性验收覆盖）

| 页面 | 加载态 | 内容态 | 空态 | 错误/降级态 |
| --- | --- | --- | --- | --- |
| 市场 | 首次进入 500ms ProgressBar | 列表+精选横幅+计数 | EmptyStateView（清除筛选动作） | — |
| 详情 | — | 头图+三格数据+元信息 | — | 不支持：禁用主按钮+原因说明 |
| 下载 | — | 进行中（进度/速度/剩余）/已安装两段 | EmptyStateView（去市场动作） | 校验失败卡（红色原因+重试） |
| 聊天 | 打字气泡（三点） | 流式气泡、停止按钮变红 | 欢迎语+隐私说明 | 停止后追加“（已停止生成）” |
| 历史 | — | 会话卡片（预览/条数） | EmptyStateView | 删除需确认对话框 |
| 诊断 | — | 设备规格网格+实时状态+基准条形 | 无安装时提示行 | 不支持原因逐条列出 |
| 设置 | — | 模式三选卡（描边高亮）、开关组 | — | 清空会话/删除均二次确认 |

深色模式：`values-night` 全量覆盖色彩令牌；浅色状态栏深字、深色反白。小屏安全：所有滚动区 `clipToPadding=false`、气泡 maxWidth、筛选行横向滚动、输入框 maxLines=4。

## 4. 与后续阶段的衔接

- 本阶段 `MockStore/Filters/MockDownloadEngine/MockChatEngine` 为纯 Java 或仅依赖 Handler，P2/P3 以真实 Repository/AIDL 替换时不需要改动页面结构。
- 文案 i18n 抽取（strings.xml 化）在 P2 数据层接入时统一处理；当前静态文案直接写在布局中，动态文案已在 strings.xml。
