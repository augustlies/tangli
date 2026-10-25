package com.ecut.campus;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 未设开学日期时，标题、日期表头、当前周必须用同一个起点（内置校历）。 */
public class EffectiveStartTest {

    @Test
    public void manualStartWins() {
        assertEquals("2026-09-07", WeekMath.effectiveStart("2026-09-09", "2026-2027-1"));
    }

    @Test
    public void emptyStart_usesCalendarBySemesterId() {
        assertEquals("2026-09-07", WeekMath.effectiveStart("", "2026-2027-1"));
        assertEquals("2027-03-01", WeekMath.effectiveStart(null, "2026-2027-2"));
    }

    @Test
    public void unknownHistoricalSemester_staysEmpty() {
        assertEquals("", WeekMath.effectiveStart("", "2019-2020-1"));
    }

    @Test
    public void week10DatesMatchOfficialCalendar() {
        String start = WeekMath.effectiveStart("", "2026-2027-1");
        String[] d = WeekMath.weekDates(start, 10);
        assertEquals("11/09", d[0]);
        assertEquals("11/15", d[6]);
    }
}
