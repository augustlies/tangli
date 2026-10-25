package com.ecut.campus.data;

import com.ecut.campus.ScheduleSettings;
import com.ecut.campus.WeekMath;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 课表 → ICS（RFC 5545）文本。全部展开为具体日期的 VEVENT，
 * 每节课一个全天事件外的 timed event，RRULE 不使用（学期内逐周展开）。
 */
public final class IcsExporter {

    private IcsExporter() {}

    /** 生成整学期 ICS 文本；无开学日期时返回 null（由调用方提示）。 */
    public static String export(Timetable t, ScheduleSettings schedule, String semesterLabel) {
        if (t == null || t.semester == null) return null;
        String start = WeekMath.normalizeToMonday(t.semester.startDate);
        if (start.isEmpty()) return null;
        int totalWeeks = SemesterMetaPolicy.clampWeeks(t.semester.totalWeeks);
        ScheduleSettings sched = schedule != null ? schedule : ScheduleSettings.createDefault();

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//TangLi//Timetable//CN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("X-WR-CALNAME:").append(escape(semesterLabel)).append("\r\n");

        SimpleDateFormat stampFmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        String stamp = stampFmt.format(new Date());
        int uidSeed = 0;

        Calendar startCal = WeekMath.parseDate(start);
        if (startCal == null) return null;

        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > 7) continue;
                for (int week = 1; week <= totalWeeks; week++) {
                    if (!s.isActive(week)) continue;
                    Calendar day = (Calendar) startCal.clone();
                    day.add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + (s.dayOfWeek - 1));
                    String ymd = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime());

                    int secStart = Math.max(1, Math.min(ScheduleSettings.SECTION_COUNT, s.startSection));
                    int secEnd = Math.max(secStart, Math.min(ScheduleSettings.SECTION_COUNT, s.endSection));
                    int startMinutes = s.hasCustomTime()
                            ? Timetable.Session.minutesOf(s.timeStart) : sched.startMinutes(secStart);
                    int endMinutes = s.hasCustomTime()
                            ? Timetable.Session.minutesOf(s.timeEnd) : sched.endMinutes(secEnd);

                    String dtStart = ymd.replace("-", "") + "T"
                            + hm(startMinutes) + "00";
                    String dtEnd = ymd.replace("-", "") + "T"
                            + hm(endMinutes) + "00";

                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:tangli-").append(System.identityHashCode(t))
                            .append('-').append(uidSeed++).append("@ecut.campus\r\n");
                    sb.append("DTSTAMP:").append(stamp).append("\r\n");
                    sb.append("DTSTART;TZID=Asia/Shanghai:").append(dtStart).append("\r\n");
                    sb.append("DTEND;TZID=Asia/Shanghai:").append(dtEnd).append("\r\n");
                    sb.append("SUMMARY:").append(escape(c.name)).append("\r\n");
                    String loc = s.location == null ? "" : s.location.trim();
                    if (!loc.isEmpty()) sb.append("LOCATION:").append(escape(loc)).append("\r\n");
                    String teacher = s.teacherFor(week, c.teacher).trim();
                    if (!teacher.isEmpty()) {
                        sb.append("DESCRIPTION:").append(escape(teacher)).append("\r\n");
                    }
                    sb.append("END:VEVENT\r\n");
                }
            }
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static String hm(int minutes) {
        int m = Math.max(0, Math.min(24 * 60 - 1, minutes));
        return String.format(Locale.US, "%02d%02d", m / 60, m % 60);
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
