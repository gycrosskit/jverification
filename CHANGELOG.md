# 更新记录

## 1.0.4

- Maven、iOS Git Pod 与 Harmony 私 OHPM 的候选版本统一为 `1.0.4`；旧 `1.0.3` Tag、资产和发布记录保留，不覆盖。
- 修复 JitPack bootstrap 在 Python 3.5 的 UTF-8 JSON 和路径 API 兼容；来源、重复字段、摘要、尺寸与解包路径校验保持。元数据仍在项目打包时正规化，远程安装不重复改写冻结制品。
- 生产代码与厂商依赖不变，保留 iOS `2000`/`6001` 为失败、`6002` 为取消的既有修复。当前为待发布候选，本地、发布与远程消费结果分别记录。

## 1.0.3

- Maven、iOS Git Pod 与 Harmony 私 OHPM 的源码版本统一为 `1.0.3`；本节为待发布记录，实际发布和远程消费分别验收。
- 仅更新版本声明、接入文档及验证工程默认版本；不修改业务代码、厂商 SDK/API 或 Kuikly 依赖。保留当前 iOS `6001`/`2000` 为失败、`6002` 为取消的既有分类。
- 所有 `1.0.2` Tag、资产和记录保留，不删除 JitPack 缓存、不覆盖既有版本。

## 1.0.2（版本对齐候选）

- Maven 默认版本与既有 Native/HAR 版本号对齐到 `1.0.2`；Maven 尚未发布，现有稳定版本仍为 `1.0.0`。
- 回流既有 `native-1.0.2` 的 iOS 错误码修复：仅 `6002` 为取消，`6001` 与 `2000` 为失败；保留厂商码，非 TOKEN 结果不返回凭据。厂商 SDK/API 版本不变。
- 继续沿用不可变 `native-1.0.2`；数字对齐不代表各渠道历史源码完全相同。
- Harmony 后续仅发布受信私 OHPM，当前私库尚无本包；历史 Tag、制品和摘要保持不变。

## Native 1.0.1 / HAR 1.0.1

- iOS 清理去除重复 cancel/clearCache 调用，保留 owner、撤销同意、close 终态及迟到 Token 隔离。
- Pod 改用独立 `native-1.0.1` Tag；Renderer Pod/HAR 由宿主选择 Kuikly 范围，Maven 复用 `1.0.0`。
- Native/HAR 状态以精确 Release/Registry 及消费收据为准；不把编译或契约检查当作真实运营商验收。

## 1.0.0（未发布）

- Maven、iOS Pod 与 OHPM 统一版本为 1.0.0；同仓模块使用同版，Kuikly 配套保持 2.29.1。
- 本地源码/产物消费与设备、远程发布验收分别记录；不覆盖旧标签或修改历史发布字节。

## 0.1.3（已发布）

- Swift SDK6000回包拒绝空字符串/纯Unicode空白token，与KMP契约一致，合法token原文保留。
- 直接编译完整生产Swift callback回归通过，core JVM8项通过；完整10个生产文件覆盖见[审查记录](docs/完整源码审查.md)。
- Maven/Git Pod/Podspec发布0.1.3，HAR保持0.1.0；真实新Git Pod完整SDK/App链接、精确远程文件核验、新目录Maven最终消费及全远程Framework/Swift桥typecheck通过，设备认证未执行。
