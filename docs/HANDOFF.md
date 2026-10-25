# 工程交接文档（HANDOFF）

> **给接手的 Agent：先读完这份文件再动手。**
> 这里记录的是**已经真机验证过的事实**，不要凭常识推翻它。
> 本项目由单人开发、自用，优先「改得动、不崩」而不是「架构优雅」。

最后更新：2026-10-24

> **课表主界面重构已实现；统一门户一次登录后的课表/成绩静默同步已在 开发者真机 真机通过。成绩全量抓取为 57 条 / 4 个学期，全程不跳网页。** 交互方案见
> [`docs/TODO_TIMETABLE_REDESIGN.md`](TODO_TIMETABLE_REDESIGN.md)。

---

## 1. 项目是什么

**烫梨（Tangli）** —— 东华理工大学学生的 Android 校园工具，包名 `com.ecut.campus`（包名为历史遗留，对外名称统一为烫梨/Tangli）。

两块功能：

1. **本地 DNS 广告过滤**（VpnService，无需 root）—— ✅ 已完成，真机验证
2. **教务系统直达 + 课表本地化**（正方教务 / aTrust 代理）—— 🚧 进行中

目标用户只有作者本人。学校系统随时改版，所以配置必须能在 App 内改。

---

## 2. 当前状态

### 已完成且验证

| 模块 | 状态 | 证据 |
|---|---|---|
| DNS 广告过滤 | ✅ 真机跑通 | logcat 记录 25+ 广告域被 BLOCK，规则 54 block / 82 observe / 190 allow |
| 教务直达（课表/成绩/选课） | ✅ 真机跑通 | 1.6 秒直达，`direct ok` 日志 |
| Material You 界面（4 个页面） | ✅ 已装真机 | 截图验证 |
| **课表数据层（`com.ecut.campus.data`）** | ✅ 已接入 UI | 单元测试全绿（当前全套 96 个） |
| **统一门户静默同步（课表/成绩）** | ✅ 真机验证 | 课表 6 门 / 14 条；成绩 57 条 / 4 学期，全程停留 App |

### 未完成（下一步）

- 课表主界面重构代码已完成；模拟器已验证新增回刷、五天模式重启持久化、作息联动与恢复默认、720×1280 无横向溢出，待 开发者真机 真机逐项复验（见待办第 10 节）
- 校园邮箱仅保留置灰占位，注册/登录流程未接入

---

## 3. 环境（必须照抄，否则编不过）

| 组件 | 路径 |
|---|---|
| JDK 17 | `<你的 JDK 17 路径>` |
| Gradle 8.4 | `<你的 Gradle 8.4 路径>` |
| Android SDK | `<你的 Android SDK 路径>`（已装 android-33） |
| ADB | `<你的 platform-tools 路径>` |
| 真机 | 开发者真机，Android 16 / SDK 36 / arm64-v8a，序列号 `<设备序列号>` |

项目自带 Gradle Wrapper（`gradlew` / `gradlew.bat`，Gradle 8.4）。Windows 用 `gradlew.bat`，Linux/macOS 与 CI 用 `./gradlew`。

### 编译命令

```bash
cd "<项目目录>"

export JAVA_HOME="<你的 JDK 17 路径>"
export ANDROID_HOME="<你的 Android SDK 路径>"
export PATH="$JAVA_HOME/bin:$PATH"

# 编译
"<你的 Gradle 8.4 路径>/bin/gradle" assembleRelease --console=plain

# 跑测试（96 个）
"<你的 Gradle 8.4 路径>/bin/gradle" testReleaseUnitTest --console=plain

# 装到手机
export PATH="<你的 platform-tools 路径>:$PATH"
adb shell input keyevent KEYCODE_WAKEUP   # 必须先唤醒，否则 vivo 拒绝安装
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 已踩过的坑

| 现象 | 原因 | 解法 |
|---|---|---|
| `IOException: 文件名、目录名或卷标语法不正确` | `local.properties` 里 `sdk.dir` 用了反斜杠转义 | **用正斜杠**：`sdk.dir=<你的 Android SDK 路径>` |
| `JAVA_HOME is set to an invalid directory` | `jdk17` 下还有一层 `jdk-17.0.16+8` | JAVA_HOME 指到最内层 |
| `INSTALL_FAILED_ABORTED: User rejected permissions` | vivo 熄屏自动拒绝安装 | 先 `adb shell input keyevent KEYCODE_WAKEUP` |
| `adb push` 路径变成 `C:/Users/.../sdcard/...` | MSYS 路径转换 | 命令前 `export MSYS_NO_PATHCONV=1` |
| `jarsigner` 说「jar 未签名」 | APK 用 v2/v3 签名方案 | 用 `apksigner.bat verify` 验证，不是 jarsigner |

---

## 4. 真机验证过的关键事实（不要重新试错）

### 4.1 教务系统结构

```
aTrust 统一门户  https://atrust1.ecut.edu.cn/portal/#/app_center
      ↓ 用户手动点「网上办事大厅」
办事大厅         https://ehall.ecut.edu.cn/new/index.html
      ↓ 点「教务系统」
代理后的教务     https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/...
```

**代理节点格式**：`172-20-130-13.atrust.ecut.edu.cn`（IP 用横杠分隔）。
正则：`([0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}\.atrust\.ecut\.edu\.cn)`

### 4.2 `gnmkdm` 参数是必需的

正方教务每个功能页都有菜单权限编号。**URL 缺少 `?gnmkdm=<ID>&layout=default` 时，服务端返回「系统升级维护中」**——这不是学校改版，是参数缺失。

| 功能 | gnmkdm | 完整路径 |
|---|---|---|
| 课表 | `N2151` | `/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default` |
| 成绩 | `N305005` | `/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default` |
| 选课 | `N253512` | `/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default` |

### 4.3 入口页 `index_initMenu.html` 是坏的

原设计「先加载入口页建会话，2.2 秒后跳目标页」。真机日志证明**入口页本身会被 aTrust 弹回应用中心**，造成无限循环。

**正确做法：直接加载目标页。** 有 aTrust 会话时无需入口页，1.6 秒命中。

### 4.4 会话失效判定

目标页加载后如果 URL **不含 `/jwglxt/`** → 会话失效 → 清空缓存节点 `Prefs.clearPortalHost()` → 退回应用中心重新嗅探。

### 4.5 应用中心抓不到链接

`<a href>` 只有 1 个（`#/`），卡片是 `<div @click>`。`scrapeAppCenterUrls()` 抓不到。
**最终方案**：由用户在应用中心手动点进教务，`maybeCaptureHost` 从 URL 嗅探节点，然后自动续跳目标页。

### 4.6 课表接口（JSON，最可靠）

```
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151
Content-Type: application/x-www-form-urlencoded
X-Requested-With: XMLHttpRequest

xnm=2026&xqm=3&kzlx=ck
```

- `xnm` = 学年起始年份（`2026`）
- `xqm` = `3`（第1学期）/ `12`（第2学期）/ `16`（第3学期）
- 课表在 **`kbList`** 数组；`sjkList` 是实践课（本机是 4 条空壳，暂不使用）

**`kbList[]` 字段**（真机 14 条样本）：

| 字段 | 示例 | 含义 |
|---|---|---|
| `kcmc` | `地球化学` | 课程名 |
| `jxb_id` | `544E7DFB...` | **教学班 ID，聚合主键** |
| `xm` | `教师C` | 教师（多人逗号分隔） |
| `xf` | `3.0` | 学分 |
| `xqj` | `1` | 星期（1=周一） |
| `jcor` | `3-4` | 节次（比 `jc`=`3-4节` 好解析） |
| `zcd` | `5-12周` | 周次 |
| `cdmc` | `4-305` | 教室（**每时段可能不同**） |
| `xqmc` | `广兰校园` | 校区 |
| `kclbmc` | `多媒体教室` | 场地类别 |
| `kcbj` | `主修` | 课程标记 |
| `khfsmc` | `未安排` | 考核方式 |

**三条关键结论：**

1. **一条记录 = 一个时段，不是一门课**。地球化学 3 条（周一/周三/周五）。必须按 `jxb_id` 聚合。
2. **同一门课教室会变**。地球化学周一 4-305，周三/周五 4-422 → `location` 必须挂在 `Session` 上。
3. **`jcor` 是固定两节一组**：`1-2` / `3-4` / `5-6`。

### 4.7 成绩页（jqGrid，只能读 DOM）

```
/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default
```

**筛选控件真实 name/value：**

| 控件 ID | name | 合法值 |
|---|---|---|
| `xnm` | `xnm` | `2026` / `2025` / `2024` / … / 空（空=全部） |
| `xqm` | `xqm` | `3`=第1学期 / `12`=第2学期 / 空=全部 |
| `kcbjdm_cx` | `kcbjdm` | `0`=主修 / `1`=辅修 / `2`=二专业 / `3`=二学位 / `4`=非学位 / `8` |

- 查询按钮：`id="search_go"`，可用 `document.getElementById('search_go').click()` 触发
- 表格 ID：**`tabGrid`**

**⚠️ `$('#tabGrid').jqGrid('getGridParam','data')` 返回 0 条** —— 服务端分页，前端不缓存。**必须读 DOM。**

**`tabGrid` 39 列索引映射（真机确认）：**

| 索引 | 内容 | 索引 | 内容 |
|---|---|---|---|
| 0 | 查看（链接） | 17 | 教师 |
| 1 | 学年 `2024-2025` | 19 | 学号 |
| 2 | 学期 | 20 | 姓名 |
| 3 | 课程代码 | **22** | **成绩（数值）** |
| **4** | **课程名称** | 23 | 是否重修 |
| **5** | **课程性质** | 27 | 绩点（重复） |
| **6** | **学分** | 29 | 学年（纯数字） |
| 7 | 成绩备注 | 30 | 学期（纯数字） |
| **8** | **绩点** | 31-33,38 | 内部 ID |
| 9 | 成绩性质 | 16 | 教学班 |

**表头在 `ui-jqgrid-htable` 里，`tabGrid` 第一行是空的。按列索引映射，不要按表头文字匹配。**

**真实样本：**
```
专业导论   | 必修 | 1.0 | 88 | 3.80 | 正常考试
普通地质学 | 必修 | 3.0 | 75 | 2.50 | 正常考试
```

### 4.8 静默同步链路（2026-10-24 编码）

```
设置页「登录统一门户」
      ↓ 用户在 WebShell 完成统一身份认证
办事大厅（真机为 aTrust 代理）
https://ehall-ecut-edu-cn-s.atrust.ecut.edu.cn/new/index.html
      ↓ 有缓存节点时直接进教务；会话失效才回退办事大厅卡片
正方教务（aTrust 代理）
      ↓ 课表：XHR POST xskbcx_cxXsKb.html
      ↓ 成绩：jqGrid 每页设 500，触发 search_go，读取 tabGrid DOM
timetable.json / grades.json
```

- 登录状态只保存布尔标记与代理节点，**不保存账号、密码或 Cookie 明文**；真实会话仍在 WebView Cookie 中。
- 办事大厅卡片可能返回 `http://172.20.130.13/...`，必须改写为 `https://172-20-130-13.atrust.ecut.edu.cn/...`。
- 后台 WebView 为 1×1、`alpha=0` 但保持 `VISIBLE`，避免页面被暂停；Activity 销毁时必须 `cancel()`。
- 办事大厅登录标记（`analysisUserId != guest` 或用户区可见）可提前确认会话；课表/成绩抓取成功也会更新该标记。
- **成绩必须把 jqGrid `rowNum` 改为 500 并 `reloadGrid`**：默认只渲染 15 行，会出现「有成绩但不全」。真机全量为 57 条 / 4 个学期。
- **成绩同步以教务结果整体覆盖本地**，不做历史合并，否则旧演示数据会永久残留。课表仍按 `jwb_id` 合并并保留自定义课程。

---

## 5. 代码地图

```
app/src/main/java/com/ecut/campus/
├── MainActivity.java          (281)  主页 + 4 个 Tab（伪 Fragment，setVisibility 切换）
├── WebShellActivity.java      (515)  教务 WebView 壳 + 节点嗅探 + 网页适配注入
├── TargetEditActivity.java    (151)  直达路径编辑
├── TargetStore.java           (136)  按钮配置存取（SharedPreferences + JSON）
├── Prefs.java                  (50)  SharedPreferences 封装
├── DnsFilterService.java      (267)  VpnService，仅接管 DNS
├── DnsPacket.java             (129)  手写 DNS 报文解析/构造
├── DomainRules.java           (119)  三级域名规则，HashSet 后缀匹配
└── data/                             ← 已接入 UI：静默同步落盘，主界面/成绩页直接读取
    ├── Timetable.java         (238)  课程/时段模型 + JSON 序列化
    ├── WeekRange.java         (187)  周次表达式解析
    ├── JwglxtFetcher.java     (233)  kbList 解析 + 按 jxb_id 聚合
    └── TimetableStore.java    (162)  本地原子写 + 同步合并

app/src/test/java/com/ecut/campus/data/
├── WeekRangeTest.java         (193)  23 个测试
└── JwglxtFetcherTest.java     (270)  22 个测试

app/src/test/resources/fixtures/
├── jwglxt_kb_sample.json             14 条课表样本（已脱敏）
└── jwglxt_grade_sample.json          2 条成绩样本（已脱敏）
```

### 数据层 API 速查

```java
// 解析课表接口响应
JwglxtFetcher.Result r = JwglxtFetcher.parse(jsonString);
if (r.ok()) {
    Timetable t = r.timetable;
    // r.rawRecords / r.courseCount / r.failedWeekExpr / r.warnings
}

// 周次
WeekRange.Parsed p = WeekRange.parse("5-12周");
p.weeks        // [5,6,7,8,9,10,11,12]
p.contains(7)  // true
WeekRange.isActive("5-12周", 7);   // true
WeekRange.compress(weeks);          // "1-3,8-10"

// 存储
Timetable local = TimetableStore.load(ctx);
Timetable merged = TimetableStore.merge(local, remote);
TimetableStore.save(ctx, merged);

// 查询
t.coursesOn(dayOfWeek, week);      // 指定周+星期的课程
course.isActive(week);              // 该课程某周是否上课
```

---

## 6. 核心设计决策（为什么这么做）

### 6.1 用 JSON 文件不用 Room，多学期归档是主源

`timetable_semesters.json` 是课表/学期元数据的唯一可写主源，
`timetable.json` 和 `Prefs` 的开学日期/总周数只做兼容镜像。
这样每个学期能独立记住自己的日期，单学期文件损坏也不会反向污染多学期归档。

归档中已有的日期/总周数（包括默认 20 周）优先级最高；旧镜像只能补
归档 JSON 里完全缺失的字段。教务同步只合并课程，不修改这两个设置值。
总周数统一按 1-30 周处理。

一学期几十门课、无复杂查询。JSON + 内存缓存足够，而且**导出的就是存储格式本身**，导入导出零额外代码。Room 会带来 schema 迁移负担。

### 6.2 同步用 WebView 会话，不直接 HTTP

正方教务需要 aTrust 代理会话，Cookie 在 WebView 里。直接构造 HTTP 请求要复刻整套握手，一改版就废。
**正确做法**：在 WebView 里用 `evaluateJavascript` 发 XHR（自动带 Cookie），把结果回传给原生。

### 6.3 手动课程同步时永不删除

| 情况 | 处理 |
|---|---|
| 教务有、本地无 | 新增 |
| 教务有、本地有 | 覆盖内容，**保留 `colorIndex` 与 `note`** |
| 教务无、本地 `source=jwglxt` | 删除 |
| 教务无、本地 `source=custom` | **永久保留**（实验课等） |

### 6.4 周次解析失败时保底显示

`WeekRange.parse()` 失败时返回空列表，但 `isActive()` 返回 `true`。
**宁可多显示，也不能让课程凭空消失**——用户看到"这周没课"比看到"课不见了"更难排查。

### 6.5 只接管 DNS，不做全流量 VPN

只把 `10.0.0.1/32` 接进 tun，业务流量走物理网卡。避免影响依赖高德 SDK 定位的第三方 App（网络路径敏感）。

---

## 7. 下一步

1. 真机验收课表重构：一屏 8 节、9-10 节滚动、七天/五天、左右滑周、点击编辑、空白格新增、作息时间联动、Dock 不遮挡。
2. 静默同步已真机通过；后续只需在学校改版时复验会话失效回退与成绩分页上限。
3. 回归 DNS 三文件零改动、课表/成绩同步落盘、导入导出与自定义课程保留。
4. 校园邮箱入口等官方流程明确后再接入，只放行 `email.ecut.edu.cn` 与 `authserver.ecut.edu.cn`。

---

## 8. 绝对不要做的事

1. **不要改 DNS 相关三个文件**（`DnsFilterService` / `DnsPacket` / `DomainRules`）
   真机验证可用，手写封包逻辑改动风险远大于收益。

2. **不要改成全流量 VPN 或引入 tun2socks**
   会影响依赖高德 SDK 定位的第三方 App 的跑步打卡。

3. **不要重构为 Fragment + Navigation + Room**
   3 个 Tab 用 `setVisibility` 零生命周期坑，旋转屏幕不崩（已配 `configChanges`）。

4. **不要把课表解析改成 DOM 解析**
   接口返回 JSON，比 DOM 稳定得多。

5. **不要提交密钥、密码、真实学号姓名**
   `.gitignore` 已排除 `*.keystore`、`keystore.properties`、`local.properties`。
   测试 fixture 已脱敏（`张三` / `2024000000`），保持这个状态。

---

## 9. 已知问题（不阻塞，但要知道）

| 问题 | 影响 | 状态 |
|---|---|---|
| 网页移动端 CSS 对课表无效 | 课程名被挤成竖排单字 | **不修**——v1.0 用原生渲染，不走 DOM |
| `onCreateWindow` 子 WebView | 已改为同窗口加载 | 已修 |
| `applyMobileAdaptation` 注入的 `table-layout:fixed` 无效 | 课表仍被挤压 | 同上，不修 |
| 成绩「导出」按钮是 JS 触发 | 若要导出成绩需另找接口 | 未研究 |
| `sjkList`（实践课）未解析 | 本机是 4 条空壳 | 暂不需要 |
| `jxb_id` 作为聚合主键 | 若别的学校该字段为空/重复会退化到「课程名+教师」聚合 | 多校兼容时需验证 |

---

## 10. 文档索引

| 文件 | 内容 |
|---|---|
| `README.md` | 项目门面 |
| `CHANGELOG.md` | 版本记录（0.1.0 / 0.2.0 / Unreleased） |
| `CONTRIBUTING.md` | 提交规范、代码约定 |
| `docs/ARCHITECTURE.md` | 模块职责、数据流、风险对策 |
| `docs/DATA-SCHEMA.md` | **两个接口的完整参数、字段映射、列索引** |
| `docs/DESIGN-SPEC.md` | 界面规范（色板、圆角、布局图） |
| `docs/ROADMAP.md` | v1.0 → v2.0 规划 |
| `docs/DEV-SETUP.md` | 编译环境、真机调试、踩坑记录 |
| `docs/WORKLOG.md` | **按时间倒序的工作实录，含失败尝试** |
| `docs/HANDOFF.md` | 本文件 |

---

## 11. 一句话总结

**能跑的部分别动；课表/学期元数据已统一由 `timetable_semesters.json` 落盘，
`timetable.json` 与 Prefs 只做镜像，下一步按架构清单逐项收口。**
