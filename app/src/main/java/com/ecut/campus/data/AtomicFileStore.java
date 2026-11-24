package com.ecut.campus.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** API 24 compatible, recoverable file replacement. No Android dependencies.
 * A backup means a replacement did not commit; reads restore the previous file.
 * All access (including clear) must go through this class, serialized in-process.
 */
public final class AtomicFileStore {
    private AtomicFileStore() {}

    public static synchronized boolean exists(File file) {
        return file.exists() || sibling(file, ".bak").exists();
    }

    public static synchronized String read(File file) throws IOException {
        recover(file);
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    public static synchronized void write(File file, String text) throws IOException {
        recover(file);
        File tmp = sibling(file, ".tmp");
        File backup = sibling(file, ".bak");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
            if (file.exists() && (!file.isFile() || !file.renameTo(backup))) {
                throw new IOException("Cannot back up " + file.getName());
            }
            if (!tmp.renameTo(file)) throw new IOException("Cannot replace " + file.getName());
            // Removing the backup commits the new file. Until then recovery rolls back.
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Cannot commit " + file.getName());
            }
        } catch (IOException e) {
            try { recover(file); } catch (IOException recovery) { e.addSuppressed(recovery); }
            throw e;
        } finally {
            if (tmp.isFile()) tmp.delete();
        }
    }

    public static synchronized void clear(File file) throws IOException {
        // Do not leave a backup that could resurrect intentionally cleared data.
        for (File target : new File[] { sibling(file, ".bak"), sibling(file, ".tmp"), file }) {
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot clear " + target.getName());
            }
        }
    }

    private static void recover(File file) throws IOException {
        File backup = sibling(file, ".bak");
        if (backup.exists() && (!backup.isFile() || !backup.renameTo(file))) {
            // Android's same-directory rename atomically replaces the uncommitted file.
            // Never delete the destination to make a failed rename succeed.
            throw new IOException("Cannot recover " + file.getName());
        }
    }

    private static File sibling(File file, String suffix) {
        return new File(file.getPath() + suffix);
    }
}
