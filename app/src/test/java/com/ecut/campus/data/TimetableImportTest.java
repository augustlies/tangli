package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class TimetableImportTest {
    static Timetable timetable(String id, String... keys) {
        Timetable t = new Timetable();
        t.semester.id = id;
        for (String key : keys) {
            Timetable.Course c = new Timetable.Course();
            c.key = key;
            c.name = key;
            c.sessions.add(new Timetable.Session());
            t.courses.add(c);
        }
        return t;
    }

    private JSONObject backup() throws Exception { return timetable("2026-2027-1", "A").toJson(); }
    private void rejects(JSONObject json) {
        assertThrows(JSONException.class, () -> TimetableStore.importJson(json.toString()));
    }

    @Test public void rejectsWrongRootsIncompleteAndTrailingData() throws Exception {
        for (String input : new String[] { "", "null", "[]", "{}", "{\"schemaVersion\":1}",
                "{\"semester\":", backup().toString() + " {}" }) {
            assertThrows(input, JSONException.class, () -> TimetableStore.importJson(input));
        }
        for (String field : new String[] { "schemaVersion", "semester", "courses" }) {
            JSONObject root = backup(); root.remove(field); rejects(root);
        }
        JSONObject root = backup(); root.getJSONObject("semester").remove("id"); rejects(root);
    }

    @Test public void rejectsCoercedTypesVersionsAndInvalidArrayMembers() throws Exception {
        for (Object value : new Object[] { "1", 1.5, 0, -1, 2, JSONObject.NULL }) {
            rejects(backup().put("schemaVersion", value));
        }
        rejects(backup().put("courses", new JSONObject()));
        rejects(backup().put("courses", new JSONArray().put(JSONObject.NULL)));
        rejects(backup().put("courses", new JSONArray().put("course")));
        JSONObject root = backup(); root.getJSONObject("semester").put("totalWeeks", "20"); rejects(root);
    }

    @Test public void rejectsIncompleteCoursesSessionsAndDuplicateKeys() throws Exception {
        for (String field : new String[] { "key", "name", "sessions" }) {
            JSONObject root = backup(); root.getJSONArray("courses").getJSONObject(0).remove(field); rejects(root);
        }
        JSONObject root = backup();
        root.getJSONArray("courses").put(root.getJSONArray("courses").getJSONObject(0)); rejects(root);
        for (String field : new String[] { "dayOfWeek", "startSection", "endSection", "weeks" }) {
            root = backup();
            root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions").getJSONObject(0).remove(field);
            rejects(root);
        }
        for (Object day : new Object[] { 0, 8, "1", 1.5, JSONObject.NULL }) {
            root = backup();
            root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions").getJSONObject(0).put("dayOfWeek", day);
            rejects(root);
        }
        root = backup(); root.getJSONArray("courses").getJSONObject(0).put("sessions", new JSONArray().put(3)); rejects(root);
        root = backup(); root.put("adjusts", new JSONArray().put(3)); rejects(root);
    }

    @Test public void exportsRoundTripAndExplicitEmptyImportCannotDeleteCourses() throws Exception {
        Timetable local = timetable("2026-2027-1", "A", "B");
        Timetable empty = TimetableStore.importJson(timetable(local.semester.id).toJson().toString());
        assertEquals(2, TimetableStore.mergeImport(local, empty).courses.size());
        Timetable imported = TimetableStore.importJson(TimetableStore.exportJson(local));
        assertEquals(2, imported.courses.size());
    }

    @Test public void rejectsBadWeeksSectionRangesAndCustomTimes() throws Exception {
        for (Object week : new Object[] { 0, 100, "1", 1.5, JSONObject.NULL }) {
            JSONObject root = backup();
            root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions")
                    .getJSONObject(0).put("weeks", new JSONArray().put(week));
            rejects(root);
        }
        JSONObject root = backup();
        root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions")
                .getJSONObject(0).put("startSection", 4).put("endSection", 2);
        rejects(root);
        root = backup();
        root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions")
                .getJSONObject(0).put("timeStart", "14:00").put("timeEnd", "13:00");
        rejects(root);
        Timetable longTerm = timetable("custom_long", "A");
        longTerm.courses.get(0).sessions.get(0).weeks.add(99);
        assertEquals(Integer.valueOf(99), TimetableStore.importJson(longTerm.toJson().toString())
                .courses.get(0).sessions.get(0).weeks.get(0));
    }

    @Test public void importBoundsInputAndNestedArrays() throws Exception {
        assertThrows(JSONException.class, () -> TimetableStore.importJson(
                " ".repeat(TimetableStore.MAX_IMPORT_CHARS + 1)));
        assertThrows(JSONException.class, () -> TimetableStore.importJson(
                "[".repeat(1000) + "0" + "]".repeat(1000)));

        JSONObject root = backup();
        JSONArray tooManyCourses = new JSONArray();
        for (int i = 0; i <= TimetableStore.MAX_IMPORT_COURSES; i++) tooManyCourses.put(JSONObject.NULL);
        rejects(root.put("courses", tooManyCourses));

        root = backup();
        JSONArray tooManySessions = new JSONArray();
        for (int i = 0; i <= TimetableStore.MAX_IMPORT_SESSIONS_PER_COURSE; i++) {
            tooManySessions.put(new JSONObject());
        }
        root.getJSONArray("courses").getJSONObject(0).put("sessions", tooManySessions);
        rejects(root);

        root = backup();
        JSONArray tooManyWeeks = new JSONArray();
        for (int i = 1; i <= 100; i++) tooManyWeeks.put(i);
        root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions")
                .getJSONObject(0).put("weeks", tooManyWeeks);
        rejects(root);

        root = backup();
        JSONArray tooManyAdjusts = new JSONArray();
        for (int i = 0; i <= TimetableStore.MAX_IMPORT_ADJUSTS; i++) tooManyAdjusts.put(JSONObject.NULL);
        root.put("adjusts", tooManyAdjusts);
        rejects(root);

        root = backup();
        root.getJSONArray("courses").getJSONObject(0).put("name", "n".repeat(257));
        rejects(root);
        root = backup();
        root.getJSONArray("courses").getJSONObject(0).getJSONArray("sessions")
                .getJSONObject(0).put("location", "l".repeat(513));
        rejects(root);

        assertNull(TimetableShare.decode(TimetableShare.PREFIX + "A".repeat(3 * 1024 * 1024)));
    }

    @Test public void newlyImportedCoursesSurviveSubsequentSchoolSync() throws Exception {
        Timetable merged = TimetableStore.mergeImport(timetable("2026-2027-1", "A"),
                timetable("2026-2027-1", "B"));
        Timetable synced = TimetableStore.merge(merged, timetable("2026-2027-1", "A"));
        assertEquals(2, synced.courses.size());
        assertEquals(Timetable.SOURCE_IMPORT, synced.findByName("B").source);
    }

    @Test public void additiveMergePreservesLocalMetadataAdjustmentsAndInputObjects() throws Exception {
        Timetable local = timetable("2026-2027-1", "A", "B");
        local.semester.startDate = "2026-09-07";
        local.semester.totalWeeks = 18;
        local.courses.get(0).note = "local note";
        local.courses.get(0).colorIndex = 5;
        Timetable.Adjust adjustment = new Timetable.Adjust(); adjustment.id = "local";
        local.adjusts.add(adjustment);
        Timetable imported = timetable(local.semester.id, "A", "C");
        imported.courses.get(0).name = "updated";
        Timetable merged = TimetableStore.mergeImport(local, imported);
        assertEquals(3, merged.courses.size());
        assertEquals("updated", merged.courses.get(0).name);
        assertEquals("local note", merged.courses.get(0).note);
        assertEquals(5, merged.courses.get(0).colorIndex);
        assertEquals("2026-09-07", merged.semester.startDate);
        assertEquals(18, merged.semester.totalWeeks);
        assertEquals(1, merged.adjusts.size());
        assertEquals(Timetable.SOURCE_IMPORT, merged.courses.get(2).source);
        assertEquals("A", local.courses.get(0).name);
        assertEquals(Timetable.SOURCE_JWGLXT, imported.courses.get(1).source);
    }

    @Test public void rejectsCrossSemesterMergeAndAllowsFirstImport() throws Exception {
        Timetable imported = timetable("2026-2027-1", "A");
        assertThrows(JSONException.class,
                () -> TimetableStore.mergeImport(timetable("2025-2026-1", "B"), imported));
        assertEquals("2026-2027-1", TimetableStore.mergeImport(new Timetable(), imported).semester.id);
    }
}
