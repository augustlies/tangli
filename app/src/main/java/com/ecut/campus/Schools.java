package com.ecut.campus;

import android.content.Context;

import com.ecut.campus.data.SchoolProfile;
import com.ecut.campus.data.TimetableSource;
import com.ecut.campus.data.TimetableSources;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 当前选中的学校。没选过就是东华理工（老用户升级无感）。 */
public final class Schools {

    private static List<SchoolProfile> builtin;

    private Schools() {}

    /** assets/schools.json 里的内置学校；读不到时至少有东华理工。 */
    public static synchronized List<SchoolProfile> builtin(Context ctx) {
        if (builtin != null) return builtin;
        List<SchoolProfile> list = new ArrayList<>();
        try (InputStream in = ctx.getApplicationContext().getAssets().open("schools.json");
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            list.addAll(SchoolProfile.parseList(new String(bos.toByteArray(), StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
        }
        if (SchoolProfile.find(list, SchoolProfile.ECUT_ID) == null) list.add(0, ecutFallback());
        builtin = list;
        return list;
    }

    public static SchoolProfile current(Context ctx) {
        String id = Prefs.schoolId(ctx);
        if (SchoolProfile.CUSTOM_ID.equals(id)) {
            SchoolProfile custom = custom(ctx);
            if (custom != null) return custom;
        }
        SchoolProfile p = SchoolProfile.find(builtin(ctx), id);
        return p != null ? p : SchoolProfile.find(builtin(ctx), SchoolProfile.ECUT_ID);
    }

    public static boolean isEcut(Context ctx) {
        return current(ctx).isEcut();
    }

    /** 用户保存的自定义学校；没有时返回 null。 */
    public static SchoolProfile custom(Context ctx) {
        String json = Prefs.customSchoolJson(ctx);
        if (json == null || json.isEmpty()) return null;
        try {
            SchoolProfile p = SchoolProfile.fromJson(new JSONObject(json));
            p.id = SchoolProfile.CUSTOM_ID;
            return p.jwBase.isEmpty() ? null : p;
        } catch (Exception e) {
            return null;
        }
    }

    /** 切换学校：清掉上一所学校的登录状态，避免拿旧会话去同步。 */
    public static void select(Context ctx, SchoolProfile p) {
        if (p == null) return;
        SchoolProfile old = current(ctx);
        String before = Prefs.schoolId(ctx);
        if (p.isCustom()) Prefs.setCustomSchoolJson(ctx, p.toJson().toString());
        Prefs.setSchoolId(ctx, p.id);
        if (!p.id.equals(before) || p.isCustom()) {
            Prefs.setPortalSession(ctx, false);
            Prefs.setSessionExpired(ctx, false);
        }
        if (!SchoolProfile.sameSchool(old, p)) {
            // 上一所学校的成绩不能挂在新学校名下，也不能当作「旧成绩」去比对新学校，
            // 否则第一次同步会把新学校的全部成绩当成新出成绩推送。成绩可从教务重新同步。
            com.ecut.campus.data.GradesStore.clear(ctx);
            Prefs.setNewGradeKeys(ctx, new java.util.ArrayList<>());
            Prefs.setGradesDemo(ctx, false);
        }
        DiagLog.log(ctx, "school", "切换学校 " + p.diagSummary());
    }

    public static TimetableSource source(SchoolProfile p) {
        return p == null ? null : TimetableSources.forVendor(p.vendor);
    }

    /** 教务登录入口的称呼：东华理工是统一门户，其他学校就叫教务系统。 */
    public static String loginLabel(Context ctx) {
        return isEcut(ctx) ? "统一门户" : "教务系统";
    }

    private static SchoolProfile ecutFallback() {
        SchoolProfile p = new SchoolProfile();
        p.id = SchoolProfile.ECUT_ID;
        p.name = "东华理工大学";
        p.vendor = SchoolProfile.Vendor.ZHENGFANG;
        p.jwBase = "https://" + CampusSyncClient.DEFAULT_JW_HOST + "/jwglxt/";
        p.access = SchoolProfile.Access.ATRUST;
        p.loginUrl = "https://atrust1.ecut.edu.cn/portal/#/app_center";
        p.hosts.add("*.ecut.edu.cn");
        p.hosts.add("172.20.130.13");
        p.verified = true;
        p.ecutFeatures = true;
        return p;
    }
}
