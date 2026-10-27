package com.ecut.campus.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缺勤安全气囊：每门课私人记的 请假 / 缺勤 / 被点名 次数。纯 Java。
 * 按规范化课名记（和配色合并同一套规则），重新同步/导入课表、课程 key 变了也不会丢。
 * 上限 = 本学期这门课总课次的 1/3（取整）；请假 + 缺勤 计入，被点名只是记个数。
 */
public final class AbsenceBook {

    public static final class Counts {
        public int leave, absent, called;

        public int used() {
            return leave + absent;
        }

        public int count(int which) {
            return which == 0 ? leave : which == 1 ? absent : called;
        }

        boolean isEmpty() {
            return leave == 0 && absent == 0 && called == 0;
        }
    }

    private final Map<String, Counts> map = new LinkedHashMap<>();

    public static String key(String courseName) {
        return CourseColorAssigner.normalizeName(courseName);
    }

    /** 没记过返回全 0 的新对象（不写回）。 */
    public Counts get(String courseName) {
        Counts c = map.get(key(courseName));
        return c == null ? new Counts() : c;
    }

    /** 改某一项（delta 可为负，最低 0）。which: 0 请假 1 缺勤 2 被点名。返回改后的计数。 */
    public Counts bump(String courseName, int which, int delta) {
        String k = key(courseName);
        Counts c = map.get(k);
        if (c == null) c = new Counts();
        if (which == 0) c.leave = Math.max(0, c.leave + delta);
        else if (which == 1) c.absent = Math.max(0, c.absent + delta);
        else c.called = Math.max(0, c.called + delta);
        if (c.isEmpty()) map.remove(k); else map.put(k, c);
        return c;
    }

    /**
     * 课改名后把计数搬到新名下（和新名已有的计数相加）。
     * 课表里还有别的课叫旧名（同名合并记的）就不搬，返回 false。t 传改名后的课表。
     */
    public boolean rename(String oldName, String newName, Timetable t) {
        String from = key(oldName), to = key(newName);
        if (from.equals(to) || !map.containsKey(from)) return false;
        if (t != null && t.courses != null) {
            for (Timetable.Course c : t.courses) if (key(c.name).equals(from)) return false;
        }
        Counts c = map.remove(from), d = map.get(to);
        if (d != null) {
            c.leave += d.leave;
            c.absent += d.absent;
            c.called += d.called;
        }
        map.put(to, c);
        return true;
    }

    /** 这门课（同名的所有时段合并）在 1..totalWeeks 里一共上几次课。 */
    public static int sessionCount(Timetable t, String courseName, int totalWeeks) {
        if (t == null || t.courses == null || totalWeeks < 1) return 0;
        String k = key(courseName);
        int n = 0;
        for (Timetable.Course c : t.courses) {
            if (!key(c.name).equals(k)) continue;
            for (Timetable.Session s : c.sessions) {
                for (int w = 1; w <= totalWeeks; w++) if (s.isActive(w)) n++;
            }
        }
        return n;
    }

    /** 缺课上限：总课次的 1/3，向下取整（不到 3 次课时为 0：缺 1 次就超过 1/3，界面上换成直白的说法）。 */
    public static int limit(int sessions) {
        return Math.max(0, sessions) / 3;
    }

    /** 请假+缺勤 达到上限的 80% 就该亮灯了（上限为 0 时缺一次就亮）。 */
    public static boolean warn(Counts c, int limit) {
        if (c == null || c.used() <= 0) return false;
        return c.used() * 5 >= limit * 4;
    }

    /** 课表上要描朱砂边的课（规范化课名）。 */
    public Set<String> warnKeys(Timetable t, int totalWeeks) {
        Set<String> out = new HashSet<>();
        if (t == null || t.courses == null) return out;
        for (Map.Entry<String, Counts> e : map.entrySet()) {
            String name = null;
            for (Timetable.Course c : t.courses) if (key(c.name).equals(e.getKey())) { name = c.name; break; }
            if (name == null) continue;
            if (warn(e.getValue(), limit(sessionCount(t, name, totalWeeks)))) out.add(e.getKey());
        }
        return out;
    }

    public String toJson() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Counts> e : map.entrySet()) {
                JSONObject c = new JSONObject();
                c.put("l", e.getValue().leave);
                c.put("a", e.getValue().absent);
                c.put("c", e.getValue().called);
                o.put(e.getKey(), c);
            }
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    public static AbsenceBook fromJson(String json) {
        AbsenceBook b = new AbsenceBook();
        if (json == null || json.isEmpty()) return b;
        try {
            JSONObject o = new JSONObject(json);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject c = o.optJSONObject(k);
                if (c == null) continue;
                Counts n = new Counts();
                n.leave = Math.max(0, c.optInt("l"));
                n.absent = Math.max(0, c.optInt("a"));
                n.called = Math.max(0, c.optInt("c"));
                if (!n.isEmpty()) b.map.put(k, n);
            }
        } catch (JSONException ignored) {
        }
        return b;
    }
}
