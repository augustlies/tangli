package com.ecut.campus.data;

/** In-process invalidation fence for pending WebView results, including A → B → A. */
public final class SyncGeneration {
    private static long generation;
    private SyncGeneration() {}
    public static synchronized long current() { return generation; }
    public static synchronized void invalidate() { generation++; }
    public static synchronized boolean isCurrent(long token) { return token == generation; }
}
