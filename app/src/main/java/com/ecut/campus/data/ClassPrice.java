package com.ecut.campus.data;

import java.util.Locale;

/**
 * 「这节课值多少钱」：每学期学费 ÷ 本学期课表里实际排了的节次总数。
 * 节次总数 = 1..totalWeeks 里有课的（周, 星期, 节）格子数；重叠的课同一格只算一次。纯 Java。
 */
public final class ClassPrice {
    private ClassPrice() {}

    /** 本学期一共排了多少「节」课（按周展开，同一周同一天同一节只算一次）。 */
    public static int totalSections(Timetable t, int totalWeeks) {
        if (t == null || t.courses == null || totalWeeks < 1) return 0;
        java.util.Set<Long> cells = new java.util.HashSet<>();
        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                int end = Math.max(s.startSection, s.endSection);
                for (int w = 1; w <= totalWeeks; w++) {
                    if (!s.isActive(w)) continue;
                    for (int k = s.startSection; k <= end; k++) {
                        cells.add(((long) w * 8 + s.dayOfWeek) * 1000 + k);
                    }
                }
            }
        }
        return cells.size();
    }

    /** 学费按节分摊；学费或节数无效时返回 0。 */
    public static double perSection(double tuition, int totalSections) {
        if (!(tuition > 0) || totalSections <= 0) return 0;
        return tuition / totalSections;
    }

    /** 用户输入的学费：允许「5500」「5,500」「¥5500」「5500元」，无效返回 0。 */
    public static double parseTuition(String raw) {
        if (raw == null) return 0;
        String s = raw.replaceAll("[\\s,，¥￥元]", "");
        if (!s.matches("[0-9]+(\\.[0-9]+)?")) return 0;
        try {
            double v = Double.parseDouble(s);
            return v > 0 && v < 10_000_000 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 金额：不到 100 元保留一位小数（19.8），否则取整（128）。 */
    public static String yuan(double v) {
        if (v < 100) return String.format(Locale.US, "%.1f", v);
        return String.valueOf(Math.round(v));
    }

    /** 早八：从每天第 1 节开始、没用自定义时间的时段。 */
    public static boolean isEarlyEight(Timetable.Session s) {
        return s != null && !s.hasCustomTime() && s.startSection == 1;
    }

    private static final String[] UNITS = {"瓶矿泉水", "个煎饼果子", "份食堂午饭", "杯奶茶", "杯咖啡", "张电影票"};
    private static final double[] PRICES = {2, 8, 12, 15, 25, 45};
    private static final String[] TEMPLATES = {
            "翘掉这节 ≈ 倒掉%s",
            "赖床的代价 ≈ %s",
            "睡过这节 ≈ 把%s留在了被窝里",
            "这节不去 ≈ 白扔%s",
    };

    /**
     * 早八的毒舌一句：把这次课的钱换算成最接近的日常小东西。
     * seed 用课名的 hash，同一门课每次看到的是同一句。cost ≤ 0 返回空串。
     */
    public static String teaseLine(double cost, long seed) {
        if (!(cost > 0)) return "";
        int best = 0;
        for (int i = 0; i < PRICES.length; i++) if (PRICES[i] <= cost) best = i;
        long n = Math.max(1, Math.round(cost / PRICES[best]));
        String thing = (n == 1 ? "一" : String.valueOf(n)) + UNITS[best];
        int t = (int) Math.floorMod(seed, (long) TEMPLATES.length);
        return String.format(Locale.ROOT, TEMPLATES[t], thing);
    }
}
