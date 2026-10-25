package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SemesterArchiveSortTest {

    private static SemesterArchive.Entry entry(String id, int sortOrder) {
        SemesterArchive.Entry e = new SemesterArchive.Entry();
        e.id = id;
        e.sortOrder = sortOrder;
        return e;
    }

    @Test
    public void noOrderFallsBackToIdDescending() {
        List<SemesterArchive.Entry> list = new ArrayList<>(Arrays.asList(
                entry("2024-2025-1", 0), entry("2026-2027-1", 0), entry("2025-2026-1", 0)));
        SemesterArchive.sortEntries(list);
        assertEquals("2026-2027-1", list.get(0).id);
        assertEquals("2025-2026-1", list.get(1).id);
        assertEquals("2024-2025-1", list.get(2).id);
    }

    @Test
    public void orderedEntriesComeFirstInOrder() {
        List<SemesterArchive.Entry> list = new ArrayList<>(Arrays.asList(
                entry("2024-2025-1", 2), entry("2026-2027-1", 1), entry("2025-2026-1", 0)));
        SemesterArchive.sortEntries(list);
        assertEquals("2026-2027-1", list.get(0).id);
        assertEquals("2024-2025-1", list.get(1).id);
        assertEquals("2025-2026-1", list.get(2).id);
    }

    @Test
    public void unorderedNewSemesterGoesLast() {
        List<SemesterArchive.Entry> list = new ArrayList<>(Arrays.asList(
                entry("2026-2027-2", 0), entry("2026-2027-1", 1), entry("2025-2026-1", 2)));
        SemesterArchive.sortEntries(list);
        assertEquals("2026-2027-1", list.get(0).id);
        assertEquals("2025-2026-1", list.get(1).id);
        assertEquals("2026-2027-2", list.get(2).id);
    }
}
