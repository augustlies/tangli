package com.ecut.campus;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExamCountdownStoreTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void manualEventSurvivesReloadAndIsScopedBySemesterAndCourse() throws Exception {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(context.getFilesDir()).thenReturn(folder.getRoot());
        when(preferences.getString("school_id", "")).thenReturn("ecut");
        assertTrue(ExamCountdownStore.save(context, "2026-2027-1", "course-a", "CET-4", "2026-12-19"));
        com.ecut.campus.data.ExamCountdown.Event event = ExamCountdownStore.get(context, "2026-2027-1", "course-a");
        assertNotNull(event);
        assertEquals("CET-4", event.name);
        assertEquals("2026-12-19", event.date);
        assertNull(ExamCountdownStore.get(context, "2026-2027-2", "course-a"));
        assertNull(ExamCountdownStore.get(context, "2026-2027-1", "course-b"));
        when(preferences.getString("school_id", "")).thenReturn("other-school");
        assertNull(ExamCountdownStore.get(context, "2026-2027-1", "course-a"));
        when(preferences.getString("school_id", "")).thenReturn("ecut");
        assertTrue(ExamCountdownStore.clear(context, "2026-2027-1", "course-a"));
        assertNull(ExamCountdownStore.get(context, "2026-2027-1", "course-a"));
    }

    @Test public void hiddenIdsAndToggleFilterEvents() {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(context.getFilesDir()).thenReturn(folder.getRoot());
        when(preferences.getString("school_id", "")).thenReturn("ecut");
        when(preferences.getBoolean("widget_exam_enabled", true)).thenReturn(true);
        when(preferences.getInt("widget_exam_lead_days", 0)).thenReturn(14);

        com.ecut.campus.data.Timetable t = new com.ecut.campus.data.Timetable();
        t.semester.id = "2026-2027-1";
        com.ecut.campus.data.Timetable.Course c1 = new com.ecut.campus.data.Timetable.Course();
        c1.key = "k1"; c1.name = "计算机二级";
        com.ecut.campus.data.Timetable.Course c2 = new com.ecut.campus.data.Timetable.Course();
        c2.key = "k2"; c2.name = "大学英语四级";
        t.courses.add(c1); t.courses.add(c2);
        assertTrue(ExamCountdownStore.save(context, "2026-2027-1", "k1", "计算机二级", "2026-11-28"));
        assertTrue(ExamCountdownStore.save(context, "2026-2027-1", "k2", "大学英语四级", "2026-12-20"));

        assertEquals(2, ExamCountdownStore.allCandidates(context, t, "2026-08-31", 20, "2026-11-24").size());
        java.util.List<com.ecut.campus.data.ExamCountdown.Event> within14 =
                ExamCountdownStore.events(context, t, "2026-08-31", 20, "2026-11-24");
        assertEquals(1, within14.size());
        assertEquals("计算机二级", within14.get(0).name);

        assertTrue(ExamCountdownStore.setHiddenIds(context, new java.util.HashSet<>(java.util.Arrays.asList("2026-2027-1|k1"))));
        assertEquals(0, ExamCountdownStore.events(context, t, "2026-08-31", 20, "2026-11-24").size());
    }
}
