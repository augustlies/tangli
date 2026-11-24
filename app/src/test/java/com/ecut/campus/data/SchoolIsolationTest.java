package com.ecut.campus.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.ecut.campus.SchoolFiles;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SchoolIsolationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Context ctx;
    private final AtomicReference<String> school = new AtomicReference<>("ecut");
    private final AtomicReference<String> customJson = new AtomicReference<>("");

    @Before public void setup() {
        ctx = mock(Context.class);
        when(ctx.getApplicationContext()).thenReturn(ctx);
        when(ctx.getFilesDir()).thenReturn(folder.getRoot());
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs);
        when(prefs.getString(eq("school_id"), anyString())).thenAnswer(i -> school.get());
        when(prefs.getString(eq("custom_school"), anyString())).thenAnswer(i -> customJson.get());
        when(prefs.edit()).thenReturn(mock(SharedPreferences.Editor.class, RETURNS_SELF));
    }

    @Test public void legacyClaimIsExclusiveAndSwitchBackRestoresArchive() throws Exception {
        Timetable old = TimetableImportTest.timetable("2026-2027-1", "A");
        File legacy = new File(folder.getRoot(), "timetable.json");
        AtomicFileStore.write(legacy, old.toJson().toString());
        assertEquals("A", TimetableStore.load(ctx).courses.get(0).name);
        school.set("other");
        assertTrue(TimetableStore.load(ctx).courses.isEmpty());
        Timetable remote = TimetableImportTest.timetable("2026-2027-1", "B");
        assertTrue(TimetableStore.save(ctx, TimetableStore.merge(TimetableStore.load(ctx), remote)));
        assertEquals(1, TimetableStore.load(ctx).courses.size());
        assertEquals("B", TimetableStore.load(ctx).courses.get(0).name);
        school.set("ecut");
        assertEquals("A", TimetableStore.load(ctx).courses.get(0).name);
        assertTrue(legacy.exists());
    }

    @Test public void clearDoesNotResurrectLegacyOrClearAnotherSchool() throws Exception {
        AtomicFileStore.write(new File(folder.getRoot(), "grades.json"), new Grades().toJson().toString());
        assertTrue(GradesStore.exists(ctx));
        long pending = SyncGeneration.current();
        GradesStore.clear(ctx);
        assertFalse(SyncGeneration.isCurrent(pending));
        assertFalse(GradesStore.exists(ctx));
        school.set("other");
        assertFalse(GradesStore.exists(ctx));
        assertTrue(GradesStore.save(ctx, new Grades()));
        school.set("ecut");
        assertFalse(GradesStore.exists(ctx));
        school.set("other");
        assertTrue(GradesStore.exists(ctx));
    }

    @Test public void clearingArchiveInvalidatesPendingTimetableAndGradeCallbacks() {
        long pending = SyncGeneration.current();
        TimetableStore.clear(ctx);
        assertFalse(SyncGeneration.isCurrent(pending));
        long next = SyncGeneration.current();
        SyncGeneration.invalidate(); // school/session switch, including A → B → A
        SyncGeneration.invalidate();
        assertFalse(SyncGeneration.isCurrent(next));
    }

    @Test public void partialOrUnverifiedGradesCannotReplaceLocalGrades() {
        assertFalse(GradeGridQuery.complete("{\"records\":57}", 15));
        assertFalse(GradeGridQuery.complete("{\"rows\":[]}", 15));
        assertFalse(GradeGridQuery.complete("{\"records\":0}", 0));
        assertFalse(GradeGridQuery.complete("", 57));
        assertTrue(GradeGridQuery.complete("{\"records\":57}", 57));
    }

    @Test public void customSchoolsAreSeparatedByHostNotSharedCustomId() throws Exception {
        school.set(SchoolProfile.CUSTOM_ID);
        SchoolProfile a = SchoolProfile.custom("https://a.example.edu/jwglxt/", SchoolProfile.Vendor.ZHENGFANG);
        SchoolProfile b = SchoolProfile.custom("https://b.example.edu/jwglxt/", SchoolProfile.Vendor.ZHENGFANG);
        customJson.set(a.toJson().toString());
        assertTrue(TimetableStore.save(ctx, TimetableImportTest.timetable("2026-2027-1", "A")));
        File first = SchoolFiles.file(ctx, "timetable.json");
        customJson.set(b.toJson().toString());
        assertTrue(TimetableStore.load(ctx).courses.isEmpty());
        assertNotEquals(first, SchoolFiles.file(ctx, "timetable.json"));
        a.jwBase = "https://a.example.edu/changed-path/";
        customJson.set(a.toJson().toString());
        assertEquals(first, SchoolFiles.file(ctx, "timetable.json"));
        assertEquals("A", TimetableStore.load(ctx).courses.get(0).name);
    }
}
