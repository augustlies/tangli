package com.ecut.campus;

import android.content.Context;
import com.ecut.campus.data.AtomicFileStore;
import com.ecut.campus.data.SchoolProfile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** School-owned files. Legacy files remain untouched as an upgrade backup. */
public final class SchoolFiles {
    private SchoolFiles() {}

    public static String owner(SchoolProfile school) {
        return school.isCustom() ? school.id + ":" + school.jwHost().toLowerCase(java.util.Locale.ROOT) : school.id;
    }

    public static synchronized File file(Context ctx, String name) {
        try {
            String owner = Prefs.schoolId(ctx);
            if (SchoolProfile.CUSTOM_ID.equals(owner)) {
                try {
                    SchoolProfile custom = SchoolProfile.fromJson(new org.json.JSONObject(Prefs.customSchoolJson(ctx)));
                    custom.id = SchoolProfile.CUSTOM_ID;
                    owner = owner(custom);
                } catch (org.json.JSONException e) {
                    throw new IOException("Invalid custom school", e);
                }
            }
            File root = ctx.getFilesDir();
            File marker = new File(root, "school_files_owner.json");
            String legacyOwner = AtomicFileStore.read(marker);
            if (legacyOwner == null) {
                AtomicFileStore.write(marker, owner);
                legacyOwner = owner;
            }
            StringBuilder key = new StringBuilder();
            byte[] digest;
            try {
                digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(owner.getBytes(StandardCharsets.UTF_8));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IOException(impossible);
            }
            for (byte b : digest) {
                key.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            }
            File dir = new File(root, "schools/" + key);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create school directory");
            File migrated = new File(dir, "migrated");
            if (!AtomicFileStore.exists(migrated)) {
                if (owner.equals(legacyOwner)) {
                    for (String legacy : new String[] {"timetable.json", "timetable_semesters.json", "grades.json"}) {
                        String text = AtomicFileStore.read(new File(root, legacy));
                        File dest = new File(dir, legacy);
                        if (text != null && !AtomicFileStore.exists(dest)) AtomicFileStore.write(dest, text);
                    }
                }
                AtomicFileStore.write(migrated, "1");
            }
            return new File(dir, name);
        } catch (IOException e) {
            // Fail closed: never fall back to another school's legacy files.
            throw new IllegalStateException("Cannot initialize school storage", e);
        }
    }
}
