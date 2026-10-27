package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 某一天实际要上的课（已算上放假、停课、调课、补课），按开始时间排好。
 * 桌面小组件和课前提醒共用。纯 Java，作息用「每节开始/结束分钟」数组传进来（下标 = 节次）。
 */
public final class DayPlan {

    private DayPlan() {}

    public static final class Item {
        public String name = "";
        public String location = "";
        public int startMin, endMin;
        /** 「3-4节」或空（自定义时间的安排） */
        public String sections = "";
        /** 「补课」「调课」或空 */
        public String tag = "";
        /** 课程色板下标（和课表同色） */
        public int colorIndex;

        public String timeLabel() {
            return Timetable.Session.formatHm(startMin) + "-" + Timetable.Session.formatHm(endMin);
        }
    }

    /**
     * @param start 第 1 周周一（yyyy-MM-dd）；无效时返回空
     * @param secStart secStart[n] = 第 n 节开始分钟
     * @param secEnd   secEnd[n] = 第 n 节结束分钟
     */
    public static List<Item> of(Timetable t, String start, String ymd, int[] secStart, int[] secEnd) {
        List<Item> out = new ArrayList<>();
        if (t == null || !Dates.valid(start) || !Dates.valid(ymd)) return out;
        int week = Dates.weekOf(start, ymd);
        if (week < 1) return out;
        int day = Dates.dayOfWeek(ymd);
        WeekOverlay o = WeekOverlay.build(t, start, week);
        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                if (s.dayOfWeek != day || !s.isActive(week) || o.cancelled.containsKey(s)) continue;
                Item it = item(c, s, secStart, secEnd);
                if (it != null) out.add(it);
            }
        }
        for (WeekOverlay.Extra e : o.extras) {
            if (e.session.dayOfWeek != day) continue;
            Item it = item(e.course, e.session, secStart, secEnd);
            if (it == null) continue;
            it.tag = e.label == null ? "" : e.label;
            out.add(it);
        }
        Collections.sort(out, (a, b) -> a.startMin != b.startMin ? Integer.compare(a.startMin, b.startMin)
                : a.name.compareTo(b.name));
        return out;
    }

    private static Item item(Timetable.Course c, Timetable.Session s, int[] secStart, int[] secEnd) {
        Item it = new Item();
        it.name = c.name == null ? "" : c.name;
        it.location = s.location == null ? "" : s.location.trim();
        it.colorIndex = c.colorIndex;
        if (s.hasCustomTime()) {
            it.startMin = Timetable.Session.minutesOf(s.timeStart);
            it.endMin = Timetable.Session.minutesOf(s.timeEnd);
        } else {
            int a = s.startSection, b = Math.max(s.startSection, s.endSection);
            if (a < 1 || b >= secStart.length || b >= secEnd.length) return null;
            it.startMin = secStart[a];
            it.endMin = secEnd[b];
            it.sections = a == b ? "第" + a + "节" : a + "-" + b + "节";
        }
        if (it.startMin < 0 || it.endMin <= it.startMin) return null;
        return it;
    }

    /** 现在正在上的课，没有返回 null。 */
    public static Item current(List<Item> items, int nowMin) {
        for (Item it : items) if (it.startMin <= nowMin && nowMin < it.endMin) return it;
        return null;
    }

    /** 还没开始的第一节课，没有返回 null。 */
    public static Item next(List<Item> items, int nowMin) {
        for (Item it : items) if (it.startMin > nowMin) return it;
        return null;
    }

    /** 今天的课是不是都上完了（今天没课也算）。 */
    public static boolean doneFor(List<Item> items, int nowMin) {
        for (Item it : items) if (it.endMin > nowMin) return false;
        return true;
    }
}
