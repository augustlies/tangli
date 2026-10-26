package com.ecut.campus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import com.ecut.campus.data.SchoolBadge;
import com.ecut.campus.data.SchoolProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 识别到学校后，从学校官网抓校徽小图标存到本地，设置页「00」位置显示原色校徽。
 * 抓不到就保持「00」，失败的域名一天内不再重试。安装包里不带任何校徽。
 */
public final class SchoolBadgeLoader {

    private SchoolBadgeLoader() {}

    public interface Callback {
        void onBadge(Bitmap bitmap);
    }

    private static final String PREFS = "school_badge";
    private static final long RETRY_MS = 24L * 3600 * 1000;
    private static final int MIN_PX = 16;
    private static final Set<String> running = new HashSet<>();

    /** 已缓存的校徽；没有返回 null。 */
    public static Bitmap cached(Context ctx, SchoolProfile school) {
        String domain = SchoolBadge.schoolDomain(school);
        if (domain.isEmpty()) return null;
        File f = file(ctx, domain);
        if (!f.isFile()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    /** 没缓存时在后台抓一次，成功后回到主线程回调。 */
    public static void ensure(Context ctx, SchoolProfile school, Callback cb) {
        Context app = ctx.getApplicationContext();
        String domain = SchoolBadge.schoolDomain(school);
        if (domain.isEmpty() || file(app, domain).isFile()) return;
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - sp.getLong("fail_" + domain, 0L) < RETRY_MS) return;
        synchronized (running) {
            if (!running.add(domain)) return;
        }
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                bmp = fetch(domain);
                if (bmp != null) save(app, domain, bmp);
            } catch (Throwable t) {
                bmp = null;
            } finally {
                synchronized (running) {
                    running.remove(domain);
                }
            }
            if (bmp == null) {
                sp.edit().putLong("fail_" + domain, System.currentTimeMillis()).apply();
                DiagLog.log(app, "badge", "校徽未取到 " + domain);
                return;
            }
            DiagLog.log(app, "badge", "校徽已缓存 " + domain + " " + bmp.getWidth() + "px");
            Bitmap done = bmp;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (cb != null) cb.onBadge(done);
            });
        }, "school-badge").start();
    }

    private static Bitmap fetch(String domain) {
        for (String home : SchoolBadge.homepageCandidates(domain)) {
            String[] page = getText(home);
            if (page == null) continue;
            List<String> icons = SchoolBadge.iconCandidates(page[1], page[0]);
            for (String icon : icons) {
                byte[] bytes = getBytes(icon, 1024 * 1024);
                if (bytes == null) continue;
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (b != null && b.getWidth() >= MIN_PX && b.getHeight() >= MIN_PX) return b;
            }
            return null; // 首页打开了但没有能用的图标，换协议也一样
        }
        return null;
    }

    /** 返回 {最终地址, html}；跟随最多 5 次跳转（含 http↔https）。 */
    private static String[] getText(String url) {
        String cur = url;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection c = null;
            try {
                c = open(cur);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) return null;
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) return null;
                String finalUrl = c.getURL().toString();
                byte[] b = read(c.getInputStream(), 512 * 1024);
                return new String[]{finalUrl, new String(b, StandardCharsets.UTF_8)};
            } catch (Exception e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static byte[] getBytes(String url, int limit) {
        HttpURLConnection c = null;
        try {
            c = open(url);
            if (c.getResponseCode() != 200) return null;
            return read(c.getInputStream(), limit);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        return c;
    }

    private static byte[] read(InputStream in, int limit) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > limit) break;
            }
            return out.toByteArray();
        }
    }

    private static void save(Context ctx, String domain, Bitmap b) throws Exception {
        File f = file(ctx, domain);
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    private static File file(Context ctx, String domain) {
        return new File(new File(ctx.getFilesDir(), "school_badge"), domain.replaceAll("[^a-z0-9.-]", "_") + ".png");
    }
}
