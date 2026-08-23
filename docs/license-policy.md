# 第三方代码依赖许可证政策

> 版本：2026-08-23（S003 建立）。本清单只覆盖**代码依赖**；模型权重、Tokenizer、Chat Template、数据集的许可证审查不在本文件范围，由模型上架流程（`docs/model-onboarding.md`）负责。

## 1. 政策规则

1. 任何进入 `app/build.gradle` 的直接依赖，必须先在本文件第 3 节登记：名称、版本、SPDX 许可证、来源 URL、引入步骤。
2. 登记不等于采用。`gradle/libs.versions.toml` 中已登记但未在构建脚本引用的库标记为“待采用”；实际引入时若升级版本，必须同步更新本文件和 `NOTICE`。
3. 引入新依赖前检查许可证兼容性：本项目为闭源商业产品，**禁止**把 AGPL-3.0、GPL 系列强传染许可的实现代码合并进仓库（参考交互与公开文档除外）；LGPL 组件须动态链接并记录。
4. 每次发布前重新运行依赖核对：`./gradlew :app:dependencies` 输出的每个直接依赖都必须能在本节找到条目。
5. 第三方代码以源码形式 vendored（如 llama.cpp/ggml）时，保留其 LICENSE 文件与 commit 记录于对应目录。

## 2. 许可证兼容性结论速查

| SPDX | 结论 |
| --- | --- |
| Apache-2.0 | 允许；保留 NOTICE 与许可证文本 |
| MIT | 允许；保留版权与许可声明 |
| BSD-2/BSD-3 | 允许；保留版权与许可声明 |
| EPL-1.0（仅 testImplementation） | 允许；测试代码不打进 APK，不分发 |
| LGPL-2.1/3.0 | 谨慎；仅动态链接且需法务复核 |
| GPL/AGPL 系列 | 禁止合入实现代码 |

## 3. 直接依赖清单

### 3.1 Native 推理引擎（vendored，S005 锁定）

| 名称 | 版本 | 许可证 | 来源 | 说明 |
| --- | --- | --- | --- | --- |
| llama.cpp | 待 S005 固定具体 commit | MIT | https://github.com/ggml-org/llama.cpp | 以源码形式导入 `app/src/main/cpp/engine_llama/`；commit、编译参数、patch 列表记录于 `docs/native-baseline.md` |
| ggml | 随 llama.cpp 固定 | MIT | https://github.com/ggml-org/ggml | llama.cpp 的底层张量库；同上保留来源与哈希 |

### 3.2 AndroidX / Jetpack（Google Maven）

统一信息：许可证 Apache-2.0；Maven 仓库 https://dl.google.com/dl/android/maven2/ ；源码 https://android.googlesource.com/platform/frameworks/support ；版本核实日期 2026-08-23。

| 名称 | 坐标 | 版本 | 许可证 | 计划引入步骤 |
| --- | --- | --- | --- | --- |
| AndroidX Core | androidx.core:core | 1.16.0（P1 由 1.19.0 调整：1.19.0 要求 compileSdk 37 + AGP 9.1，与工程固定工具链 AGP 8.13.2/compileSdk 36 不兼容） | Apache-2.0 | P1（首次实际引入） |
| AppCompat | androidx.appcompat:appcompat | 1.8.0 | Apache-2.0 | P1（首次实际引入） |
| RecyclerView | androidx.recyclerview:recyclerview | 1.4.0 | Apache-2.0 | P1（首次实际引入） |
| Lifecycle Runtime | androidx.lifecycle:lifecycle-runtime | 2.11.0 | Apache-2.0 | 随 ViewModel 使用步骤 |
| Room Runtime | androidx.room:room-runtime | 2.8.4 | Apache-2.0 | S014（下载状态机）/ S028 |
| Room Compiler | androidx.room:room-compiler | 2.8.4 | Apache-2.0 | 同上，Java annotationProcessor 方式 |
| WorkManager | androidx.work:work-runtime | 2.11.2 | Apache-2.0 | S018（可恢复后台任务） |

备注：Room 维持 2.x（2.8.4，2025-11-19 发布）；Room 3.x 仅支持 Kotlin/KSP，与本 Java 工程不匹配，禁止引入。WorkManager 取最新稳定 2.11.2（2.12.0 尚在 RC）。

### 3.3 网络

| 名称 | 坐标 | 版本 | 许可证 | 来源 | 实际引入步骤 |
| --- | --- | --- | --- | --- | --- |
| OkHttp | com.squareup.okhttp3:okhttp | 4.12.0（P2 由 5.5.0 调整：5.5.0 强制 compileSdk 37，与工程固定工具链 AGP 8.13.2 / compileSdk 36 不兼容；4.12.0 为 4.x 稳定线最终版，Range/ETag/流式下载 API 与本项目所需一致） | Apache-2.0 | https://github.com/square/okhttp （Maven Central） | P2（Manifest 校验、断点下载） |

### 3.6 JSON 解析（P2 引入）

| 名称 | 坐标 | 版本 | 许可证 | 来源 | 实际引入步骤 |
| --- | --- | --- | --- | --- | --- |
| Gson | com.google.code.gson:gson | 2.11.0 | Apache-2.0 | https://github.com/google/gson （Maven Central） | P2（Manifest/Catalog 解析） |

### 3.4 UI 组件（P1 引入，2026-08-23 登记）

| 名称 | 坐标 | 版本 | 许可证 | 来源 | 实际引入步骤 |
| --- | --- | --- | --- | --- | --- |
| Material Components for Android | com.google.android.material:material | 1.12.0 | Apache-2.0 | https://github.com/material-components/material-components-android （Google Maven） | P1（前端视觉基线：M3 主题、Chip、BottomNav、BottomSheet、ProgressIndicator、MaterialSwitch、Dialog） |

### 3.5 测试（testImplementation，不随 APK 分发）

| 名称 | 坐标 | 版本 | 许可证 | 来源 | 实际引入步骤 |
| --- | --- | --- | --- | --- | --- |
| JUnit 4 | junit:junit | 4.13.2 | EPL-1.0 | https://github.com/junit-team/junit4 | S006 |
| Robolectric | org.robolectric:robolectric | 4.16 | MIT | https://github.com/robolectric/robolectric （Maven Central） | P2（Room DAO / 下载管线 JVM 测试；android-all 镜像走阿里云 central） |
| AndroidX Test Core | androidx.test:core | 1.6.1 | Apache-2.0 | Google Maven | P2（Robolectric 配套 ApplicationProvider） |
| AndroidX Test Runner | androidx.test:runner | 1.6.2 | Apache-2.0 | Google Maven | P3（仪器化测试运行器，androidTest 域，不随 APK 分发） |
| AndroidX Test Rules | androidx.test:rules | 1.6.1 | Apache-2.0 | Google Maven | P3（同上） |
| AndroidX Test ext:junit | androidx.test.ext:junit | 1.2.1 | Apache-2.0 | Google Maven | P3（AndroidJUnit4 运行器） |

### 3.7 自研/vendored 代码（P2 引入）

| 名称 | 位置 | 许可证 | 说明 |
| --- | --- | --- | --- |
| Ed25519 验证器（verify-only） | `app/src/main/java/com/example/localai/data/network/Ed25519.java` | CC0（公共领域，算法结构参考 str4d/ed25519-java） | 自研纯 Java 验证路径，供 API 26+ 无平台 EdDSA 设备使用；单测与 JDK 17 Ed25519 交叉验证；归属声明见 NOTICE |

## 4. 构建工具链（不随应用分发，仅列示）

Gradle 8.14（Apache-2.0）、Android Gradle Plugin 8.13.2（Apache-2.0）、NDK r28（Apache-2.0 及第三方组件见 `$SDK/ndk/.../NOTICE`）、CMake 3.22.1（BSD-3）、JDK Temurin 17（GPLv2+Classpath）。工具链产物不打包进 APK，发布归档时在 SBOM 中单独列出。

## 5. 当前状态核对（2026-08-23，P3 更新）

截至 P3，`app/build.gradle` 实际直接依赖集合（不含传递依赖）：

- implementation：androidx.core 1.16.0、appcompat 1.8.0、recyclerview 1.4.0、material 1.12.0、okhttp 4.12.0、gson 2.11.0、room-runtime 2.8.4（annotationProcessor：room-compiler 2.8.4）。
- testImplementation：junit 4.13.2、robolectric 4.16、androidx.test:core 1.6.1、room-runtime 2.8.4（testAnnotationProcessor：room-compiler 2.8.4）。
- androidTestImplementation（不随 APK 分发）：androidx.test:runner 1.6.2、rules 1.6.1、ext:junit 1.2.1、core 1.6.1。

全部条目已在本清单登记；Room 2.8.4 / OkHttp（4.12.0 调整）版本决策见上表备注。`NOTICE` 已同步更新（含批准评估模型 SmolLM-135M-Instruct 的权重许可归属说明）。
