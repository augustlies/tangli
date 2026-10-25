package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表数据模型。
 *
 * 结构对应 docs/DATA-SCHEMA.md 的 timetable.json：
 *   Course（课程）1 ── N Session（时段）
 *
 * 关键设计：
 *   - 一门课可有多个时段（地球化学周一/周三/周五），时段各自有教室
 *   - Course.key 是聚合主键：教务课程用 jxb_id，手动添加用 custom_xxx
 *   - source 区分来源，同步时 custom 课程永不删除
 */
public final class Timetable {

    public static final int SCHEMA_VERSION = 1;

    public static final String SOURCE_JWGLXT = "jwglxt";
    public static final String SOURCE_CUSTOM = "custom";
    public static final String SOURCE_IMPORT = "import";

    /** 学期信息 */
    public static final class Semester {
        public String id = "";          // 2026-2027-1
        public String name = "";        // 2026-2027 学年第一学期
        public String startDate = "";   // 2026-09-01（第 1 周周一），可为空
        public int totalWeeks = 20;
        public boolean startDateTouched = false; // 用户手动改过开学日期
        public int sortOrder = 0;       // 归档内显示顺序（用户拖动后写入）

        public Semester() {}

        public Semester(String id, String name, String startDate, int totalWeeks) {
            this.id = id;
            this.name = name;
            this.startDate = startDate;
            this.totalWeeks = totalWeeks;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("startDate", startDate);
            o.put("totalWeeks", totalWeeks);
            o.put("startDateTouched", startDateTouched);
            o.put("sortOrder", sortOrder);
            return o;
        }

        public static Semester fromJson(JSONObject o) {
            Semester s = new Semester();
            s.id = o.optString("id", "");
            s.name = o.optString("name", "");
            s.startDate = o.optString("startDate", "");
            s.totalWeeks = o.optInt("totalWeeks", 20);
            s.startDateTouched = o.optBoolean("startDateTouched", false);
            s.sortOrder = o.optInt("sortOrder", 0);
            return s;
        }
    }

    /** 一个上课时段 */
    public static final class Session {
        public int dayOfWeek = 1;        // 1=周一 … 7=周日
        public int startSection = 1;     // 起始节次
        public int endSection = 1;       // 结束节次（含）
        public List<Integer> weeks = new ArrayList<>();
        public String weeksRaw = "";     // 原始表达式，保留以便排查
        public String location = "";     // 教室（每个时段可能不同）
        public String campus = "";       // 校区
        public String roomType = "";     // 场地类别
        /** 本时段教师；空串表示沿用课程教师。 */
        public String teacher = "";
        /** 按周换老师（如形势与政策每周一个主讲）：周次 → 教师，优先于 teacher。 */
        public java.util.Map<Integer, String> weekTeachers = new java.util.TreeMap<>();
        /** 自定义起止时间 HH:mm（如考试 14:30–16:30）；都为空时按节次画。 */
        public String timeStart = "";
        public String timeEnd = "";

        public Session() {}

        /** 有合法的自定义时间（开始早于结束）。 */
        public boolean hasCustomTime() {
            int a = minutesOf(timeStart), b = minutesOf(timeEnd);
            return a >= 0 && b > a;
        }

        /** 这一周显示哪位老师：按周 > 本时段 > 课程。 */
        public String teacherFor(int week, String courseTeacher) {
            String w = weekTeachers.get(week);
            if (w != null && !w.isEmpty()) return w;
            if (teacher != null && !teacher.isEmpty()) return teacher;
            return courseTeacher == null ? "" : courseTeacher;
        }

        /** "HH:mm" → 当天分钟数；格式不对返回 -1。 */
        public static int minutesOf(String hm) {
            if (hm == null) return -1;
            String s = hm.trim();
            int c = s.indexOf(':');
            if (c <= 0) return -1;
            try {
                int h = Integer.parseInt(s.substring(0, c));
                int m = Integer.parseInt(s.substring(c + 1));
                if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
                return h * 60 + m;
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        public static String formatHm(int minutes) {
            return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
        }

        public Session copy() {
            Session s = new Session();
            s.dayOfWeek = dayOfWeek;
            s.startSection = startSection;
            s.endSection = endSection;
            s.weeks.addAll(weeks);
            s.weeksRaw = weeksRaw;
            s.location = location;
            s.campus = campus;
            s.roomType = roomType;
            s.teacher = teacher;
            s.weekTeachers.putAll(weekTeachers);
            s.timeStart = timeStart;
            s.timeEnd = timeEnd;
            return s;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("dayOfWeek", dayOfWeek);
            o.put("startSection", startSection);
            o.put("endSection", endSection);
            JSONArray wa = new JSONArray();
            for (int w : weeks) wa.put(w);
            o.put("weeks", wa);
            o.put("weeksRaw", weeksRaw);
            o.put("location", location);
            o.put("campus", campus);
            o.put("roomType", roomType);
            if (teacher != null && !teacher.isEmpty()) o.put("teacher", teacher);
            if (!weekTeachers.isEmpty()) {
                JSONObject wt = new JSONObject();
                for (java.util.Map.Entry<Integer, String> e : weekTeachers.entrySet()) {
                    wt.put(String.valueOf(e.getKey()), e.getValue());
                }
                o.put("weekTeachers", wt);
            }
            if (hasCustomTime()) {
                o.put("timeStart", timeStart);
                o.put("timeEnd", timeEnd);
            }
            return o;
        }

        public static Session fromJson(JSONObject o) {
            Session s = new Session();
            s.dayOfWeek = o.optInt("dayOfWeek", 1);
            s.startSection = o.optInt("startSection", 1);
            s.endSection = o.optInt("endSection", s.startSection);
            s.weeksRaw = o.optString("weeksRaw", "");
            JSONArray wa = o.optJSONArray("weeks");
            if (wa != null) {
                for (int i = 0; i < wa.length(); i++) s.weeks.add(wa.optInt(i));
            }
            if (s.weeks.isEmpty() && !s.weeksRaw.isEmpty()) {
                s.weeks.addAll(WeekRange.parse(s.weeksRaw).weeks);
            }
            s.location = o.optString("location", "");
            s.campus = o.optString("campus", "");
            s.roomType = o.optString("roomType", "");
            s.teacher = o.optString("teacher", "");
            JSONObject wt = o.optJSONObject("weekTeachers");
            if (wt != null) {
                java.util.Iterator<String> it = wt.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    try { s.weekTeachers.put(Integer.parseInt(k), wt.optString(k, "")); }
                    catch (NumberFormatException ignored) {}
                }
            }
            s.timeStart = o.optString("timeStart", "");
            s.timeEnd = o.optString("timeEnd", "");
            return s;
        }

        /** 该时段是否在某周上课 */
        public boolean isActive(int week) {
            if (weeks.isEmpty()) return true; // 周次未解析出来时保底显示
            return weeks.contains(week);
        }
    }

    /** 一门课程 */
    public static final class Course {
        public String key = "";          // 聚合主键（jxb_id 或 custom_xxx）
        public String name = "";
        public String teacher = "";
        public double credit = 0;
        public String category = "";     // 主修 / 辅修 …
        public String examType = "";     // 正常考试 / 考查 …
        public int colorIndex = 0;       // 0-7，色板索引
        public boolean colorTouched = false; // 用户在编辑器里手动选过颜色
        public String source = SOURCE_JWGLXT;
        public String note = "";         // 用户备注
        public List<Session> sessions = new ArrayList<>();

        public Course() {}

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("name", name);
            o.put("teacher", teacher);
            o.put("credit", credit);
            o.put("category", category);
            o.put("examType", examType);
            o.put("colorIndex", colorIndex);
            o.put("colorTouched", colorTouched);
            o.put("source", source);
            o.put("note", note);
            JSONArray sa = new JSONArray();
            for (Session s : sessions) sa.put(s.toJson());
            o.put("sessions", sa);
            return o;
        }

        public static Course fromJson(JSONObject o) {
            Course c = new Course();
            c.key = o.optString("key", "");
            c.name = o.optString("name", "");
            c.teacher = o.optString("teacher", "");
            c.credit = o.optDouble("credit", 0);
            c.category = o.optString("category", "");
            c.examType = o.optString("examType", "");
            c.colorIndex = o.optInt("colorIndex", 0);
            c.colorTouched = o.optBoolean("colorTouched", false);
            c.source = o.optString("source", SOURCE_JWGLXT);
            c.note = o.optString("note", "");
            JSONArray sa = o.optJSONArray("sessions");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    JSONObject so = sa.optJSONObject(i);
                    if (so != null) c.sessions.add(Session.fromJson(so));
                }
            }
            return c;
        }

        /** 该课程在某周是否上课 */
        public boolean isActive(int week) {
            for (Session s : sessions) {
                if (s.isActive(week)) return true;
            }
            return false;
        }
    }

    /**
     * 单次调整（不改课程本身）：
     *   cancel  某周某天某节停一次课（start=0 表示那天这门课全停）
     *   move    把某次课调到另一周/天/节（或自定义时间），原位置显示「已调走」
     *   off     某天放假（date），当天课程置灰
     *   makeup  某天补课（date），上 fromDate 那天的课
     */
    public static final class Adjust {
        public static final String CANCEL = "cancel";
        public static final String MOVE = "move";
        public static final String OFF = "off";
        public static final String MAKEUP = "makeup";

        public String id = "";
        public String type = CANCEL;
        public String name = "";        // 课程名（cancel/move）
        public int week, day, start, end;
        public int toWeek, toDay, toStart, toEnd;
        public String toTimeStart = "", toTimeEnd = "", toLocation = "";
        public String date = "";        // off/makeup：yyyy-MM-dd
        public String fromDate = "";    // makeup：上哪天的课
        public String note = "";

        public boolean hasToTime() {
            int a = Session.minutesOf(toTimeStart), b = Session.minutesOf(toTimeEnd);
            return a >= 0 && b > a;
        }

        /** 是否同一件事（去重用，忽略 id/note）。 */
        public boolean sameAs(Adjust o) {
            return type.equals(o.type) && name.equals(o.name) && week == o.week && day == o.day
                    && start == o.start && end == o.end && toWeek == o.toWeek && toDay == o.toDay
                    && toStart == o.toStart && toEnd == o.toEnd && toTimeStart.equals(o.toTimeStart)
                    && toTimeEnd.equals(o.toTimeEnd) && date.equals(o.date) && fromDate.equals(o.fromDate);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            if (!name.isEmpty()) o.put("name", name);
            if (week != 0) o.put("week", week);
            if (day != 0) o.put("day", day);
            if (start != 0) o.put("start", start);
            if (end != 0) o.put("end", end);
            if (toWeek != 0) o.put("toWeek", toWeek);
            if (toDay != 0) o.put("toDay", toDay);
            if (toStart != 0) o.put("toStart", toStart);
            if (toEnd != 0) o.put("toEnd", toEnd);
            if (!toTimeStart.isEmpty()) o.put("toTimeStart", toTimeStart);
            if (!toTimeEnd.isEmpty()) o.put("toTimeEnd", toTimeEnd);
            if (!toLocation.isEmpty()) o.put("toLocation", toLocation);
            if (!date.isEmpty()) o.put("date", date);
            if (!fromDate.isEmpty()) o.put("fromDate", fromDate);
            if (!note.isEmpty()) o.put("note", note);
            return o;
        }

        public static Adjust fromJson(JSONObject o) {
            Adjust a = new Adjust();
            a.id = o.optString("id", "");
            a.type = o.optString("type", CANCEL);
            a.name = o.optString("name", "");
            a.week = o.optInt("week"); a.day = o.optInt("day");
            a.start = o.optInt("start"); a.end = o.optInt("end");
            a.toWeek = o.optInt("toWeek"); a.toDay = o.optInt("toDay");
            a.toStart = o.optInt("toStart"); a.toEnd = o.optInt("toEnd");
            a.toTimeStart = o.optString("toTimeStart", "");
            a.toTimeEnd = o.optString("toTimeEnd", "");
            a.toLocation = o.optString("toLocation", "");
            a.date = o.optString("date", "");
            a.fromDate = o.optString("fromDate", "");
            a.note = o.optString("note", "");
            return a;
        }
    }

    public Semester semester = new Semester();
    public List<Course> courses = new ArrayList<>();
    /** 停课/调课/放假/补课，跟着学期走，同步教务时保留。 */
    public List<Adjust> adjusts = new ArrayList<>();

    public Timetable() {}

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("semester", semester.toJson());
        JSONArray ca = new JSONArray();
        for (Course c : courses) ca.put(c.toJson());
        o.put("courses", ca);
        if (!adjusts.isEmpty()) {
            JSONArray aa = new JSONArray();
            for (Adjust a : adjusts) aa.put(a.toJson());
            o.put("adjusts", aa);
        }
        return o;
    }

    public static Timetable fromJson(JSONObject o) throws JSONException {
        int ver = o.optInt("schemaVersion", 0);
        if (ver > SCHEMA_VERSION) {
            throw new JSONException("课表数据版本 " + ver + " 高于当前支持版本 " + SCHEMA_VERSION + "，请升级 App");
        }
        Timetable t = new Timetable();
        JSONObject so = o.optJSONObject("semester");
        if (so != null) t.semester = Semester.fromJson(so);
        JSONArray ca = o.optJSONArray("courses");
        if (ca != null) {
            for (int i = 0; i < ca.length(); i++) {
                JSONObject co = ca.optJSONObject(i);
                if (co != null) t.courses.add(Course.fromJson(co));
            }
        }
        JSONArray aa = o.optJSONArray("adjusts");
        if (aa != null) {
            for (int i = 0; i < aa.length(); i++) {
                JSONObject ao = aa.optJSONObject(i);
                if (ao != null) t.adjusts.add(Adjust.fromJson(ao));
            }
        }
        return t;
    }

    public static Timetable fromJson(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    /** 按课程名查（调试用） */
    public Course findByName(String name) {
        for (Course c : courses) {
            if (name.equals(c.name)) return c;
        }
        return null;
    }

    /** 指定周、指定星期的所有时段 */
    public List<Course> coursesOn(int dayOfWeek, int week) {
        List<Course> out = new ArrayList<>();
        for (Course c : courses) {
            for (Session s : c.sessions) {
                if (s.dayOfWeek == dayOfWeek && s.isActive(week)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }
}
