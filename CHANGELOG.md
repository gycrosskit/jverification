# 更新记录

## 0.1.3（候选，未发布）

- Swift SDK6000回包拒绝空字符串/纯Unicode空白token，与KMP契约一致，合法token原文保留。
- 直接编译完整生产Swift callback回归通过，core JVM8项通过；完整10个生产文件覆盖见[审查记录](docs/完整源码审查.md)。
- Maven/Git Pod/Podspec候选0.1.3，HAR保持0.1.0；待真实Pod ABI/link及新候选远程核验。
