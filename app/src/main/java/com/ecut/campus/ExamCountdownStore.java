package com.ecut.campus;

import android.content.Context;
import com.ecut.campus.data.AtomicFileStore;
import com.ecut.campus.data.ExamCountdown;
import com.ecut.campus.data.Timetable;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** User-defined exam dates live separately from synced course data. */
final class ExamCountdownStore {
    private ExamCountdownStore() {}
    private static String safe(String s) { return s == null ? "" : s; }
    private static JSONObject root(Context c) {
        try { String text=AtomicFileStore.read(SchoolFiles.file(c,"exam_countdowns.json")); return text==null?new JSONObject():new JSONObject(text); }
        catch(Exception e) { return new JSONObject(); }
    }
    static ExamCountdown.Event get(Context c, String semester, String course) {
        JSONObject o=root(c).optJSONObject(safe(semester)+"|"+safe(course));
        return o==null?null:new ExamCountdown.Event(o.optString("name"),o.optString("date"));
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
    static List<ExamCountdown.Event> events(Context c, Timetable t, String start, int totalWeeks, String today) {
        List<ExamCountdown.Event> out=new ArrayList<>(); if(t==null)return out;
        JSONObject r=root(c); String sem=t.semester==null?"":t.semester.id;
        for(Timetable.Course course:t.courses) {
            JSONObject manual=r.optJSONObject(sem+"|"+course.key);
            if(manual!=null) out.add(new ExamCountdown.Event(manual.optString("name"),manual.optString("date")));
            else if(ExamCountdown.isExam(course.name)) { String d=ExamCountdown.nextCourseDate(t,course,start,totalWeeks,today);
                if(!d.isEmpty())out.add(new ExamCountdown.Event(course.name,d)); }
        }
        return ExamCountdown.upcoming(out, today, Integer.MAX_VALUE);
    }
}
