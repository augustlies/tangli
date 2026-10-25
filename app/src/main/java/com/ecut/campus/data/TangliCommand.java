package com.ecut.campus.data;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「烫梨指令」：任何 AI 都能写的纯文本课表改动。
 *
 * <pre>
 * 烫梨指令 v1            （写「烫梨指令 v1 整表」= 先清空再按下面重建）
 * + 地质学 | 周三 | 3-4节 | 1-16周 | 3-402 | 张老师
 * + 地质学考试 | 周三 | 14:30-16:30 | 第10周 | 3-402
 * - 大学英语 | 周五 | 1-2节 | 全部
 * 停 大学英语 | 第10周 | 周五 | 1-2节
 * 调 大学英语 | 第10周 | 周五 | 1-2节 -> 第11周 | 周六 | 3-4节 | 2-101
 * 假 2027-01-01 至 2027-01-03 | 元旦
 * 补 2027-01-04 | 2027-01-01
 * 开学 2026-09-07
 * 备注 地质学 | 交第三章作业
 * 设置 主题=深色 | 显示周末=否
 * 作息 第1节 08:00-08:45 | 第2节 08:55-09:40
 * 同步 课表      导出 诊断日志      打开 校园邮箱
 * </pre>
 *
 * 字段顺序随意，按内容识别；全角符号、代码块、项目符号都能容忍。
 * 解析不了的行单独列出原因，不影响其它行。
 */
public final class TangliCommand {

    public static final String HEADER = "烫梨指令";

    private TangliCommand() {}

    /** 解析/换算需要的外部信息（data 包不依赖 Android 和作息设置类）。 */
    public static final class Env {
        public String start = "";          // 第 1 周周一，可空
        public int totalWeeks = 18;        // 省略周次时默认 1..totalWeeks
        public int[] sectionStart = new int[0]; // 下标 = 节次，分钟数
        public int[] sectionEnd = new int[0];
        public String today = "";          // 解析「本周」「下周」用
        /** 当前各项设置的规范值（{@link AppSetting}），用来判断「设置」是不是真有改动。 */
        public Map<String, String> settings = new HashMap<>();
    }

    public static final class Item {
        public String line = "";
        public boolean ok;
        public boolean changed;     // 有实际改动（重复的不算）
        public boolean warn;        // 需要醒目提醒（删教务课、整表清空）
        public String desc = "";    // 人话描述 / 错误原因
        /** 非课表的动作：set / section / sync / export / open；空 = 改课表。 */
        public String action = "";
        public String key = "";
        public String value = "";
        /** set 时可能一行改多项：key → 规范值。 */
        public final Map<String, String> settings = new LinkedHashMap<>();
    }

    public static final class Plan {
        public final List<Item> items = new ArrayList<>();
        public boolean replaceAll;
        public Timetable result;
        /** 作息改动后的每节起止（分钟，下标 = 节次）；sectionsChanged=false 时不用管。 */
        public int[] sectionStart = new int[0];
        public int[] sectionEnd = new int[0];
        public boolean sectionsChanged;

        /** 课表本身有没有改动（不算设置、作息和动作）。 */
        public boolean timetableChanged() {
            for (Item i : items) if (i.ok && i.changed && i.action.isEmpty()) return true;
            return false;
        }

        public List<Item> actions(String type) {
            List<Item> out = new ArrayList<>();
            for (Item i : items) if (i.ok && i.changed && i.action.equals(type)) out.add(i);
            return out;
        }

        /** 应用后要合并写入的设置。 */
        public Map<String, String> settings() {
            Map<String, String> m = new LinkedHashMap<>();
            for (Item i : actions("set")) m.putAll(i.settings);
            return m;
        }

        public int changeCount() {
            int n = 0;
            for (Item i : items) if (i.ok && i.changed) n++;
            return n;
        }

        public int errorCount() {
            int n = 0;
            for (Item i : items) if (!i.ok) n++;
            return n;
        }
    }

    /** 文本里是否有烫梨指令（剪贴板检测用）。 */
    public static boolean looksLike(String text) {
        return text != null && text.contains(HEADER);
    }

    // ------------------------------------------------------------------ 解析

    public static Plan plan(String text, Timetable current, Env env) {
        Plan plan = new Plan();
        Timetable t;
        try {
            t = Timetable.fromJson(current == null ? new Timetable().toJson() : current.toJson());
        } catch (JSONException e) {
            t = new Timetable();
        }
        if (env == null) env = new Env();
        if (text == null) text = "";
        plan.sectionStart = env.sectionStart.clone();
        plan.sectionEnd = env.sectionEnd.clone();
        Map<String, String> settingsNow = new HashMap<>(env.settings);

        boolean seenHeader = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = normalize(raw);
            if (line.isEmpty() || line.startsWith("```") || line.startsWith("#") || line.startsWith("//")) continue;
            if (line.contains(HEADER)) {
                seenHeader = true;
                if (line.contains("整表") && !plan.replaceAll) {
                    plan.replaceAll = true;
                    Item it = new Item();
                    it.line = raw.trim();
                    it.ok = true;
                    it.changed = !t.courses.isEmpty() || !t.adjusts.isEmpty();
                    it.warn = it.changed;
                    it.desc = "整表重建：先清空现有 " + t.courses.size() + " 门课和临时安排";
                    t.courses.clear();
                    t.adjusts.clear();
                    plan.items.add(it);
                }
                continue;
            }
            Op op = detect(line);
            if (op == null) continue; // AI 的客套话之类，忽略
            Item it = new Item();
            it.line = raw.trim();
            try {
                apply(op, t, env, it, plan, settingsNow);
                it.ok = true;
            } catch (CmdError e) {
                it.ok = false;
                it.changed = false;
                it.desc = e.getMessage();
            }
            plan.items.add(it);
        }
        if (!seenHeader && plan.items.isEmpty()) {
            Item it = new Item();
            it.ok = false;
            it.desc = "没有找到烫梨指令";
            plan.items.add(it);
        }
        CourseColorAssigner.assign(t);
        plan.result = t;
        return plan;
    }

    private static final class CmdError extends Exception {
        CmdError(String m) { super(m); }
    }

    private enum Kind { ADD, REMOVE, CANCEL, MOVE, OFF, MAKEUP, START, NOTE, SET, SECTION, SYNC, EXPORT, OPEN }

    private static final class Op {
        Kind kind;
        String body;
        Op(Kind k, String b) { kind = k; body = b.trim(); }
    }

    private static final String[][] VERBS = {
            {"开学", "START"}, {"添加", "ADD"}, {"新增", "ADD"}, {"增加", "ADD"}, {"加", "ADD"},
            {"删除", "REMOVE"}, {"删", "REMOVE"}, {"停课", "CANCEL"}, {"停", "CANCEL"},
            {"调课", "MOVE"}, {"调", "MOVE"}, {"放假", "OFF"}, {"假", "OFF"},
            {"补课", "MAKEUP"}, {"补", "MAKEUP"}, {"备注", "NOTE"}, {"作息", "SECTION"},
    };

    /** 这些动词后面可以直接跟内容（「同步课表」「打开邮箱」），不要求空格。 */
    private static final String[][] LOOSE_VERBS = {
            {"设置", "SET"}, {"同步", "SYNC"}, {"导出", "EXPORT"}, {"打开", "OPEN"}, {"进入", "OPEN"},
    };

    private static Op detect(String line) {
        // 去掉 markdown 列表符号「* 」「• 」「1. 」，但保留作为操作符的 + / -
        String s = line.replaceFirst("^(?:[*•·]\\s+|\\d+[.、)]\\s*)", "");
        if (s.startsWith("+")) return new Op(Kind.ADD, s.substring(1));
        if (s.startsWith("-") && !s.startsWith("->")) return new Op(Kind.REMOVE, s.substring(1));
        for (String[] v : LOOSE_VERBS) {
            if (s.startsWith(v[0]) && s.length() > v[0].length()) {
                String rest = s.substring(v[0].length()).trim();
                if (rest.startsWith(":") || rest.startsWith("|")) rest = rest.substring(1);
                // 同步/导出/打开 的对象都很短；长句多半是 AI 的解释文字，不当指令
                if (!rest.trim().isEmpty() && (v[1].equals("SET") || rest.trim().length() <= 10)) {
                    return new Op(Kind.valueOf(v[1]), rest);
                }
            }
        }
        for (String[] v : VERBS) {
            if (s.startsWith(v[0])) {
                String rest = s.substring(v[0].length());
                // 「假」「补」后面必须跟空白或分隔，避免误吞「补考」「加油」这类词
                if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)) && rest.charAt(0) != '|'
                        && rest.charAt(0) != ':' && !Character.isDigit(rest.charAt(0))) {
                    continue;
                }
                if (rest.startsWith(":")) rest = rest.substring(1);
                return new Op(Kind.valueOf(v[1]), rest);
            }
        }
        return null;
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= '０' && c <= '９') c = (char) ('0' + (c - '０'));
            else if (c == '｜' || c == '丨' || c == '│') c = '|';
            else if (c == '：') c = ':';
            else if (c == '～' || c == '〜' || c == '—' || c == '–' || c == '－' || c == '−') c = '-';
            else if (c == '，' || c == '、') c = ',';
            else if (c == '（') c = '(';
            else if (c == '）') c = ')';
            else if (c == '＋') c = '+';
            else if (c == '\u3000' || c == '\t') c = ' ';
            sb.append(c);
        }
        String s = sb.toString().trim();
        s = s.replace("→", "⇒").replace("=>", "⇒").replace("-->", "⇒").replace("->", "⇒")
                .replace("调到", "⇒").replace("改到", "⇒").replace("换到", "⇒").replace("挪到", "⇒");
        s = s.replaceAll("^`+|`+$", "").trim();
        return s;
    }

    // ------------------------------------------------------------------ 字段

    /** 一组字段识别结果。 */
    static final class Fields {
        List<String> texts = new ArrayList<>();
        int day;
        int secStart, secEnd;
        String timeStart = "", timeEnd = "";
        List<Integer> weeks; // null = 没写
        boolean allWeeks;    // 写了「全部」
        List<String> dates = new ArrayList<>();
        String location = "", teacher = "";

        String name() { return texts.isEmpty() ? "" : texts.get(0); }
    }

    private static final Pattern P_TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})$");
    private static final Pattern P_SEC = Pattern.compile("^第?(\\d{1,2})(?:\\s*[-,至到]\\s*(\\d{1,2}))?节课?$");
    private static final Pattern P_DAY = Pattern.compile("^(?:周|星期|礼拜)([一二三四五六日天七1-7])$");
    private static final Pattern P_DATE_FULL = Pattern.compile("^(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?$");
    private static final Pattern P_DATE_MD = Pattern.compile("^(\\d{1,2})月(\\d{1,2})[日号]?$");
    private static final Pattern P_DATE_SLASH = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$");
    private static final Pattern P_REL_WEEK = Pattern.compile("^(本|这|下下|下|上)周$");

    static Fields parseFields(String body, Env env) throws CmdError {
        Fields f = new Fields();
        List<String> parts = new ArrayList<>();
        for (String p : body.split("\\|")) {
            String q = p.trim();
            if (!q.isEmpty()) parts.add(q);
        }
        for (String part : parts) {
            // 带标签的字段：地点:3-402 / 老师:张三
            Matcher lab = Pattern.compile("^(地点|教室|位置|老师|教师|任课老师|课程|课程名|名称|时间|星期|节次|周次|日期)\\s*:\\s*(.*)$").matcher(part);
            if (lab.find()) {
                String k = lab.group(1), v = lab.group(2).trim();
                if (k.equals("地点") || k.equals("教室") || k.equals("位置")) { f.location = v; continue; }
                if (k.startsWith("老师") || k.startsWith("教师") || k.equals("任课老师")) { f.teacher = v; continue; }
                if (k.startsWith("课程") || k.equals("名称")) { f.texts.add(0, v); continue; }
                part = v;
            }
            if (classifyWhole(part, f, env)) continue;
            // 整段识别不了：按空白再拆一次（「第10周 周五 1-2节」写在一格里）
            String spaced = part.replaceAll("(本|这|下下|下|上)周([一二三四五六日天])", "$1周 周$2")
                    .replaceAll("(\\d周)(周|星期)", "$1 $2")
                    .replaceAll("(周[一二三四五六日天七])(\\d|第)", "$1 $2")
                    .replaceAll("(周[一二三四五六日天七])(上午|下午|晚上)", "$1 $2");
            String[] toks = spaced.split("\\s+");
            if (toks.length > 1) {
                StringBuilder rest = new StringBuilder();
                for (String tok : toks) {
                    if (!classifyWhole(tok, f, env)) {
                        if (rest.length() > 0) rest.append(' ');
                        rest.append(tok);
                    }
                }
                if (rest.length() > 0) f.texts.add(rest.toString());
            } else {
                f.texts.add(part);
            }
        }
        // 第 2、3 段文字：地点 / 老师
        List<String> extra = f.texts.size() > 1 ? new ArrayList<>(f.texts.subList(1, f.texts.size())) : new ArrayList<>();
        for (String x : extra) {
            String tx = x.replaceAll("(老师|教师)$", "").trim();
            boolean teacherLike = x.endsWith("老师") || x.endsWith("教师");
            boolean placeLike = x.matches(".*\\d.*") || x.matches(".*(楼|室|馆|场|厅|教|实验|中心|校区|线上|腾讯会议|机房).*");
            if (teacherLike && f.teacher.isEmpty()) f.teacher = tx;
            else if (placeLike && f.location.isEmpty()) f.location = x;
            else if (f.location.isEmpty()) f.location = x;
            else if (f.teacher.isEmpty()) f.teacher = tx;
        }
        return f;
    }

    private static boolean classifyWhole(String s, Fields f, Env env) throws CmdError {
        String p = s.trim();
        if (p.isEmpty()) return true;
        Matcher m;
        if ((m = P_TIME.matcher(p)).matches()) {
            int a = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
            if (a >= b || b > 24 * 60) throw new CmdError("时间不对：" + p);
            f.timeStart = Timetable.Session.formatHm(a);
            f.timeEnd = Timetable.Session.formatHm(b);
            return true;
        }
        if ((m = P_SEC.matcher(p)).matches()) {
            int a = Integer.parseInt(m.group(1));
            int b = m.group(2) == null ? a : Integer.parseInt(m.group(2));
            if (a < 1 || b < a || b > 16) throw new CmdError("节次不对：" + p);
            f.secStart = a;
            f.secEnd = b;
            return true;
        }
        if ((m = P_DAY.matcher(p)).matches()) {
            f.day = dayIndex(m.group(1));
            return true;
        }
        String date = parseDate(p, env);
        if (date != null) {
            f.dates.add(date);
            return true;
        }
        if (p.equals("全部") || p.equals("全周") || p.equals("每周") || p.equals("所有周") || p.equals("整学期")) {
            f.allWeeks = true;
            return true;
        }
        if ((m = P_REL_WEEK.matcher(p)).matches()) {
            int cur = Dates.weekOf(env.start, env.today);
            if (cur == 0) throw new CmdError("没设开学日期，算不出「" + p + "」是第几周");
            String r = m.group(1);
            int w = r.equals("下") ? cur + 1 : r.equals("下下") ? cur + 2 : r.equals("上") ? cur - 1 : cur;
            f.weeks = new ArrayList<>(Collections.singletonList(w));
            return true;
        }
        if (p.equals("单周") || p.equals("双周")) {
            List<Integer> w = new ArrayList<>();
            for (int i = p.equals("单周") ? 1 : 2; i <= env.totalWeeks; i += 2) w.add(i);
            f.weeks = w;
            return true;
        }
        if (p.matches("^[第\\d,\\-()单双周至到\\s]+$") && p.contains("周") && p.matches(".*\\d.*")) {
            String expr = p.replace("第", "").replace("至", "-").replace("到", "-").replace("单周", "周(单)").replace("双周", "周(双)");
            WeekRange.Parsed wr = WeekRange.parse(expr);
            if (!wr.ok()) throw new CmdError("周次看不懂：" + p);
            f.weeks = new ArrayList<>(wr.weeks);
            return true;
        }
        return false;
    }

    static int dayIndex(String c) {
        switch (c) {
            case "一": case "1": return 1;
            case "二": case "2": return 2;
            case "三": case "3": return 3;
            case "四": case "4": return 4;
            case "五": case "5": return 5;
            case "六": case "6": return 6;
            default: return 7;
        }
    }

    /** 各种写法的日期 → yyyy-MM-dd；不是日期返回 null。缺年份时取离开学日期最近的那一年。 */
    static String parseDate(String s, Env env) {
        Matcher m;
        int y = 0, mo, d;
        if ((m = P_DATE_FULL.matcher(s)).matches()) {
            y = Integer.parseInt(m.group(1));
            mo = Integer.parseInt(m.group(2));
            d = Integer.parseInt(m.group(3));
        } else if ((m = P_DATE_MD.matcher(s)).matches() || (m = P_DATE_SLASH.matcher(s)).matches()) {
            mo = Integer.parseInt(m.group(1));
            d = Integer.parseInt(m.group(2));
        } else {
            return null;
        }
        if (y == 0) {
            String ref = Dates.valid(env.today) ? env.today : env.start;
            if (!Dates.valid(ref)) return null;
            int ry = Integer.parseInt(ref.substring(0, 4));
            String best = null;
            long bestGap = Long.MAX_VALUE;
            for (int cy = ry - 1; cy <= ry + 1; cy++) {
                String cand = String.format(Locale.US, "%04d-%02d-%02d", cy, mo, d);
                if (!Dates.valid(cand)) continue;
                long gap = Math.abs(Dates.epochDay(cand) - Dates.epochDay(ref));
                if (gap < bestGap) { bestGap = gap; best = cand; }
            }
            return best;
        }
        String out = String.format(Locale.US, "%04d-%02d-%02d", y, mo, d);
        return Dates.valid(out) ? out : null;
    }

    // ------------------------------------------------------------------ 执行

    private static void apply(Op op, Timetable t, Env env, Item it, Plan plan,
                              Map<String, String> settingsNow) throws CmdError {
        switch (op.kind) {
            case NOTE: note(t, op.body, it); break;
            case SET: set(op.body, it, settingsNow); break;
            case SECTION: section(op.body, it, plan); break;
            case SYNC: simple(op.body, it, "sync", SYNC_TARGETS, "同步"); break;
            case EXPORT: simple(op.body, it, "export", EXPORT_TARGETS, "导出"); break;
            case OPEN: simple(op.body, it, "open", OPEN_TARGETS, "打开"); break;
            case ADD: add(t, env, parseFields(op.body, env), it); break;
            case REMOVE: remove(t, env, parseFields(op.body, env), it); break;
            case CANCEL: cancel(t, env, parseFields(op.body, env), it); break;
            case MOVE: move(t, env, op.body, it); break;
            case OFF: off(t, env, op.body, it); break;
            case MAKEUP: makeup(t, env, parseFields(op.body, env), it); break;
            case START: start(t, env, parseFields(op.body, env), it); break;
        }
    }

    private static void add(Timetable t, Env env, Fields f, Item it) throws CmdError {
        String name = f.name();
        if (name.isEmpty()) throw new CmdError("缺课程名");
        // 写了日期就换算成周次+星期
        if (!f.dates.isEmpty()) {
            if (!Dates.valid(env.start)) throw new CmdError("没设开学日期，日期换算不了周次");
            String dt = f.dates.get(0);
            if (f.day == 0) f.day = Dates.dayOfWeek(dt);
            if (f.weeks == null) f.weeks = new ArrayList<>(Collections.singletonList(Dates.weekOf(env.start, dt)));
        }
        if (f.day == 0) throw new CmdError("缺星期（周一…周日）");
        boolean timed = !f.timeStart.isEmpty();
        if (!timed && f.secStart == 0) throw new CmdError("缺节次（如 3-4节）或时间（如 14:30-16:30）");

        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = f.day;
        if (timed) {
            s.timeStart = f.timeStart;
            s.timeEnd = f.timeEnd;
            int[] cover = cover(env, Timetable.Session.minutesOf(f.timeStart), Timetable.Session.minutesOf(f.timeEnd));
            s.startSection = f.secStart > 0 ? f.secStart : cover[0];
            s.endSection = f.secStart > 0 ? f.secEnd : cover[1];
        } else {
            s.startSection = f.secStart;
            s.endSection = f.secEnd;
        }
        List<Integer> weeks = f.weeks;
        if (weeks == null || f.allWeeks) {
            weeks = new ArrayList<>();
            for (int i = 1; i <= env.totalWeeks; i++) weeks.add(i);
        }
        for (int w : weeks) if (w < 1 || w > 99) throw new CmdError("周次超出范围：" + w);
        s.weeks.addAll(weeks);
        s.weeksRaw = weekExpr(weeks);
        s.location = f.location;

        Timetable.Course target = null;
        for (Timetable.Course c : t.courses) {
            if (!c.name.equals(name)) continue;
            for (Timetable.Session e : c.sessions) {
                if (sameSlot(e, s) && e.weeks.equals(s.weeks)) {
                    it.changed = false;
                    it.desc = "已存在，跳过：" + name + " " + slotText(s);
                    return;
                }
            }
            if (!Timetable.SOURCE_JWGLXT.equals(c.source) && target == null) target = c;
        }
        if (target == null) {
            target = new Timetable.Course();
            target.name = name;
            target.key = "import_" + Integer.toHexString((name + System.nanoTime()).hashCode());
            target.source = Timetable.SOURCE_IMPORT;
            target.teacher = f.teacher;
            t.courses.add(target);
        } else if (!f.teacher.isEmpty() && !f.teacher.equals(target.teacher)) {
            s.teacher = f.teacher;
        }
        target.sessions.add(s);
        it.changed = true;
        it.desc = "添加 " + name + " · " + slotText(s) + (s.location.isEmpty() ? "" : " · " + s.location);
    }

    private static void remove(Timetable t, Env env, Fields f, Item it) throws CmdError {
        String name = f.name();
        if (name.isEmpty()) throw new CmdError("缺课程名");
        List<Timetable.Course> hits = new ArrayList<>();
        for (Timetable.Course c : t.courses) if (c.name.equals(name)) hits.add(c);
        if (hits.isEmpty()) throw new CmdError("课表里没有「" + name + "」");
        boolean jw = false;
        for (Timetable.Course c : hits) jw |= Timetable.SOURCE_JWGLXT.equals(c.source);

        boolean whole = f.day == 0 && f.secStart == 0 && f.timeStart.isEmpty() && (f.weeks == null || f.allWeeks);
        if (whole) {
            t.courses.removeAll(hits);
            it.changed = true;
            it.warn = jw;
            it.desc = "删除整门 " + name + (jw ? "（教务课，重新同步会回来）" : "");
            return;
        }
        int touched = 0;
        for (Timetable.Course c : hits) {
            List<Timetable.Session> drop = new ArrayList<>();
            for (Timetable.Session s : c.sessions) {
                if (f.day != 0 && s.dayOfWeek != f.day) continue;
                if (f.secStart != 0 && (s.endSection < f.secStart || s.startSection > f.secEnd)) continue;
                if (!f.timeStart.isEmpty() && !f.timeStart.equals(s.timeStart)) continue;
                if (f.weeks != null && !f.allWeeks) {
                    if (s.weeks.isEmpty()) for (int i = 1; i <= env.totalWeeks; i++) s.weeks.add(i);
                    int before = s.weeks.size();
                    s.weeks.removeAll(f.weeks);
                    if (s.weeks.size() == before) continue;
                    s.weeksRaw = weekExpr(s.weeks);
                    if (s.weeks.isEmpty()) drop.add(s);
                } else {
                    drop.add(s);
                }
                touched++;
            }
            c.sessions.removeAll(drop);
        }
        t.courses.removeIf(c -> hits.contains(c) && c.sessions.isEmpty());
        if (touched == 0) throw new CmdError("「" + name + "」没有对得上的时段");
        it.changed = true;
        it.warn = jw;
        it.desc = "删除 " + name + " " + describe(f) + (jw ? "（教务课，重新同步会回来）" : "");
    }

    /** 停课 / 调课的源：课程名 + 周（或日期）+ 星期 + 节次，返回 [week, day]。 */
    private static Timetable.Adjust source(Timetable t, Env env, Fields f, String type) throws CmdError {
        String name = f.name();
        if (name.isEmpty()) throw new CmdError("缺课程名");
        int week = 0, day = f.day;
        if (!f.dates.isEmpty()) {
            if (!Dates.valid(env.start)) throw new CmdError("没设开学日期，日期换算不了周次");
            week = Dates.weekOf(env.start, f.dates.get(0));
            day = Dates.dayOfWeek(f.dates.get(0));
        } else if (f.weeks != null && f.weeks.size() == 1) {
            week = f.weeks.get(0);
        } else if (f.weeks != null && f.weeks.size() > 1) {
            throw new CmdError("一行只写一周（多周请分多行）");
        }
        if (week == 0) throw new CmdError("缺第几周（或日期）");
        Timetable.Adjust a = new Timetable.Adjust();
        a.type = type;
        a.name = name;
        a.week = week;
        a.day = day;
        a.start = f.secStart;
        a.end = f.secEnd;
        int hits = 0;
        for (Timetable.Course c : t.courses) {
            if (!c.name.equals(name)) continue;
            for (Timetable.Session s : c.sessions) if (WeekOverlay.matches(a, s, week)) hits++;
        }
        if (hits == 0) throw new CmdError("第" + week + "周" + Dates.cnDay(day) + "没有「" + name + "」");
        if (Timetable.Adjust.MOVE.equals(type) && hits > 1 && a.day == 0) {
            throw new CmdError("第" + week + "周「" + name + "」有好几次，请写清星期和节次");
        }
        return a;
    }

    private static void cancel(Timetable t, Env env, Fields f, Item it) throws CmdError {
        if (f.weeks != null && f.weeks.size() > 1 && f.dates.isEmpty()) {
            // 「停 xx | 10-12周 | 周五」：拆成多周
            int added = 0;
            List<Integer> ws = new ArrayList<>(f.weeks);
            for (int w : ws) {
                f.weeks = new ArrayList<>(Collections.singletonList(w));
                Timetable.Adjust a;
                try { a = source(t, env, f, Timetable.Adjust.CANCEL); } catch (CmdError e) { continue; }
                if (addAdjust(t, a)) added++;
            }
            if (added == 0 && !hasAny(t, f.name())) throw new CmdError("课表里没有「" + f.name() + "」");
            it.changed = added > 0;
            it.desc = "停课 " + f.name() + " · 第" + weekExpr(ws) + "周 " + Dates.cnDay(f.day) + secText(f);
            return;
        }
        Timetable.Adjust a = source(t, env, f, Timetable.Adjust.CANCEL);
        it.changed = addAdjust(t, a);
        it.desc = (it.changed ? "" : "已存在，跳过：") + "停课 " + a.name + " · 第" + a.week + "周 " + Dates.cnDay(a.day) + secText(f);
    }

    private static void move(Timetable t, Env env, String body, Item it) throws CmdError {
        int arrow = body.indexOf('⇒');
        if (arrow < 0) throw new CmdError("调课要写成「原时间 -> 新时间」");
        Fields from = parseFields(body.substring(0, arrow), env);
        Fields to = parseFields(body.substring(arrow + 1), env);
        Timetable.Adjust a = source(t, env, from, Timetable.Adjust.MOVE);
        if (!to.dates.isEmpty()) {
            if (!Dates.valid(env.start)) throw new CmdError("没设开学日期，日期换算不了周次");
            a.toWeek = Dates.weekOf(env.start, to.dates.get(0));
            a.toDay = Dates.dayOfWeek(to.dates.get(0));
        } else {
            a.toWeek = to.weeks != null && !to.weeks.isEmpty() ? to.weeks.get(0) : a.week;
            a.toDay = to.day;
        }
        if (a.toWeek < 1 || a.toWeek > 99) throw new CmdError("新周次不对");
        a.toStart = to.secStart;
        a.toEnd = to.secEnd;
        a.toTimeStart = to.timeStart;
        a.toTimeEnd = to.timeEnd;
        a.toLocation = !to.location.isEmpty() ? to.location : (!to.texts.isEmpty() ? to.texts.get(0) : "");
        if (a.toDay == 0 && a.toStart == 0 && !a.hasToTime() && a.toWeek == a.week && a.toLocation.isEmpty()) {
            throw new CmdError("没写调到哪里");
        }
        it.changed = addAdjust(t, a);
        it.desc = (it.changed ? "" : "已存在，跳过：") + "调课 " + a.name + " · 第" + a.week + "周" + Dates.cnDay(a.day)
                + secText(from) + " → 第" + a.toWeek + "周" + Dates.cnDay(a.toDay > 0 ? a.toDay : a.day)
                + (a.hasToTime() ? " " + a.toTimeStart + "-" + a.toTimeEnd : a.toStart > 0 ? " " + a.toStart + "-" + (a.toEnd > 0 ? a.toEnd : a.toStart) + "节" : "")
                + (a.toLocation.isEmpty() ? "" : " · " + a.toLocation);
    }

    private static final Pattern P_ANY_DATE = Pattern.compile("(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?|\\d{1,2}月\\d{1,2}[日号]?|\\d{1,2}/\\d{1,2})");

    private static void off(Timetable t, Env env, String body, Item it) throws CmdError {
        List<String> dates = new ArrayList<>();
        StringBuilder label = new StringBuilder();
        for (String part : body.split("\\|")) {
            Matcher m = P_ANY_DATE.matcher(part);
            boolean any = false;
            while (m.find()) {
                String d = parseDate(m.group(1), env);
                if (d != null) { dates.add(d); any = true; }
            }
            if (!any && !part.trim().isEmpty()) label.append(part.trim());
            else if (any) {
                String rest = P_ANY_DATE.matcher(part).replaceAll("").replaceAll("[至到\\-~\\s]", "");
                if (!rest.isEmpty() && label.length() == 0) label.append(rest);
            }
        }
        if (dates.isEmpty()) throw new CmdError("缺放假日期");
        String from = dates.get(0), to = dates.size() > 1 ? dates.get(dates.size() - 1) : from;
        if (to.compareTo(from) < 0) { String x = from; from = to; to = x; }
        if (Dates.epochDay(to) - Dates.epochDay(from) > 60) throw new CmdError("放假超过 60 天，检查一下日期");
        int added = 0;
        for (String d = from; d.compareTo(to) <= 0; d = Dates.addDays(d, 1)) {
            Timetable.Adjust a = new Timetable.Adjust();
            a.type = Timetable.Adjust.OFF;
            a.date = d;
            a.note = label.toString();
            if (addAdjust(t, a)) added++;
        }
        it.changed = added > 0;
        it.desc = (it.changed ? "" : "已存在，跳过：") + "放假 " + Dates.md(from) + (to.equals(from) ? "" : "–" + Dates.md(to))
                + (label.length() > 0 ? " · " + label : "");
    }

    private static void makeup(Timetable t, Env env, Fields f, Item it) throws CmdError {
        if (f.dates.isEmpty()) throw new CmdError("缺补课日期");
        String date = f.dates.get(0);
        String fromDate;
        if (f.dates.size() > 1) {
            fromDate = f.dates.get(1);
        } else if (f.day > 0) {
            // 「补 10月10日 | 周三」：上同一周周三的课
            fromDate = Dates.addDays(date, f.day - Dates.dayOfWeek(date));
        } else {
            throw new CmdError("要写上哪天的课（日期或星期）");
        }
        Timetable.Adjust a = new Timetable.Adjust();
        a.type = Timetable.Adjust.MAKEUP;
        a.date = date;
        a.fromDate = fromDate;
        a.note = f.texts.isEmpty() ? "" : f.texts.get(0).replaceAll("^上|的课$", "");
        it.changed = addAdjust(t, a);
        it.desc = (it.changed ? "" : "已存在，跳过：") + "补课 " + Dates.md(date) + Dates.cnDay(Dates.dayOfWeek(date))
                + " 上 " + Dates.md(fromDate) + Dates.cnDay(Dates.dayOfWeek(fromDate)) + " 的课";
    }

    private static void start(Timetable t, Env env, Fields f, Item it) throws CmdError {
        if (f.dates.isEmpty()) throw new CmdError("缺开学日期");
        String d = f.dates.get(0);
        String monday = Dates.addDays(d, 1 - Dates.dayOfWeek(d));
        it.changed = !monday.equals(t.semester.startDate);
        t.semester.startDate = monday;
        t.semester.startDateTouched = true;
        env.start = monday;
        it.desc = "第 1 周从 " + monday + "（周一）开始";
    }

    // ------------------------------------------------------------------ 备注 / 设置 / 作息 / 动作

    private static void note(Timetable t, String body, Item it) throws CmdError {
        String b = body.trim();
        int bar = b.indexOf('|');
        String name = (bar < 0 ? b : b.substring(0, bar)).trim();
        String text = bar < 0 ? "" : b.substring(bar + 1).trim();
        if (name.isEmpty()) throw new CmdError("缺课程名");
        boolean clear = text.isEmpty() || text.equals("清空") || text.equals("删除") || text.equals("无");
        if (clear) text = "";
        int hit = 0;
        boolean changed = false;
        for (Timetable.Course c : t.courses) {
            if (!c.name.equals(name)) continue;
            hit++;
            if (!text.equals(c.note)) {
                c.note = text;
                changed = true;
            }
        }
        if (hit == 0) throw new CmdError("课表里没有「" + name + "」");
        it.changed = changed;
        it.desc = (changed ? "" : "没变化，跳过：") + (clear ? "清空 " + name + " 的备注" : name + " 备注：" + text);
    }

    private static void set(String body, Item it, Map<String, String> now) throws CmdError {
        List<String> pairs = new ArrayList<>();
        for (String p : body.split("[|,;；]")) if (!p.trim().isEmpty()) pairs.add(p.trim());
        if (pairs.isEmpty()) throw new CmdError("缺设置项");
        StringBuilder desc = new StringBuilder();
        boolean changed = false;
        for (String p : pairs) {
            String name, value;
            Matcher m = Pattern.compile("^(.+?)\\s*[=:]\\s*(.*)$").matcher(p);
            if (m.find()) {
                name = m.group(1).trim();
                value = m.group(2).trim();
            } else {
                int sp = p.lastIndexOf(' ');
                if (sp < 0) throw new CmdError("「" + p + "」要写成 名字=值");
                name = p.substring(0, sp).trim();
                value = p.substring(sp + 1).trim();
            }
            String[] kv;
            try {
                kv = AppSetting.resolve(name, value);
            } catch (IllegalArgumentException e) {
                throw new CmdError(e.getMessage());
            }
            it.settings.put(kv[0], kv[1]);
            boolean diff = !kv[1].equals(now.get(kv[0]));
            if (diff) changed = true;
            now.put(kv[0], kv[1]);
            if (desc.length() > 0) desc.append("，");
            desc.append(AppSetting.label(kv[0])).append(" → ").append(AppSetting.display(kv[0], kv[1]));
            if (!diff) desc.append("（已是）");
        }
        it.action = "set";
        it.changed = changed;
        it.desc = (changed ? "设置 " : "没变化，跳过：设置 ") + desc;
    }

    private static final Pattern P_SECTION_RANGE = Pattern.compile(
            "第?\\s*(\\d{1,2})\\s*节?\\s*:?\\s*(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})");

    private static void section(String body, Item it, Plan plan) throws CmdError {
        Matcher m = P_SECTION_RANGE.matcher(body);
        int n = plan.sectionStart.length - 1;
        if (n < 1) throw new CmdError("读不到当前作息");
        StringBuilder desc = new StringBuilder();
        boolean any = false, changed = false;
        while (m.find()) {
            any = true;
            int sec = Integer.parseInt(m.group(1));
            int st = Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
            int en = Integer.parseInt(m.group(4)) * 60 + Integer.parseInt(m.group(5));
            if (sec < 1 || sec > n) throw new CmdError("只有第 1 到第 " + n + " 节");
            if (en <= st || st >= 24 * 60 || en > 24 * 60) throw new CmdError("第" + sec + "节时间不对");
            if (sec > 1 && st < plan.sectionEnd[sec - 1]) {
                throw new CmdError("第" + sec + "节开始早于第" + (sec - 1) + "节下课（"
                        + hm(plan.sectionEnd[sec - 1]) + "）");
            }
            if (plan.sectionStart[sec] != st || plan.sectionEnd[sec] != en) changed = true;
            // 后面的节次保持原来的时长和课间，整体顺延
            int shift = st - plan.sectionStart[sec];
            int[] dur = new int[n + 1], brk = new int[n + 1];
            for (int i = 1; i <= n; i++) {
                dur[i] = plan.sectionEnd[i] - plan.sectionStart[i];
                brk[i] = i < n ? plan.sectionStart[i + 1] - plan.sectionEnd[i] : 0;
            }
            plan.sectionStart[sec] = st;
            plan.sectionEnd[sec] = en;
            for (int i = sec + 1; i <= n; i++) {
                plan.sectionStart[i] = plan.sectionEnd[i - 1] + brk[i - 1];
                plan.sectionEnd[i] = plan.sectionStart[i] + dur[i];
            }
            if (shift == 0 && dur[sec] == en - st) continue;
            if (desc.length() > 0) desc.append("，");
            desc.append("第").append(sec).append("节 ").append(hm(st)).append('-').append(hm(en));
        }
        if (!any) throw new CmdError("作息要写成 第1节 08:00-08:45");
        it.action = "section";
        it.changed = changed;
        if (changed) plan.sectionsChanged = true;
        it.desc = changed ? "作息 " + desc + "（后面的节次按原来的课间顺延）" : "作息没变化，跳过";
    }

    private static String hm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** 名字 → 规范值。第一个名字是预览里显示的。 */
    private static final String[][] SYNC_TARGETS = {
            {"timetable", "课表", "课程表", "课程"}, {"grades", "成绩", "分数"}, {"history", "历史学期", "历史课表", "以前的学期"},
    };
    private static final String[][] EXPORT_TARGETS = {
            {"diag", "诊断日志", "日志", "诊断"}, {"ics", "日历", "ICS", "ics"}, {"share", "课表", "课表指令", "分享"},
            {"all", "全部", "全部设置", "备份", "所有"},
    };
    private static final String[][] OPEN_TARGETS = {
            {"mail", "校园邮箱", "邮箱"}, {"library", "图书馆", "预约图书馆"}, {"grades", "成绩"},
            {"calendar", "校历"}, {"map", "校园地图", "地图"}, {"classmates", "同学", "共同空闲"},
            {"share", "二维码", "分享二维码", "课表二维码"}, {"about", "关于", "检查更新"},
            {"schedule", "作息时间", "作息"}, {"ttsettings", "课表设置"}, {"debug", "调试台", "调试"},
            {"settings", "设置"}, {"adblock", "去广告", "广告拦截", "拦截"},
    };

    private static void simple(String body, Item it, String action, String[][] targets, String verb) throws CmdError {
        String b = body.trim().replaceAll("^(一下|一次)\\s*", "");
        for (String[] t : targets) {
            for (int i = 1; i < t.length; i++) {
                if (b.equals(t[i]) || b.equals(t[i] + "页") || b.equals(t[i] + "页面")) {
                    it.action = action;
                    it.value = t[0];
                    it.changed = true;
                    it.desc = verb + " " + t[1];
                    return;
                }
            }
        }
        StringBuilder ok = new StringBuilder();
        for (String[] t : targets) {
            if (ok.length() > 0) ok.append("、");
            ok.append(t[1]);
        }
        throw new CmdError("不能" + verb + "「" + b + "」，可以：" + ok);
    }

    /** 调试台「导出全部」：课表 + 临时安排 + 备注 + 设置 + 作息，粘回来就能还原。 */
    public static String exportAll(Timetable t, Env env) {
        StringBuilder sb = new StringBuilder(export(t, true, true));
        if (t != null) {
            for (Timetable.Course c : t.courses) {
                if (c.note != null && !c.note.isEmpty()) sb.append("备注 ").append(c.name).append(" | ").append(c.note.replace('\n', ' ')).append('\n');
            }
        }
        if (env != null) {
            StringBuilder set = new StringBuilder();
            for (String k : AppSetting.ORDER) {
                String v = env.settings.get(k);
                if (v == null || (AppSetting.SHARE_NAME.equals(k) && v.isEmpty())) continue;
                if (set.length() > 0) set.append(" | ");
                set.append(AppSetting.label(k)).append('=').append(AppSetting.display(k, v));
            }
            if (set.length() > 0) sb.append("设置 ").append(set).append('\n');
            int n = env.sectionStart.length - 1;
            if (n >= 1) {
                sb.append("作息");
                for (int i = 1; i <= n; i++) {
                    sb.append(i == 1 ? " " : " | ").append("第").append(i).append("节 ")
                            .append(hm(env.sectionStart[i])).append('-').append(hm(env.sectionEnd[i]));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean hasAny(Timetable t, String name) {
        for (Timetable.Course c : t.courses) if (c.name.equals(name)) return true;
        return false;
    }

    /** 加入临时安排；已有一模一样的返回 false。 */
    public static boolean addAdjust(Timetable t, Timetable.Adjust a) {
        for (Timetable.Adjust e : t.adjusts) if (e.sameAs(a)) return false;
        if (a.id.isEmpty()) a.id = "a" + Long.toString(System.nanoTime() & 0xFFFFFFFFL, 36) + t.adjusts.size();
        t.adjusts.add(a);
        return true;
    }

    private static int[] cover(Env env, int a, int b) {
        int first = 0, last = 0;
        for (int i = 1; i < env.sectionStart.length && i < env.sectionEnd.length; i++) {
            if (env.sectionEnd[i] > a && env.sectionStart[i] < b) {
                if (first == 0) first = i;
                last = i;
            }
        }
        if (first == 0) {
            // 不和任何节次重叠（比如晚上 21 点后）：贴到最近的一节
            int best = 1;
            for (int i = 1; i < env.sectionStart.length; i++) if (env.sectionStart[i] <= a) best = i;
            first = last = best;
        }
        return new int[]{first, last};
    }

    private static boolean sameSlot(Timetable.Session a, Timetable.Session b) {
        if (a.dayOfWeek != b.dayOfWeek) return false;
        if (a.hasCustomTime() || b.hasCustomTime()) {
            return a.timeStart.equals(b.timeStart) && a.timeEnd.equals(b.timeEnd);
        }
        return a.startSection == b.startSection && a.endSection == b.endSection;
    }

    private static String describe(Fields f) {
        StringBuilder sb = new StringBuilder();
        if (f.day > 0) sb.append(Dates.cnDay(f.day));
        sb.append(secText(f));
        if (!f.timeStart.isEmpty()) sb.append(' ').append(f.timeStart).append('-').append(f.timeEnd);
        if (f.weeks != null && !f.allWeeks) sb.append(" 第").append(weekExpr(f.weeks)).append("周");
        return sb.toString().trim();
    }

    private static String secText(Fields f) {
        if (f.secStart == 0) return "";
        return " " + f.secStart + (f.secEnd > f.secStart ? "-" + f.secEnd : "") + "节";
    }

    static String slotText(Timetable.Session s) {
        String time = s.hasCustomTime() ? s.timeStart + "-" + s.timeEnd
                : s.startSection + (s.endSection > s.startSection ? "-" + s.endSection : "") + "节";
        return Dates.cnDay(s.dayOfWeek) + " " + time + " · " + weekExpr(s.weeks) + "周";
    }

    /** 周次列表 → 「1-16」「1-15(单)」「2,4,9」。 */
    public static String weekExpr(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) return "";
        List<Integer> w = new ArrayList<>(weeks);
        Collections.sort(w);
        if (w.size() >= 3) {
            boolean step2 = true;
            for (int i = 1; i < w.size(); i++) if (w.get(i) - w.get(i - 1) != 2) { step2 = false; break; }
            if (step2) return w.get(0) + "-" + w.get(w.size() - 1) + (w.get(0) % 2 == 1 ? "(单)" : "(双)");
        }
        return WeekRange.compress(w);
    }

    // ------------------------------------------------------------------ 导出

    /** 把课表导出成指令：发给同学，或作为「现有课表」给 AI 参考。 */
    public static String export(Timetable t, boolean withHeader) {
        return export(t, withHeader, true);
    }

    /** 发给同学用：普通指令头（只添加，不清空对方课表），不带停课调课。 */
    public static String exportForShare(Timetable t) {
        return HEADER + " v1\n" + export(t, false, false);
    }

    public static String export(Timetable t, boolean withHeader, boolean withAdjusts) {
        StringBuilder sb = new StringBuilder();
        if (withHeader) sb.append(HEADER).append(" v1 整表\n");
        if (t == null) return sb.toString();
        if (withHeader && Dates.valid(t.semester.startDate)) sb.append("开学 ").append(t.semester.startDate).append('\n');
        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                sb.append("+ ").append(c.name).append(" | ").append(Dates.cnDay(s.dayOfWeek)).append(" | ");
                if (s.hasCustomTime()) sb.append(s.timeStart).append('-').append(s.timeEnd);
                else sb.append(s.startSection).append(s.endSection > s.startSection ? "-" + s.endSection : "").append("节");
                if (!s.weeks.isEmpty()) sb.append(" | ").append(weekExpr(s.weeks)).append("周");
                if (!s.location.isEmpty()) sb.append(" | ").append(s.location);
                String teacher = !s.teacher.isEmpty() ? s.teacher : c.teacher;
                if (teacher != null && !teacher.isEmpty()) sb.append(" | ").append(teacher).append(teacher.endsWith("老师") ? "" : "老师");
                sb.append('\n');
            }
        }
        if (withAdjusts) for (Timetable.Adjust a : t.adjusts) {
            String line = adjustLine(a);
            if (!line.isEmpty()) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static String adjustLine(Timetable.Adjust a) {
        String src = a.name + " | 第" + a.week + "周" + (a.day > 0 ? " | " + Dates.cnDay(a.day) : "")
                + (a.start > 0 ? " | " + a.start + (a.end > a.start ? "-" + a.end : "") + "节" : "");
        switch (a.type) {
            case Timetable.Adjust.CANCEL:
                return "停 " + src;
            case Timetable.Adjust.MOVE:
                return "调 " + src + " -> 第" + WeekOverlay.targetWeek(a) + "周"
                        + (a.toDay > 0 ? " | " + Dates.cnDay(a.toDay) : "")
                        + (a.hasToTime() ? " | " + a.toTimeStart + "-" + a.toTimeEnd
                        : a.toStart > 0 ? " | " + a.toStart + (a.toEnd > a.toStart ? "-" + a.toEnd : "") + "节" : "")
                        + (a.toLocation.isEmpty() ? "" : " | " + a.toLocation);
            case Timetable.Adjust.OFF:
                return "假 " + a.date + (a.note.isEmpty() ? "" : " | " + a.note);
            case Timetable.Adjust.MAKEUP:
                return "补 " + a.date + " | " + a.fromDate;
            default:
                return "";
        }
    }
}
