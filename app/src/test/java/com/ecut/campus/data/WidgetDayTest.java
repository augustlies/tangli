package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WidgetDayTest {

    private static DayPlan.Item it(String name, int s, int e, String loc, int color) {
        DayPlan.Item i = new DayPlan.Item();
        i.name = name; i.startMin = s; i.endMin = e; i.location = loc; i.colorIndex = color;
        return i;
    }

    private static final int H = 60;
    private static final List<DayPlan.Item> TUE = Arrays.asList(
            it("原子核物理", 8 * H, 9 * H + 40, "3-518", 4),
            it("检测与转换技术★", 10 * H, 11 * H + 40, "南昌校区 3-416", 3),
            it("嵌入式实验", 16 * H, 17 * H + 40, "", 2));
    private static final List<DayPlan.Item> WED = Arrays.asList(
            it("构造地质学", 8 * H, 9 * H + 40, "A201", 1),
            it("大学物理", 14 * H, 15 * H + 40, "B105", 5));
    private static final List<DayPlan.Item> NONE = Collections.emptyList();

    @Test
    public void ongoingAndUpcomingRemainFinishedDisappear() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "2026-09-07", TUE, WED, 11 * H + 23, 3);
        assertEquals("今日周二，还有 2 门课要上", c.title);
        assertEquals("2", c.title.substring(c.numStart, c.numEnd));
        assertEquals("10.27 / 第8周", c.sub);
        assertEquals(2, c.chips.size());
        assertEquals("检测与转换技术★", c.chips.get(0).name);
        assertEquals("10:00 - 11:40  南昌校区 3-416", c.chips.get(0).line);
        assertTrue(c.chips.get(0).ongoing);
        assertEquals("进行中", c.chips.get(0).tag);
        assertEquals(3, c.chips.get(0).colorIndex);
        assertEquals("16:00 - 17:40", c.chips.get(1).line); // 没教室只写时间
        assertFalse(c.chips.get(1).ongoing);
        assertEquals("", c.chips.get(1).tag);
        assertEquals("", c.note);
    }

    @Test
    public void classEndingNowIsGoneAndStartingNowIsOngoing() {
        // 9:40 第一节刚下课
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 9 * H + 40, 5);
        assertEquals(2, c.chips.size());
        assertEquals("检测与转换技术★", c.chips.get(0).name);
        // 10:00 整开始算进行中
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 10 * H, 5);
        assertTrue(c.chips.get(0).ongoing);
    }

    @Test
    public void capShowsPlusMoreOnLastChip() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 7 * H, 2);
        assertEquals("今日周二，还有 3 门课要上", c.title);
        assertEquals(2, c.chips.size());
        assertEquals(1, c.more);
        assertEquals("+1", c.chips.get(1).tag);
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 8 * H + 5, 1);
        assertEquals("进行中 · +2", c.chips.get(0).tag);
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 7 * H, 0); // cap 至少 1
        assertEquals(1, c.chips.size());
    }

    @Test
    public void doneTodayFallsBackToTomorrowFirstClass() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 18 * H, 3);
        assertEquals("今天的课上完了", c.title);
        assertEquals(-1, c.numStart);
        assertEquals(1, c.chips.size());
        assertEquals("构造地质学", c.chips.get(0).name);
        assertEquals("明天", c.chips.get(0).tag);
        assertEquals("08:00 - 09:40  A201", c.chips.get(0).line);
    }

    @Test
    public void noClassTodayAndTomorrow() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-31", 8, "", NONE, WED, 9 * H, 3);
        assertEquals("今天没课", c.title);
        assertEquals("明天", c.chips.get(0).tag);
        c = WidgetDay.build(WidgetDay.OK, "2026-10-31", 8, "", NONE, NONE, 9 * H, 3);
        assertEquals("今天没课", c.title);
        assertTrue(c.chips.isEmpty());
        assertEquals("明天也没课，好好休息", c.note);
    }

    @Test
    public void semesterStates() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.BEFORE, "2026-08-30", -1, "2026-09-07", NONE, NONE, 0, 3);
        assertEquals("还没开学", c.title);
        assertEquals("开学见，第 1 周从 9.07 开始", c.note);
        assertEquals("8.30 周日", c.sub);
        c = WidgetDay.build(WidgetDay.AFTER, "2027-01-20", 20, "2026-09-07", NONE, NONE, 0, 3);
        assertEquals("这学期的课上完啦", c.title);
        assertFalse(c.sub.contains("周 /"));
        assertTrue(c.chips.isEmpty());
        c = WidgetDay.build(WidgetDay.NO_TIMETABLE, "2026-10-27", 0, "", NONE, NONE, 0, 3);
        assertEquals("还没有课表", c.title);
        assertFalse(c.note.isEmpty());
        c = WidgetDay.build(WidgetDay.NO_START, "2026-10-27", 0, "", NONE, NONE, 0, 3);
        assertFalse(c.note.isEmpty());
    }

    @Test
    public void nextBoundaryIsNearestStartOrEnd() {
        assertEquals(8 * H, WidgetDay.nextBoundary(TUE, 7 * H));
        assertEquals(9 * H + 40, WidgetDay.nextBoundary(TUE, 8 * H + 30));
        assertEquals(10 * H, WidgetDay.nextBoundary(TUE, 9 * H + 40));
        assertEquals(-1, WidgetDay.nextBoundary(TUE, 18 * H));
        assertEquals(-1, WidgetDay.nextBoundary(new ArrayList<>(), 0));
        assertEquals(2, WidgetDay.remaining(TUE, 9 * H + 40).size());
    }

    @Test
    public void dayPlanCarriesCourseColor() {
        Timetable t = new Timetable();
        Timetable.Course a = new Timetable.Course();
        a.name = "构造地质学";
        a.colorIndex = 7;
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 2; s.startSection = 1; s.endSection = 2; s.location = "A201";
        s.weeks = TimetableShare.list(1, 2, 3);
        a.sessions.add(s);
        t.courses.add(a);
        int[] st = new int[13], en = new int[13];
        for (int i = 1; i <= 12; i++) { st[i] = 480 + (i - 1) * 55; en[i] = st[i] + 45; }
        List<DayPlan.Item> l = DayPlan.of(t, "2026-09-07", "2026-09-08", st, en);
        assertEquals(1, l.size());
        assertEquals(7, l.get(0).colorIndex);
    }
}
