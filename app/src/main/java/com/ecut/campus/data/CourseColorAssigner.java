package com.ecut.campus.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按规范化课程名统一分配色板索引。
 *
 * 同名课程（不同时段/不同周次）拿到同一颜色；不同课程名在同一学期内
 * 绝不撞色（色板 24 色，超过 24 门不同课才不得不复用）。手动课程和用户
 * 改过颜色的课程保持原色，并且算作「已占用」，自动分配会绕开它们。
 */
public final class CourseColorAssigner {

    private CourseColorAssigner() {}

    /** 与 TimetableView.PALETTE_BG 保持同长度；数据层不依赖 UI 类 */
    public static final int PALETTE_SIZE = 24;

    /**
     * 对课表内全部课程重新分配 colorIndex。
     * 返回映射（课程名 → 颜色索引），供调用方把同一配色同步到其他学期。
     */
    public static Map<String, Integer> assign(Timetable timetable) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (timetable == null || timetable.courses == null) return out;
        boolean[] used = new boolean[PALETTE_SIZE];
        Map<String, Integer> fixed = fixedColors(timetable, used);
        // 和手动设过颜色的同名课保持一致
        for (Map.Entry<String, Integer> e : fixed.entrySet()) {
            for (Timetable.Course c : timetable.courses) {
                if (isAuto(c) && normalizeName(c.name).equals(e.getKey())) out.put(e.getKey(), e.getValue());
            }
        }
        // 第一轮：已有颜色、而且这个颜色在本学期还没被别的课名占用 → 保持原色不动。
        // 这样同步回来的课程顺序变了、增删了一门课，其余课的颜色都不会跟着挪。
        for (Timetable.Course course : timetable.courses) {
            if (!isAuto(course)) continue;
            String name = normalizeName(course.name);
            if (out.containsKey(name)) continue;
            int idx = Math.abs(course.colorIndex) % PALETTE_SIZE;
            if (!used[idx]) {
                used[idx] = true;
                out.put(name, idx);
            }
        }
        // 第二轮：撞色的课名换到第一个空闲颜色
        for (Timetable.Course course : timetable.courses) {
            if (!isAuto(course)) continue;
            String name = normalizeName(course.name);
            Integer idx = out.get(name);
            if (idx == null) {
                idx = firstFree(used, name);
                used[idx] = true;
                out.put(name, idx);
            }
            course.colorIndex = idx;
        }
        return out;
    }

    /** 新建手动课程时的默认颜色：本学期还没人用的第一个颜色。 */
    public static int firstFree(Timetable timetable) {
        boolean[] used = new boolean[PALETTE_SIZE];
        if (timetable != null && timetable.courses != null) {
            for (Timetable.Course c : timetable.courses) used[Math.abs(c.colorIndex) % PALETTE_SIZE] = true;
        }
        int n = timetable == null || timetable.courses == null ? 0 : timetable.courses.size();
        return firstFree(used, "#" + n);
    }

    /**
     * 同一学期里不同课程名撞色时，把后出现的自动课程挪到空闲颜色上。
     * 跨学期统一配色会因为总课程名超过色板而循环，这一步保证单个学期内依旧不撞。
     */
    public static void dedupe(Timetable timetable) {
        if (timetable == null || timetable.courses == null) return;
        boolean[] used = new boolean[PALETTE_SIZE];
        Map<Integer, String> owner = new HashMap<>();
        Map<String, Integer> fixed = fixedColors(timetable, used);
        for (Map.Entry<String, Integer> e : fixed.entrySet()) owner.putIfAbsent(e.getValue(), e.getKey());
        Map<String, Integer> decided = new HashMap<>(fixed);
        for (Timetable.Course course : timetable.courses) {
            if (!isAuto(course)) continue;
            String name = normalizeName(course.name);
            Integer idx = decided.get(name);
            if (idx == null) {
                int want = Math.abs(course.colorIndex) % PALETTE_SIZE;
                String who = owner.get(want);
                idx = (who == null || who.equals(name)) ? want : firstFree(used, name);
                used[idx] = true;
                owner.putIfAbsent(idx, name);
                decided.put(name, idx);
            }
            course.colorIndex = idx;
        }
    }

    private static boolean isAuto(Timetable.Course c) {
        return Timetable.SOURCE_JWGLXT.equals(c.source) && !c.colorTouched;
    }

    /** 手动课程 / 用户改过色的课程：颜色不动，标记为已占用。返回 课程名 → 颜色。 */
    private static Map<String, Integer> fixedColors(Timetable t, boolean[] used) {
        Map<String, Integer> fixed = new HashMap<>();
        for (Timetable.Course c : t.courses) {
            if (isAuto(c)) continue;
            int idx = Math.abs(c.colorIndex) % PALETTE_SIZE;
            used[idx] = true;
            fixed.putIfAbsent(normalizeName(c.name), idx);
        }
        return fixed;
    }

    private static int firstFree(boolean[] used, String name) {
        for (int i = 0; i < used.length; i++) if (!used[i]) return i;
        return Math.abs(name.hashCode()) % used.length;
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
        dedupe(timetable);
    }

    static String normalizeName(String name) {
        return (name == null ? "" : name)
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", "")
                .trim();
    }
}
