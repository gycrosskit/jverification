# 更新记录

## 1.0.2（HAR 候选）

- 补齐包内当前版本更新记录，修复 OHPM 提交时缺少当前版本 CHANGELOG 的拒绝。
- 延续上一版组件能力及宿主可选的 Kuikly Renderer 范围；API、厂商依赖与生命周期行为不变。
- 使用新不可变 HAR 版本，不覆盖旧标签或制品；构建、公开消费、审核与 Registry 安装分别验收，当前尚未发布。

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
