package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 周次解析器测试。
 *
 * 本机真实样本：5-12周 / 10-13周 / 9-16周（全是单区间）
 * 其余格式为正方普遍存在但本机未出现的，必须兼容。
 */
public class WeekRangeTest {

    // ---------- 本机真实格式 ----------

    @Test
    public void singleRange_realSample() {
        WeekRange.Parsed p = WeekRange.parse("5-12周");
        assertTrue(p.ok());
        assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12), p.weeks);
        assertEquals(5, p.min());
        assertEquals(12, p.max());
    }

    @Test
    public void singleRange_10to13() {
        WeekRange.Parsed p = WeekRange.parse("10-13周");
        assertEquals(Arrays.asList(10, 11, 12, 13), p.weeks);
    }

    @Test
    public void singleRange_9to16() {
        WeekRange.Parsed p = WeekRange.parse("9-16周");
        assertEquals(8, p.weeks.size());
        assertTrue(p.contains(9));
        assertTrue(p.contains(16));
        assertFalse(p.contains(17));
    }

    // ---------- 多区间 ----------

    @Test
    public void multiRange() {
        WeekRange.Parsed p = WeekRange.parse("1-5周,7-12周");
        assertTrue(p.ok());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12), p.weeks);
        assertFalse(p.contains(6)); // 第 6 周不上
    }

    @Test
    public void multiRange_fullWidthComma() {
        WeekRange.Parsed p = WeekRange.parse("1-3周，8-10周");
        assertEquals(Arrays.asList(1, 2, 3, 8, 9, 10), p.weeks);
    }

    // ---------- 单双周 ----------

    @Test
    public void oddWeeks() {
        WeekRange.Parsed p = WeekRange.parse("1-10周(单)");
        assertTrue(p.hasParity);
        assertEquals(Boolean.TRUE, p.oddOnly);
        assertEquals(Arrays.asList(1, 3, 5, 7, 9), p.weeks);
    }

    @Test
    public void evenWeeks() {
        WeekRange.Parsed p = WeekRange.parse("1-10周(双)");
        assertEquals(Boolean.FALSE, p.oddOnly);
        assertEquals(Arrays.asList(2, 4, 6, 8, 10), p.weeks);
    }

    @Test
    public void oddWeeks_单周word() {
        WeekRange.Parsed p = WeekRange.parse("1-16周(单周)");
        assertEquals(8, p.weeks.size());
        assertTrue(p.contains(1));
        assertFalse(p.contains(2));
    }

    // ---------- 离散周 ----------

    @Test
    public void discreteWeeks() {
        WeekRange.Parsed p = WeekRange.parse("1,3,5,7周");
        assertEquals(Arrays.asList(1, 3, 5, 7), p.weeks);
    }

    @Test
    public void singleWeek() {
        WeekRange.Parsed p = WeekRange.parse("3周");
        assertEquals(Arrays.asList(3), p.weeks);
    }

    // ---------- 无「周」字 ----------

    @Test
    public void noWeekSuffix() {
        WeekRange.Parsed p = WeekRange.parse("1-16");
        assertEquals(16, p.weeks.size());
    }

    // ---------- 异常与容错 ----------

    @Test
    public void reversedRange() {
        // 写反了也要认
        WeekRange.Parsed p = WeekRange.parse("12-5周");
        assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12), p.weeks);
    }

    @Test
    public void emptyInput() {
        WeekRange.Parsed p = WeekRange.parse("");
        assertFalse(p.ok());
        assertTrue(p.weeks.isEmpty());
    }

    @Test
    public void nullInput() {
        WeekRange.Parsed p = WeekRange.parse(null);
        assertNotNull(p);
        assertFalse(p.ok());
    }

    @Test
    public void garbageInput() {
        WeekRange.Parsed p = WeekRange.parse("待定");
        assertFalse(p.ok());
    }

    @Test
    public void absurdRangeRejected() {
        // 1-999 这种异常区间应被丢弃，不能撑爆内存
        WeekRange.Parsed p = WeekRange.parse("1-999周");
        assertFalse(p.ok());
    }

    // ---------- isActive 保底行为 ----------

    @Test
    public void isActive_parsedOk() {
        assertTrue(WeekRange.isActive("5-12周", 7));
        assertFalse(WeekRange.isActive("5-12周", 13));
    }

    @Test
    public void isActive_parseFailed_returnsTrue() {
        // 解析不出来时保底显示，避免课程凭空消失
        assertTrue(WeekRange.isActive("待定", 7));
        assertTrue(WeekRange.isActive("", 7));
    }

    // ---------- 压缩回表达式 ----------

    @Test
    public void compress_singleRun() {
        List<Integer> w = Arrays.asList(5, 6, 7, 8);
        assertEquals("5-8", WeekRange.compress(w));
    }

    @Test
    public void compress_twoRuns() {
        List<Integer> w = Arrays.asList(1, 2, 3, 8, 9, 10);
        assertEquals("1-3,8-10", WeekRange.compress(w));
    }

    @Test
    public void compress_singleWeek() {
        assertEquals("3", WeekRange.compress(Arrays.asList(3)));
    }

    @Test
    public void compress_empty() {
        assertEquals("", WeekRange.compress(Arrays.asList()));
    }

    @Test
    public void roundTrip() {
        // 解析 → 压缩 → 再解析，应等价
        WeekRange.Parsed a = WeekRange.parse("1-5周,7-12周");
        String compressed = WeekRange.compress(a.weeks);
        WeekRange.Parsed b = WeekRange.parse(compressed);
        assertEquals(a.weeks, b.weeks);
    }
}
