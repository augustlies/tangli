# 数据格式规范

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
| 16 | 教学班 | `专业导论-0024` |
| 17 | 教师 | `教师A` |
| 19 | 学号 | `2024000000` |
| 20 | 姓名 | `张三` |
| **22** | **成绩（数值）** | `88` |
| 23 | 是否重修 | `否` |
| 27 | 绩点（重复） | `3.80` |
| 28 | 课程编号 | `01` |
| 29 | 学年（纯数字） | `2024` |
| 30 | 学期（纯数字） | `3` |
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
      "id": "c_1a2b3c",
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
| `courses[].id` | string | 稳定 ID。教务同步的用 `jwglxt_<hash>`，手动的用 `custom_<uuid8>` |
| `courses[].colorIndex` | int | 0-7，对应预设色板，用户可改 |
| `courses[].source` | string | `jwglxt` / `custom` / `import` |
| `sessions[].dayOfWeek` | int | 1=周一 … 7=周日 |
| `sessions[].startSection` | int | 起始节次，从 1 开始 |
| `sessions[].endSection` | int | 结束节次，含 |
| `sessions[].weeks` | [int,int] | `[起, 止]` 闭区间。单周课写 `[3,3]` |
| `sessions[].weekParity` | string | `all` / `odd` / `even`，与 `weeks` 叠加生效 |

### 同步合并规则

同步时按 `id` 匹配：

- 教务返回的课程 `id` 存在 → **覆盖**该课程的 `name/teacher/sessions`，保留用户的 `colorIndex` 与 `note`
- 教务返回的 `id` 不存在 → 新增
- 本地有但教务没有，且 `source == "jwglxt"` → **删除**（教务已取消该课）
- 本地有但教务没有，且 `source == "custom"` → **保留**（实验课等手动添加的课程永不删除）

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
