# 更新记录

## 0.1.3（已发布）

- Swift SDK6000回包拒绝空字符串/纯Unicode空白token，与KMP契约一致，合法token原文保留。
- 直接编译完整生产Swift callback回归通过，core JVM8项通过；完整10个生产文件覆盖见[审查记录](docs/完整源码审查.md)。
- Maven/Git Pod/Podspec发布0.1.3，HAR保持0.1.0；真实新Git Pod完整SDK/App链接、精确远程文件核验、新目录Maven最终消费及全远程Framework/Swift桥typecheck通过，设备认证未执行。
