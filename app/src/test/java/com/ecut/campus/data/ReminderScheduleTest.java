package com.ecut.campus.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class ReminderScheduleTest {
    @Test public void sameStartRetainsAllSimultaneousClasses() {
        DayPlan.Item first = item("地质学", 500);
        DayPlan.Item second = item("英语", 500);
        DayPlan.Item later = item("实验课", 600);
        List<DayPlan.Item> selected = ReminderSchedule.sameStart(
                java.util.Arrays.asList(first, second, later), 500);
        assertEquals(2, selected.size());
        assertSame(first, selected.get(0));
        assertSame(second, selected.get(1));
        assertTrue(ReminderSchedule.sameStart(null, 500).isEmpty());
    }

    @Test public void emptyWindowRetryUsesFirstDayOutsideEightDaySearch() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            Calendar expected = new GregorianCalendar(2026, Calendar.MARCH, 2);
            long midnight = expected.getTimeInMillis();
            expected.add(Calendar.DAY_OF_MONTH, ReminderSchedule.LOOKAHEAD_DAYS);
            assertEquals(expected.getTimeInMillis(), ReminderSchedule.nextSearchAt(midnight));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test public void retryIsNeededOnlyWhenTheFirstOutsideDayCanHaveAClass() {
        Timetable t = new Timetable();
        Timetable.Course course = new Timetable.Course();
        Timetable.Session session = new Timetable.Session();
        session.dayOfWeek = 2;
        session.weeks.add(4);
        course.sessions.add(session);
        t.courses.add(course);

        // The boundary falls on week 4 Tuesday; a Tuesday class belongs to the new search window.
        assertTrue(ReminderSchedule.hasPotentialFutureClass(t, "2026-03-02", "2026-03-24"));
        session.dayOfWeek = 1;
        assertFalse(ReminderSchedule.hasPotentialFutureClass(t, "2026-03-02", "2026-03-24"));
        assertFalse(ReminderSchedule.hasPotentialFutureClass(t, "", "2026-03-24"));
    }

    @Test public void unknownWeekSessionUsesTheSameAlwaysActiveFallbackAsDayPlan() {
        Timetable t = new Timetable();
        t.semester.totalWeeks = 3;
        Timetable.Course course = new Timetable.Course();
        course.sessions.add(new Timetable.Session()); // empty weeks use the normal fallback rule
        t.courses.add(course);
        assertTrue(ReminderSchedule.hasPotentialFutureClass(t, "2026-03-02", "2026-03-24"));
    }

    private static DayPlan.Item item(String name, int start) {
        DayPlan.Item item = new DayPlan.Item();
        item.name = name;
        item.startMin = start;
        return item;
    }
}
