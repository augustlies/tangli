# 数据格式规范

### STEP 2：学校所有权与旧数据迁移

课表主源 `timetable_semesters.json`、兼容镜像 `timetable.json` 和成绩
`grades.json` 存于 `filesDir/schools/<owner 的 UTF-8 SHA-256 十六进制>/`，JSON 内容格式不变。
内置学校 owner 为学校 ID；自定义学校为 `custom:<小写教务主机>`，同主机的来源识别升级不改变归属。
首次访问以 `school_files_owner.json` 记录当时选中的学校，只有该 owner
能复制旧根目录数据；切校前先完成旧学校认领。旧文件保留为升级备份。
每个目录的 `migrated` 标记防止清空后再次导入旧文件；迁移失败不回退读取公共文件。
切校不会合并两校课程，切回恢复各自归档与成绩。切校/清空递增内存代次，
同步结果必须同时匹配捕获的学校与代次；取消/超时也会关闭客户端回调入口。
正方成绩仅在查询完成、每页 500、第一页且无剩余页、全部学年/学期、
DOM 行数及解析条数等于服务端 records 时整体覆盖；零条或超过 500 条保留本地数据。

本文定义 App 内部持久化与对外导入导出的 JSON 结构。**导入导出格式即本文件定义的结构**，保证换机、备份、分享互通。

设计原则：

1. **显式版本号**：每个文件顶层带 `schemaVersion`，解析时校验，未知版本拒绝导入并提示。
2. **课程与时间分离**：课程实体（`Course`）与上课时段（`Session`）拆开，一门课可有多个时段（理论课 + 实验课）。
3. **周次用区间表示**：`[1,16]` 而非布尔数组，紧凑且可读。
4. **保留原始来源**：`source` 字段标记数据来自教务同步还是手动添加，手动添加的不会被同步覆盖。

---

## 0. 数据来源：正方教务真实接口（已真机验证）

**2026-10-23 真机抓取确认**，v1.0 的同步直接调用这两个接口，不走 DOM 解析。

### 课表接口

```
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151
Content-Type: application/x-www-form-urlencoded
X-Requested-With: XMLHttpRequest

xnm=2026&xqm=3&kzlx=ck
```

- `xnm` 学年，`xqm` 学期（`3` = 第 1 学期，`12` = 第 2 学期，`16` = 第 3 学期）
- 返回 JSON，课表在 **`kbList`** 数组；`sjkList` 是实践课，暂不使用

### 成绩接口

**真机确认（2026-10-23）**：成绩页是 jqGrid 服务端分页表格，`getGridParam('data')` 拿不到数据，**必须读 DOM**。

页面 URL：
```
/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default
```

筛选参数（页面 `<select>` 的真实 name/value）：

| 控件 ID | name | 合法值 | 含义 |
|---|---|---|---|
| `xnm` | `xnm` | `2026` / `2025` / `2024` / … / 空 | 学年，**纯起始年份**，空=全部 |
| `xqm` | `xqm` | `3` / `12` / 空 | 学期，`3`=第1学期，`12`=第2学期，空=全部 |
| `kcbjdm_cx` | `kcbjdm` | `0` / `1` / `2` / `3` / `4` / `8` / 空 | 课程标记，`0`=主修、`1`=辅修、`2`=二专业、`3`=二学位、`4`=非学位、`8`=二学位转一专 |

查询按钮：`id="search_go"`，可用 `document.getElementById('search_go').click()` 触发。

表格 ID：**`tabGrid`**

### `tabGrid` 列索引映射（39 列，真机确认）

| 索引 | 内容 | 样本 |
|---|---|---|
| 0 | 查看（链接） | `查看` |
| 1 | 学年 | `2024-2025` |
| 2 | 学期 | `1` |
| 3 | 课程代码 | `DK1101ZB` |
| **4** | **课程名称** | `专业导论` |
| **5** | **课程性质** | `必修` |
| **6** | **学分** | `1.0` |
| 7 | 成绩备注 | 空 |
| **8** | **绩点** | `3.80` |
| 9 | 成绩性质 | `正常考试` |
| 10 | 是否学位课 | `否` |
| 11 | 开课学院 | `地球与行星科学学院…` |
| 12 | 课程标记 | `主修` |
| 15 | 教学班 | `专业导论-0024` |
| 16 | 任课教师 | `教师A`（`补考一` 可能为 `无`） |
| 17 | 考核方式 | `考试` / `考查` / 空（列偏移时回退为教师） |
| 18 | 学号 | `2024000000` |
| 19 | 姓名 | `张三` |
| **22** | **成绩（数值）** | `88` |
| 23 | 是否成绩作废 | `否` |
| 25 | 学分绩点 | `3.80` |
| 27 | 百分制成绩 | `88` |
| 28 | 学年（纯数字） | `2024` |
| 29 | 学期（纯数字） | `3` |
| 30 | `jxb_id` | 教学班内部 ID |
| 31–33, 38 | 内部 ID | 用于「查看」详情 |

**重要**：表头在 `ui-jqgrid-htable` 里，`tabGrid` 第一行是空的。**按列索引映射，不要按表头文字匹配**（表头文字可能随学校改版）。

### 成绩解析注意事项

1. **`[22]` 成绩可能是文本**：`优秀`、`合格`、`缓考`、`缺考` 等，需 `tryParseDouble` 失败时保留原文
2. **`[8]` 绩点可能为空**：非百分制课程通常没有绩点
3. **服务端分页**：默认每页 15 条，需要在下拉框 `currentPage` 改成 500 才能一次拿全
4. **DOM 读取时机**：必须等 AJAX 完成，建议监听 `tabGrid` 的 DOM 变化或延迟 3~5 秒

### `kbList[]` 字段映射（真机样本）

| 正方字段 | 示例 | 映射到 | 说明 |
|---|---|---|---|
| `kcmc` | `地球化学` | `Course.name` | 课程名 |
| `jxb_id` | `544E7DFB...` | `Course.key` | **教学班 ID，聚合主键** |
| `xm` | `教师C` | `Course.teacher` | 教师（多人用逗号分隔） |
| `xf` | `3.0` | `Course.credit` | 学分 |
| `xqj` | `1` | `Session.dayOfWeek` | 星期（1=周一） |
| `jcor` | `3-4` | `Session.startSection/endSection` | 节次，比 `jc`（`3-4节`）更好解析 |
| `zcd` | `5-12周` | `Session.weeks` | 周次 |
| `cdmc` | `4-305` | `Session.location` | 教室（**每个时段可能不同**） |
| `xqmc` | `广兰校园` | `Session.campus` | 校区 |
| `kclbmc` | `多媒体教室` | `Session.roomType` | 场地类别 |
| `khfsmc` | `未安排` | `Course.examType` | 考核方式 |
| `kcbj` | `主修` | `Course.category` | 课程标记 |

### 真机样本的 3 条关键结论

**1. 一个时段 = 一条记录，必须聚合**

「地球化学」返回 3 条（周一 4-305 / 周三 4-422 / 周五 4-422），「矿床学」返回 3 条。
→ 聚合主键用 **`jxb_id`**（教学班 ID），不是课程名（可能有同名课）。

**2. 同一门课的教室在不同时段会变**

「地球化学」周一在 4-305，周三/周五在 4-422。
→ `location` 必须放在 `Session` 而非 `Course`。

**3. `zcd` 的已知格式与需兼容的格式**

本机样本全部是 `N-M周` 单区间：
```
10-13周  5-12周  5-8周  6-10周  6-11周  7-13周  9-16周
```
解析器**必须同时兼容**以下格式（未在本机出现，但正方普遍存在）：

| 格式 | 示例 | 含义 |
|---|---|---|
| 单区间 | `5-12周` | 第 5 到 12 周 |
| 多区间 | `1-5周,7-12周` | 逗号分隔 |
| 单双周 | `1-16周(单)` 或 `1-16周(双)` | 隔周 |
| 离散周 | `1,3,5,7周` | 列举 |
| 单周单节 | `3周` | 仅一周 |

### `jcor` 格式

固定两节一组：`1-2` / `3-4` / `5-6` / `7-8` / `9-10` / `11-12`。
解析：`jcor.split("-")` → `[start, end]`。

---

## 1. 课表存储：主源与镜像

`filesDir/timetable_semesters.json` 是**唯一可写的课表主源**：

```json
{
  "schemaVersion": 1,
  "activeSemesterId": "2026-2027-1",
  "timetables": ["一个或多学期 Timetable 对象"]
}
```

`filesDir/timetable.json` 是当前学期的**只读镜像**，保留旧调用方兼容：

- 主源写入成功后，由 `SemesterArchive` 刷新镜像；镜像不能再反写主源。
- 老版本只有 `timetable.json` 时，首次读取会把该学期导入主源；旧镜像只补归档 JSON 中完全缺失的日期/总周数，已有值（包括 20 周）一律保留。
- `timetables: []` 表示已初始化且明确为空的主源（例如删除最后一个学期），不是待迁移状态；此时旧镜像不得重建学期，也不得作为当前课表回退。已有归档同样不得从镜像追加已删除学期；同学期缺失元数据的兼容补齐仍保留。
- `Prefs.semester_start_date` / `semester_total_weeks` 是兼容缓存，只由主源刷新。
- 设置页保存日期/总周数时只写主源，再刷新镜像与缓存。
- 总周数统一范围为 1-30 周；教务同步只更新课程，不改主源里的日期和总周数。

### 当前学期镜像 `timetable.json`

```json
{
  "schemaVersion": 1,
  "semester": {
    "id": "2026-2027-1",
    "name": "2026-2027 学年第一学期",
    "startDate": "2026-09-01",
    "totalWeeks": 20
  },
  "courses": [
    {
      "key": "c_1a2b3c",
      "name": "地学信息三维可视化",
      "teacher": "张三",
      "location": "教302",
      "credit": 2.0,
      "colorIndex": 0,
      "source": "jwglxt",
      "note": "",
      "sessions": [
        {
          "dayOfWeek": 1,
          "startSection": 1,
          "endSection": 2,
          "weeks": [1, 16],
          "weekParity": "all",
          "location": "",
          "teacher": ""
        }
      ]
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `schemaVersion` | int | 固定 `1`，用于未来迁移 |
| `semester.id` | string | 学期唯一标识，格式 `学年-学期`，如 `2026-2027-1` |
| `semester.startDate` | string | 第 1 周周一日期，`yyyy-MM-dd`。用于计算「今天第几周」 |
| `semester.totalWeeks` | int | 学期总周数，通常 20 |
| `courses[].key` | string | 稳定 ID。教务同步的用 `jwglxt_<hash>`，手动的用 `custom_<uuid8>` |
| `courses[].colorIndex` | int | 0-7，对应预设色板，用户可改 |
| `courses[].source` | string | `jwglxt` / `custom` / `import` |
| `sessions[].dayOfWeek` | int | 1=周一 … 7=周日 |
| `sessions[].startSection` | int | 起始节次，从 1 开始 |
| `sessions[].endSection` | int | 结束节次，含 |
| `sessions[].weeks` | [int,int] | `[起, 止]` 闭区间。单周课写 `[3,3]` |
| `sessions[].weekParity` | string | `all` / `odd` / `even`，与 `weeks` 叠加生效 |

### 同步合并规则

同步时按课程 `key` 匹配：

- 教务返回的课程 `key` 存在 → **覆盖**该课程的 `name/teacher/sessions`，保留用户的 `colorIndex` 与 `note`
- 教务返回的 `key` 不存在 → 新增
- 本地有但教务没有，且 `source == "jwglxt"` → **删除**（教务已取消该课）
- 本地有但教务没有，且 `source == "custom"` → **保留**（实验课等手动添加的课程永不删除）

---

### JSON 文件导入（与教务同步分开）

- 必须是单个完整课表对象，显式包含整数 `schemaVersion: 1`、`semester` 对象及非空字符串 `semester.id`、`courses` 数组（允许明确的空数组）。
- 课程必须有非空字符串 `key`（同一备份内唯一）、`name` 和 `sessions` 数组；每个时段必须有整数 `dayOfWeek`（1-7）、1-99 的起止节次（结束不早于开始）及整数 `weeks` 数组（1-99，空数组沿用未知周次的兼容语义）。可选字段若存在，必须符合模型类型；损坏条目不得静默跳过或转换为空课表。
- 导入只对同学期按 `key` 增量合并；其他学期必须先切换，空主源允许首次导入。备份中缺少的本地课程全部保留（包括教务课程），不会执行教务全量同步的删除规则；显式空课程数组也不会清空本地。
- 同 key 更新课程内容，但保留本地备注、颜色及来源；新增课程标记 `source=import`，避免下次教务同步删除。已有学期元数据和调课记录保留，备份中的新调课记录去重追加。
- 只有主源保存成功才报告导入成功。JSON schema 版本和磁盘对象结构不变，旧文件读取仍走兼容解析器。
- JSON 备份文件最多 2 MiB；分享码文本最多 3 MiB，解压后最多 2 MiB；两种格式都最多嵌套 64 层。JSON 备份最多 500 门课、每门 100 个时段、总计 10,000 个时段、每个时段 99 个周次、1,000 条调课；分享码课表数组使用相同课程/时段上限，随码分享的作息数组最多 16 节。课程名最多 256 字符，其他导入文本字段最多 4,096 字符，教师/教室等时段字段最多 512 字符。超过上限会拒绝整个导入，不会截断后继续写入。

### 文件替换与恢复

课表归档、课表镜像、成绩及作息设置统一先写同目录 `.tmp` 并 flush/fsync，
再将旧文件改名为 `.bak` 后安装新文件；删除 `.bak` 才视为提交完成。
替换失败或启动时发现 `.bak`，优先恢复上一个已提交版本；不删除目标文件来强行完成 rename。
未提交的 `.tmp` 不作为有效数据读取；显式清空同时清理 `.bak` / `.tmp`，避免恢复出已清除的数据。
这些文件访问在进程内串行执行，兼容 Android API 24，不依赖 API 26 的 `java.nio.file`。

---

## 2. 成绩 `grades.json`

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-10-23T14:30:00+08:00",
  "summary": {
    "gpa": 3.62,
    "weightedScore": 87.4,
    "totalCredit": 68.0,
    "passedCount": 21,
    "failedCount": 1
  },
  "semesters": [
    {
      "id": "2026-2027-1",
      "name": "2026-2027 学年第一学期",
      "gpa": 3.71,
      "weightedScore": 88.2,
      "totalCredit": 22.0,
      "courses": [
        {
          "name": "地学信息三维可视化",
          "score": "92",
          "scoreValue": 92.0,
          "credit": 2.0,
          "gradePoint": 4.0,
          "nature": "必修",
          "examType": "考试",
          "isPassed": true
        }
      ]
    }
  ]
}
```

### 字段说明

| 字段 | 说明 |
|---|---|
| `score` | 原始文本，可能为「优秀」「合格」「缓考」等非数字 |
| `scoreValue` | 数值分，非数字成绩为 `null` |
| `gradePoint` | 绩点，可能为 `null` |
| `nature` | 课程性质：必修 / 选修 / 限选 / 公选 |
| `examType` | 考核方式：考试 / 考查 |
| `isPassed` | 是否通过。`score` 为「合格」类文本时按语义判断 |

### 计算口径

- **加权平均分** = `Σ(scoreValue × credit) / Σ(credit)`，仅统计有数值分的课程
- **GPA** = `Σ(gradePoint × credit) / Σ(credit)`，仅统计有绩点的课程
- 非数字成绩（优秀/合格）不参与计算，但在列表中正常显示

> 口径可能与学校官方略有差异，界面需标注「仅供参考」。

---

## 3. 通用约定

- 编码：UTF-8，无 BOM
- 时间：ISO 8601 带时区
- 文件命名：主源 `timetable_semesters.json`；镜像 `timetable.json`；`grades.json`
- 导出路径：`Download/Tangli/`
- 导入时对 `schemaVersion > 1` 的文件拒绝并提示「请升级 App」

---

## 4. 多学校（v1.2.4 起）

`semester` 与 `courses` 结构对学校无感知，学校差异只在「从哪里抓、怎么解析」。

### 学校配置 `assets/schools.json`

```json
{ "schemaVersion": 1, "dataVersion": "2026.10.26-1",
  "schools": [{
    "id": "ecut", "name": "东华理工大学",
    "vendor": "zhengfang",            // zhengfang | qiangzhi | other
    "jwBase": "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/",
    "access": "atrust",               // direct | atrust | webvpn
    "loginUrl": "https://atrust1.ecut.edu.cn/portal/#/app_center",
    "hosts": ["*.ecut.edu.cn", "172.20.130.13"],   // *.x 同时匹配 x 本身
    "verified": true, "ecutFeatures": true }]}
```

- 解析：`data/SchoolProfile.parseList`，坏条目跳过，整份坏掉返回空列表；读不到时 App 内置东华理工兜底。
- 当前学校：SharedPreferences `school_id`（缺省 `ecut`，老用户升级无感）；自定义学校存 `custom_school`（`SchoolProfile.toJson()`，id 固定为 `custom`）。
- 自定义学校由 `SchoolProfile.custom(网址, 厂商或 null)` 生成：正方 `jwBase` 截到 `/jwglxt/`（部署在根目录时截到 `/xtgl/` 之前），强智截到 `/jsxsd/`；白名单 = 该主机 + 学校主域名（`*.abc.edu.cn`）。

### 课表来源 `data/TimetableSource`

```java
interface TimetableSource {
    SchoolProfile.Vendor vendor();
    int detect(String url, String html);          // 0-100，纯函数
    String timetableUrl(SchoolProfile p);         // 后台同步打开的页面
    boolean isLoggedInPage(String url);
    boolean isLoginPage(String url);
    String extractionJs(SchoolProfile p, String xnm, String xqm); // 在已登录 WebView 里取原始文本
    JwglxtFetcher.Result parse(String raw);       // 纯 Java 解析
}
```

- `ZhengfangSource`：kbList JSON（`xskbcx_cxXsKb`，取不到时再试 `cxXsgrkb`），解析仍是 `JwglxtFetcher`。东华理工的同步 JS 与流程保持在 `CampusSyncClient` 原样不动。
- `QiangzhiSource`：`jsxsd/xskb/xskb_list.do` 的 `#kbtable`，只解析 `div.kbcontent`，星期按表头 → div id → 列序，节次按 `[01-02节]` → 行表头。课程 key 为 `qiangzhi_<课名>_<教师>`，source 仍记 `jwglxt`（重新导入会替换）。
- `TimetableSources.detect`：所有来源打分取最高，低于 40 视为认不出。
- 认不出时可导出页面结构样本（`PageSample`，只含标签/id/class/字段名，去中文、长数字、邮箱、查询参数），存 `diag/page_sample.json` 并附在诊断日志里。
- 成绩只支持正方（`SchoolProfile.supportsGrades()`）。


---

## 5. 小玩意本地设置 `SharedPreferences("fun")`（v1.2.6 起）

只存在本机，不参与同步、导出和分享码；`fun/FunPrefs` 读写，逻辑在 `data/`（纯 Java）。

| key | 类型 | 说明 |
|---|---|---|
| `grade_verdict` | boolean，缺省 `true` | 翻牌批语开关（`data/GradeVerdict`） |
| `tuition` | string，缺省空 | 用户填的每学期学费原文，`ClassPrice.parseTuition` 解析；空 = 不显示单价 |
| `absence_<学期 id>` | string（JSON） | 缺勤记录，按学期分开（`data/AbsenceBook`） |

缺勤记录 JSON：

```json
{ "高等数学a": { "l": 1, "a": 0, "c": 2 } }
```

- key 为规范化课名（`CourseColorAssigner.normalizeName`，与配色合并同一规则），所以重新同步、课程 key 变化不丢；课名改了就对不上
- `l` 请假、`a` 缺勤、`c` 被点名，都 ≥ 0；三项全 0 的条目不写
- 上限 = 本学期该课（同名所有时段）在 1..总周数 内的总课次 ÷ 3 向下取整；`l + a` 计入，`c` 只计数；`l + a` ≥ 上限的 80% 时课表描红边


## 手动考试倒计时

用户设置的考试倒计时独立存于当前学校目录 `exam_countdowns.json`，按 `学期 ID|课程 key` 隔离；记录包含显示名称和 `yyyy-MM-dd` 日期。与课表文件分离可确保教务同步、导入合并不会覆盖用户设定。升级旧版本时该文件首次为空，既有课表无需迁移；课程名自动识别的倒计时由课表学期起始日、周次和星期动态计算，不写入/改写课表 JSON。
