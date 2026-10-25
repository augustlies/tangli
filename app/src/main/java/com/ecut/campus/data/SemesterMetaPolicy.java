package com.ecut.campus.data;

/**
 * Semester metadata ownership rules.
 *
 * The archive is the only writable source of truth. timetable.json and Prefs
 * are mirrors for legacy readers. Pure Java so JVM tests can verify migration
 * and precedence without Android.
 */
public final class SemesterMetaPolicy {

    public static final int MIN_WEEKS = 1;
    /** Keep the archive policy aligned with the existing settings/UI limit. */
    public static final int MAX_WEEKS = 30;
    public static final int DEFAULT_WEEKS = 20;

    private SemesterMetaPolicy() {}

    public static int clampWeeks(int weeks) {
        return Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));
    }

    /**
     * Backfill only a missing archive start date from a legacy timetable.json
     * mirror. An existing archive date always wins, even if the mirror is newer,
     * because background writes must not revive stale dates.
     */
    public static boolean shouldSeedStartDate(String archiveSemesterId,
                                              String mirrorSemesterId,
                                              String archiveStartDate,
                                              String mirrorStartDate) {
        if (!sameSemester(archiveSemesterId, mirrorSemesterId)) return false;
        boolean hasArchiveStart = archiveStartDate != null && !archiveStartDate.isEmpty();
        boolean hasMirrorStart = mirrorStartDate != null && !mirrorStartDate.isEmpty();
        return !hasArchiveStart && hasMirrorStart;
    }

    /**
     * Total weeks are an archive value too. A legacy mirror may fill the field
     * only when the archive JSON did not contain that field at all; a present
     * value (including the default 20) must not be replaced by the mirror.
     */
    public static boolean shouldSeedTotalWeeks(String archiveSemesterId,
                                               String mirrorSemesterId,
                                               boolean archiveHasTotalWeeks,
                                               int mirrorTotalWeeks) {
        if (!sameSemester(archiveSemesterId, mirrorSemesterId)) return false;
        if (archiveHasTotalWeeks) return false;
        return mirrorTotalWeeks >= MIN_WEEKS && mirrorTotalWeeks <= MAX_WEEKS;
    }

    private static boolean sameSemester(String archiveSemesterId, String mirrorSemesterId) {
        return archiveSemesterId != null
                && !archiveSemesterId.isEmpty()
                && archiveSemesterId.equals(mirrorSemesterId);
    }

    /**
     * Archive wins. A missing archive date is preserved as missing rather than
     * copied from the mirror; explicit user saves update the archive first.
     */
    public static String resolveStartDate(String archiveStartDate) {
        return archiveStartDate == null ? "" : archiveStartDate;
    }

    public static int resolveTotalWeeks(int archiveTotalWeeks) {
        return clampWeeks(archiveTotalWeeks);
    }
}
