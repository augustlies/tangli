package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入后自检：找出「同一周同一天同一节有 3 门以上的课」这类大概率没导对的情况，
 * 预览窗里提醒一句，并引导一键发送错误日志。纯计算，不依赖 Android。
 */
public final class ImportCheck {

    /** 同一时刻挤了这么多门课才算可疑（两门重叠在选修/实验里很常见）。 */
    public static final int CROWD = 3;

    private ImportCheck() {}

    public static final class Result {
        /** 至少有一周里、同一天同一节挤了 ≥ CROWD 门课的「天-节」个数（不同周去重）。 */
        public int crowdedSlots;
        /** 最挤的时候同时有几门。 */
        public int maxTogether;
        /** 用到的最大节次。 */
        public int maxSection;

        public boolean suspicious() { return crowdedSlots > 0; }
    }

    public static Result check(Timetable t) {
        Result r = new Result();
        if (t == null || t.courses == null) return r;
        // key = week*1000 + day*100 + section  ->  门数
        Map<Integer, Integer> count = new HashMap<>();
        List<Integer> crowded = new ArrayList<>();
        for (Timetable.Course c : t.courses) {
            if (c == null || c.sessions == null) continue;
            for (Timetable.Session s : c.sessions) {
                if (s == null) continue;
                int from = Math.min(s.startSection, s.endSection), to = Math.max(s.startSection, s.endSection);
                r.maxSection = Math.max(r.maxSection, to);
                if (s.weeks == null) continue;
                for (int w : s.weeks) {
                    for (int sec = from; sec <= to; sec++) {
                        int key = w * 1000 + s.dayOfWeek * 100 + sec;
                        Integer n = count.get(key);
                        int v = n == null ? 1 : n + 1;
                        count.put(key, v);
                        r.maxTogether = Math.max(r.maxTogether, v);
                        if (v == CROWD) {
                            int slot = s.dayOfWeek * 100 + sec;
                            if (!crowded.contains(slot)) crowded.add(slot);
                        }
                    }
                }
            }
        }
        r.crowdedSlots = crowded.size();
        return r;
    }

    /** 预览窗里的一句提醒；没问题返回空串。 */
    public static String warning(Result r) {
        if (r == null || !r.suspicious()) return "";
        return "有 " + r.crowdedSlots + " 个时段同一节挤了 " + r.maxTogether
                + " 门课，可能没导对。导入后如果课表乱了，点「发给开发者」发给开发者。";
    }
}
