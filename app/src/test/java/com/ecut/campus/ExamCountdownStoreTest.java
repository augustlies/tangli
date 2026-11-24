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
}
