package com.ecut.campus;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 作息配置原子读写（filesDir/schedule_settings.json）。 */
public final class ScheduleSettingsStore {

    private static final String TAG = "ScheduleSettingsStore";
    private static final String FILE_NAME = "schedule_settings.json";
    private static final String TMP_NAME = "schedule_settings.json.tmp";

    private ScheduleSettingsStore() {}

    public static ScheduleSettings load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return ScheduleSettings.createDefault();
        try {
            String json = readAll(new FileInputStream(f));
            return ScheduleSettings.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "作息配置损坏，回落默认值", e);
            return ScheduleSettings.createDefault();
        }
    }

    public static boolean save(Context ctx, ScheduleSettings settings) {
        File dir = ctx.getFilesDir();
        File tmp = new File(dir, TMP_NAME);
        File dst = new File(dir, FILE_NAME);
        try {
            String json = settings.toJson().toString();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.getFD().sync();
            }
            if (dst.exists() && !dst.delete()) {
                Log.w(TAG, "旧作息配置删除失败，继续覆盖");
            }
            if (!tmp.renameTo(dst)) {
                Log.e(TAG, "作息配置 rename 失败");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存作息配置失败", e);
            if (tmp.exists()) tmp.delete();
            return false;
        }
    }

    public static void clear(Context ctx) {
        File dir = ctx.getFilesDir();
        new File(dir, FILE_NAME).delete();
        new File(dir, TMP_NAME).delete();
    }

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
