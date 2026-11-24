package com.ecut.campus;

import android.content.Context;
import android.util.Log;

import com.ecut.campus.data.AtomicFileStore;

import java.io.File;

/** 作息配置原子读写（filesDir/schedule_settings.json）。 */
public final class ScheduleSettingsStore {

    private static final String TAG = "ScheduleSettingsStore";
    private static final String FILE_NAME = "schedule_settings.json";

    private ScheduleSettingsStore() {}

    public static ScheduleSettings load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        try {
            String json = AtomicFileStore.read(f);
            return json == null ? ScheduleSettings.createDefault() : ScheduleSettings.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "作息配置损坏，回落默认值", e);
            return ScheduleSettings.createDefault();
        }
    }

    public static boolean save(Context ctx, ScheduleSettings settings) {
        try {
            AtomicFileStore.write(new File(ctx.getFilesDir(), FILE_NAME), settings.toJson().toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存作息配置失败", e);
            return false;
        }
    }

    public static void clear(Context ctx) {
        try {
            AtomicFileStore.clear(new File(ctx.getFilesDir(), FILE_NAME));
        } catch (Exception e) {
            Log.e(TAG, "清理本地文件失败", e);
        }
    }

}
