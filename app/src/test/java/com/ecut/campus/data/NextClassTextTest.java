package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 1.2.7：通知栏常驻「下一节课」显示哪一门、写什么。 */
public class NextClassTextTest {

    private static DayPlan.Item it(String name, int s, int e, String loc) {
        DayPlan.Item i = new DayPlan.Item();
        i.name = name;
        i.startMin = s;
        i.endMin = e;
        i.location = loc;
        return i;
    }

    private static final List<DayPlan.Item> DAY = Arrays.asList(
            it("高等数学", 8 * 60, 9 * 60 + 40, "3-518"),
            it("地球化学", 10 * 60, 11 * 60 + 40, ""),
            it("大学英语", 14 * 60, 15 * 60 + 40, "1-201"));

    @Test
    public void beforeFirstClassFarAwayShowsNextWithoutCountdown() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 6 * 60);
        assertEquals(NextClassText.NEXT, n.kind);
        assertEquals("下一节 · 高等数学", n.title);
        assertEquals("08:00 - 09:40  3-518 · 今天还有 3 门", n.text);
        assertEquals(-1, n.countdownMin);
        assertEquals("", NextClassText.countdownLabel(n, 6 * 60));
    }

    @Test
    public void nextWithinAnHourCountsDownToStart() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 7 * 60 + 48);
        assertEquals(NextClassText.NEXT, n.kind);
        assertEquals(8 * 60, n.countdownMin);
        assertEquals("12 分钟后上课", NextClassText.countdownLabel(n, 7 * 60 + 48));
    }

    @Test
    public void duringClassShowsCurrentEndAndFollowing() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 9 * 60 + 6);
        assertEquals(NextClassText.CURRENT, n.kind);
        assertEquals("正在上 · 高等数学", n.title);
        assertEquals("09:40 下课 · 3-518 · 下一节 10:00 地球化学", n.text);
        assertEquals(9 * 60 + 40, n.countdownMin);
        assertEquals("还剩 34 分钟", NextClassText.countdownLabel(n, 9 * 60 + 6));
    }

    @Test
    public void currentWithoutRoomAndNextHasNoDanglingDots() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 14 * 60 + 30);
        assertEquals("正在上 · 大学英语", n.title);
        assertEquals("15:40 下课 · 1-201", n.text);
        n = NextClassText.build(DAY, null, 10 * 60 + 5);
        assertEquals("11:40 下课 · 下一节 14:00 大学英语", n.text);
    }

    @Test
    public void exactlyAtEndMovesOn() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 9 * 60 + 40);
        assertEquals(NextClassText.NEXT, n.kind);
        assertEquals("下一节 · 地球化学", n.title);
        assertEquals("10:00 - 11:40 · 今天还有 2 门", n.text);
    }

    @Test
    public void lastClassOfDayHasNoRemainingSuffix() {
        NextClassText.Notice n = NextClassText.build(DAY, null, 12 * 60);
        assertEquals("14:00 - 15:40  1-201", n.text);
    }

    @Test
    public void afterLastClassShowsTomorrowFirst() {
        List<DayPlan.Item> tmr = Arrays.asList(it("普通地质学", 8 * 60, 9 * 60 + 40, "5-101"), it("体育", 10 * 60, 11 * 60, ""));
        NextClassText.Notice n = NextClassText.build(DAY, tmr, 16 * 60);
        assertEquals(NextClassText.TOMORROW, n.kind);
        assertEquals("明天第一节 · 普通地质学", n.title);
        assertEquals("08:00 - 09:40  5-101", n.text);
        assertEquals(-1, n.countdownMin);
    }

    @Test
    public void nothingTodayOrTomorrowHides() {
        assertEquals(NextClassText.NONE, NextClassText.build(DAY, null, 16 * 60).kind);
        assertEquals(NextClassText.NONE, NextClassText.build(new ArrayList<>(), new ArrayList<>(), 9 * 60).kind);
        assertEquals(NextClassText.NONE, NextClassText.build(null, null, 9 * 60).kind);
    }

    @Test
    public void emptyTodayButTomorrowHasClass() {
        NextClassText.Notice n = NextClassText.build(null, Arrays.asList(it("体育", 10 * 60, 11 * 60, "")), 9 * 60);
        assertEquals(NextClassText.TOMORROW, n.kind);
        assertEquals("10:00 - 11:00", n.text);
    }
}
