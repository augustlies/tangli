package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java exam-name matching, semester scheduling and widget ordering. */
public final class ExamCountdown {
    private ExamCountdown() {}
    public static final class Event {
        public final String name, date;
        public Event(String name, String date) { this.name = name == null ? "" : name; this.date = date == null ? "" : date; }
    }
    public static boolean isExam(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT).replace(" ", "").replace("　", "");
        if (n.contains("cet") || n.contains("四六级") || n.contains("英语四级") || n.contains("英语六级")
                || n.matches(".*英语.*[四六四五六七八九一二三]级.*") || n.matches(".*[四六四五六七八九一二三]级.*英语.*")
                || n.contains("计算机二级") || n.contains("全国计算机等级") || n.contains("考研")
                || n.contains("研究生入学考试") || n.contains("国考") || n.contains("国家公务员考试")
                || n.contains("普通话水平测试")) return true;
        return n.contains("考试") && !n.equals("考试") || n.contains("测验") || n.contains("检测")
                || n.contains("期中") || n.contains("期末");
    }
    public static String nextCourseDate(Timetable timetable, Timetable.Course course, String semesterStart, int totalWeeks, String today) {
        if (course == null || !Dates.valid(semesterStart) || !Dates.valid(today)) return "";
        String next = "";
        int lastWeek = totalWeeks > 0 ? Math.min(totalWeeks, WeekRange.MAX_WEEK) : WeekRange.MAX_WEEK;
        for (int week = 1; week <= lastWeek; week++) {
            WeekOverlay overlay = WeekOverlay.build(timetable, semesterStart, week);
            for (Timetable.Session session : course.sessions) {
                if (session.dayOfWeek < 1 || session.dayOfWeek > 7 || !session.isActive(week)
                        || overlay.cancelled.containsKey(session)) continue;
                String candidate = Dates.dateOf(semesterStart, week, session.dayOfWeek);
                if (candidate.compareTo(today) >= 0 && (next.isEmpty() || candidate.compareTo(next) < 0)) next = candidate;
            }
            for (WeekOverlay.Extra extra : overlay.extras) {
                if (extra.course == course && extra.session.dayOfWeek >= 1 && extra.session.dayOfWeek <= 7) {
                    String candidate = Dates.dateOf(semesterStart, week, extra.session.dayOfWeek);
                    if (candidate.compareTo(today) >= 0 && (next.isEmpty() || candidate.compareTo(next) < 0)) next = candidate;
                }
            }
        }
        return next;
    }
    public static List<Event> upcoming(List<Event> source, String today, int capacity) {
        List<Event> out = new ArrayList<>();
        if (source != null && Dates.valid(today)) for (Event e : source)
            if (e != null && !e.name.trim().isEmpty() && Dates.valid(e.date) && e.date.compareTo(today) >= 0) out.add(e);
        out.sort((a,b) -> a.date.compareTo(b.date));
        if (out.size() > Math.max(0, capacity)) out.subList(Math.max(0, capacity), out.size()).clear();
        return out;
    }
    public static int remainingDays(String today, String date) {
        return Dates.valid(today) && Dates.valid(date) ? (int)(Dates.epochDay(date)-Dates.epochDay(today)) : -1;
    }
    public static int capacity(int level) { return level == WidgetDay.WIDE ? 2 : 1; }
}
