package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ecut.campus.ScheduleSettings;

import org.junit.Test;

public class SemesterMetaPolicyTest {

    @Test
    public void archiveDateAlwaysWinsOverStaleMirror() {
        assertEquals("2026-09-07", SemesterMetaPolicy.resolveStartDate("2026-09-07"));
        assertEquals("", SemesterMetaPolicy.resolveStartDate(null));
        assertEquals("", SemesterMetaPolicy.resolveStartDate(""));
    }

    @Test
    public void totalWeeksAreClampedToSupportedRange() {
        assertEquals(1, SemesterMetaPolicy.resolveTotalWeeks(0));
        assertEquals(20, SemesterMetaPolicy.resolveTotalWeeks(20));
        assertEquals(30, SemesterMetaPolicy.resolveTotalWeeks(99));
        assertEquals(SemesterMetaPolicy.MAX_WEEKS, 30);
    }

    @Test
    public void legacyMirrorOnlySeedsSameSemester() {
        assertTrue(SemesterMetaPolicy.shouldSeedStartDate(
                "2026-2027-1", "2026-2027-1", "", "2026-09-07"));
        assertFalse(SemesterMetaPolicy.shouldSeedStartDate(
                "2026-2027-1", "2025-2026-1", "", "2026-09-07"));
    }

    @Test
    public void existingArchiveDateIsNeverReplacedByMirror() {
        assertFalse(SemesterMetaPolicy.shouldSeedStartDate(
                "2026-2027-1", "2026-2027-1", "2026-09-07", "2026-09-14"));
    }

    @Test
    public void archiveTotalWeeksWinsEvenWhenItEqualsDefault() {
        assertFalse(SemesterMetaPolicy.shouldSeedTotalWeeks(
                "2026-2027-1", "2026-2027-1", true, 18));
        assertFalse(SemesterMetaPolicy.shouldSeedTotalWeeks(
                "2026-2027-1", "2026-2027-1", true, 24));
        assertTrue(SemesterMetaPolicy.shouldSeedTotalWeeks(
                "2026-2027-1", "2026-2027-1", false, 24));
        assertFalse(SemesterMetaPolicy.shouldSeedTotalWeeks(
                "2026-2027-1", "2025-2026-1", false, 24));
    }

    @Test
    public void archiveDateIsIndependentFromTotalWeeksSeeding() {
        assertTrue(SemesterMetaPolicy.shouldSeedStartDate(
                "2026-2027-1", "2026-2027-1", "", "2026-09-07"));
        assertFalse(SemesterMetaPolicy.shouldSeedStartDate(
                "2026-2027-1", "2026-2027-1", "2026-09-14", "2026-09-07"));
    }

    @Test
    public void syncMergeKeepsArchiveWeeksAndOnlyUsesRemoteDateForNewSemester() {
        Timetable local = semester("2026-2027-1", "2026-09-07", 18, true);
        Timetable remote = semester("2026-2027-1", "", 20, false);
        Timetable merged = TimetableStore.merge(local, remote);
        assertEquals("2026-09-07", merged.semester.startDate);
        assertTrue(merged.semester.startDateTouched);
        assertEquals(18, merged.semester.totalWeeks);

        Timetable other = semester("2025-2026-1", "2025-02-24", 12, true);
        Timetable remoteOther = semester("2025-2026-1", "", 20, false);
        Timetable switched = TimetableStore.merge(local, remoteOther);
        assertEquals("", switched.semester.startDate);
        assertFalse(switched.semester.startDateTouched);
        assertEquals(20, switched.semester.totalWeeks);

        Timetable noDate = semester("2025-2026-1", "", 16, false);
        Timetable mergedNoDate = TimetableStore.merge(noDate, remoteOther);
        assertEquals("", mergedNoDate.semester.startDate);
        assertEquals(16, mergedNoDate.semester.totalWeeks);
    }

    @Test
    public void legacyCourseMirrorStillProducesIcsAfterSettingsSave() {
        Timetable t = new Timetable();
        t.semester.id = "2026-2027-1";
        t.semester.name = "2026-2027 学年第一学期";
        t.semester.startDate = "2026-09-07";
        t.semester.totalWeeks = 20;

        Timetable.Course c = new Timetable.Course();
        c.name = "矿床学";
        Timetable.Session s = new Timetable.Session();
        s.weeksRaw = "1-2周";
        s.weeks.addAll(WeekRange.parse(s.weeksRaw).weeks);
        c.sessions.add(s);
        t.courses.add(c);

        String ics = IcsExporter.export(t, ScheduleSettings.createDefault(), "测试学期");
        assertTrue(ics != null && ics.contains("SUMMARY:矿床学"));
    }

    private static Timetable semester(String id, String startDate, int totalWeeks,
                                      boolean touched) {
        Timetable t = new Timetable();
        t.semester.id = id;
        t.semester.name = id;
        t.semester.startDate = startDate;
        t.semester.totalWeeks = totalWeeks;
        t.semester.startDateTouched = touched;
        t.semester.sortOrder = 3;
        return t;
    }
}
