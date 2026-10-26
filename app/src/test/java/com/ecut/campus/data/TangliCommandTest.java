package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class TangliCommandTest {

    private static TangliCommand.Env env() {
        TangliCommand.Env e = new TangliCommand.Env();
        e.start = "2026-09-07";
        e.today = "2026-10-25";
        e.totalWeeks = 18;
        e.sectionStart = new int[]{0, 480, 535, 600, 655, 840, 895, 950, 1005, 1140, 1195};
        e.sectionEnd = new int[]{0, 525, 580, 645, 700, 885, 940, 995, 1050, 1185, 1240};
        return e;
    }

    private static Timetable base() {
        Timetable t = new Timetable();
        Timetable.Course c = new Timetable.Course();
        c.key = "jx1"; c.name = "大学英语"; c.source = Timetable.SOURCE_JWGLXT; c.teacher = "教师甲";
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 5; s.startSection = 1; s.endSection = 2;
        for (int i = 1; i <= 16; i++) s.weeks.add(i);
        c.sessions.add(s);
        t.courses.add(c);
        return t;
    }

    @Test
    public void addWithCodeFenceFullWidthAndChatter() {
        String text = "好的，这是指令：\n```\n烫梨指令 v1\n＋ 地质学 ｜ 周三 ｜ 3-4节 ｜ 1-16周(单) ｜ 3-402 ｜ 教师乙\n+ 地质学考试 | 周三 | 14:30-16:30 | 第10周 | 3-402\n```";
        TangliCommand.Plan p = TangliCommand.plan(text, base(), env());
        assertEquals(2, p.changeCount());
        assertEquals(0, p.errorCount());
        Timetable.Course geo = p.result.findByName("地质学");
        assertEquals(Timetable.SOURCE_IMPORT, geo.source);
        Timetable.Session s = geo.sessions.get(0);
        assertEquals(3, s.dayOfWeek);
        assertEquals(3, s.startSection);
        assertEquals(8, s.weeks.size());
        assertEquals("3-402", s.location);
        assertEquals("教师乙", geo.teacher);
        Timetable.Session ex = p.result.findByName("地质学考试").sessions.get(0);
        assertEquals("14:30", ex.timeStart);
        assertEquals(5, ex.startSection); // 14:30-16:30 覆盖第 5-7 节
        assertEquals(7, ex.endSection);
        assertEquals(java.util.Collections.singletonList(10), ex.weeks);
    }

    @Test
    public void addTwiceIsIdempotent() {
        String text = "烫梨指令 v1\n+ 地质学 | 周三 | 3-4节 | 1-16周";
        Timetable once = TangliCommand.plan(text, base(), env()).result;
        TangliCommand.Plan again = TangliCommand.plan(text, once, env());
        assertEquals(0, again.changeCount());
        assertEquals(1, once.findByName("地质学").sessions.size());
    }

    @Test
    public void removeWeeksAndWholeAndWarnJw() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n- 大学英语 | 周五 | 1-2节 | 9-16周", base(), env());
        assertTrue(p.items.get(0).warn);
        assertEquals(8, p.result.findByName("大学英语").sessions.get(0).weeks.size());
        TangliCommand.Plan q = TangliCommand.plan("烫梨指令 v1\n- 大学英语", base(), env());
        assertNull(q.result.findByName("大学英语"));
        TangliCommand.Plan r = TangliCommand.plan("烫梨指令 v1\n- 不存在的课", base(), env());
        assertEquals(1, r.errorCount());
    }

    @Test
    public void cancelAndMoveShowInOverlay() {
        String text = "烫梨指令 v1\n停 大学英语 | 第10周周五 1-2节\n调 大学英语 | 第11周 | 周五 | 1-2节 -> 第12周 | 周六 | 3-4节 | 2-101";
        TangliCommand.Plan p = TangliCommand.plan(text, base(), env());
        assertEquals(0, p.errorCount());
        assertEquals(2, p.result.adjusts.size());
        Timetable.Session orig = p.result.courses.get(0).sessions.get(0);
        WeekOverlay w10 = WeekOverlay.build(p.result, "2026-09-07", 10);
        assertEquals("停课", w10.cancelled.get(orig).label);
        WeekOverlay w11 = WeekOverlay.build(p.result, "2026-09-07", 11);
        assertEquals("已调走", w11.cancelled.get(orig).label);
        WeekOverlay w12 = WeekOverlay.build(p.result, "2026-09-07", 12);
        assertEquals(1, w12.extras.size());
        Timetable.Session moved = w12.extras.get(0).session;
        assertEquals(6, moved.dayOfWeek);
        assertEquals(3, moved.startSection);
        assertEquals(4, moved.endSection);
        assertEquals("2-101", moved.location);
        // 重复导入不重复加
        assertEquals(0, TangliCommand.plan(text, p.result, env()).changeCount());
        // 没这节课的周
        assertEquals(1, TangliCommand.plan("烫梨指令 v1\n停 大学英语 | 第17周 | 周五", base(), env()).errorCount());
    }

    @Test
    public void relativeWeekAndDate() {
        // 今天 2026-10-25 是第 7 周周日 → 下周 = 第 8 周
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n停 大学英语 | 下周五", base(), env());
        assertEquals(0, p.errorCount());
        assertEquals(8, p.result.adjusts.get(0).week);
        TangliCommand.Plan q = TangliCommand.plan("烫梨指令 v1\n停 大学英语 | 11月6日", base(), env());
        assertEquals(9, q.result.adjusts.get(0).week);
        assertEquals(5, q.result.adjusts.get(0).day);
    }

    @Test
    public void builtInHolidayAndMakeup() {
        Timetable t = base();
        Timetable.Course c = new Timetable.Course();
        c.key = "jx2"; c.name = "地球化学"; c.source = Timetable.SOURCE_JWGLXT;
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 3; s.startSection = 3; s.endSection = 4;
        for (int i = 1; i <= 16; i++) s.weeks.add(i);
        c.sessions.add(s);
        t.courses.add(c);
        // 第 4 周：10/1–10/7 周四到周三… 10/1 是第 4 周周四
        WeekOverlay w4 = WeekOverlay.build(t, "2026-09-07", 4);
        assertTrue(w4.dayOff[5]);
        assertEquals("放假", w4.cancelled.get(t.courses.get(0).sessions.get(0)).label);
        // 第 5 周周三（10/7）放假，周六（10/10）补 10/7 的课
        WeekOverlay w5 = WeekOverlay.build(t, "2026-09-07", 5);
        assertTrue(w5.dayOff[3]);
        assertEquals(1, w5.extras.size());
        assertEquals(6, w5.extras.get(0).session.dayOfWeek);
        assertEquals("补周三", w5.dayLabels[6]);
    }

    @Test
    public void userOffAndMakeupAndExportRoundTrip() {
        String text = "烫梨指令 v1\n假 2027-01-04 至 2027-01-05 | 校庆\n补 2027-01-09 | 周一";
        TangliCommand.Plan p = TangliCommand.plan(text, base(), env());
        assertEquals(0, p.errorCount());
        assertEquals(3, p.result.adjusts.size());
        String out = TangliCommand.export(p.result, true);
        assertTrue(out.startsWith("烫梨指令 v1 整表"));
        TangliCommand.Plan back = TangliCommand.plan(out, new Timetable(), env());
        assertEquals(0, back.errorCount());
        assertEquals(1, back.result.courses.size());
        assertEquals(3, back.result.adjusts.size());
        assertEquals(16, back.result.courses.get(0).sessions.get(0).weeks.size());
    }

    @Test
    public void replaceAllClearsFirst() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1 整表\n开学 2026-09-09\n+ 新课 | 周一 | 第1节", base(), env());
        assertTrue(p.replaceAll);
        assertTrue(p.items.get(0).warn);
        assertEquals(1, p.result.courses.size());
        assertEquals("2026-09-07", p.result.semester.startDate);
        assertEquals(1, p.result.courses.get(0).sessions.get(0).endSection);
    }

    @Test
    public void badLinesReportedOthersApplied() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n+ 只有名字\n+ 好课 | 周二 | 5-6节", base(), env());
        assertEquals(1, p.errorCount());
        assertEquals(1, p.changeCount());
    }

    @Test
    public void guideContainsCurrentWeekAndCourses() {
        String g = AiGuide.build(base(), env(), "第1节 08:00-08:45");
        assertTrue(g.contains("本周是第 7 周"));
        assertTrue(g.contains("+ 大学英语 | 周五 | 1-2节 | 1-16周"));
    }

    @Test
    public void mergeKeepsAdjusts() {
        Timetable local = TangliCommand.plan("烫梨指令 v1\n停 大学英语 | 第10周 | 周五", base(), env()).result;
        local.semester.id = "2026-2027-1";
        Timetable remote = base();
        remote.semester.id = "2026-2027-1";
        assertEquals(1, TimetableStore.merge(local, remote).adjusts.size());
    }
    @Test
    public void addSameSlotWithRoomUpdatesInsteadOfSkipping() {
        Timetable once = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 第1周", base(), env()).result;
        TangliCommand.Plan again = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 第1周 | 3-402", once, env());
        assertEquals(1, again.changeCount());
        Timetable.Course geo = again.result.findByName("地质学");
        assertEquals(1, geo.sessions.size());
        assertEquals("3-402", geo.sessions.get(0).location);
        // 再发一次完全一样的：不算改动
        assertEquals(0, TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 第1周 | 3-402", again.result, env()).changeCount());
    }
    @Test
    public void partialWeekRoomSplitsInsteadOfDuplicating() {
        Timetable once = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 1-16周", base(), env()).result;
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 第1周 | 3-402", once, env());
        assertEquals(1, p.changeCount());
        Timetable.Course geo = p.result.findByName("地质学");
        assertEquals(2, geo.sessions.size());
        Timetable.Session rest = geo.sessions.get(0), w1 = geo.sessions.get(1);
        assertEquals(15, rest.weeks.size());
        assertFalse(rest.weeks.contains(1));
        assertEquals("", rest.location);
        assertEquals(java.util.Collections.singletonList(1), w1.weeks);
        assertEquals("3-402", w1.location);
        assertTrue(p.items.get(0).desc.contains("其余周不变"));
    }

    @Test
    public void allWeeksRoomUpdatesWithoutExtendingWeeks() {
        Timetable once = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 1-16周", base(), env()).result;
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n+ 地质学 | 周一 | 1-2节 | 全部 | 3-402", once, env());
        Timetable.Course geo = p.result.findByName("地质学");
        assertEquals(1, geo.sessions.size());
        assertEquals(16, geo.sessions.get(0).weeks.size());
        assertEquals("3-402", geo.sessions.get(0).location);
    }

    @Test
    public void moveMultiWeekSplitsPerWeek() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n调 大学英语 | 3-5周 | 周五 | 1-2节 -> 周六 | 3-4节", base(), env());
        assertEquals(0, p.errorCount());
        assertEquals(3, p.result.adjusts.size());
        java.util.Set<Integer> weeks = new java.util.HashSet<>();
        for (Timetable.Adjust a : p.result.adjusts) { weeks.add(a.week); assertEquals(a.week, a.toWeek); assertEquals(6, a.toDay); }
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(3, 4, 5)), weeks);
    }
}
