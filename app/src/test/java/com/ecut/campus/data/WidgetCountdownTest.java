package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 1.2.6 vc42：「今日课程」拉成 2×2 的收紧规则和倒计时。 */
public class WidgetCountdownTest {

    private static DayPlan.Item it(String name, int s, int e, String loc, int color) {
        DayPlan.Item i = new DayPlan.Item();
        i.name = name; i.startMin = s; i.endMin = e; i.location = loc; i.colorIndex = color;
        return i;
    }

    private static final int H = 60;
    private static final List<DayPlan.Item> TUE = Arrays.asList(
            it("原子核物理", 8 * H, 9 * H + 40, "3-518", 4),
            it("检测与转换技术", 10 * H, 11 * H + 40, "3-416", 3),
            it("嵌入式实验", 11 * H + 50, 12 * H + 30, "B105", 2),
            it("大学物理", 16 * H, 17 * H + 40, "", 5));
    private static final List<DayPlan.Item> WED = Arrays.asList(it("构造地质学", 8 * H, 9 * H + 40, "A201", 1));

    @Test
    public void widthLevels() {
        assertEquals(WidgetDay.WIDE, WidgetDay.widthLevel(250, 1f));
        assertEquals(WidgetDay.WIDE, WidgetDay.widthLevel(0, 1f)); // 拿不到宽度按 4×2
        assertEquals(WidgetDay.MID, WidgetDay.widthLevel(200, 1f));
        assertEquals(WidgetDay.NARROW, WidgetDay.widthLevel(150, 1f));
        assertEquals(WidgetDay.NARROW, WidgetDay.widthLevel(200, 1.3f)); // 字大了更早收紧
        assertEquals(WidgetDay.WIDE, WidgetDay.widthLevel(250, 0.85f));
    }

    @Test
    public void ongoingCountsDownToEndAndNextWithinHourCountsDownToStart() {
        // 11:00：检测与转换技术进行中（到 11:40），嵌入式实验 11:50 开始（50 分钟内）
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 11 * H, 4);
        WidgetDay.Chip a = c.chips.get(0), b = c.chips.get(1), d = c.chips.get(2);
        assertEquals(WidgetDay.Chip.CD_LEFT, a.cdKind);
        assertEquals(11 * H + 40, a.cdMin);
        assertEquals("进行中", a.tag);
        assertEquals(WidgetDay.Chip.CD_SOON, b.cdKind);
        assertEquals(11 * H + 50, b.cdMin);
        assertEquals("", b.tag);
        assertEquals(WidgetDay.Chip.CD_NONE, d.cdKind); // 只给最近的一门
        assertEquals(-1, d.cdMin);
    }

    @Test
    public void nextMoreThanAnHourAwayHasNoCountdown() {
        // 9:50：下一门 10:00 → 有；12:40：下一门 16:00 → 没有
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 9 * H + 50, 4);
        assertEquals(WidgetDay.Chip.CD_SOON, c.chips.get(0).cdKind);
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 12 * H + 40, 4);
        assertEquals(1, c.chips.size());
        assertEquals(WidgetDay.Chip.CD_NONE, c.chips.get(0).cdKind);
        // 正好 60 分钟算
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 15 * H, 4);
        assertEquals(WidgetDay.Chip.CD_SOON, c.chips.get(0).cdKind);
    }

    @Test
    public void tomorrowChipNeverCountsDown() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 23 * H, 4);
        assertEquals("明天", c.chips.get(0).tag);
        assertEquals(WidgetDay.Chip.CD_NONE, c.chips.get(0).cdKind);
    }

    @Test
    public void narrowCompactsHeaderChipsAndLines() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 11 * H, 5, WidgetDay.NARROW);
        assertEquals("周二 · 还有 3 门", c.title);
        assertEquals("3", c.title.substring(c.numStart, c.numEnd));
        assertEquals(2, c.chips.size()); // 窄的时候最多 2 条
        assertEquals(1, c.more);
        assertEquals("", c.chips.get(0).line); // 倒计时单占第二行
        assertTrue(c.chips.get(0).ongoing);
        assertEquals("", c.chips.get(0).tag); // 窄的时候不写「进行中」
        assertEquals("", c.chips.get(1).line);
        assertEquals("+1", c.chips.get(1).tag);
        // 没有倒计时的课：只丢教室，时间留着
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 6 * H, 5, WidgetDay.NARROW); // 离上课 2 小时，没有倒计时
        assertEquals("08:00 - 09:40", c.chips.get(0).line);
    }

    @Test
    public void midDropsRoomKeepsTimeAndCountdownLine() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 11 * H, 5, WidgetDay.MID);
        assertEquals("今日周二，还有 3 门课要上", c.title);
        assertEquals("10:00 - 11:40", c.chips.get(0).line);
        assertEquals(WidgetDay.Chip.CD_LEFT, c.chips.get(0).cdKind);
        // 宽：时间 + 教室
        c = WidgetDay.build(WidgetDay.OK, "2026-10-27", 8, "", TUE, WED, 11 * H, 5);
        assertEquals("10:00 - 11:40  3-416", c.chips.get(0).line);
    }

    @Test
    public void refreshAlsoWakesAnHourBeforeClass() {
        // 12:40 之后：下一刻是 15:00（16:00 前 60 分钟），不是 16:00
        assertEquals(15 * H, WidgetDay.nextRefreshMin(TUE, 12 * H + 40));
        // 11:00：最近的是 11:40 下课（11:50 前 60 = 10:50 已过）
        assertEquals(11 * H + 40, WidgetDay.nextRefreshMin(TUE, 11 * H));
        assertEquals(-1, WidgetDay.nextRefreshMin(TUE, 18 * H));
        assertEquals(-1, WidgetDay.nextRefreshMin(null, 0));
    }

    @Test
    public void staticFallbackText() {
        assertEquals("还剩 34 分钟", WidgetDay.countdownText(WidgetDay.Chip.CD_LEFT, 11 * H + 40, 11 * H + 6));
        assertEquals("12 分钟后上课", WidgetDay.countdownText(WidgetDay.Chip.CD_SOON, 10 * H, 9 * H + 48));
        assertEquals("", WidgetDay.countdownText(WidgetDay.Chip.CD_NONE, 10 * H, 9 * H));
    }

    @Test
    public void nonOkStatesUnchangedWhenNarrow() {
        WidgetDay.Card c = WidgetDay.build(WidgetDay.NO_TIMETABLE, "2026-10-27", 0, "", null, null, 0, 2, WidgetDay.NARROW);
        assertEquals("还没有课表", c.title);
        assertTrue(c.chips.isEmpty());
    }
}
