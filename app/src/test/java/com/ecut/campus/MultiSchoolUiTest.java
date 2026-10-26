package com.ecut.campus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.ecut.campus.data.CourseColorAssigner;
import com.ecut.campus.data.Timetable;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 1.2.4：可变节数、课程不撞色、胶囊 Dock、拦截页小工具。 */
public class MultiSchoolUiTest {

    @Test
    public void sectionCountDefaultsToTenAndRoundTrips() throws Exception {
        ScheduleSettings s = ScheduleSettings.createDefault();
        assertEquals(10, s.sectionCount());
        s.setSectionCount(12);
        ScheduleSettings back = ScheduleSettings.fromJson(s.toJson());
        assertEquals(12, back.sectionCount());
        ScheduleSettings old = ScheduleSettings.fromJson("{\"schemaVersion\":1,\"firstClassStart\":\"08:00\"}");
        assertEquals(10, old.sectionCount());
        s.setSectionCount(99);
        assertEquals(ScheduleSettings.MAX_SECTIONS, s.sectionCount());
        s.setSectionCount(1);
        assertEquals(ScheduleSettings.MIN_SECTIONS, s.sectionCount());
    }

    @Test
    public void ensureCoversOnlyGrowsAndAddsBreak() {
        ScheduleSettings s = ScheduleSettings.createDefault();
        assertFalse(s.ensureCovers(9));
        assertEquals(10, s.sectionCount());
        s.section(10).breakAfterMinutes = 0;
        assertTrue(s.ensureCovers(12));
        assertEquals(12, s.sectionCount());
        assertEquals(10, s.section(10).breakAfterMinutes);
        assertEquals("20:50", s.startTime(11));
        assertFalse(s.ensureCovers(11));
        assertEquals(12, s.sectionCount());
    }

    @Test
    public void maxSectionUsedReadsSessions() {
        Timetable t = new Timetable();
        assertEquals(0, ScheduleSettings.maxSectionUsed(t));
        t.courses.add(course("高数", Timetable.SOURCE_JWGLXT, 11, 12));
        t.courses.add(course("英语", Timetable.SOURCE_JWGLXT, 1, 2));
        assertEquals(12, ScheduleSettings.maxSectionUsed(t));
    }

    @Test
    public void differentNamesNeverShareColorWithinSemester() {
        Timetable t = new Timetable();
        for (int i = 0; i < 20; i++) t.courses.add(course("课" + i, Timetable.SOURCE_JWGLXT, 1, 2));
        t.courses.add(course("课3", Timetable.SOURCE_JWGLXT, 5, 6));
        CourseColorAssigner.assign(t);
        Map<String, Integer> byName = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (Timetable.Course c : t.courses) {
            Integer prev = byName.get(c.name);
            if (prev != null) {
                assertEquals(prev.intValue(), c.colorIndex);
            } else {
                assertTrue("不同课程不撞色: " + c.name, used.add(c.colorIndex));
                byName.put(c.name, c.colorIndex);
            }
        }
        assertEquals(20, used.size());
    }

    @Test
    public void autoColorsAvoidManualCourses() {
        Timetable t = new Timetable();
        Timetable.Course manual = course("社团", "custom", 9, 10);
        manual.colorIndex = 0;
        t.courses.add(manual);
        t.courses.add(course("地球化学", Timetable.SOURCE_JWGLXT, 1, 2));
        CourseColorAssigner.assign(t);
        assertEquals(0, manual.colorIndex);
        assertNotEquals(0, t.courses.get(1).colorIndex);
    }

    @Test
    public void dedupeSplitsCollidingNames() {
        Timetable t = new Timetable();
        Timetable.Course a = course("矿床学", Timetable.SOURCE_JWGLXT, 1, 2);
        Timetable.Course b = course("矿石学", Timetable.SOURCE_JWGLXT, 3, 4);
        Timetable.Course b2 = course("矿石学", Timetable.SOURCE_JWGLXT, 9, 10);
        a.colorIndex = 4;
        b.colorIndex = 4;
        b2.colorIndex = 4;
        t.courses.add(a);
        t.courses.add(b);
        t.courses.add(b2);
        CourseColorAssigner.dedupe(t);
        assertEquals(4, a.colorIndex);
        assertNotEquals(4, b.colorIndex);
        assertEquals(b.colorIndex, b2.colorIndex);
    }

    @Test
    public void paletteSizesAgree() {
        assertEquals(CourseColorAssigner.PALETTE_SIZE, TimetableView.PALETTE_BG.length);
        assertEquals(CourseColorAssigner.PALETTE_SIZE, TimetableView.PALETTE_FG.length);
        assertEquals(CourseColorAssigner.PALETTE_SIZE, TimetableView.PALETTE_GLASS.length);
        Set<Integer> distinct = new HashSet<>();
        for (int c : TimetableView.PALETTE_GLASS) distinct.add(c);
        assertEquals(CourseColorAssigner.PALETTE_SIZE, distinct.size());
    }

    @Test
    public void dockSlotFollowsFinger() {
        assertEquals(0f, CapsuleDock.slotAt(-50f, 10f, 100f, 3), 0f);
        assertEquals(0f, CapsuleDock.slotAt(60f, 10f, 100f, 3), 1e-4);
        assertEquals(1f, CapsuleDock.slotAt(160f, 10f, 100f, 3), 1e-4);
        assertEquals(2f, CapsuleDock.slotAt(999f, 10f, 100f, 3), 0f);
        assertEquals(1.5f, CapsuleDock.slotAt(210f, 10f, 100f, 3), 1e-4);
    }

    @Test
    public void dnsHostNormalizeAndLabels() {
        assertEquals("ad.example.com", DnsControl.normalizeHost(" https://AD.example.com:443/x?y#z "));
        assertEquals("example.com", DnsControl.normalizeHost("example.com."));
        assertEquals("", DnsControl.normalizeHost(null));
        assertEquals("刚刚开启", DnsControl.uptimeLabel(30_000));
        assertEquals("已守护 12 分钟", DnsControl.uptimeLabel(12 * 60_000L));
        assertEquals("已守护 3 小时 12 分", DnsControl.uptimeLabel((3 * 60 + 12) * 60_000L));
        assertEquals("已守护 2 小时", DnsControl.uptimeLabel(120 * 60_000L));
        assertEquals("已守护 1 天 2 小时", DnsControl.uptimeLabel(26 * 3_600_000L));
        assertEquals("已暂停，30 分钟后自动恢复", DnsControl.pauseLabel(30 * 60_000L, 0));
        assertEquals("已暂停，1 分钟后自动恢复", DnsControl.pauseLabel(5_000L, 0));
    }

    @Test
    public void pickerWeekKeyGroupsMondayToSunday() {
        int mon = GaugePickers.weekKey(2026, 8, 7);
        assertEquals(mon, GaugePickers.weekKey(2026, 8, 10));
        assertEquals(mon, GaugePickers.weekKey(2026, 8, 13));
        assertNotEquals(mon, GaugePickers.weekKey(2026, 8, 14));
        assertNotEquals(mon, GaugePickers.weekKey(2026, 8, 6));
    }

    @Test
    public void assignKeepsExistingColorsWhenOrderChanges() {
        Timetable t = new Timetable();
        for (int i = 0; i < 6; i++) t.courses.add(course("课" + i, Timetable.SOURCE_JWGLXT, 1, 2));
        CourseColorAssigner.assign(t);
        Map<String, Integer> before = new HashMap<>();
        for (Timetable.Course c : t.courses) before.put(c.name, c.colorIndex);
        java.util.Collections.reverse(t.courses);
        t.courses.remove(2);
        t.courses.add(course("新课", Timetable.SOURCE_JWGLXT, 3, 4));
        t.courses.get(t.courses.size() - 1).colorIndex = before.get("课0"); // 新课碰巧撞色
        CourseColorAssigner.assign(t);
        for (Timetable.Course c : t.courses) {
            if (before.containsKey(c.name)) assertEquals(c.name, before.get(c.name).intValue(), c.colorIndex);
        }
        assertNotEquals(before.get("课0").intValue(), t.courses.get(t.courses.size() - 1).colorIndex);
    }

    @Test
    public void minuteStepsKeepOddValue() {
        assertEquals(12, GaugePickers.minuteSteps(30).length);
        int[] odd = GaugePickers.minuteSteps(37);
        assertEquals(13, odd.length);
        assertEquals(37, odd[8]);
    }

    static Timetable.Course course(String name, String source, int start, int end) {
        Timetable.Course c = new Timetable.Course();
        c.name = name;
        c.source = source;
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 1;
        s.startSection = start;
        s.endSection = end;
        c.sessions.add(s);
        return c;
    }
}
