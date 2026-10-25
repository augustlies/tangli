package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 正方教务课表数据解析。
 *
 * 输入是 {@code /jwglxt/kbcx/xskbcx_cxXsKb.html} 返回的 JSON 字符串。
 * 输出是 {@link Timetable}。
 *
 * 真机确认（2026-10-23）：
 *   - 课表在 kbList 数组，一条记录 = 一个时段（不是一门课）
 *   - 同一门课的多条记录共享 jxb_id（教学班 ID）→ 用它聚合
 *   - 同一门课不同时段的教室可能不同 → location 挂在 Session 上
 *   - jcor 形如 "3-4"，zcd 形如 "5-12周"
 *
 * 纯解析类，无 Android 依赖（除 Log），可在 JVM 单测里直接跑。
 */
public final class JwglxtFetcher {

    private JwglxtFetcher() {}

    /** 解析结果，带统计信息便于排查 */
    public static final class Result {
        public Timetable timetable = new Timetable();
        public int rawRecords;          // kbList 原始条数
        public int courseCount;         // 聚合后的课程数
        public int failedWeekExpr;      // 周次解析失败的条数
        public int mergedDuplicates;    // 同名同时段被合并的重复时段数
        public List<String> warnings = new ArrayList<>();

        public boolean ok() {
            return courseCount > 0;
        }
    }

    /**
     * 解析课表接口返回的 JSON。
     *
     * @param json 接口原始响应
     * @return 解析结果；json 无法解析时返回 courseCount=0 的结果，不抛异常
     */
    public static Result parse(String json) {
        Result r = new Result();
        if (json == null || json.trim().isEmpty()) {
            r.warnings.add("响应为空");
            return r;
        }

        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            r.warnings.add("JSON 解析失败: " + e.getMessage());
            return r;
        }

        // 1. 学期信息
        JSONObject xsxx = root.optJSONObject("xsxx");
        if (xsxx != null) {
            String xnm = xsxx.optString("XNM", "");       // 2026
            String xqm = xsxx.optString("XQM", "");       // 3 / 12
            String xnmc = xsxx.optString("XNMC", "");     // 2026-2027
            String xqmmc = xsxx.optString("XQMMC", "");   // 1 / 2
            r.timetable.semester.id = buildSemesterId(xnm, xqm);
            r.timetable.semester.name = xnmc + " 学年第" + xqmmc + "学期";
            r.timetable.semester.totalWeeks = 20;
        }

        // 2. 课表记录
        JSONArray kbList = root.optJSONArray("kbList");
        if (kbList == null) {
            r.warnings.add("响应中没有 kbList");
            return r;
        }
        r.rawRecords = kbList.length();

        // 3. 按 jxb_id 聚合。没有 jxb_id 时退化为「课程名+教师」
        Map<String, Timetable.Course> grouped = new LinkedHashMap<>();

        for (int i = 0; i < kbList.length(); i++) {
            JSONObject rec = kbList.optJSONObject(i);
            if (rec == null) continue;

            String name = clean(rec.optString("kcmc", ""));
            if (name.isEmpty()) continue;

            String teacher = clean(rec.optString("xm", ""));
            String jxbId = clean(rec.optString("jxb_id", ""));
            String key = !jxbId.isEmpty() ? ("jwglxt_" + jxbId) : ("jwglxt_" + name + "_" + teacher);

            Timetable.Course course = grouped.get(key);
            if (course == null) {
                course = new Timetable.Course();
                course.key = key;
                course.name = name;
                course.teacher = teacher;
                course.credit = parseDouble(rec.optString("xf", "0"));
                course.category = clean(rec.optString("kcbj", ""));
                course.examType = clean(rec.optString("khfsmc", ""));
                course.source = Timetable.SOURCE_JWGLXT;
                course.colorIndex = Math.abs(key.hashCode()) % 8;
                grouped.put(key, course);
            }

            // 4. 构造时段
            Timetable.Session session = new Timetable.Session();
            session.dayOfWeek = parseInt(rec.optString("xqj", "0"), 1);

            String jcor = clean(rec.optString("jcor", ""));
            if (jcor.isEmpty()) jcor = clean(rec.optString("jc", ""));
            int[] sections = parseSections(jcor);
            session.startSection = sections[0];
            session.endSection = sections[1];

            String zcd = clean(rec.optString("zcd", ""));
            if (zcd.isEmpty()) zcd = clean(rec.optString("zcmc", ""));
            session.weeksRaw = zcd;
            WeekRange.Parsed wr = WeekRange.parse(zcd);
            session.weeks.addAll(wr.weeks);
            if (!wr.ok()) {
                r.failedWeekExpr++;
                r.warnings.add("周次解析失败: '" + zcd + "' (" + name + ")");
            }

            session.location = clean(rec.optString("cdmc", ""));
            session.campus = clean(rec.optString("xqmc", ""));
            session.roomType = clean(rec.optString("kclbmc", ""));
            session.teacher = teacher;

            // 去重：同一课程同一时段可能被重复下发
            if (!containsSession(course, session)) {
                course.sessions.add(session);
            }
        }

        List<Timetable.Course> courses = new ArrayList<>(grouped.values());
        r.mergedDuplicates = mergeOverlaps(courses);
        if (r.mergedDuplicates > 0) {
            r.warnings.add("合并了 " + r.mergedDuplicates + " 个同名同时段的重复时段");
        }
        r.timetable.courses.addAll(courses);
        CourseColorAssigner.assign(r.timetable);
        r.courseCount = r.timetable.courses.size();
        return r;
    }

    // ---------- 字段清洗与转换 ----------

    /**
     * 清洗字段。正方返回的文本偶尔带 HTML 转义与全角空格。
     * 注意：不能对中文做 URL 解码（会导致乱码），只处理明确的转义实体。
     */
    static String clean(String s) {
        if (s == null) return "";
        String out = s
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace('\u00A0', ' ')   // NBSP
                .replace('\u3000', ' ')   // 全角空格
                .trim();
        return out;
    }

    static double parseDouble(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 解析节次。真实格式 {@code 1-2} / {@code 3-4}，兜底格式 {@code 1-2节}。
     * @return [start, end]
     */
    static int[] parseSections(String jcor) {
        if (jcor == null || jcor.isEmpty()) return new int[]{1, 1};
        String digits = jcor.replaceAll("[^0-9\\-]", "");
        if (digits.isEmpty()) return new int[]{1, 1};
        int dash = digits.indexOf('-');
        if (dash > 0 && dash < digits.length() - 1) {
            int a = parseInt(digits.substring(0, dash), 1);
            int b = parseInt(digits.substring(dash + 1), a);
            if (a > b) { int t = a; a = b; b = t; }
            return new int[]{a, b};
        }
        int a = parseInt(digits.replace("-", ""), 1);
        return new int[]{a, a};
    }

    static String buildSemesterId(String xnm, String xqm) {
        String year = xnm;
        if (year.length() == 4) {
            try {
                int y = Integer.parseInt(year);
                year = y + "-" + (y + 1);
            } catch (NumberFormatException ignored) {}
        }
        int term;
        switch (xqm) {
            case "3":  term = 1; break;
            case "12": term = 2; break;
            case "16": term = 3; break;
            default:   term = 1; break;
        }
        return year + "-" + term;
    }

    /**
     * 教务偶尔把同一门课在同一时段下发成两条（不同 jxb_id、或同一教学班周次写法不同），
     * 例如「形势与政策」按专题拆成多个教学班。课表上它们是同一节课，不应排成两列冲突。
     * 规则：课程名相同 + 星期相同 + 节次相同 + 周次有交集 → 并成一个时段（周次取并集，
     * 教师/教室不同时拼在一起）。不同名的课真冲突时照常并排显示。
     *
     * @return 被合并掉的时段数
     */
    static int mergeOverlaps(List<Timetable.Course> courses) {
        int merged = 0;
        for (int i = 0; i < courses.size(); i++) {
            Timetable.Course a = courses.get(i);
            for (int j = i; j < courses.size(); j++) {
                Timetable.Course b = courses.get(j);
                if (!a.name.equals(b.name)) continue;
                for (int si = 0; si < a.sessions.size(); si++) {
                    Timetable.Session x = a.sessions.get(si);
                    int start = (a == b) ? si + 1 : 0;
                    for (int sj = start; sj < b.sessions.size(); sj++) {
                        Timetable.Session y = b.sessions.get(sj);
                        if (x.dayOfWeek != y.dayOfWeek || x.startSection != y.startSection
                                || x.endSection != y.endSection || !intersects(x.weeks, y.weeks)) continue;
                        mergeTeachers(x, y, a.teacher, b.teacher);
                        boolean added = false;
                        for (Integer w : y.weeks) if (!x.weeks.contains(w)) { x.weeks.add(w); added = true; }
                        java.util.Collections.sort(x.weeks);
                        if (added && !y.weeksRaw.isEmpty() && !x.weeksRaw.equals(y.weeksRaw)) {
                            x.weeksRaw = x.weeksRaw.isEmpty() ? y.weeksRaw : x.weeksRaw + "," + y.weeksRaw;
                        }
                        x.location = joinDistinct(x.location, y.location, " / ");
                        b.sessions.remove(sj);
                        sj--;
                        merged++;
                    }
                }
            }
        }
        for (int i = courses.size() - 1; i >= 0; i--) {
            if (courses.get(i).sessions.isEmpty()) courses.remove(i);
        }
        return merged;
    }

    /**
     * 合并两条记录的教师：周次范围更窄的那条更具体（如「第11周 刘锦春」比「11-14周 夏菲」具体），
     * 它覆盖的周记到 weekTeachers 里；宽的那条作为本时段默认教师。
     */
    private static void mergeTeachers(Timetable.Session x, Timetable.Session y,
                                      String courseTeacherX, String courseTeacherY) {
        String tx = x.teacher.isEmpty() ? courseTeacherX : x.teacher;
        String ty = y.teacher.isEmpty() ? courseTeacherY : y.teacher;
        if (ty == null || ty.isEmpty() || ty.equals(tx)) {
            for (java.util.Map.Entry<Integer, String> e : y.weekTeachers.entrySet()) {
                x.weekTeachers.putIfAbsent(e.getKey(), e.getValue());
            }
            return;
        }
        if (y.weeks.size() <= x.weeks.size() || x.weeks.isEmpty()) {
            for (Integer w : y.weeks) x.weekTeachers.put(w, ty);
        } else {
            for (Integer w : x.weeks) x.weekTeachers.putIfAbsent(w, tx);
            x.teacher = ty;
        }
        for (java.util.Map.Entry<Integer, String> e : y.weekTeachers.entrySet()) {
            x.weekTeachers.put(e.getKey(), e.getValue());
        }
    }

    private static boolean intersects(List<Integer> a, List<Integer> b) {
        for (Integer w : a) if (b.contains(w)) return true;
        return false;
    }

    private static String joinDistinct(String a, String b, String sep) {
        if (b == null || b.isEmpty()) return a == null ? "" : a;
        if (a == null || a.isEmpty()) return b;
        for (String p : a.split(java.util.regex.Pattern.quote(sep))) if (p.equals(b)) return a;
        return a + sep + b;
    }

    private static boolean containsSession(Timetable.Course c, Timetable.Session s) {
        for (Timetable.Session x : c.sessions) {
            if (x.dayOfWeek == s.dayOfWeek
                    && x.startSection == s.startSection
                    && x.endSection == s.endSection
                    && x.weeksRaw.equals(s.weeksRaw)) {
                return true;
            }
        }
        return false;
    }
}
