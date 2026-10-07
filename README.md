# GY CrossKit JVerification

统一候选 **0.1.4**：Swift `authenticate(true)` 与 common/OHOS 同样自动初始化，撤销同意后可重新同意授权；复用已有 setup/waiter/busy/cancel 流程。真实厂商 headers 编译和回调合同通过；新远程消费与设备运营商结果待发布后验收。HAR 源码未变，配套仍为 0.1.0。

封装 Android、iOS 和 HarmonyOS 极光一键认证的初始化、预取号、授权 Token 与释放。用户同意、AppKey、应用登记、授权页样式及 Token 后端换票由宿主负责；组件不创建账号、不保存 Token、不返回厂商 SDK content。

上一版 Maven / Git Pod 固定版本 **0.1.3**：Swift拒绝6000回包中的纯空白token，与KMP保持一致，合法凭据保留原文。Podspec内部版本同步0.1.3，HAR保持0.1.0。**已发布，新 Git Pod 全源码/厂商 SDK/App 链接及精确远程文件核验通过，Maven新目录最终消费通过，结果见完整审查**。旧Maven0.1.2、Git Pod0.1.1的历史验收不代算此次修复；详见[完整源码审查](docs/完整源码审查.md)。

## 平台与产物

| 产物 | 平台 / 要求 |
| --- | --- |
| `jverification-core` | Android API 24+ / iOS 15+；共用协程 client、Android driver、iOS 闭包 driver |
| `jverification-kuikly` | OHOS Kotlin Module；Kuikly `2.28.0-2.0.21-ohos` |
| `GycJVerificationNative` | iOS 15+ / Swift 5.9；独立 CocoaPods Git 源，供 Swift 或 KMP driver 使用 |
| `@gycrosskit/jverification-native` | HarmonyOS API 22 兼容 HAR；原生服务及 Kuikly Renderer Module |

KMP 工具链基线为 OpenHarmony Kotlin `2.2.21-1.0.0` / JDK 17 / Gradle 8.11.1 / AGP 8.10.1。Core 的 OHOS / JVM 变体仅提供公共能力，实际 SDK 驱动由 HAR / Kuikly 或宿主实现提供。

厂商版本：Android JVerification `3.4.8` / JCore `5.5.6`，iOS JVerification `3.4.7` / JCore `5.5.1`，HarmonyOS `@jg/verify@1.2.2`。

## 架构与调用流程

KMP client 管理同意、并发与等待；原生适配控制 SDK owner 和授权页，宿主处理 UI 与后端换票。

```mermaid
flowchart TB
    Host["宿主<br/>同意 / UI / 后端换票"] --> Client["jverification-core<br/>JVerificationClient"]
    Client --> Android["AndroidJVerificationDriver<br/>Main<br/>极光 Android SDK"]
    Client --> IOS["IosJVerificationDriver<br/>宿主闭包接线"]
    IOS --> Swift["JVerificationNativeClient<br/>Main<br/>极光 iOS SDK"]
    Client --> Kuikly["JVerificationModule<br/>宿主页面 dispatcher"]
    Kuikly --> ArkTS["GycJVerificationModule<br/>GycJVerificationService<br/>极光鸿蒙 SDK"]
```

下面展示 `authenticate(true)` 的正常与取消路径；`prepare` 初始化后只预取号，不打开授权页。

```mermaid
sequenceDiagram
    participant H as 宿主
    participant C as Client
    participant D as Driver
    participant SDK as SDK
    H->>C: authenticate(...)
    C->>C: 校验状态
    C->>D: initialize(callback)
    D->>SDK: owner 门控后初始化
    SDK-->>D: READY
    D-->>C: READY
    C->>C: await 后复核请求
    C->>D: authenticate(...)
    D->>SDK: 拉授权页
    SDK-->>D: 授权页 opened 事件
    D-->>C: opened
    C-->>H: opened（一次）
    alt 当前请求返回 Token
        SDK-->>D: Token 回调
        D-->>C: VerificationResult
        C->>C: 返回前复核请求
        C-->>H: 当前请求结果
    else 取消、撤销同意、超时或 close
        C->>D: cancel()
        D->>SDK: 关闭 owner 授权页
        Note over C,D: 撤销另清缓存；旧回调失效
        Note over D,SDK: 底层初始化未必停止
    end
```

```mermaid
classDiagram
    class JVerificationClient {
        +prepare(consentGranted) VerificationResult
        +authenticate(consentGranted, opened) VerificationResult
        +revokeConsent()
        +close()
    }
    class JVerificationDriver {
        <<interface>>
        +initialize(callback)
        +preLogin(callback)
        +authenticate(opened, callback)
        +cancel()
        +close()
    }
    class VerificationResult {
        +VerificationStatus status
        +Int vendorCode
        +String token
    }
    class JVerificationModule
    JVerificationClient --> JVerificationDriver
    JVerificationClient ..> VerificationResult
    JVerificationModule ..|> JVerificationDriver
```

源码：[client、driver 契约与结果](jverification-core/src/commonMain/kotlin/io/github/gycrosskit/jverification/JVerificationClient.kt)、[Android driver](jverification-core/src/androidMain/kotlin/io/github/gycrosskit/jverification/AndroidJVerificationDriver.kt)、[iOS 闭包 driver](jverification-core/src/iosMain/kotlin/io/github/gycrosskit/jverification/IosJVerificationDriver.kt)、[Swift 原生 client](iosApp/Sources/GycJVerificationNative/JVerificationClient.swift)、[Kotlin Kuikly 模块](jverification-kuikly/src/commonMain/kotlin/io/github/gycrosskit/jverification/kuikly/JVerificationModule.kt)、[ArkTS service](ohos/jverification-native/src/main/ets/GycJVerificationService.ets)、[ArkTS 模块](ohos/jverification-native/src/main/ets/GycJVerificationModule.ets)。

Android/iOS 使用 Main，Kuikly client 注入宿主页面 dispatcher；SDK 回调会回到 client dispatcher 结算。各原生适配限制同一进程的 SDK owner，client 和桥均检查请求 generation；`close` 的清理使用 `NonCancellable`，页面销毁仍需关闭 client 和 dispose 模块。组件不保存 Token，也不把取消描述为卸载 SDK；授权、取消和运营商设备验收边界见下文。

## 安装

项目 `settings.gradle.kts` 依赖仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.eazytec-cloud.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/") }
    }
}
```

共享模块 `build.gradle.kts`：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.gycrosskit.jverification:jverification-core:0.1.3")
        }
    }
}
```

原生宿主按平台安装：

```ruby
# iOS Podfile：未上传 CocoaPods Specs，使用 Git 源。
pod 'GycJVerificationNative',
    :git => 'https://github.com/gycrosskit/jverification.git',
    :tag => '0.1.3'
```

```sh
ohpm install @gycrosskit/jverification-native@0.1.0
```

Git Tag 与 Podspec 内部版本均为已发布 `0.1.3`；旧0.1.1内部Pod版本0.1.0保持历史记录。各渠道分别版本化；插件仓库、iOS 闭包接线与 Kuikly 双侧注册见[接入指南](docs/接入指南.md)。

## 快速使用

Android 在 `androidMain` 创建 driver；构造不会启动 SDK：

```kotlin
import io.github.gycrosskit.jverification.AndroidJVerificationDriver
import io.github.gycrosskit.jverification.JVerificationClient

val client = JVerificationClient(AndroidJVerificationDriver(
    activity = activity,
    appKey = hostAppKey,
    uiConfig = { hostJVerifyUIConfig() },
    configureBeforeInit = { hostConfigureSdkCollection() },
))
```

`activity`、AppKey、授权页和采集配置由宿主提供。在宿主协程中调用：

```kotlin
import io.github.gycrosskit.jverification.VerificationStatus

// 可选预取号，不打开授权页。
client.prepare(consentGranted = hostConsent)
val result = client.authenticate(consentGranted = hostConsent, opened = { hostHideLoading() })
when (result.status) {
    VerificationStatus.TOKEN -> result.token?.let { hostExchangeToken(it) }
    VerificationStatus.CANCELED -> Unit
    else -> hostShowAlternativeLogin(result.status, result.vendorCode)
}
// 撤销同意时：client.revokeConsent()
// 页面结束使用时：client.close()
```

`hostConsent`、loading 和换票均为宿主逻辑。`opened` 只在 SDK 授权页真实打开事件后通知一次，通知不会结束认证；Token、取消、失败才是终结结果。拉页失败、预取号和取消后的迟到事件不会触发通知。

未同意时不初始化 SDK；一进程只配置一个 AppKey，并保持一个活动 SDK owner。关闭、超时或撤销同意使旧回调失效；撤销不能卸载厂商 SDK 或撤回已发送数据。Token 仅交后端换票，宿主不要记录它。

Kotlin client、Swift 原生 client 和 OHOS service 的 `authenticate(true)` 均先初始化再授权；预取号可选，CMP 与 Kuikly 宿主沿用同一原生入口和结果合同。

## 文档与支持

- [接入指南](docs/接入指南.md)：三端初始化、同意、opened、超时、清理及 Kuikly。
- [开发与验证](docs/开发与验证.md)、[历史验证记录](verification/结果.md)：SDK mock、编译和独立消费边界。
- [GitHub Releases](https://github.com/gycrosskit/jverification/releases)：版本及发布归档。
- [GitHub Issues](https://github.com/gycrosskit/jverification/issues)：提供平台、组件/SDK 版本、阶段、脱敏错误码与最小复现。

已有记录覆盖远程 Maven / OHPM 产物消费、iOS Simulator Framework 链接、Swift SDK 编译和行为替身测试。真实 SIM/运营商认证、授权页、采集策略与后端换票仍需宿主验收。

自有源码使用 [Apache-2.0](LICENSE)。极光 SDK 通过 Maven、CocoaPods、OHPM 依赖引入，遵循厂商许可；仓库不复制 SDK 二进制。

## 0.1.2 本轮测试与远程验收

2026-10-05：本轮自有源码和公开 API 审查、关键回归与受影响平台编译通过；真实 JitPack `0.1.2` 的最终标签提交、9 个 publications 的 POM/Module、所有变体文件大小与四种声明哈希、内部精确版本及 available-at 均通过。Release Maven 归档重新下载 SHA-256 为 `1514c9290fa7ba02bf82e1b59c8ace43d4481d0fa30ff20bb5b1169c9f66349a`。公开 MD5/SHA-1 sidecar 通过；SHA-256/SHA-512 sidecar 的 HTTP 404 记录为渠道缺失。

干净消费工程使用固定远程版本，没有本地 Maven、includeBuild 或其他组件源码替代；通过现有入口的 Android/iOS / OHOS / JVM 编译和相应最终链接。 JitPack 顶层 component.url 改写地址返回404，实际变体/available-at与真实消费者正常；未创建伪坐标掩盖此字段。

完整回归范围、精简原则、注释契约与仍需设备/业务验收的边界见 [14 个功能组件测试与 API 审查](https://github.com/gycrosskit/.github/blob/main/docs/组件测试与API审查.md)。源码测试与远程消费不代替真机和厂商业务验收。

## 自动回归

PR 和 `main` push 运行 `contracts`、`android`、`native`：复用现有发布检查器测试、Node OHOS 行为测试、JVM/Android 单测、Swift 回调契约、iOS Simulator 单测及 iOS/OHOS Kotlin 编译。Node 测试使用 TypeScript transpile 与 mock SDK，只覆盖回调协议。

`Release validation` 在 Release 发布或手动填写精确 Maven tag 时下载归档，检查 `release-checksums.txt` 的 SHA-256、POM/Module 和各变体文件；随后独立消费工程直接从 JitPack 编译 Android、iOS 和 OHOS，iOS Simulator 链接 Framework。不使用 `mavenLocal`、本库源码或归档作为消费依赖；缺失版本/产物直接失败。CI 不发布二进制、不访问业务 SDK 服务。

GitHub-hosted `ubuntu-24.04` 和 `macos-15` 的实际结果以 Actions 为准；没有 DevEco/ohpm runner，因此 HAR 构建、ohpm Registry 安装、完整原生 SDK 集成及真机业务验收仍按现有验证文档执行，不能由这些 job 的成功代算。

PR 的远程验收固定使用已发布 `0.1.3` 作为回归基线，验证 CI 检查器及消费工程；这不代表 PR 候选源码已经发布。正式 Release 事件始终使用事件自己的精确 tag，手动运行也必须填写精确已发布版本。

公网核验同步组织 `templates/check-public-maven.py`：使用冻结归档给出的完整 publications 清单，核对 JitPack tag/commit、每个公开 POM/Module、全部声明变体字节大小和四类哈希、内部精确版本及 `available-at`；MD5/SHA-1 sidecar 必须匹配。SHA-256/SHA-512 sidecar 的 HTTP 404 单独输出为渠道缺失，不计为校验通过。

源码 CI 使用 JDK 17；已发布 `0.1.3` 的 JVM JAR 实测 class major 65，需要 Java 21。当前远程 CI 覆盖 Android/iOS/OHOS，未独立消费 JVM 变体，不能把源码 `jvmTest` 成功解释为已发布 JVM 支持 Java 17。
