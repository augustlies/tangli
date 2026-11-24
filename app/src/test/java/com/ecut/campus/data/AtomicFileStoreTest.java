package com.ecut.campus.data;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class AtomicFileStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File file() { return new File(folder.getRoot(), "data.json"); }
    private File sibling(String suffix) { return new File(file().getPath() + suffix); }
    private void put(File f, String text) throws IOException {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void createsAndReplacesWithoutLeavingRecoveryFiles() throws Exception {
        assertNull(AtomicFileStore.read(file()));
        AtomicFileStore.write(file(), "旧数据");
        AtomicFileStore.write(file(), "新数据");
        assertEquals("新数据", AtomicFileStore.read(file()));
        assertFalse(sibling(".tmp").exists());
        assertFalse(sibling(".bak").exists());
    }

    @Test public void interruptedBeforeBackupKeepsCommittedFile() throws Exception {
        put(file(), "committed");
        put(sibling(".tmp"), "partial");
        assertEquals("committed", AtomicFileStore.read(file()));
        AtomicFileStore.write(file(), "next");
        assertEquals("next", AtomicFileStore.read(file()));
    }

    @Test public void orphanTempIsNeverReadAsCommittedData() throws Exception {
        put(sibling(".tmp"), "uncommitted first write");
        assertNull(AtomicFileStore.read(file()));
        assertFalse(AtomicFileStore.exists(file()));
    }

    @Test public void interruptedAfterBackupRestoresMissingDestination() throws Exception {
        put(sibling(".bak"), "committed");
        put(sibling(".tmp"), "replacement");
        assertTrue(AtomicFileStore.exists(file()));
        assertEquals("committed", AtomicFileStore.read(file()));
        assertFalse(sibling(".bak").exists());
    }

    @Test public void interruptedBeforeCommitRollsBackUncommittedDestination() throws Exception {
        put(sibling(".bak"), "committed");
        put(file(), "uncommitted");
        assertEquals("committed", AtomicFileStore.read(file()));
        AtomicFileStore.write(file(), "next");
        assertEquals("next", AtomicFileStore.read(file()));
    }

    @Test public void failedTempWritePreservesDestination() throws Exception {
        put(file(), "committed");
        assertTrue(sibling(".tmp").mkdir());
        assertThrows(IOException.class, () -> AtomicFileStore.write(file(), "next"));
        assertEquals("committed", AtomicFileStore.read(file()));
    }

    @Test public void failedBackupNeverDeletesDestination() throws Exception {
        put(file(), "committed");
        assertTrue(sibling(".bak").mkdir());
        assertThrows(IOException.class, () -> AtomicFileStore.write(file(), "next"));
        assertEquals("committed", new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8));
    }

    @Test public void failedRecoveryRetainsBackupForRetry() throws Exception {
        put(sibling(".bak"), "committed");
        assertTrue(file().mkdir());
        put(new File(file(), "blocker"), "blocker");
        assertThrows(IOException.class, () -> AtomicFileStore.read(file()));
        assertEquals("committed", new String(Files.readAllBytes(sibling(".bak").toPath()), StandardCharsets.UTF_8));
        Files.delete(new File(file(), "blocker").toPath());
        Files.delete(file().toPath());
        assertEquals("committed", AtomicFileStore.read(file()));
    }

    @Test public void failedReplacementRollsBackBackup() throws Exception {
        put(file(), "committed");
        File destination = new File(file().getPath()) {
            @Override public boolean renameTo(File backup) {
                boolean moved = super.renameTo(backup);
                // Fault injection: lose the staged file after the backup step.
                assertTrue(sibling(".tmp").delete());
                return moved;
            }
        };
        assertThrows(IOException.class, () -> AtomicFileStore.write(destination, "next"));
        assertEquals("committed", AtomicFileStore.read(file()));
        assertFalse(sibling(".bak").exists());
    }

    @Test public void clearRemovesAllRecoveryState() throws Exception {
        put(file(), "uncommitted");
        put(sibling(".bak"), "committed");
        put(sibling(".tmp"), "partial");
        AtomicFileStore.clear(file());
        assertFalse(AtomicFileStore.exists(file()));
        assertFalse(sibling(".tmp").exists());
        assertNull(AtomicFileStore.read(file()));
    }
}
