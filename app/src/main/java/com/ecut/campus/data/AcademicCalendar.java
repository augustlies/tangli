package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 东华理工大学校历（内置数据，来源：官网「校园生活 → 校历」，2026-06-22 发布）。
 * 纯 Java，不依赖 Android；日期一律 yyyy-MM-dd 字符串。
 * 每年 6 月学校发布新校历后，在 YEARS 里追加一个学年即可。
 */
public final class AcademicCalendar {

    private AcademicCalendar() {}

    public static final String SOURCE_LIST_URL = "https://www.ecut.edu.cn/7595/list.htm";

    public static final class Term {
        public final String id;          // 2026-2027-1
        public final String name;        // 2026-2027 学年第一学期
        public final String classStart;  // 第 1 周周一
        public final int teachingWeeks;
        public final String examStart, examEnd;
        public final String breakName, breakStart, breakEnd;

        Term(String id, String name, String classStart, int teachingWeeks,
             String examStart, String examEnd, String breakName, String breakStart, String breakEnd) {
            this.id = id; this.name = name; this.classStart = classStart;
            this.teachingWeeks = teachingWeeks; this.examStart = examStart; this.examEnd = examEnd;
            this.breakName = breakName; this.breakStart = breakStart; this.breakEnd = breakEnd;
        }
    }

    public static final class Event {
        public final String start, end; // 含首尾
        public final String label;
        public final boolean holiday;   // 放假/节日（true）还是校内安排（false）

        Event(String start, String end, String label, boolean holiday) {
            this.start = start; this.end = end; this.label = label; this.holiday = holiday;
        }

        public boolean covers(String ymd) {
            return ymd.compareTo(start) >= 0 && ymd.compareTo(end) <= 0;
        }
    }

    public static final List<Term> TERMS = new ArrayList<>();
    public static final List<Event> EVENTS = new ArrayList<>();

    static {
        // ===== 2026-2027 学年 =====
        TERMS.add(new Term("2026-2027-1", "2026-2027 学年第一学期", "2026-09-07", 18,
                "2027-01-11", "2027-01-22", "寒假", "2027-01-23", "2027-02-26"));
        TERMS.add(new Term("2026-2027-2", "2026-2027 学年第二学期", "2027-03-01", 17,
                "2027-06-26", "2027-07-02", "暑假", "2027-07-03", "2027-09-01"));

        ev("2026-09-03", "2026-09-04", "老生报到", false);
        ev("2026-09-05", "2026-09-06", "南昌校区新生报到", false);
        ev("2026-09-09", "2026-09-09", "抚州校区新生报到", false);
        ev("2026-09-10", "2026-09-10", "教师节", true);
        ev("2026-09-25", "2026-09-25", "中秋节", true);
        ev("2026-10-01", "2026-10-07", "国庆节", true);
        ev("2026-10-16", "2026-10-16", "70 周年校庆", false);
        ev("2026-10-21", "2026-10-23", "校运动会", false);
        ev("2027-01-01", "2027-01-01", "元旦", true);
        ev("2027-01-11", "2027-01-22", "期末考试周", false);
        ev("2027-01-23", "2027-02-26", "寒假", true);
        ev("2027-02-05", "2027-02-05", "除夕", true);
        ev("2027-02-06", "2027-02-06", "春节", true);
        ev("2027-02-27", "2027-02-28", "学生报到注册", false);
        ev("2027-04-05", "2027-04-05", "清明节", true);
        ev("2027-05-01", "2027-05-01", "劳动节", true);
        ev("2027-06-09", "2027-06-09", "端午节", true);
        ev("2027-06-26", "2027-07-02", "期末考试周", false);
        ev("2027-07-03", "2027-09-01", "暑假", true);
    }

    private static void ev(String s, String e, String label, boolean holiday) {
        EVENTS.add(new Event(s, e, label, holiday));
    }

    /** 今天所在（或即将开始）的学期：考试结束 + 假期内仍算这个学期；都过了返回 null。 */
    public static Term termFor(String ymd) {
        Term prev = null;
        for (Term t : TERMS) {
            if (ymd.compareTo(t.breakEnd) <= 0) return t;
            prev = t;
        }
        return null;
    }

    /** 当天是第几教学周（开学前为 0 或负数）。 */
    public static int weekOf(Term t, String ymd) {
        long d = daysBetween(t.classStart, ymd);
        return (int) Math.floorDiv(d, 7L) + 1;
    }

    /** 当天正在进行的事件（节假日/考试周等）。 */
    public static List<Event> eventsOn(String ymd) {
        List<Event> out = new ArrayList<>();
        for (Event e : EVENTS) if (e.covers(ymd)) out.add(e);
        return out;
    }

    /** 从今天起（含正在进行的）最多 n 个事件。 */
    public static List<Event> upcoming(String ymd, int n) {
        List<Event> out = new ArrayList<>();
        for (Event e : EVENTS) {
            if (e.end.compareTo(ymd) >= 0) out.add(e);
            if (out.size() >= n) break;
        }
        return out;
    }

    /** 一句话状态：「第一学期 · 第 8 周 · 距寒假 90 天」。 */
    public static String statusLine(String ymd) {
        Term t = termFor(ymd);
        if (t == null) return "校历暂未更新到这一学年";
        String termShort = t.id.endsWith("-1") ? "第一学期" : "第二学期";
        if (ymd.compareTo(t.classStart) < 0) {
            return termShort + " · 距开学 " + daysBetween(ymd, t.classStart) + " 天";
        }
        if (ymd.compareTo(t.breakStart) >= 0) {
            return t.breakName + "中 · 距" + (t.breakName.equals("寒假") ? "开学" : "新学年")
                    + " " + Math.max(0, daysBetween(ymd, t.breakEnd) + 1) + " 天";
        }
        if (ymd.compareTo(t.examStart) >= 0) {
            return termShort + " · 期末考试周 · 距" + t.breakName + " " + daysBetween(ymd, t.breakStart) + " 天";
        }
        int w = weekOf(t, ymd);
        return termShort + " · 第 " + w + " 周 · 距" + t.breakName + " " + daysBetween(ymd, t.breakStart) + " 天";
    }

    public static long daysBetween(String fromYmd, String toYmd) {
        Calendar a = parse(fromYmd), b = parse(toYmd);
        if (a == null || b == null) return 0;
        return Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000.0);
    }

    public static String shortDate(String ymd) {
        Calendar c = parse(ymd);
        if (c == null) return ymd;
        return String.format(Locale.US, "%02d.%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static Calendar parse(String ymd) {
        if (ymd == null || ymd.length() < 10) return null;
        try {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1,
                    Integer.parseInt(ymd.substring(8, 10)), 12, 0, 0);
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
