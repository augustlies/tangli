package com.ecut.campus.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按规范化课程名统一分配色板索引。
 *
 * 同名课程（不同时段/不同周次）拿到同一颜色；不同课程名按出现顺序
 * 分配未占用的颜色，出现更晚的课程才循环复用。仅对教务自动同步的
 * 课程生效，手动课程的用户选择不会被覆盖。
 */
public final class CourseColorAssigner {

    private CourseColorAssigner() {}

    /** 与 TimetableView.PALETTE_BG 保持同长度；数据层不依赖 UI 类 */
    public static final int PALETTE_SIZE = 12;

    /**
     * 对课表内全部课程重新分配 colorIndex。
     * 返回映射（课程名 → 颜色索引），供调用方把同一配色同步到其他学期。
     */
    public static Map<String, Integer> assign(Timetable timetable) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (timetable == null || timetable.courses == null) return out;
        int paletteSize = PALETTE_SIZE;
        boolean[] used = new boolean[paletteSize];

        for (Timetable.Course course : timetable.courses) {
            if (!Timetable.SOURCE_JWGLXT.equals(course.source)) continue;
            if (course.colorTouched) continue;
            String name = normalizeName(course.name);
            Integer assigned = out.get(name);
            if (assigned == null) {
                int pick = -1;
                for (int i = 0; i < paletteSize; i++) {
                    if (!used[i]) { pick = i; break; }
                }
                if (pick < 0) pick = Math.abs(name.hashCode()) % paletteSize;
                used[pick] = true;
                assigned = pick;
                out.put(name, assigned);
            }
            course.colorIndex = assigned;
        }
        return out;
    }

    /** 把已知的课程名配色应用到一个学期，未命中的名字保持原样。 */
    public static void applyKnown(Timetable timetable, Map<String, Integer> knownColors) {
        if (timetable == null || timetable.courses == null || knownColors == null) return;
        for (Timetable.Course course : timetable.courses) {
            if (!Timetable.SOURCE_JWGLXT.equals(course.source)) continue;
            if (course.colorTouched) continue;
            Integer idx = knownColors.get(normalizeName(course.name));
            if (idx != null) course.colorIndex = idx;
        }
    }

    static String normalizeName(String name) {
        return (name == null ? "" : name)
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", "")
                .trim();
    }
}
