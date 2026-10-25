# 工作实录

按时间倒序记录关键决策与验证结果。用于回溯「为什么当初这么做」。

---

## 2026-10-24 · 统一门户一次登录 + 课表/成绩静默同步

### 目标

解决成绩同步仍像旧课表一样跳网页、直接打开 `/jwglxt/` 落到正方登录页的问题。要求设置页登录统一门户一次后，课表与成绩都无感更新。

### 实现

- 新增 `CampusSyncClient`：隐藏 WebView 复用 Cookie，从办事大厅自动点击「学生课表查询 / 学生成绩查询」；接住 `window.open`；课表读 `kbList`，成绩触发 `search_go` 后读 `tabGrid` DOM。
- 新增 `CampusUrlPolicy`：纯 Java 改写裸校内 IP，保留 path/query/fragment；JVM 单测可验证。
- `WebShellActivity` 检测办事大厅登录标记，提前写入 `portal_session_ready`；会话失效时清除标记。
- `MainActivity` / `GradesActivity` 改为后台同步；透明 WebView 保持 `VISIBLE`，Activity 销毁时 `cancel()`。
- 文案收敛为「登录统一门户」「后台静默更新」。

### 验证

- `testDebugUnitTest`：88 tests，0 failures；`assembleRelease` 成功。
- APK：`app/build/outputs/apk/release/app-release.apk`，包名 `com.ecut.campus`，versionName `0.1`，v2 签名验证通过。
- **2026-10-24 真机复验（开发者真机 / Android 16）**：设置页登录后，课表与成绩同步全程停留 App 内，不跳网页。
- 课表从缓存的 aTrust 节点直达教务：`Timetable saved: 6 courses (14 records)`。
- 成绩页首屏原来只加载 15 行，导致四个学期不全。改为把 jqGrid 每页设为 500 并触发 `reloadGrid` 后，真机一次读取 57 行：
  `2025-2026-2=14, 2025-2026-1=16, 2024-2025-2=14, 2024-2025-1=13`。
- 成绩同步改为**以教务全量结果覆盖本地**，不再合并旧数据；因此旧演示课程会被清除。真机结果：`57 门课程 · 4 个学期`。
- 学期胶囊在 1260px 真机首屏完整显示四个学期，并保留横向滚动。

### 关键约束

- 不保存账号、密码或 Cookie 明文，只保存登录状态标记与代理节点。
- 不修改 DNS 三文件。
- `timetable.json` / `grades.json` 顶层结构未改。

## 2026-10-23 下午 · 数据层摸底完成

### 目标

为 v1.0「原生课表」确定数据来源。原计划解析页面 DOM，真机验证后改为**直接调接口**。

### 课表：JSON 接口（成功）

```
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151
xnm=2026&xqm=3&kzlx=ck
```

返回 `kbList[]`，14 条记录。字段映射见 [DATA-SCHEMA.md](DATA-SCHEMA.md)。

**关键结论：**

1. **一个时段 = 一条记录**。地球化学 3 条（周一/周三/周五），矿床学 3 条。
2. **聚合主键用 `jxb_id`**（教学班 ID），不是课程名——可能有同名课。
3. **同一门课教室会变**。地球化学周一在 4-305，周三/周五在 4-422。→ `location` 放 `Session`。
4. **`jcor` 是固定两节一组**：`1-2` / `3-4` / `5-6`。
5. **`zcd` 本机样本全是单区间**（`10-13周`），但解析器必须兼容多区间、单双周、离散周。

### 成绩：jqGrid 服务端分页（部分成功）

页面：`/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default`

**踩坑记录：**

| 尝试 | 结果 |
|---|---|
| `GET/POST ...?doType=query` 直接请求 | 返回非预期结构 |
| `$('#tabGrid').jqGrid('getGridParam','data')` | **返回 0 条**，服务端分页不缓存前端 |
| 读 `tabGrid` DOM | ✅ 成功，16 行数据 |
| 猜 `xqm=16` 为「全部学期」 | ❌ 无效，真实选项只有 `3` / `12` / 空 |

**最终方案：读 DOM，按列索引映射。**

`tabGrid` 39 列，关键索引：`[4]`课程名、`[5]`性质、`[6]`学分、`[8]`绩点、`[22]`成绩。

### 验证过程中发现的 UI 问题（未修）

- `applyMobileAdaptation` 注入的 `table-layout:fixed` 对课表无效，课程名仍被挤成竖排单字
- 根因：课表 DOM 结构不是 `<table>`，是 `ylkbTable` 这类 DIV 容器

**但 v1.0 不走 DOM 渲染，此问题对原生课表无影响。**

### 产出

- [docs/DATA-SCHEMA.md](DATA-SCHEMA.md) — 补全两个接口的真实字段、参数、列索引
- `app/src/test/resources/fixtures/jwglxt_kb_sample.json` — 14 条课表样本（已脱敏）
- `app/src/test/resources/fixtures/jwglxt_grade_sample.json` — 2 条成绩样本（已脱敏）

---

## 2026-10-23 上午 · 真机验证与 v0.2.0 收尾

### 验证结果（开发者真机 / Android 16）

| 功能 | 结果 | 证据 |
|---|---|---|
| DNS 广告过滤 | ✅ 生效 | logcat 记录 25+ 广告域名被 BLOCK，规则 54/82/190 全部加载 |
| 课表直达 | ✅ 命中 | `direct ok` 日志，1.6 秒加载完成 |
| 成绩查询 | ✅ 命中 | 同上 |
| 自主选课 | ✅ 命中 | 同上 |

### 关键发现

**1. `gnmkdm` 参数是必需的**

正方教务页面 URL 缺少 `?gnmkdm=<菜单ID>&layout=default` 时，服务端返回「系统升级维护中」。这不是学校改版，是参数缺失。

- 课表 `N2151`
- 成绩 `N305005`
- 选课 `N253512`

**2. `index_initMenu.html` 入口页是坏的**

原设计「先加载入口页建会话，2.2 秒后跳目标页」。真机日志显示入口页本身会被 aTrust 弹回应用中心，造成无限循环。

改为**直接加载目标页**，1.6 秒命中，无需入口页。

**3. aTrust 代理节点格式**

`172-20-130-13.atrust.ecut.edu.cn`（IP 用横杠分隔）。原正则正确，抓取也正常。

但应用中心页面的卡片是 `<div @click>` 而非 `<a href>`，`scrapeAppCenterUrls()` 抓不到链接。最终方案：由用户在应用中心手动点进教务，`maybeCaptureHost` 从 URL 里嗅探节点，然后自动续跳目标页。

**4. 页面弹回是会话失效信号**

目标页加载后 URL 不含 `/jwglxt/` → 判定会话失效 → 清空缓存的节点 → 退回应用中心重新嗅探。这是原设计注释里承诺但从未实现的兜底。

### 架构问题记录

| 问题 | 现状 | 计划 |
|---|---|---|
| 每次打开都要等 1~2 秒代理握手 | 未解决 | v1.0 本地缓存课表/成绩 |
| 网页界面在手机上挤压变形 | 已缓解（注入移动端 CSS） | v1.0 原生渲染 |
| `observe` 规则无界面开关 | 已修复 | — |
| `onCreateWindow` 子 WebView 无法关闭 | 已修复（改为同窗口加载） | — |
| 顶栏被系统状态栏颜色污染 | 已修复（浅色状态栏） | — |

---

## 2026-10-22 · 初始版本 v0.1.0

### 做了什么

1. WebView 壳 + 多窗口 + Cookie 持久化
2. `DnsFilterService`：仅路由 `10.0.0.1/32` 的 DNS 过滤
3. 手写 DNS 报文解析与伪造应答（返回 `0.0.0.0` / `::` 而非 NXDOMAIN）
4. 域名规则三集合（`HashSet` 后缀匹配）

### 规则从哪来

从公开广告 SDK 域名样本中提取 URL 域名（325 个），用语义分类做 allow/block/observe 三级归类：

- 关键词分类已证明不准确，仅作初筛
- Jev 的 `choice` 模式准确率明显高于多个独立 `noul`
- 提示词里绝不能暗示答案（写「路径含 /ad/ 是强信号」会把淘宝也判成广告）
- 置信度 < 0.5 进人工复核队列

最终产出：54 block / 82 observe / 190 allow。

### 为什么只接管 DNS

如果走全流量 VPN（`0.0.0.0/0` + tun2socks），会：
- 影响依赖高德 SDK 定位的第三方 App（网络路径敏感）
- 增加电量消耗
- 实现复杂度和崩溃面大幅上升

只把 `10.0.0.1/32` 接进 tun，业务流量完全走物理网卡，风险最小。
