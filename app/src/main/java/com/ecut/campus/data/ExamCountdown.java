package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure Java exam-name matching, semester scheduling and widget ordering. */
public final class ExamCountdown {
    private ExamCountdown() {}
    public static final class Event {
        public final String id, name, date;
        public Event(String name, String date) {
            this("", name, date);
        }
        public Event(String id, String name, String date) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.date = date == null ? "" : date;
        }
    }
    public static boolean isExam(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT).replace(" ", "").replace("　", "");
        if (n.equals("四级") || n.equals("六级") || n.equals("ncre") || n.matches(".*ncre(一级|二级|三级|四级)?考试.*") || n.matches(".*cet-?([46]|四六级).*" ) || n.contains("四六级") || n.contains("英语四级") || n.contains("英语六级")
                || n.matches(".*英语.*[四六四五六七八九一二三]级.*") || n.matches(".*[四六四五六七八九一二三]级.*英语.*")
                || n.contains("计算机二级") || n.contains("全国计算机等级") || n.contains("考研")
                || n.contains("研究生入学考试") || n.contains("国考") || n.contains("国家公务员考试")
                || n.contains("普通话水平测试")) return true;
        return n.contains("考试") && !n.equals("考试") || n.contains("测验") || n.contains("检测")
                || n.contains("期中") || n.contains("期末");
    }
    public static String nextCourseDate(Timetable timetable, Timetable.Course course, String semesterStart, int totalWeeks, String today) {
        return nextCourseDates(timetable, semesterStart, totalWeeks, today).getOrDefault(course, "");
    }

    public static Map<Timetable.Course, String> nextCourseDates(Timetable timetable, String semesterStart,
                                                                 int totalWeeks, String today) {
        Map<Timetable.Course, String> nextDates = new IdentityHashMap<>();
        if (timetable == null || !Dates.valid(semesterStart) || !Dates.valid(today)) return nextDates;
        int lastWeek = totalWeeks > 0 ? Math.min(totalWeeks, WeekRange.MAX_WEEK) : WeekRange.MAX_WEEK;
        for (int week = 1; week <= lastWeek; week++) {
            WeekOverlay overlay = WeekOverlay.build(timetable, semesterStart, week);
            for (Timetable.Course course : timetable.courses) {
                for (Timetable.Session session : course.sessions) {
                    if (session.dayOfWeek < 1 || session.dayOfWeek > 7 || !session.isActive(week)
                            || overlay.cancelled.containsKey(session)) continue;
                    keepEarlier(nextDates, course, Dates.dateOf(semesterStart, week, session.dayOfWeek), today);
                }
            }
            for (WeekOverlay.Extra extra : overlay.extras) {
                if (extra.session.dayOfWeek >= 1 && extra.session.dayOfWeek <= 7)
                    keepEarlier(nextDates, extra.course,
                            Dates.dateOf(semesterStart, week, extra.session.dayOfWeek), today);
            }
        }
        return nextDates;
    }

    private static void keepEarlier(Map<Timetable.Course, String> nextDates, Timetable.Course course,
                                    String candidate, String today) {
        if (candidate.compareTo(today) >= 0) {
            String previous = nextDates.get(course);
            if (previous == null || candidate.compareTo(previous) < 0) nextDates.put(course, candidate);
        }
    }
    public static List<Event> upcoming(List<Event> source, String today, int capacity) {
        return upcoming(source, today, capacity, 0, null);
    }
    public static List<Event> upcoming(List<Event> source, String today, int capacity,
                                       int maxDays, Set<String> hiddenIds) {
        List<Event> out = new ArrayList<>();
        if (source != null && Dates.valid(today)) for (Event e : source) {
            if (e == null || e.name.trim().isEmpty() || !Dates.valid(e.date)) continue;
            int days = remainingDays(today, e.date);
            if (days < 0) continue;
            if (maxDays > 0 && days > maxDays) continue;
            if (hiddenIds != null && !e.id.isEmpty() && hiddenIds.contains(e.id)) continue;
            out.add(e);
        }
        out.sort((a, b) -> a.date.compareTo(b.date));
        if (out.size() > Math.max(0, capacity)) out.subList(Math.max(0, capacity), out.size()).clear();
        return out;
    }
    public static int remainingDays(String today, String date) {
        return Dates.valid(today) && Dates.valid(date) ? (int)(Dates.epochDay(date)-Dates.epochDay(today)) : -1;
    }
    public static int capacity(int level) { return level == WidgetDay.WIDE ? 2 : 1; }
}
