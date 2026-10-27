package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 双人课表海报的统计（纯 Java）：同一周两个人的课叠在一起，
 * 算重合度、同时段同楼节数、一起吃饭的空档、一个好玩的标题。
 * 作息用 secStart[n] / secEnd[n]（第 n 节的开始/结束分钟）传进来。
 */
public final class PairStats {
    private PairStats() {}

    public static final int LUNCH_FROM = 11 * 60, LUNCH_TO = 14 * 60;
    public static final int DINNER_FROM = 17 * 60, DINNER_TO = 19 * 60 + 30;
    /** 两人都空的连续时间至少这么久，才算能一起吃顿饭。 */
    public static final int MEAL_MIN = 40;

    public static final class Result {
        public int week;
        public int mySections, theirSections, both, either;
        /** 重合度 = 两人同时有课的节数 ÷ 至少一人有课的节数。 */
        public int overlapPct;
        public int sameBuildingSections;
        public String topBuilding = "";
        /** 能一起吃午饭/晚饭的日子（1=周一）。 */
        public List<Integer> lunchDays = new ArrayList<>(), dinnerDays = new ArrayList<>();
        public String title = "";
        public String subtitle = "";
    }

    /**
     * @param days 吃饭只统计周一到周几（5 或 7）；两人都没课的那天整段都空，当然也算
     */
    public static Result compute(Timetable me, Timetable them, int week, int days,
                                 int[] secStart, int[] secEnd) {
        Result r = new Result();
        r.week = week;
        int sections = Math.max(0, Math.min(secStart.length, secEnd.length) - 1);
        String[][] a = grid(me, week, sections), b = grid(them, week, sections);
        Map<String, Integer> shared = new HashMap<>();
        for (int d = 1; d <= 7; d++) {
            for (int s = 1; s <= sections; s++) {
                boolean x = a[d][s] != null, y = b[d][s] != null;
                if (x) r.mySections++;
                if (y) r.theirSections++;
                if (x || y) r.either++;
                if (x && y) {
                    r.both++;
                    String ba = building(a[d][s]), bb = building(b[d][s]);
                    if (!ba.isEmpty() && ba.equals(bb)) {
                        r.sameBuildingSections++;
                        shared.merge(ba, 1, Integer::sum);
                    }
                }
            }
        }
        r.overlapPct = r.either == 0 ? 0 : Math.round(r.both * 100f / r.either);
        int best = 0;
        for (Map.Entry<String, Integer> e : shared.entrySet()) {
            if (e.getValue() > best || (e.getValue() == best && e.getKey().compareTo(r.topBuilding) < 0)) {
                best = e.getValue();
                r.topBuilding = e.getKey();
            }
        }
        for (int d = 1; d <= Math.max(1, Math.min(7, days)); d++) {
            if (commonFree(a[d], b[d], secStart, secEnd, LUNCH_FROM, LUNCH_TO) >= MEAL_MIN) r.lunchDays.add(d);
            if (commonFree(a[d], b[d], secStart, secEnd, DINNER_FROM, DINNER_TO) >= MEAL_MIN) r.dinnerDays.add(d);
        }
        title(r);
        return r;
    }

    /** grid[day][section] = 教室（有课但没写教室时为 ""），没课为 null。 */
    static String[][] grid(Timetable t, int week, int sections) {
        String[][] g = new String[8][sections + 1];
        if (t == null || t.courses == null) return g;
        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > 7 || !s.isActive(week)) continue;
                for (int k = Math.max(1, s.startSection); k <= Math.min(sections, s.endSection); k++) {
                    if (g[s.dayOfWeek][k] == null || g[s.dayOfWeek][k].isEmpty()) {
                        g[s.dayOfWeek][k] = s.location == null ? "" : s.location;
                    }
                }
            }
        }
        return g;
    }

    /** 窗口 [from,to) 内两人都没课的最长连续分钟数。 */
    static int commonFree(String[] a, String[] b, int[] secStart, int[] secEnd, int from, int to) {
        boolean[] busy = new boolean[to - from];
        for (int s = 1; s < a.length && s < secStart.length && s < secEnd.length; s++) {
            if (a[s] == null && b[s] == null) continue;
            for (int m = Math.max(from, secStart[s]); m < Math.min(to, secEnd[s]); m++) busy[m - from] = true;
        }
        int bestRun = 0, run = 0;
        for (boolean x : busy) {
            run = x ? 0 : run + 1;
            bestRun = Math.max(bestRun, run);
        }
        return bestRun;
    }

    /**
     * 从教室里取「楼」：去掉开头的（校区），
     * 「N1-305」「3-201」取横杠前；「三教A201」「致远楼305」取第一个数字前；
     * 纯数字（「201」）认不出楼，返回空。
     */
    public static String building(String location) {
        if (location == null) return "";
        String s = location.trim().replaceAll("^[(（][^)）]*[)）]", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z]*\\d+)\\s*[-－—]\\s*\\d+").matcher(s);
        if (m.find()) return m.group(1).toUpperCase(java.util.Locale.ROOT);
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) i++;
        return s.substring(0, i).replaceAll("[\\s\\-－—_#]+$", "").trim();
    }

    private static void title(Result r) {
        int meals = r.lunchDays.size() + r.dinnerDays.size();
        if (r.either == 0) {
            r.title = "两张白纸";
            r.subtitle = "这周谁都没课，约就完事了";
        } else if (r.theirSections == 0) {
            r.title = "等 TA 交课表";
            r.subtitle = "对方的课表还是空的，先让 TA 同步一下再来";
        } else if (r.mySections == 0) {
            r.title = "先交你的课表";
            r.subtitle = "你的课表还是空的，先去同步一下再来";
        } else if (r.overlapPct >= 60) {
            r.title = "连体婴课表";
            r.subtitle = "上课在一起，下课也在一起";
        } else if (r.sameBuildingSections >= 4) {
            r.title = "教学楼室友";
            r.subtitle = "同一栋楼里，各自努力";
        } else if (r.lunchDays.size() >= 4) {
            r.title = "干饭搭子认证";
            r.subtitle = r.overlapPct >= 30 ? "一半的课一起熬，饭也一起吃" : "课不一样，饭点一样";
        } else if (r.overlapPct >= 30) {
            r.title = "半个同桌";
            r.subtitle = "一半的课，一起熬";
        } else if (r.overlapPct >= 10) {
            r.title = "偶尔擦肩";
            r.subtitle = "走廊里点个头的交情";
        } else {
            r.title = "平行宇宙";
            r.subtitle = meals > 0 ? "课表不相交，饭点还能见" : "你上课我下课，完美错开";
        }
    }
}
