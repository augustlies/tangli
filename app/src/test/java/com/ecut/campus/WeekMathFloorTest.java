package com.ecut.campus;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeekMathFloorTest {
    @Test public void firstDayIsWeekOne() {
        assertEquals(1, WeekMath.weekOfDate("2026-09-07", "2026-09-07"));
        assertEquals(1, WeekMath.weekOfDate("2026-09-07", "2026-09-13"));
        assertEquals(2, WeekMath.weekOfDate("2026-09-07", "2026-09-14"));
    }

    @Test public void beforeSemesterIsNotWeekOne() {
        assertEquals(0, WeekMath.weekOfDate("2026-09-07", "2026-09-06"));
        assertEquals(0, WeekMath.weekOfDate("2026-09-07", "2026-08-31"));
        assertEquals(-1, WeekMath.weekOfDate("2026-09-07", "2026-08-30"));
    }
}
