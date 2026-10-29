package com.ecut.campus.data;

import java.util.List;

/**
 * 通知栏常驻「下一节课」显示什么：正在上的课 / 今天下一节 / 明天第一节 / 不显示。
 * 纯 Java，不碰 Android，方便单测。读课表、发通知由 NextClassNotice 负责。
 */
public final class NextClassText {

    private NextClassText() {}

    public static final int NONE = 0, CURRENT = 1, NEXT = 2, TOMORROW = 3;

    public static final class Notice {
        public int kind = NONE;
        public String title = "";
        public String text = "";
        /** 倒计时到今天第几分钟（CURRENT = 下课，NEXT = 上课且在 {@link WidgetDay#SOON_MIN} 分钟内）；不倒计时为 -1 */
        public int countdownMin = -1;
    }

    /**
     * @param today    今天全部的课（DayPlan.of），按开始时间排好；课表状态不对时传 null
     * @param tomorrow 明天全部的课；null 或空 = 明天没课
     * @param nowMin   现在是今天第几分钟
     */
    public static Notice build(List<DayPlan.Item> today, List<DayPlan.Item> tomorrow, int nowMin) {
        Notice n = new Notice();
        List<DayPlan.Item> left = WidgetDay.remaining(today, nowMin);
        if (!left.isEmpty()) {
            DayPlan.Item it = left.get(0);
            if (it.startMin <= nowMin) {
                n.kind = CURRENT;
                n.title = "正在上 · " + it.name;
                StringBuilder sb = new StringBuilder(Timetable.Session.formatHm(it.endMin)).append(" 下课");
                if (!empty(it.location)) sb.append(" · ").append(it.location);
                if (left.size() > 1) {
                    DayPlan.Item nx = left.get(1);
                    sb.append(" · 下一节 ").append(Timetable.Session.formatHm(nx.startMin)).append(' ').append(nx.name);
                }
                n.text = sb.toString();
                n.countdownMin = it.endMin;
            } else {
                n.kind = NEXT;
                n.title = "下一节 · " + it.name;
                n.text = WidgetDay.line(it) + (left.size() > 1 ? " · 今天还有 " + left.size() + " 门" : "");
                if (it.startMin - nowMin <= WidgetDay.SOON_MIN) n.countdownMin = it.startMin;
            }
            return n;
        }
        if (tomorrow != null && !tomorrow.isEmpty()) {
            DayPlan.Item it = tomorrow.get(0);
            n.kind = TOMORROW;
            n.title = "明天第一节 · " + it.name;
            n.text = WidgetDay.line(it);
        }
        return n;
    }

    /** 倒计时的静态文字（不用系统倒计时时）：「还剩 34 分钟」「12 分钟后上课」；不倒计时为空。 */
    public static String countdownLabel(Notice n, int nowMin) {
        if (n == null || n.countdownMin < 0) return "";
        int m = Math.max(0, n.countdownMin - nowMin);
        return n.kind == CURRENT ? "还剩 " + m + " 分钟" : m + " 分钟后上课";
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }
}
