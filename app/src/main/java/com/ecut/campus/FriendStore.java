package com.ecut.campus;

import android.content.Context;
import android.content.SharedPreferences;

import com.ecut.campus.data.TimetableShare;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 同学课表：只存对方的分享码（课程名/地点/时间），不存任何账号信息。 */
public final class FriendStore {
    private FriendStore() {}

    public static final class Friend {
        public String id;
        public String name;
        public String code;
        public long addedAt;
        public TimetableShare.Shared shared;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("friends", Context.MODE_PRIVATE);
    }

    public static List<Friend> load(Context c) {
        List<Friend> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Friend f = new Friend();
                f.id = o.optString("id");
                f.name = o.optString("name");
                f.code = o.optString("code");
                f.addedAt = o.optLong("at");
                f.shared = TimetableShare.decode(f.code);
                if (f.shared != null) out.add(f);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Friend> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Friend f : list) {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                o.put("code", f.code);
                o.put("at", f.addedAt);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString("list", arr.toString()).apply();
    }

    /** 添加或按名字覆盖（同一个同学再发一次新课表就更新）。返回添加后的对象，解析失败返回 null。 */
    public static Friend add(Context c, String text, String nameOverride) {
        TimetableShare.Shared sh = TimetableShare.decode(text);
        if (sh == null) return null;
        int i = text.indexOf(TimetableShare.PREFIX);
        int k = i + TimetableShare.PREFIX.length();
        while (k < text.length() && isB64(text.charAt(k))) k++;
        String code = text.substring(i, k);
        String name = nameOverride != null && !nameOverride.trim().isEmpty() ? nameOverride.trim()
                : (sh.owner.isEmpty() ? "同学" : sh.owner);
        List<Friend> list = load(c);
        Friend target = null;
        for (Friend f : list) if (f.name.equals(name)) target = f;
        if (target == null) {
            target = new Friend();
            target.id = "f" + System.currentTimeMillis();
            list.add(target);
        }
        target.name = name;
        target.code = code;
        target.addedAt = System.currentTimeMillis();
        target.shared = sh;
        save(c, list);
        return target;
    }

    public static void remove(Context c, String id) {
        List<Friend> list = load(c);
        list.removeIf(f -> f.id.equals(id));
        save(c, list);
    }

    public static void rename(Context c, String id, String name) {
        List<Friend> list = load(c);
        for (Friend f : list) if (f.id.equals(id)) f.name = name;
        save(c, list);
    }

    private static boolean isB64(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                || ch == '-' || ch == '_';
    }
}
