# 架构设计

## 现状（v0.2.0）

```
com.ecut.campus
├── MainActivity          主页 + 3 个 Tab（伪 Fragment，setVisibility 切换）
├── WebShellActivity      教务 WebView 壳 + 代理节点嗅探 + 网页适配注入
├── CampusSyncClient      统一门户一次登录后的课表/成绩后台同步
├── CampusUrlPolicy       校园 URL 改写与域名白名单（纯 Java 可测）
├── TargetEditActivity    直达路径编辑
├── DnsFilterService      仅接管 DNS 的 VpnService
├── DnsPacket             最小 DNS 报文解析 / 构造
├── DomainRules           三级域名规则，HashSet 后缀匹配
├── TargetStore           按钮目标配置（SharedPreferences + JSON）
└── Prefs                 SharedPreferences 封装
```

**核心特征**：App 是「网页书签启动器」。点按钮 → 打开 WebView → 每次都要等代理握手 → 看 PC 网页。

**问题**：卡顿（每次 1~2 秒）、离线不可用、界面受制于学校网页。

---

## 目标（v1.0）

**离线优先的原生工作台**。数据本地化，网页只用于「同步」和「选课」。

```
┌─────────────────────────────────────────────┐
│  UI 层                                       │
│  课表页 · 成绩页 · 拦截页 · 设置页            │
└──────────────────┬──────────────────────────┘
                   │ 只读本地数据，0 网络
┌──────────────────▼──────────────────────────┐
│  仓储层 Repository                           │
│  TimetableRepository · GradesRepository      │
└──────────────────┬──────────────────────────┘
                   │ 读写
┌──────────────────▼──────────────────────────┐
│  存储层                                      │
│  timetable_semesters.json（主源）              │
│  timetable.json（只读镜像） · grades.json      │
└──────────────────▲──────────────────────────┘
                   │ 写入（仅同步时）
┌──────────────────┴──────────────────────────┐
│  同步层 Fetcher                              │
│  WebView 会话内提取 → 解析 → 合并            │
└─────────────────────────────────────────────┘
```

### 关键决策

**1. 为什么用 JSON 文件而不是 Room**

单人项目、数据量小（一学期几十门课）、无复杂查询需求。JSON 文件 + 内存缓存足够，且**导入导出天然免费**——导出的就是存储格式本身。Room 会引入 schema 迁移负担，收益不成正比。

**2. 为什么同步要用 WebView 而不是直接 HTTP**

正方教务的接口需要 aTrust 代理会话，Cookie 与 ticket 在 WebView 里。直接构造 HTTP 请求需要复刻整套握手，且一改版就失效。**复用 WebView 会话是唯一低维护成本方案**：让页面正常加载，然后注入 JS 把 DOM 里的表格读成 JSON。

**3. 为什么课表要支持手动添加**

实验课、体育课分项、重修班等教务系统经常不收录或不显示。手动添加的课程 `source == "custom"`，同步时**永不删除**。

**4. 为什么不用 Fragment**

3 个 Tab 的切换用 `setVisibility` 零生命周期坑，旋转屏幕不崩（已配 `configChanges`）。改用 `FragmentTransaction` 会引入状态丢失、重叠等 bug，收益为零。**但课表/成绩页内部复杂后会拆成独立的自定义 View**，避免 Activity 膨胀。

---

## 模块职责（v1.0 规划）

| 类 | 职责 | 依赖 |
|---|---|---|
| `MainActivity` | 底部导航容器，Tab 切换 | 各 Page View |
| `TimetableView` | 原生周课表网格渲染、点击详情、手动添加 | `TimetableRepository` |
| `GradesView` | 成绩卡片流、GPA 概览、学期筛选 | `GradesRepository` |
| `AdBlockView` | 拦截开关、规则统计、observe 开关 | `DnsFilterService`、`DomainRules` |
| `SettingsView` | 登录入口、同步管理、路径编辑、关于 | 全部 |
| `TimetableRepository` | 课表读写、同步合并、导入导出 | `TimetableStore` / `SemesterArchive` |
| `GradesRepository` | 成绩读写、GPA 计算 | `GradesStore` |
| `JsonFileStore` | 通用 JSON 文件读写（原子写、版本校验） | — |
| `CampusFetcher` | 抓取器接口 | — |
| `JwglxtFetcher` | 正方教务抓取实现 | `CampusFetcher` |
| `SyncController` | 编排同步：开 WebView → 注入 JS → 收数据 → 调 Repository | `CampusFetcher` |
| `PortalRouter` | aTrust 节点嗅探与直达路由（从 WebShellActivity 剥离） | `Prefs` |
| `DnsFilterService` | VpnService 主循环 | `DnsPacket`、`DomainRules` |
| `DomainRules` | 规则加载与匹配 | — |
| `DnsPacket` | DNS 报文解析构造 | — |

**注意**：`DnsFilterService` / `DnsPacket` / `DomainRules` **v1.0 不做改动**。这部分已经在真机验证可用，且手写封包逻辑改动风险远大于收益。

---

## 数据流

### 首次使用

```
启动 → 课表页（空态）
        ↓ 引导卡片：「尚未同步」
点「登录并同步」
        ↓
WebView 打开 aTrust 门户 → 用户登录 → 进入教务
        ↓
SyncController 注入 JS 提取课表 DOM
        ↓
JwglxtFetcher 解析 → Timetable
        ↓
TimetableRepository.merge() → 写 timetable_semesters.json → 刷新 timetable.json / Prefs 镜像
        ↓
关闭 WebView → 课表页渲染（原生网格）
```

### 日常使用

```
启动 → 读 timetable_semesters.json 当前学期（毫秒级）→ 渲染课表
        ↓
断网 / 无校园网 / aTrust 掉线 → 完全不影响
```

### 同步（用户主动）

```
设置页登录统一门户一次
        ↓
主界面「同步课表」/ 成绩页「同步成绩」
        ↓
CampusSyncClient 用共享 Cookie 的隐藏 WebView 打开办事大厅
        ↓
自动点击「学生课表查询 / 学生成绩查询」→ 进入教务
        ↓
课表读 kbList JSON；成绩触发查询后读 tabGrid DOM
        ↓
合并：教务课程覆盖、custom 课程保留
        ↓
写 timetable_semesters.json / grades.json → 刷新 timetable.json 与 Prefs 镜像 → 刷新 UI
```

会话失效时不静默失败：提示用户在设置页重新登录。账号密码始终只输入学校页面，App 不接触凭据。

---

## 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 教务改版导致 DOM 结构变化 | 同步失败 | 解析失败时不清空本地数据，提示「同步失败，本地数据保留」 |
| aTrust 节点 IP 变化 | 直达失败 | 已有兜底：失败自动清缓存退回门户重新嗅探 |
| 注入 JS 被 CSP 拦截 | 提取不到数据 | 降级方案：用 `evaluateJavascript` 读 `document.body.innerText` 后正则提取 |
| 学校封禁 | 无法同步 | 本地数据仍在，App 仍可作课表使用 |
| Android 14+ 前台服务限制 | VpnService 崩溃 | 已真机验证 Android 16 可用，但需在 Manifest 补 `foregroundServiceType` |

---

## 不做的事

- 不做账号密码存储。登录态完全依赖 WebView 的 Cookie，App 不接触凭据。
- 不做后台自动同步。只在用户主动点击同步时联网；登录一次后，后续同步无页面跳转。
- 不做多账号。
- 不引入 `tun2socks` 或全流量 VPN。只接管 DNS，业务流量直连。
