package com.ecut.campus;

import com.ecut.campus.data.AcademicCalendar;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.SemesterMetaPolicy;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 教学周与日期计算，避免 UI 层各写一套。 */
public final class WeekMath {

    private WeekMath() {}

    public static int todayDayOfWeek() {
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        return (dow == Calendar.SUNDAY) ? 7 : (dow - 1);
    }

    /** 不设学期总周数：周次从第 1 周一直往后排，最多到这里（足够覆盖整学期 + 假期）。 */
    public static final int OPEN_WEEKS_MAX = 99;
    /** 可翻页的最少周数，保证刚开学也能往后翻看。 */
    public static final int OPEN_WEEKS_MIN = 30;

    /** 开放式周跨度：至少 OPEN_WEEKS_MIN，今天所在周之后再留 4 周，最多 OPEN_WEEKS_MAX。 */
    public static int openWeekSpan(String startDate) {
        int span = OPEN_WEEKS_MIN;
        String ymd = todayDateLabel();
        int today = (startDate == null || startDate.isEmpty() || ymd == null) ? -1 : weekOfDate(startDate, ymd);
        if (today > 0) span = Math.max(span, today + 4);
        return Math.min(OPEN_WEEKS_MAX, span);
    }

    public static int currentWeek(String startDate, int totalWeeks) {
        int total = Math.max(1, Math.min(OPEN_WEEKS_MAX, totalWeeks));
        Calendar start = parseDate(normalizeToMonday(startDate));
        if (start == null) return 1;

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        long diff = today.getTimeInMillis() - start.getTimeInMillis();
        long days = Math.round(diff / (24.0 * 60 * 60 * 1000));  // 抵消夏令时等 ±1h
        if (days < 0) return 1;
        int week = (int) Math.floorDiv(days, 7L) + 1;
        return Math.max(1, Math.min(total, week));
    }

    /**
     * 实际用于算周次和日期的第 1 周周一。
     * 用户没手动设开学日期时（新用户同步完就是这样），按内置校历兜底：
     * 先按学期 id 找，学期 id 未知时取今天所在学期。都找不到返回空串。
     */
    public static String effectiveStart(String startDate, String semesterId) {
        String s = normalizeToMonday(startDate);
        if (!s.isEmpty()) return s;
        AcademicCalendar.Term t = AcademicCalendar.termById(semesterId);
        if (t == null && (semesterId == null || semesterId.isEmpty())) {
            t = AcademicCalendar.termFor(todayDateLabel());
        }
        return t != null ? t.classStart : "";
    }

    /** 任意日期落在第几周（周一为一周起点；解析失败返回 -1，可能越界由调用方收敛）。 */
    public static int weekOfDate(String startDate, String ymd) {
        Calendar start = parseDate(normalizeToMonday(startDate));
        Calendar day = parseDate(normalizeToMonday(ymd));
        if (start == null || day == null) return -1;
        long diff = day.getTimeInMillis() - start.getTimeInMillis();
        long days = Math.round(diff / (24.0 * 60 * 60 * 1000));
        // floorDiv：开学前的日期得到 0、-1…，而不是被截断成第 1 周
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    /** 距开学还有几天；已开学或未设置返回 0。 */
    public static int daysUntilStart(String startDate) {
        Calendar start = parseDate(normalizeToMonday(startDate));
        if (start == null) return 0;
        Calendar today = parseDate(todayDateLabel());
        if (today == null) return 0;
        long d = Math.round((start.getTimeInMillis() - today.getTimeInMillis()) / (24.0 * 60 * 60 * 1000));
        return d > 0 ? (int) d : 0;
    }

    /** 第 week 周周一的 yyyy-MM-dd；开学日期无效返回空串。 */
    public static String weekRangeStart(String startDate, int week) {
        Calendar cal = parseDate(normalizeToMonday(startDate));
        if (cal == null) return "";
        cal.add(Calendar.DAY_OF_YEAR, (week - 1) * 7);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    public static String[] weekDates(String startDate, int week) {
        String[] out = new String[7];
        Calendar cal = parseDate(normalizeToMonday(startDate));
        if (cal == null) {
            cal = Calendar.getInstance();
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
            cal.add(Calendar.DAY_OF_YEAR, offsetToMon);
        }
        cal.add(Calendar.DAY_OF_YEAR, (Math.max(1, week) - 1) * 7);

        SimpleDateFormat fmt = new SimpleDateFormat("MM/dd", Locale.US);
        for (int i = 0; i < 7; i++) {
            out[i] = fmt.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    /**
     * 将任意日期归一到所在周的周一。
     *
     * 学期第一周固定从周一开始；旧数据或日期选择器传入其他星期时，
     * 在这里统一收口，避免周日期范围从周三、周五开始。
     */
    public static String normalizeToMonday(String date) {
        Calendar cal = parseDate(date);
        if (cal == null) return "";
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMonday = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        cal.add(Calendar.DAY_OF_YEAR, offsetToMonday);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    public static boolean isMonday(String date) {
        Calendar cal = parseDate(date);
        return cal != null && cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
    }

    public static String weekRangeLabel(String startDate, int week) {
        String[] dates = weekDates(startDate, week);
        return dates[0] + " - " + dates[6];
    }

    public static String shortDate(String date) {
        Calendar cal = parseDate(date);
        if (cal == null) return "";
        return new SimpleDateFormat("M/d", Locale.US).format(cal.getTime());
    }

    /**
     * 从课表里最早的周次与星期反推首节课日期。
     * 例：第一周 2026-09-07，最早课程在第 5 周周一，则返回 2026-10-05。
     */
    public static String firstCourseDate(String startDate, Timetable timetable) {
        Calendar start = parseDate(normalizeToMonday(startDate));
        if (start == null || timetable == null || timetable.courses == null) return "";

        int bestWeek = Integer.MAX_VALUE;
        int bestDay = 0;
        for (Timetable.Course course : timetable.courses) {
            if (course.sessions == null) continue;
            for (Timetable.Session session : course.sessions) {
                int day = Math.max(1, Math.min(7, session.dayOfWeek));
                if (session.weeks == null || session.weeks.isEmpty()) continue;
                for (int week : session.weeks) {
                    if (week < 1) continue;
                    if (week < bestWeek || (week == bestWeek && day < bestDay)) {
                        bestWeek = week;
                        bestDay = day;
                    }
                }
            }
        }
        if (bestWeek == Integer.MAX_VALUE) return "";

        Calendar first = (Calendar) start.clone();
        first.add(Calendar.DAY_OF_YEAR, (bestWeek - 1) * 7 + bestDay - 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(first.getTime());
    }

    public static String firstCourseLabel(String startDate, Timetable timetable) {
        String date = firstCourseDate(startDate, timetable);
        if (date.isEmpty()) return "暂无可计算的首节课";
        Calendar cal = parseDate(date);
        int dow = cal == null ? 1 : cal.get(Calendar.DAY_OF_WEEK);
        String[] dayNames = {"日", "一", "二", "三", "四", "五", "六"};
        return shortDate(date) + " 周" + dayNames[dow - 1];
    }

    public static Calendar parseDate(String date) {
        if (date == null || date.trim().isEmpty()) return null;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setLenient(false);
        try {
            Date d = fmt.parse(date.trim());
            if (d == null) return null;
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public static String todayDateLabel() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
