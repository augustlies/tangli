package com.ecut.campus;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 教务按钮目标配置。
 *
 * 设计目的：学校改版 / 直达路径填错时，**无需重新编译**，App 内直接改。
 * 存储：SharedPreferences 里一条 JSON 数组，首次启动从 assets/default-targets.json 初始化。
 *
 * 每条配置：
 *   key     唯一标识（误删可恢复，改动不影响识别）
 *   title   按钮文字
 *   path    相对教务根的路径，或完整 http(s) 地址
 *   entry   入口页（建立会话用），为空则用默认
 *   builtin 内置项不可删除（可改路径）
 */
public final class TargetStore {

    private static final String TAG = "TargetStore";
    private static final String ASSET = "default-targets.json";
    private static final String KEY_CONFIG = "targets_json";

    public static final class Target {
        public String key;
        public String title;
        public String path;
        public String entry;
        public boolean builtin;

        public Target(String key, String title, String path, String entry, boolean builtin) {
            this.key = key;
            this.title = title;
            this.path = path;
            this.entry = entry;
            this.builtin = builtin;
        }

        public boolean isAbsolute() {
            return path != null && (path.startsWith("http://") || path.startsWith("https://"));
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("key", key);
                o.put("title", title);
                o.put("path", path);
                o.put("entry", entry == null ? "" : entry);
                o.put("builtin", builtin);
            } catch (Exception ignored) {}
            return o;
        }

        public static Target fromJson(JSONObject o) {
            return new Target(
                    o.optString("key", ""),
                    o.optString("title", ""),
                    o.optString("path", ""),
                    o.optString("entry", ""),
                    o.optBoolean("builtin", false));
        }
    }

    private TargetStore() {}

    /** 读取当前生效配置；首次或解析失败时回落到 assets 默认值 */
    public static List<Target> load(Context ctx) {
        String json = Prefs.targetsJson(ctx);
        if (json != null) {
            List<Target> parsed = parse(json);
            if (!parsed.isEmpty()) return parsed;
        }
        List<Target> defaults = loadDefaults(ctx);
        save(ctx, defaults);
        return defaults;
    }

    public static void save(Context ctx, List<Target> list) {
        JSONArray arr = new JSONArray();
        for (Target t : list) arr.put(t.toJson());
        Prefs.setTargetsJson(ctx, arr.toString());
    }

    /** 恢复默认 */
    public static List<Target> resetToDefaults(Context ctx) {
        List<Target> defaults = loadDefaults(ctx);
        save(ctx, defaults);
        return defaults;
    }

    private static List<Target> parse(String json) {
        List<Target> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Target t = Target.fromJson(arr.getJSONObject(i));
                if (t.key != null && !t.key.isEmpty()) out.add(t);
            }
        } catch (Exception e) {
            Log.w(TAG, "parse targets failed", e);
        }
        return out;
    }

    private static List<Target> loadDefaults(Context ctx) {
        List<Target> out = new ArrayList<>();
        try (InputStream is = ctx.getAssets().open(ASSET)) {
            byte[] buf = new byte[is.available()];
            int off = 0;
            while (off < buf.length) {
                int n = is.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            out = parse(new String(buf, 0, off, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "load default targets failed", e);
        }
        if (out.isEmpty()) {
            // 兜底，理论上不会走到
            out.add(new Target("portal",
                    "应用中心", "https://atrust1.ecut.edu.cn/portal/#/app_center", "", true));
        }
        return out;
    }
}
