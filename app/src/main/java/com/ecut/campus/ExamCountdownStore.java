package com.ecut.campus;

import android.content.Context;
import com.ecut.campus.data.AtomicFileStore;
import com.ecut.campus.data.ExamCountdown;
import com.ecut.campus.data.Timetable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User-defined exam dates live separately from synced course data. */
final class ExamCountdownStore {
    private ExamCountdownStore() {}
    private static String safe(String s) { return s == null ? "" : s; }
    private static JSONObject root(Context c) {
        try { String text=AtomicFileStore.read(SchoolFiles.file(c,"exam_countdowns.json")); return text==null?new JSONObject():new JSONObject(text); }
        catch(Exception e) { return new JSONObject(); }
    }
    static ExamCountdown.Event get(Context c, String semester, String course) {
        String id = safe(semester) + "|" + safe(course);
        JSONObject o=root(c).optJSONObject(id);
        return o==null?null:new ExamCountdown.Event(id, o.optString("name"),o.optString("date"));
    }
    static boolean save(Context c, String semester, String course, String name, String date) {
        try { JSONObject r=root(c); JSONObject e=new JSONObject(); e.put("name",name.trim());e.put("date",date);
            r.put(safe(semester)+"|"+safe(course),e);AtomicFileStore.write(SchoolFiles.file(c,"exam_countdowns.json"),r.toString());return true;
        } catch(Exception e) { return false; }
    }
    static boolean clear(Context c, String semester, String course) {
        try { JSONObject r=root(c);r.remove(safe(semester)+"|"+safe(course));AtomicFileStore.write(SchoolFiles.file(c,"exam_countdowns.json"),r.toString());return true; }
        catch(Exception e) { return false; }
    }
    static Set<String> hiddenIds(Context c) {
        Set<String> out = new HashSet<>();
        try {
            String text = AtomicFileStore.read(SchoolFiles.file(c, "exam_countdown_hidden.json"));
            if (text == null || text.trim().isEmpty()) return out;
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i, "");
                if (!id.isEmpty()) out.add(id);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
    static boolean setHiddenIds(Context c, Set<String> ids) {
        try {
            JSONArray arr = new JSONArray();
            if (ids != null) for (String id : ids) if (id != null && !id.isEmpty()) arr.put(id);
            AtomicFileStore.write(SchoolFiles.file(c, "exam_countdown_hidden.json"), arr.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    static List<ExamCountdown.Event> allCandidates(Context c, Timetable t, String start, int totalWeeks, String today) {
        List<ExamCountdown.Event> out = new ArrayList<>();
        if (t == null) return out;
        JSONObject r = root(c);
        String sem = t.semester == null ? "" : t.semester.id;
        java.util.Map<Timetable.Course, String> nextDates = ExamCountdown.nextCourseDates(t, start, totalWeeks, today);
        for (Timetable.Course course : t.courses) {
            String id = safe(sem) + "|" + safe(course.key);
            JSONObject manual = r.optJSONObject(id);
            if (manual != null) {
                out.add(new ExamCountdown.Event(id, manual.optString("name"), manual.optString("date")));
            } else if (ExamCountdown.isExam(course.name)) {
                String d = nextDates.getOrDefault(course, "");
                if (!d.isEmpty()) out.add(new ExamCountdown.Event(id, course.name, d));
            }
        }
        return ExamCountdown.upcoming(out, today, Integer.MAX_VALUE);
    }
    static List<ExamCountdown.Event> events(Context c, Timetable t, String start, int totalWeeks, String today) {
        if (!Prefs.widgetExamEnabled(c)) return new ArrayList<>();
        List<ExamCountdown.Event> all = allCandidates(c, t, start, totalWeeks, today);
        return ExamCountdown.upcoming(all, today, Integer.MAX_VALUE, Prefs.widgetExamLeadDays(c), hiddenIds(c));
    }
}
