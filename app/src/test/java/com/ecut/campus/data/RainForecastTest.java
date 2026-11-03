package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RainForecastTest {

    @Test
    public void levelOfThresholdsAndMaxWithMm() {
        assertEquals(RainForecast.NONE, RainForecast.levelOf(0, 0.0));
        assertEquals(RainForecast.NONE, RainForecast.levelOf(29, 0.4));
        assertEquals(RainForecast.LIGHT, RainForecast.levelOf(30, 0.0));
        assertEquals(RainForecast.LIGHT, RainForecast.levelOf(59, 0.2));
        assertEquals(RainForecast.MID, RainForecast.levelOf(60, 0.0));
        assertEquals(RainForecast.MID, RainForecast.levelOf(89, 1.0));
        assertEquals(RainForecast.HEAVY, RainForecast.levelOf(90, 0.0));
        assertEquals(RainForecast.HEAVY, RainForecast.levelOf(100, 8.5));

        // 按降水量 mm 加权，二者取大
        assertEquals(RainForecast.LIGHT, RainForecast.levelOf(10, 0.6));
        assertEquals(RainForecast.MID, RainForecast.levelOf(35, 1.8));
        assertEquals(RainForecast.HEAVY, RainForecast.levelOf(50, 4.5));
    }

    @Test
    public void parseAndQueryMaxOverClassHours() {
        String json = "{\n"
                + "  \"hourly\": {\n"
                + "    \"time\": [\n"
                + "      \"2026-11-03T08:00\",\n"
                + "      \"2026-11-03T09:00\",\n"
                + "      \"2026-11-03T10:00\",\n"
                + "      \"2026-11-03T11:00\",\n"
                + "      \"2026-11-03T14:00\",\n"
                + "      \"2026-11-03T15:00\"\n"
                + "    ],\n"
                + "    \"precipitation_probability\": [15, 25, 38, 72, 92, 85],\n"
                + "    \"precipitation\": [0.0, 0.1, 0.4, 1.8, 5.2, 3.1]\n"
                + "  }\n"
                + "}";
        long t0 = 1_700_000_000_000L;
        RainForecast rf = RainForecast.parse(json, t0);
        assertNotNull(rf);
        assertFalse(rf.isEmpty());

        // 08:00 - 09:40（8点、9点两小时，最大 25% < 30%）：不下雨
        RainForecast.Hit h1 = rf.query("2026-11-03", 8 * 60, 9 * 60 + 40, t0 + 1000L);
        assertEquals(RainForecast.NONE, h1.level);
        assertFalse(h1.isRaining());
        assertEquals("", h1.summaryText());

        // 10:00 - 11:40（10点 38%、11点 72%）：取最大 72% -> 中雨
        RainForecast.Hit h2 = rf.query("2026-11-03", 10 * 60, 11 * 60 + 40, t0 + 1000L);
        assertEquals(RainForecast.MID, h2.level);
        assertEquals(72, h2.maxProb);
        assertEquals(1.8, h2.maxMm, 0.01);
        assertEquals("72% 可能下雨", h2.summaryText());
        assertTrue(h2.detailText().contains("72% 可能下雨 · 中雨"));
        assertTrue(h2.detailText().contains("记得带伞"));

        // 14:00 - 15:40（14点 92% / 5.2mm）：大雨
        RainForecast.Hit h3 = rf.query("2026-11-03", 14 * 60, 15 * 60 + 40, t0 + 1000L);
        assertEquals(RainForecast.HEAVY, h3.level);
        assertEquals(92, h3.maxProb);
    }

    @Test
    public void refreshAndExpiryBoundaries() {
        String json = "{\"hourly\":{\"time\":[\"2026-11-03T10:00\"],"
                + "\"precipitation_probability\":[80],\"precipitation\":[2.0]}}";
        long t0 = 1_700_000_000_000L;
        RainForecast rf = RainForecast.parse(json, t0);
        assertNotNull(rf);

        // 3 小时内无需刷新，满 3 小时需要刷新
        assertFalse(rf.needsRefresh(t0 + RainForecast.REFRESH_MS - 1L));
        assertTrue(rf.needsRefresh(t0 + RainForecast.REFRESH_MS));

        // 离线缓存未超 18 小时仍可用，超过后过期不显示
        assertFalse(rf.isExpired(t0 + RainForecast.EXPIRE_MS));
        assertEquals(RainForecast.MID,
                rf.query("2026-11-03", 10 * 60, 10 * 60 + 45, t0 + RainForecast.EXPIRE_MS).level);
        assertTrue(rf.isExpired(t0 + RainForecast.EXPIRE_MS + 1L));
        assertEquals(RainForecast.NONE,
                rf.query("2026-11-03", 10 * 60, 10 * 60 + 45, t0 + RainForecast.EXPIRE_MS + 1L).level);
    }

    @Test
    public void invalidJsonReturnsNull() {
        assertNull(RainForecast.parse(null, 1000L));
        assertNull(RainForecast.parse("", 1000L));
        assertNull(RainForecast.parse("{}", 1000L));
        assertNull(RainForecast.parse("{\"hourly\":{\"time\":[]}}", 1000L));
    }

    @Test
    public void widgetChipPropagatesRainLevel() {
        DayPlan.Item a = new DayPlan.Item();
        a.name = "普通地质学";
        a.startMin = 8 * 60;
        a.endMin = 9 * 60 + 40;
        a.location = "广兰 3-416";
        a.colorIndex = 2;
        a.rainLevel = RainForecast.MID;

        DayPlan.Item b = new DayPlan.Item();
        b.name = "大学物理实验";
        b.startMin = 14 * 60;
        b.endMin = 15 * 60 + 40;
        b.location = "实验楼 208";
        b.colorIndex = 5;
        b.rainLevel = RainForecast.HEAVY;

        WidgetDay.Card card = WidgetDay.build(
                WidgetDay.OK, "2026-11-03", 9, "2026-09-07",
                Arrays.asList(a, b), Collections.emptyList(), 7 * 60 + 30, 3);
        assertEquals(2, card.chips.size());
        assertEquals(RainForecast.MID, card.chips.get(0).rainLevel);
        assertEquals(RainForecast.HEAVY, card.chips.get(1).rainLevel);
    }

    @Test
    public void rainBlueNeverUsesVermilionAndParamsMatchSpec() {
        assertNotEquals(0xFFFF4A1C, RainForecast.RAIN_BLUE_DAY);
        assertNotEquals(0xFFFF4A1C, RainForecast.RAIN_BLUE_NIGHT);
        assertEquals(6, RainForecast.streakCount(RainForecast.LIGHT));
        assertEquals(11, RainForecast.streakCount(RainForecast.MID));
        assertEquals(18, RainForecast.streakCount(RainForecast.HEAVY));
        assertEquals(0.45f, RainForecast.glowStrength(RainForecast.LIGHT), 0.001f);
        assertEquals(0.70f, RainForecast.glowStrength(RainForecast.MID), 0.001f);
        assertEquals(1.00f, RainForecast.glowStrength(RainForecast.HEAVY), 0.001f);
    }

    @Test
    public void urlUsesTwoDecimalPlacesForPrivacy() {
        String u = RainForecast.url(28.681234, 115.859876);
        assertTrue(u.contains("latitude=28.68&longitude=115.86"));
        assertFalse(u.contains("28.6812"));
        assertFalse(u.contains("115.8599"));
    }

    @Test
    public void widgetBitmapsAt3xDensityAndMaxChipsStayFarBelow1MB() {
        int[] wh = RainForecast.widgetChipSizePx(3.0f);
        assertEquals(240, wh[0]);
        assertEquals(46, wh[1]);
        int oneChipBytes = RainForecast.widgetTotalBitmapBytes(1, 3.0f);
        assertEquals(240 * 46 * 4, oneChipBytes);
        int maxTotalBytes = RainForecast.widgetTotalBitmapBytes(RainForecast.WIDGET_MAX_CHIPS, 3.0f);
        // 8 条课程全部下雨时约 353 KB，远低于 1MB 跨进程上限
        assertEquals(353_280, maxTotalBytes);
        assertTrue(maxTotalBytes < RainForecast.BINDER_SAFE_BYTES / 2);
    }
}
