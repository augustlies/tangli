package com.ecut.campus;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 1.2.4 交互：课块实时进度、拨动周数、圆形扩散半径。 */
public class InteractionMathTest {

    @Test
    public void liveProgressOnlyInsideClass() {
        assertEquals(-1f, TimetableView.liveProgressOf(479, 480, 525), 0f);
        assertEquals(-1f, TimetableView.liveProgressOf(525, 480, 525), 0f);
        assertEquals(0.5f, TimetableView.liveProgressOf(500, 480, 520), 1e-4);
        // 刚开始也给一点点，让人看得出「已经开始」
        assertEquals(0.02f, TimetableView.liveProgressOf(480, 480, 525), 1e-4);
        assertEquals(-1f, TimetableView.liveProgressOf(500, 520, 480), 0f);
    }

    @Test
    public void scrubStepsAndClamp() {
        assertEquals(0, WeekScrubber.stepsFor(20f, 26f));
        assertEquals(2, WeekScrubber.stepsFor(55f, 26f));
        assertEquals(-1, WeekScrubber.stepsFor(-30f, 26f));
        assertEquals(0, WeekScrubber.stepsFor(100f, 0f));
        assertEquals(1, WeekScrubber.clamp(-3, 20));
        assertEquals(20, WeekScrubber.clamp(25, 20));
        assertEquals(7, WeekScrubber.clamp(7, 20));
    }

    @Test
    public void revealCoversFarthestCorner() {
        assertEquals(500f, ThemeReveal.revealRadius(0f, 0f, 300f, 400f), 1e-3);
        assertEquals(250f, ThemeReveal.revealRadius(150f, 200f, 300f, 400f), 1e-3);
    }

    @Test
    public void blockPlanKeepsRoomBeforeTeacherAndTag() {
        float[] title3 = {14f, 28f, 42f};
        // 地方够：全都放
        int[] p = TimetableView.blockPlan(200f, title3, 3f, 12f, 0f, 12f, 12f);
        assertEquals(3, p[0]);
        assertEquals(1 | 4 | 8, p[1]);
        // 两节课高度但字体大：课名 3 行 + 教室 + 教师，标签挤掉，绝不裁切
        p = TimetableView.blockPlan(73f, title3, 3f, 12f, 0f, 12f, 12f);
        assertEquals(3, p[0]);
        assertEquals(1 | 4, p[1]);
        // 很矮：课名压到 2 行也要留教室
        p = TimetableView.blockPlan(44f, title3, 3f, 12f, 0f, 12f, 12f);
        assertEquals(2, p[0]);
        assertEquals(1, p[1]);
        // 连 1 行课名 + 教室都放不下：只剩课名
        p = TimetableView.blockPlan(20f, title3, 3f, 12f, 0f, 12f, 12f);
        assertEquals(1, p[0]);
        assertEquals(0, p[1]);
        // 没有教室：教师、标签按顺序
        p = TimetableView.blockPlan(60f, title3, 3f, 0f, 0f, 12f, 12f);
        assertEquals(3, p[0]);
        assertEquals(4, p[1]);
    }
}
