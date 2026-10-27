package com.ecut.campus;

import static org.junit.Assert.*;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableShare;

import org.junit.Test;

public class ScheduleShareTest {

    @Test
    public void scheduleTravelsInShareCode() throws Exception {
        ScheduleSettings mine = ScheduleSettings.createDefault();
        mine.firstClassStart = "08:20";
        mine.setSectionCount(12);
        mine.section(3).durationMinutes = 50;
        mine.section(4).breakAfterMinutes = 120;
        Timetable t = new Timetable();
        t.semester.startDate = "2026-09-07";
        String code = TimetableShare.encode(t, "小董", mine.toShare());
        TimetableShare.Shared sh = TimetableShare.decode(code);
        assertNotNull(sh.schedule);
        ScheduleSettings theirs = ScheduleSettings.fromShare(sh.schedule);
        assertNotNull(theirs);
        assertEquals(12, theirs.sectionCount());
        assertTrue(theirs.sameTimesAs(mine));
        assertFalse(theirs.sameTimesAs(ScheduleSettings.createDefault()));
        assertEquals(mine.rangeLabel(5), theirs.rangeLabel(5));
    }

    @Test
    public void oldCodesHaveNoSchedule() throws Exception {
        String code = TimetableShare.encode(new Timetable(), "a");
        assertNull(TimetableShare.decode(code).schedule);
        assertNull(ScheduleSettings.fromShare(null));
        assertNull(ScheduleSettings.fromShare(new org.json.JSONObject().put("f", "25:00")));
    }
}
