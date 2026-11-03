package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 课块下雨提示的数据模型与分级逻辑（纯 Java，不依赖 Android）。
 *
 * 数据源：Open-Meteo 7 天逐小时预报（precipitation_probability + precipitation）。
 * 分级：
 * - 不下雨（NONE = 0）：概率 &lt; 30%（且降水量不构成降雨）
 * - 小雨（LIGHT = 1）：概率 ≥ 30%（或降水量 ≥ 0.5 mm/h）
 * - 中雨（MID = 2）：概率 ≥ 60%（或降水量 ≥ 1.5 mm/h）
 * - 大雨（HEAVY = 3）：概率 ≥ 90%（或降水量 ≥ 4.0 mm/h）
 */
public final class RainForecast {

    public static final int NONE = 0, LIGHT = 1, MID = 2, HEAVY = 3;

    /** 约 3 小时刷新一次。 */
    public static final long REFRESH_MS = 3 * 3600_000L;
    /** 离线时可用缓存；超过 18 小时算过期，不再显示。 */
    public static final long EXPIRE_MS = 18 * 3600_000L;

    /** 雨雾冷色光晕（不用朱砂色）：日间 #5B7FA6，夜间 #8FB3DA。 */
    public static final int RAIN_BLUE_DAY = 0xFF5B7FA6;
    public static final int RAIN_BLUE_NIGHT = 0xFF8FB3DA;

    /** 小组件单条课程雨效位图固定按 1 倍密度绘制（240×46 ARGB_8888 ≈ 43KB），避免 3 倍屏多节课超过 1MB 跨进程上限。 */
    public static final int WIDGET_CHIP_W_DP = 240;
    public static final int WIDGET_CHIP_H_DP = 46;
    public static final float WIDGET_CHIP_DENSITY = 1f;
    public static final int WIDGET_MAX_CHIPS = 8;
    public static final int BINDER_SAFE_BYTES = 1_000_000;

    public static final class Hit {
        public static final Hit NONE_HIT = new Hit(NONE, 0, 0.0);

        public final int level;
        public final int maxProb;
        public final double maxMm;

        public Hit(int level, int maxProb, double maxMm) {
            this.level = Math.max(NONE, Math.min(HEAVY, level));
            this.maxProb = Math.max(0, Math.min(100, maxProb));
            this.maxMm = Math.max(0.0, maxMm);
        }

        public boolean isRaining() {
            return level > NONE;
        }

        /** 详情页主文案，如「70% 可能下雨」。 */
        public String summaryText() {
            if (level <= NONE) return "";
            if (maxProb > 0) return maxProb + "% 可能下雨";
            return "可能下" + levelName(level);
        }

        /** 详情页完整说明，如「70% 可能下雨 · 中雨，记得带伞」。 */
        public String detailText() {
            if (level <= NONE) return "";
            StringBuilder sb = new StringBuilder(summaryText());
            sb.append(" · ").append(levelName(level));
            if (maxMm >= 0.1) {
                sb.append(String.format(Locale.US, "（约 %.1f mm/h）", maxMm));
            }
            sb.append("，记得带伞");
            return sb.toString();
        }
    }

    private static final class HourSlot {
        final int prob;
        final double mm;

        HourSlot(int prob, double mm) {
            this.prob = prob;
            this.mm = mm;
        }
    }

    private final long fetchedAt;
    private final Map<String, HourSlot[]> days = new HashMap<>();

    private RainForecast(long fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public long fetchedAt() {
        return fetchedAt;
    }

    public boolean isEmpty() {
        return days.isEmpty();
    }

    public boolean needsRefresh(long now) {
        return fetchedAt <= 0L || now - fetchedAt >= REFRESH_MS;
    }

    public boolean isExpired(long now) {
        return fetchedAt <= 0L || now - fetchedAt > EXPIRE_MS || days.isEmpty();
    }

    /** Open-Meteo 请求地址（免费、免 Key；经纬度保留 2 位小数，约 1 公里精度）。 */
    public static String url(double latitude, double longitude) {
        return String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&hourly=precipitation_probability,precipitation&forecast_days=7&timezone=Asia/Shanghai",
                latitude, longitude);
    }

    /** 小组件单条课程位图宽高（px）：固定按 1 倍密度画，不随屏幕密度放大。 */
    public static int[] widgetChipSizePx(float screenDensity) {
        int w = Math.round(WIDGET_CHIP_W_DP * WIDGET_CHIP_DENSITY);
        int h = Math.round(WIDGET_CHIP_H_DP * WIDGET_CHIP_DENSITY);
        return new int[]{w, h};
    }

    /** 小组件全部下雨课程条位图加起来的总字节数（ARGB_8888，每像素 4 字节）。 */
    public static int widgetTotalBitmapBytes(int chipCount, float screenDensity) {
        int[] wh = widgetChipSizePx(screenDensity);
        return Math.max(0, chipCount) * wh[0] * wh[1] * 4;
    }

    /**
     * 单小时降水概率（0..100）与降水量（mm）分级，二者取大。
     * 概率 &lt; 30% 且降水量 &lt; 0.5 mm 时算不下雨。
     */
    public static int levelOf(int prob, double mm) {
        int pLv = prob >= 90 ? HEAVY : prob >= 60 ? MID : prob >= 30 ? LIGHT : NONE;
        int mLv = mm >= 4.0 ? HEAVY : mm >= 1.5 ? MID : mm >= 0.5 ? LIGHT : NONE;
        return Math.max(pLv, mLv);
    }

    public static String levelName(int level) {
        switch (level) {
            case LIGHT: return "小雨";
            case MID: return "中雨";
            case HEAVY: return "大雨";
            default: return "不下雨";
        }
    }

    /** 效果图 rain2.py 对应的雨丝条数：小雨 6、中雨 11、大雨 18。 */
    public static int streakCount(int level) {
        switch (level) {
            case LIGHT: return 6;
            case MID: return 11;
            case HEAVY: return 18;
            default: return 0;
        }
    }

    /** 效果图 rain2.py 对应的雨丝基准长度（dp）：小雨 6、中雨 9、大雨 12。 */
    public static float streakLenDp(int level) {
        switch (level) {
            case LIGHT: return 6f;
            case MID: return 9f;
            case HEAVY: return 12f;
            default: return 0f;
        }
    }

    /** 效果图 rain2.py 对应的雨丝基础不透明度（0..255）。 */
    public static int streakAlpha(int level) {
        switch (level) {
            case LIGHT: return 0x60;
            case MID: return 0x80;
            case HEAVY: return 0xA0;
            default: return 0;
        }
    }

    /** 效果图 rain2.py 对应的光晕强度系数：小雨 0.45、中雨 0.70、大雨 1.00。 */
    public static float glowStrength(int level) {
        switch (level) {
            case LIGHT: return 0.45f;
            case MID: return 0.70f;
            case HEAVY: return 1.00f;
            default: return 0f;
        }
    }

    /**
     * 解析 Open-Meteo 返回的 JSON；无效或空数据时返回 null。
     */
    public static RainForecast parse(String json, long fetchedAt) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject hourly = root.optJSONObject("hourly");
            if (hourly == null) return null;
            JSONArray times = hourly.optJSONArray("time");
            if (times == null || times.length() == 0) return null;
            JSONArray probs = hourly.optJSONArray("precipitation_probability");
            JSONArray precs = hourly.optJSONArray("precipitation");

            RainForecast rf = new RainForecast(fetchedAt);
            int n = times.length();
            for (int i = 0; i < n; i++) {
                String t = times.optString(i, "");
                // 形如 "2026-11-03T08:00"
                if (t.length() < 13 || t.charAt(10) != 'T') continue;
                String ymd = t.substring(0, 10);
                int hour;
                try {
                    hour = Integer.parseInt(t.substring(11, 13));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (hour < 0 || hour > 23) continue;
                int prob = probs != null && !probs.isNull(i) ? probs.optInt(i, 0) : 0;
                double mm = precs != null && !precs.isNull(i) ? precs.optDouble(i, 0.0) : 0.0;
                HourSlot[] slots = rf.days.get(ymd);
                if (slots == null) {
                    slots = new HourSlot[24];
                    rf.days.put(ymd, slots);
                }
                slots[hour] = new HourSlot(Math.max(0, Math.min(100, prob)), Math.max(0.0, mm));
            }
            return rf.days.isEmpty() ? null : rf;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询某天 ymd（yyyy-MM-dd）在 [startMin, endMin) 上课时间段内的最大雨级与降水概率。
     * 缓存过期或不在 7 天预报内时返回 {@link Hit#NONE_HIT}。
     */
    public Hit query(String ymd, int startMin, int endMin, long now) {
        if (isExpired(now) || ymd == null || !Dates.valid(ymd) || endMin <= startMin) {
            return Hit.NONE_HIT;
        }
        HourSlot[] slots = days.get(ymd);
        if (slots == null) return Hit.NONE_HIT;

        int h0 = Math.max(0, Math.min(23, startMin / 60));
        int h1 = Math.max(h0, Math.min(23, (endMin - 1) / 60));
        int maxProb = 0;
        double maxMm = 0.0;
        int maxLv = NONE;
        boolean anySlot = false;
        for (int h = h0; h <= h1; h++) {
            HourSlot s = slots[h];
            if (s == null) continue;
            anySlot = true;
            if (s.prob > maxProb) maxProb = s.prob;
            if (s.mm > maxMm) maxMm = s.mm;
            int lv = levelOf(s.prob, s.mm);
            if (lv > maxLv) maxLv = lv;
        }
        if (!anySlot) return Hit.NONE_HIT;
        int combined = Math.max(maxLv, levelOf(maxProb, maxMm));
        return combined <= NONE ? new Hit(NONE, maxProb, maxMm) : new Hit(combined, maxProb, maxMm);
    }
}
