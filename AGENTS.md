# AGENTS.md — 给 AI Agent 的项目说明

> 本项目由单人开发、自用。**所有 Agent 动手前必须先读 `docs/HANDOFF.md`。**

## 铁律

1. **先读 `docs/HANDOFF.md` 第 4 节「真机验证过的关键事实」**。那里记的是实测结论（比如 `gnmkdm` 参数必需、`getGridParam('data')` 返回 0 条），不要凭常识推翻。
2. **不要改 DNS 相关三个文件**：`DnsFilterService.java`、`DnsPacket.java`、`DomainRules.java`。真机验证可用，手写封包逻辑改动风险远大于收益。
3. **不要把课表解析改成 DOM 解析**。正方有 JSON 接口（`kbList`），比 DOM 稳定得多。
4. **不要提交密钥**。`*.keystore`、`keystore.properties`、`local.properties` 已在 `.gitignore`，确认 `git status` 里看不到。
5. **测试 fixture 必须保持脱敏**（`张三` / `2024000000`），不要写入真实学号姓名。

## 编译

```bash
export JAVA_HOME="<你的 JDK 17 路径>"
export ANDROID_HOME="<你的 Android SDK 路径>"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleRelease --console=plain
./gradlew testReleaseUnitTest --console=plain
```

改完代码**必须跑测试**（当前 279 个，应全绿）。

## 装到真机

```bash
export PATH="<你的 platform-tools 路径>:$PATH"
adb shell input keyevent KEYCODE_WAKEUP   # 必须先唤醒，vivo 熄屏会拒绝安装
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 代码约定

- Java 17，不引入 Kotlin
- 不为「以后可能用到」写抽象层
- 数据层（`com.ecut.campus.data`）保持无 Android 依赖，便于 JVM 单测
- 多学校：学校配置在 `assets/schools.json` + `data/SchoolProfile`，课表来源实现 `data/TimetableSource`（正方 / 强智）。东华理工的同步流程（零信任 + 办事大厅兜底）不要动，其他学校走独立分支
- 改动涉及数据结构时，同步更新 `docs/DATA-SCHEMA.md` 和 `CHANGELOG.md`

## 下一步

见 `docs/HANDOFF.md` 第 7 节。`data` 包已接入 UI：课表/成绩由统一门户静默同步落盘，主界面与成绩页均直接读取本地数据。
