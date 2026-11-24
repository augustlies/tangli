package com.ecut.campus.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.ecut.campus.ScheduleSettings;
import com.ecut.campus.ScheduleSettingsStore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real disk I/O and store code; only the Android Context/Prefs boundary is mocked. */
public class LocalStoreIntegrityTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Context ctx;

    @Before public void context() {
        ctx = mock(Context.class);
        when(ctx.getFilesDir()).thenReturn(folder.getRoot());
        when(ctx.getApplicationContext()).thenReturn(ctx);
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
        when(prefs.edit()).thenReturn(editor);
        when(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs);
    }

    private File file(String name) {
        return name.startsWith("schedule_settings") ? new File(folder.getRoot(), name)
                : com.ecut.campus.SchoolFiles.file(ctx, name);
    }

    @Test public void firstRunMigratesLegacyMirror() throws Exception {
        Timetable legacy = TimetableImportTest.timetable("2026-2027-1", "A");
        AtomicFileStore.write(new File(folder.getRoot(), "timetable.json"), legacy.toJson().toString());
        assertFalse(SemesterArchive.isInitialized(ctx));
        assertEquals(1, TimetableStore.load(ctx).courses.size());
        assertTrue(SemesterArchive.isInitialized(ctx));
        assertEquals(legacy.semester.id, SemesterArchive.activeSemesterId(ctx));
        assertEquals(1, SemesterArchive.listSemesters(ctx).size());
    }

    @Test public void rejectedImportLeavesAuthoritativeFilesUntouched() throws Exception {
        Timetable t = TimetableImportTest.timetable("2026-2027-1", "A");
        assertTrue(TimetableStore.save(ctx, t));
        String archive = AtomicFileStore.read(file("timetable_semesters.json"));
        String mirror = AtomicFileStore.read(file("timetable.json"));
        assertThrows(org.json.JSONException.class, () -> TimetableStore.importJson("{}"));
        assertEquals(archive, AtomicFileStore.read(file("timetable_semesters.json")));
        assertEquals(mirror, AtomicFileStore.read(file("timetable.json")));
        assertEquals(1, TimetableStore.load(ctx).courses.size());
    }

    @Test public void deletingLastSemesterSurvivesRepeatedReloadAndAllowsNewSemester() throws Exception {
        Timetable t = TimetableImportTest.timetable("2026-2027-1", "A");
        assertTrue(TimetableStore.save(ctx, t));
        assertTrue(SemesterArchive.deleteSemester(ctx, t.semester.id));
        // Deliberately leave the stale mirror on disk, as after a crash before cleanup.
        assertTrue(file("timetable.json").exists());
        for (int i = 0; i < 3; i++) {
            assertTrue(TimetableStore.load(ctx).courses.isEmpty());
            assertEquals("", SemesterArchive.activeSemesterId(ctx));
            assertTrue(SemesterArchive.listSemesters(ctx).isEmpty());
        }
        assertTrue(TimetableStore.save(ctx, TimetableImportTest.timetable("custom_new", "B")));
        assertEquals("custom_new", TimetableStore.load(ctx).semester.id);
    }

    @Test public void sameSemesterLegacyMetadataBackfillStillWorks() throws Exception {
        Timetable legacy = TimetableImportTest.timetable("2026-2027-1", "A");
        legacy.semester.startDate = "2026-09-07";
        legacy.semester.totalWeeks = 18;
        org.json.JSONObject archived = legacy.toJson();
        archived.getJSONObject("semester").remove("startDate");
        archived.getJSONObject("semester").remove("totalWeeks");
        org.json.JSONObject root = new org.json.JSONObject()
                .put("schemaVersion", 1).put("activeSemesterId", legacy.semester.id)
                .put("timetables", new org.json.JSONArray().put(archived));
        AtomicFileStore.write(file("timetable_semesters.json"), root.toString());
        AtomicFileStore.write(file("timetable.json"), legacy.toJson().toString());
        Timetable loaded = TimetableStore.load(ctx);
        assertEquals("2026-09-07", loaded.semester.startDate);
        assertEquals(18, loaded.semester.totalWeeks);
        assertEquals(1, SemesterArchive.listSemesters(ctx).size());
    }

    @Test public void deletingActiveSemesterDoesNotAppendStaleMirrorToRemainingArchive() throws Exception {
        Timetable a = TimetableImportTest.timetable("2025-2026-1", "A");
        Timetable b = TimetableImportTest.timetable("2026-2027-1", "B");
        assertTrue(SemesterArchive.saveSemester(ctx, a, true));
        assertTrue(SemesterArchive.saveSemester(ctx, b, true));
        assertTrue(SemesterArchive.deleteSemester(ctx, b.semester.id));
        assertEquals(a.semester.id, TimetableStore.load(ctx).semester.id);
        assertEquals(1, SemesterArchive.listSemesters(ctx).size());
        assertFalse(SemesterArchive.contains(ctx, b.semester.id));
    }

    @Test public void interruptedArchiveDeletionRestoresPreviousCommittedSemester() throws Exception {
        Timetable t = TimetableImportTest.timetable("2026-2027-1", "A");
        assertTrue(TimetableStore.save(ctx, t));
        String committed = AtomicFileStore.read(file("timetable_semesters.json"));
        assertTrue(SemesterArchive.deleteSemester(ctx, t.semester.id));
        AtomicFileStore.write(file("timetable_semesters.json.bak"), committed);
        assertEquals(t.semester.id, TimetableStore.load(ctx).semester.id);
        assertEquals(1, SemesterArchive.listSemesters(ctx).size());
    }

    @Test public void gradesMirrorAndSettingsReadRecoverableBackups() throws Exception {
        Grades grades = new Grades();
        assertTrue(GradesStore.save(ctx, grades));
        assertTrue(file("grades.json").renameTo(file("grades.json.bak")));
        assertTrue(GradesStore.exists(ctx));
        assertNotNull(GradesStore.load(ctx));
        assertTrue(file("grades.json").exists());

        ScheduleSettings settings = ScheduleSettings.createDefault();
        settings.setSectionCount(12);
        assertTrue(ScheduleSettingsStore.save(ctx, settings));
        assertTrue(file("schedule_settings.json").renameTo(file("schedule_settings.json.bak")));
        assertEquals(12, ScheduleSettingsStore.load(ctx).sectionCount());

        Timetable legacy = TimetableImportTest.timetable("2026-2027-1", "A");
        AtomicFileStore.write(file("timetable.json.bak"), legacy.toJson().toString());
        assertEquals(1, TimetableStore.load(ctx).courses.size());
        assertTrue(file("timetable.json").exists());
    }

    @Test public void explicitClearAlsoRemovesBackups() throws Exception {
        for (String name : new String[] { "timetable.json", "timetable_semesters.json",
                "grades.json", "schedule_settings.json" }) {
            AtomicFileStore.write(file(name + ".bak"), "{}");
        }
        TimetableStore.clear(ctx);
        GradesStore.clear(ctx);
        ScheduleSettingsStore.clear(ctx);
        for (String name : new String[] { "timetable.json", "timetable_semesters.json",
                "grades.json", "schedule_settings.json" }) {
            assertFalse(AtomicFileStore.exists(file(name)));
        }
    }
}
