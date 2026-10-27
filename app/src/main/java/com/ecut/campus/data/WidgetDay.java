package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面小组件「今日课程」要显示什么：标题、日期行、一排课程条（正在上 / 快上课的带倒计时）、兜底一句话。
 * 同一个小组件拉成 2×2 时按 {@link #NARROW} 收紧。
 * 纯 Java，不碰 Android，方便单测。读课表、画界面由 ClassWidget 负责。
 */
public final class WidgetDay {

    private WidgetDay() {}

    /** 课表状态 */
    public static final int OK = 0, NO_TIMETABLE = 1, NO_START = 2, BEFORE = 3, AFTER = 4;

    public static final class Chip {
        public String name = "";
        /** 「10:00 - 11:40  3-518」（没教室就只有时间） */
        public String line = "";
        /** 右上角小字：「进行中」「明天」「+2」或空 */
        public String tag = "";
        public boolean ongoing;
        public int colorIndex;
        /** 倒计时：无 / 「还剩 mm:ss」（正在上，到下课）/ 「mm:ss 后上课」（下一门，开始前 60 分钟内） */
        public static final int CD_NONE = 0, CD_LEFT = 1, CD_SOON = 2;
        public int cdKind = CD_NONE;
        /** 倒计时到今天第几分钟（下课或上课）；没有为 -1 */
        public int cdMin = -1;
    }

    public static final class Card {
        public String title = "";
        /** 标题里要标朱砂色的那段 [numStart, numEnd)；没有为 -1 */
        public int numStart = -1, numEnd = -1;
        public String sub = "";
        public final List<Chip> chips = new ArrayList<>();
        /** 没有课程条时的一句话；有课程条时为空 */
        public String note = "";
        /** 放不下、没显示出来的课数 */
        public int more;
    }

    /** 今天还没下课的课（正在上 + 还没开始），保持原顺序。 */
    public static List<DayPlan.Item> remaining(List<DayPlan.Item> items, int nowMin) {
        List<DayPlan.Item> out = new ArrayList<>();
        if (items == null) return out;
        for (DayPlan.Item it : items) if (it.endMin > nowMin) out.add(it);
        return out;
    }

    /** 下一个要刷新的时刻（今天的分钟数）：最近的上课或下课；今天没有了返回 -1。 */
    public static int nextBoundary(List<DayPlan.Item> items, int nowMin) {
        int best = -1;
        if (items == null) return best;
        for (DayPlan.Item it : items) {
            if (it.startMin > nowMin && (best < 0 || it.startMin < best)) best = it.startMin;
            if (it.endMin > nowMin && (best < 0 || it.endMin < best)) best = it.endMin;
        }
        return best;
    }

    /** 「10.27」 */
    public static String dot(String ymd) {
        String[] p = ymd == null ? new String[0] : ymd.split("-");
        if (p.length != 3) return ymd == null ? "" : ymd;
        try {
            return Integer.parseInt(p[1]) + "." + p[2];
        } catch (NumberFormatException e) {
            return ymd;
        }
    }

    /** 「10:00 - 11:40  3-518」 */
    public static String line(DayPlan.Item it) {
        String t = Timetable.Session.formatHm(it.startMin) + " - " + Timetable.Session.formatHm(it.endMin);
        return it.location == null || it.location.isEmpty() ? t : t + "  " + it.location;
    }

    /**
     * @param state     OK / NO_TIMETABLE / NO_START / BEFORE / AFTER
     * @param ymd       今天 yyyy-MM-dd
     * @param week      今天是第几周（state=OK 时 ≥1）
     * @param start     第 1 周周一（BEFORE 时用来说哪天开学）
     * @param today     今天全部的课（DayPlan.of），按开始时间排好
     * @param tomorrow  明天全部的课
     * @param nowMin    现在是今天第几分钟
     * @param cap       最多显示几条（≥1）
     */
    public static Card build(int state, String ymd, int week, String start,
                             List<DayPlan.Item> today, List<DayPlan.Item> tomorrow, int nowMin, int cap) {
        return build(state, ymd, week, start, today, tomorrow, nowMin, cap, WIDE);
    }

    /** @param level {@link #WIDE} / {@link #MID} / {@link #NARROW}，其余参数同上 */
    public static Card build(int state, String ymd, int week, String start,
                             List<DayPlan.Item> today, List<DayPlan.Item> tomorrow, int nowMin, int cap, int level) {
        Card c = new Card();
        String wd = Dates.cnDay(Dates.dayOfWeek(ymd));
        c.sub = state == OK && week >= 1 ? dot(ymd) + " / 第" + week + "周" : dot(ymd) + (wd.isEmpty() ? "" : " " + wd);
        switch (state) {
            case NO_TIMETABLE:
                c.title = "还没有课表";
                c.note = "打开烫梨同步一下，这里就会列出今天的课";
                return c;
            case NO_START:
                c.title = "还不知道哪天开学";
                c.note = "在烫梨里设一下开学日期就能显示";
                return c;
            case BEFORE:
                c.title = "还没开学";
                c.note = Dates.valid(start) ? "开学见，第 1 周从 " + dot(start) + " 开始" : "开学见";
                return c;
            case AFTER:
                c.title = "这学期的课上完啦";
                c.note = "放假愉快，新学期课表同步后会显示在这";
                return c;
            default:
                break;
        }
        cap = Math.max(1, cap);
        if (level == NARROW) cap = Math.min(cap, 2);
        List<DayPlan.Item> left = remaining(today, nowMin);
        if (!left.isEmpty()) {
            String head = level == NARROW ? (wd.isEmpty() ? "" : wd + " · ") + "还有 " : "今日" + wd + "，还有 ";
            String n = String.valueOf(left.size());
            c.title = head + n + (level == NARROW ? " 门" : " 门课要上");
            c.numStart = head.length();
            c.numEnd = head.length() + n.length();
            int shown = Math.min(cap, left.size());
            c.more = left.size() - shown;
            boolean soonDone = false;
            for (int i = 0; i < shown; i++) {
                DayPlan.Item it = left.get(i);
                Chip ch = chip(it, level);
                ch.ongoing = it.startMin <= nowMin;
                if (ch.ongoing) {
                    ch.tag = level == NARROW ? "" : "进行中";
                    ch.cdKind = Chip.CD_LEFT;
                    ch.cdMin = it.endMin;
                } else if (!soonDone) {
                    soonDone = true; // 只给最近要上的那一门
                    if (it.startMin - nowMin <= SOON_MIN) {
                        ch.cdKind = Chip.CD_SOON;
                        ch.cdMin = it.startMin;
                    }
                }
                // 窄的时候倒计时单占第二行，时间让给它
                if (level == NARROW && ch.cdKind != Chip.CD_NONE) ch.line = "";
                c.chips.add(ch);
            }
            if (c.more > 0) {
                Chip last = c.chips.get(c.chips.size() - 1);
                last.tag = last.tag.isEmpty() ? "+" + c.more : last.tag + " · +" + c.more;
            }
            return c;
        }
        c.title = today == null || today.isEmpty() ? "今天没课" : "今天的课上完了";
        if (tomorrow == null || tomorrow.isEmpty()) {
            c.note = "明天也没课，好好休息";
        } else {
            Chip ch = chip(tomorrow.get(0), level);
            ch.tag = "明天";
            c.chips.add(ch);
        }
        return c;
    }

    private static Chip chip(DayPlan.Item it, int level) {
        Chip ch = new Chip();
        ch.name = it.name;
        ch.line = level == WIDE ? line(it) : timeRange(it); // 先丢教室，时间留着
        ch.colorIndex = it.colorIndex;
        return ch;
    }
    /** 「10:00 - 11:40」 */
    public static String timeRange(DayPlan.Item it) {
        return Timetable.Session.formatHm(it.startMin) + " - " + Timetable.Session.formatHm(it.endMin);
    }

    // ===== 宽度档位：同一个「今日课程」拉宽拉窄 =====

    /**
     * 宽（4×2 及以上）：完整标题、「时间  教室」；中：去掉教室；窄（拉成 2×2）：标题缩成「周二 · 还有 N 门」、
     * 不要日期行、最多 2 条、倒计时单占第二行，正在上的不再写「进行中」（朱砂色的「还剩」已经说明了，课名放得下）。
     */
    public static final int WIDE = 0, MID = 1, NARROW = 2;

    /** 按小组件竖屏宽度（dp）和字号定档。 */
    public static int widthLevel(int wDp, float fontScale) {
        float fs = Math.max(1f, fontScale);
        if (wDp <= 0) return WIDE;
        if (wDp >= 230 * fs) return WIDE;
        if (wDp >= 185 * fs) return MID;
        return NARROW;
    }

    // ===== 倒计时 =====

    /** 正在上的课离下课、或下一门离上课在这么多分钟以内才显示倒计时。 */
    public static final int SOON_MIN = 60;

    /**
     * 下一个要刷新的时刻（今天的分钟数）：上课、下课，以及上课前 {@link #SOON_MIN} 分钟（那时开始显示「mm:ss 后上课」）；
     * 今天没有了返回 -1。
     */
    public static int nextRefreshMin(List<DayPlan.Item> items, int nowMin) {
        int best = nextBoundary(items, nowMin);
        if (items == null) return best;
        for (DayPlan.Item it : items) {
            int t = it.startMin - SOON_MIN;
            if (t > nowMin && (best < 0 || t < best)) best = t;
        }
        return best;
    }

    /** 倒计时的静态兜底文字（系统不支持倒计时控件时用）：「还剩 34 分钟」「12 分钟后上课」。 */
    public static String countdownText(int kind, int targetMin, int nowMin) {
        int m = Math.max(0, targetMin - nowMin);
        if (kind == Chip.CD_LEFT) return "还剩 " + m + " 分钟";
        if (kind == Chip.CD_SOON) return m + " 分钟后上课";
        return "";
    }
}
