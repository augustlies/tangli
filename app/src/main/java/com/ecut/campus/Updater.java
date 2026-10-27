package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.ecut.campus.data.VersionCompare;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 从 GitHub Releases 检查新版本。只读公开接口，不需要任何账号。 */
public final class Updater {
    private Updater() {}

    public static final String REPO = "augustlies/tangli";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String RELEASES_URL = REPO_URL + "/releases";
    public static final String ISSUES_URL = REPO_URL + "/issues/new";
    public static final String FEEDBACK_EMAIL = "sueexin@163.com";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long AUTO_INTERVAL = 6L * 60 * 60 * 1000;

    public static final class Release {
        public String tag, name, notes, pageUrl, apkUrl;
        /** GitHub 资产自带的 SHA-256（"digest": "sha256:…"），没有时为空 */
        public String sha256 = "";
        public long size;
    }

    public interface Callback {
        /** release 为 null 表示网络或接口失败。 */
        void done(Release release);
    }

    public static void fetchLatest(Callback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Release r = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "tangli-android");
                if (c.getResponseCode() == 200) {
                    try (InputStream is = c.getInputStream()) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                        JSONObject o = new JSONObject(bo.toString(StandardCharsets.UTF_8.name()));
                        r = new Release();
                        r.tag = o.optString("tag_name", "");
                        r.name = o.optString("name", r.tag);
                        r.notes = o.optString("body", "");
                        r.pageUrl = o.optString("html_url", RELEASES_URL);
                        JSONArray assets = o.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject a = assets.getJSONObject(i);
                                if (a.optString("name").endsWith(".apk")) {
                                    r.apkUrl = a.optString("browser_download_url");
                                    r.size = a.optLong("size", 0);
                                    String dg = a.optString("digest", "");
                                    if (dg.startsWith("sha256:")) r.sha256 = dg.substring(7).toLowerCase(java.util.Locale.US);
                                    break;
                                }
                            }
                        }
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
                r = null;
            }
            final Release fr = r;
            main.post(() -> cb.done(fr));
        }, "update-check").start();
    }

    public static boolean isNewer(Release r) {
        return r != null && VersionCompare.isNewer(r.tag, BuildConfig.VERSION_NAME);
    }

    /** 打开 App 时静默检查，6 小时最多一次；勾了「不再提示」的版本不再弹。 */
    public static void autoCheck(Activity a) {
        try { UpdateInstaller.cleanup(a); } catch (Exception ignored) {}
        if (!Prefs.updateAutoCheck(a)) return;
        long now = System.currentTimeMillis();
        if (now - Prefs.updateCheckedAt(a) < AUTO_INTERVAL) return;
        Prefs.setUpdateCheckedAt(a, now);
        fetchLatest(r -> {
            if (a.isFinishing() || !isNewer(r)) return;
            if (r.tag.equals(Prefs.updateSkippedTag(a))) return;
            showDialog(a, r, true);
        });
    }

    public static void showDialog(Activity a, Release r, boolean allowSkip) {
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 600) notes = notes.substring(0, 600) + "…";
        float dp = a.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Math.round(24 * dp), Math.round(8 * dp), Math.round(24 * dp), 0);
        android.widget.TextView msg = new android.widget.TextView(a);
        msg.setText((notes.isEmpty() ? "" : notes + "\n\n") + "当前版本 v" + BuildConfig.VERSION_NAME);
        msg.setTextSize(14f);
        msg.setTextColor(G.SUB);
        msg.setLineSpacing(0f, 1.15f);
        box.addView(msg);
        android.widget.CheckBox never = new android.widget.CheckBox(a);
        never.setText("这个版本不再提示");
        never.setTextSize(13f);
        never.setTextColor(G.SUB);
        if (allowSkip) {
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * dp);
            lp.leftMargin = Math.round(-6 * dp);
            box.addView(never, lp);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        new MaterialAlertDialogBuilder(a)
                .setTitle("发现新版本 " + r.tag)
                .setView(sv)
                .setPositiveButton("更新", (d, w) -> {
                    if (r.apkUrl != null && !r.apkUrl.isEmpty()) UpdateInstaller.start(a, r);
                    else open(a, r.pageUrl);
                })
                .setNegativeButton("关闭", (d, w) -> {
                    if (allowSkip && never.isChecked()) Prefs.setUpdateSkippedTag(a, r.tag);
                })
                .setOnCancelListener(d -> {
                    if (allowSkip && never.isChecked()) Prefs.setUpdateSkippedTag(a, r.tag);
                })
                .show();
    }

    public static void open(Context c, String url) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "没有可以打开链接的应用", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
