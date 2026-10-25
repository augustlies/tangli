# 贡献指南

## 项目定位

个人自用的校园工具，长期维护。欢迎 Issue 与 PR，但保持「简单、可用、可维护」优先于「功能丰富」。

## 提交前

1. 能编译：`gradle assembleRelease` 通过
2. 能在真机跑起来（至少启动不崩）
3. 改动涉及数据结构时，同步更新 `docs/DATA-SCHEMA.md` 并处理版本兼容
4. 在 `CHANGELOG.md` 的 `[Unreleased]` 段记录改动

## 提交信息

采用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
feat: 课表支持单双周
fix: 同步失败时不再清空本地数据
docs: 补充数据格式说明
refactor: 从 WebShellActivity 剥离 PortalRouter
style: 统一卡片圆角为 18dp
chore: 更新 Gradle 到 8.4
```

## 代码约定

- Java 17，不引入 Kotlin（保持单人维护的心智负担）
- 不为「以后可能用到」写抽象层
- 加密钥、密码、cookie、抓包文件**绝对不入库**
- DNS 相关模块（`DnsPacket` / `DomainRules` / `DnsFilterService`）改动需附真机验证记录

## 多校适配贡献

新增学校支持请提供：

1. 学校名称与教务系统类型（正方 / URP / 其他）
2. 课表页 URL 模板（`gnmkdm` 等必要参数）
3. 一份脱敏后的页面 HTML 片段（用于写解析器）
4. 新增的 `CampusFetcher` 实现

**不要提交**：真实学号、姓名、Cookie、token。

## 安全

发现安全问题（如凭据泄露、注入风险）请直接开 Issue 或邮件联系，不要公开细节。
