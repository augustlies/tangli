package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class ImportCheckTest {

    private static Timetable.Course course(String name, int day, int a, int b, int... weeks) {
        Timetable.Course c = new Timetable.Course();
        c.name = name;
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = day; s.startSection = a; s.endSection = b;
        s.weeks = TimetableShare.list(weeks);
        c.sessions.add(s);
        return c;
    }

    @Test public void twoOverlapsAreFine() {
        Timetable t = new Timetable();
        t.courses.add(course("高等数学", 1, 1, 2, 1, 2, 3));
        t.courses.add(course("大学英语", 1, 1, 2, 1, 2, 3));
        ImportCheck.Result r = ImportCheck.check(t);
        assertFalse(r.suspicious());
        assertEquals("", ImportCheck.warning(r));
        assertEquals(2, r.maxSection);
    }

    @Test public void threeInSameWeekIsSuspicious() {
        Timetable t = new Timetable();
        t.courses.add(course("A", 3, 5, 6, 8));
        t.courses.add(course("B", 3, 5, 6, 8));
        t.courses.add(course("C", 3, 6, 7, 8, 9));
        t.courses.add(course("D", 3, 6, 6, 8));
        ImportCheck.Result r = ImportCheck.check(t);
        assertTrue(r.suspicious());
        assertEquals(4, r.maxTogether);
        assertEquals(1, r.crowdedSlots); // only 周三第6节 reaches 3 (5th section has 2)
        assertTrue(ImportCheck.warning(r).contains("发给开发者"));
    }

    @Test public void differentWeeksDoNotStack() {
        Timetable t = new Timetable();
        t.courses.add(course("A", 2, 1, 2, 1, 2));
        t.courses.add(course("B", 2, 1, 2, 3, 4));
        t.courses.add(course("C", 2, 1, 2, 5, 6));
        assertFalse(ImportCheck.check(t).suspicious());
    }

    @Test public void nullSafe() {
        assertFalse(ImportCheck.check(null).suspicious());
    }
}
