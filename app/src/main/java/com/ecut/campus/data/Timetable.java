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

        public Session() {}

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

    public Semester semester = new Semester();
    public List<Course> courses = new ArrayList<>();

    public Timetable() {}

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("semester", semester.toJson());
        JSONArray ca = new JSONArray();
        for (Course c : courses) ca.put(c.toJson());
        o.put("courses", ca);
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
