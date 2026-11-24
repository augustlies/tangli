package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Pure scheduling helpers shared by the Android reminder receiver and JVM tests. */
public final class ReminderSchedule {
    public static final int LOOKAHEAD_DAYS = 8;

    private ReminderSchedule() {}

    /** First local midnight outside a search covering today through the next seven days. */
    public static long nextSearchAt(long todayMidnight) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(todayMidnight);
        cal.add(Calendar.DAY_OF_MONTH, LOOKAHEAD_DAYS);
        return cal.getTimeInMillis();
    }

    /** All same-day classes with the selected class's exact start time. */
    public static List<DayPlan.Item> sameStart(List<DayPlan.Item> items, int startMin) {
        List<DayPlan.Item> out = new ArrayList<>();
        if (items == null) return out;
        for (DayPlan.Item item : items) if (item.startMin == startMin) out.add(item);
        return out;
    }

    /** Whether any recurring session or explicit adjustment can produce a class from the boundary onward. */
    public static boolean hasPotentialFutureClass(Timetable timetable, String semesterStart,
                                                  String firstOutsideLookahead) {
        if (timetable == null || !Dates.valid(semesterStart) || !Dates.valid(firstOutsideLookahead)) return false;
        int boundaryWeek = Dates.weekOf(semesterStart, firstOutsideLookahead);
        int boundaryDay = Dates.dayOfWeek(firstOutsideLookahead);
        if (boundaryWeek <= 0 || boundaryDay <= 0) return false;
        if (timetable.courses != null) {
            for (Timetable.Course course : timetable.courses) {
                if (course == null || course.sessions == null) continue;
                for (Timetable.Session session : course.sessions) {
                    if (session == null) continue;
                    if (session.dayOfWeek < 1 || session.dayOfWeek > 7) continue;
                    if (!session.hasCustomTime() && (session.startSection < 1
                            || session.endSection < session.startSection || session.endSection >= 17)) continue;
                    if (session.weeks == null || session.weeks.isEmpty()) {
                        // Empty weeks follow the timetable's fallback rule and remain active every week.
                        return true;
                    } else {
                        for (int week : session.weeks) {
                            if (week > boundaryWeek
                                    || (week == boundaryWeek && session.dayOfWeek >= boundaryDay)) return true;
                        }
                    }
                }
            }
        }
        if (timetable.adjusts != null) {
            for (Timetable.Adjust adjust : timetable.adjusts) {
                if (adjust == null) continue;
                if (Timetable.Adjust.MAKEUP.equals(adjust.type)
                        && Dates.valid(adjust.date) && adjust.date.compareTo(firstOutsideLookahead) >= 0) return true;
                if (Timetable.Adjust.MOVE.equals(adjust.type)) {
                    int targetWeek = WeekOverlay.targetWeek(adjust);
                    int targetDay = adjust.toDay > 0 ? adjust.toDay : adjust.day;
                    if (targetWeek > boundaryWeek
                            || (targetWeek == boundaryWeek
                            && (targetDay == 0 || targetDay >= boundaryDay))) return true;
                }
            }
        }
        return false;
    }
}
