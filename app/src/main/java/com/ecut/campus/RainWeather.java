package com.ecut.campus;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ecut.campus.data.RainForecast;
import com.ecut.campus.data.Timetable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 课块下雨提示的定位、Open-Meteo 拉取、3 小时缓存和小组件静态雨效位图。
 *
 * 规则：
 * - 只用系统 LocationManager（NETWORK / PASSIVE），不依赖谷歌服务。
 * - 拒绝定位 / 拿不到位置 / 离线 / 缓存过期：什么都不显示，不弹错误。
 */
public final class RainWeather {

    public static final int REQ_LOCATION = 2009;
    private static final String CACHE_FILE = "rain_forecast.json";

    private static volatile RainForecast memoryCache;
    private static volatile long memoryLoadedAt = -1L;
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);

    private RainWeather() {}

    public static boolean hasPermission(Context ctx) {
        if (ctx == null) return false;
        try {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 开关打开且已授予粗略定位权限。 */
    public static boolean active(Context ctx) {
        return ctx != null && Prefs.rainHint(ctx) && hasPermission(ctx);
    }

    private static File cacheFile(Context ctx) {
        return new File(ctx.getApplicationContext().getFilesDir(), CACHE_FILE);
    }

    /**
     * 读取当前可用的天气预报；未开开关、无定位权限或缓存已过期时一律返回 null。
     */
    @Nullable
    public static RainForecast current(Context ctx) {
        if (!active(ctx)) return null;
        long fetchedAt = Prefs.rainFetchedAt(ctx);
        if (fetchedAt <= 0L) return null;
        RainForecast rf = memoryCache;
        if (rf == null || memoryLoadedAt != fetchedAt) {
            try {
                File f = cacheFile(ctx);
                if (!f.isFile()) return null;
                String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                rf = RainForecast.parse(raw, fetchedAt);
                memoryCache = rf;
                memoryLoadedAt = fetchedAt;
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (rf == null || rf.isExpired(System.currentTimeMillis())) return null;
        return rf;
    }

    /** 查询某天某时段的降雨情况。 */
    public static RainForecast.Hit hitFor(Context ctx, String ymd, int startMin, int endMin) {
        RainForecast rf = current(ctx);
        if (rf == null) return RainForecast.Hit.NONE_HIT;
        return rf.query(ymd, startMin, endMin, System.currentTimeMillis());
    }

    /** 查询某天某时段的雨级（0=不下雨, 1=小雨, 2=中雨, 3=大雨）。 */
    public static int levelFor(Context ctx, String ymd, int startMin, int endMin) {
        return hitFor(ctx, ymd, startMin, endMin).level;
    }

    /** 查询某个课程 Session 在指定日期的降雨情况。 */
    public static RainForecast.Hit querySession(Context ctx, String ymd, Timetable.Session s, ScheduleSettings sched) {
        if (ctx == null || s == null || ymd == null || ymd.isEmpty()) return RainForecast.Hit.NONE_HIT;
        ScheduleSettings sc = sched != null ? sched : ScheduleSettingsStore.load(ctx);
        int startMin;
        int endMin;
        if (s.hasCustomTime()) {
            startMin = Timetable.Session.minutesOf(s.timeStart);
            endMin = Timetable.Session.minutesOf(s.timeEnd);
        } else {
            int maxSec = sc.sectionCount();
            int st = Math.max(1, Math.min(maxSec, s.startSection));
            int en = Math.max(st, Math.min(maxSec, s.endSection));
            startMin = sc.startMinutes(st);
            endMin = sc.endMinutes(en);
        }
        return hitFor(ctx, ymd, startMin, endMin);
    }

    /** 给小组件列表 URI 用的版本戳：开关、权限或天气更新时间变了就刷新列表。 */
    public static long widgetStamp(Context ctx) {
        if (!active(ctx)) return 0L;
        return Prefs.rainFetchedAt(ctx);
    }

    /**
     * 第一次需要时（课表里有课、开关开着、还没问过定位权限）弹一句简短说明再请求定位权限。
     * 用户拒绝或点「暂不」后不再自动打扰。
     */
    public static void maybeAskPermissionOnce(Activity a, boolean hasCourses) {
        if (a == null || a.isFinishing() || !hasCourses) return;
        if (!Prefs.rainHint(a) || hasPermission(a) || Prefs.rainPermAsked(a)) return;
        Prefs.setRainPermAsked(a, true);
        showPermissionReasonDialog(a);
    }

    /** 用户在设置里手动打开「课块下雨提示」且还没给定位权限时调用。 */
    public static void askPermissionFromSetting(Activity a) {
        if (a == null || a.isFinishing() || hasPermission(a)) return;
        Prefs.setRainPermAsked(a, true);
        showPermissionReasonDialog(a);
    }

    private static void showPermissionReasonDialog(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("课块下雨提示")
                .setMessage("需要大致位置（城市级）来看看你上课那几个小时会不会下雨，在课块上泛起雨雾提醒带伞。\n\n拒绝也没关系，随时可以在设置里关掉。")
                .setNegativeButton("暂不", null)
                .setPositiveButton("去开启", (d, w) -> ActivityCompat.requestPermissions(
                        a, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION))
                .show();
    }

    /**
     * 需要时在后台刷新天气（默认约 3 小时一次），成功后刷新桌面小组件并回调 UI。
     */
    public static void refreshIfNeeded(Context ctx, boolean force, @Nullable Runnable onUiUpdated) {
        if (ctx == null || !active(ctx)) return;
        Context app = ctx.getApplicationContext();
        RainForecast cur = current(app);
        long now = System.currentTimeMillis();
        if (!force && cur != null && !cur.needsRefresh(now)) return;
        if (!refreshing.compareAndSet(false, true)) return;

        resolveLocation(app, loc -> {
            if (loc == null) {
                refreshing.set(false);
                return;
            }
            new Thread(() -> {
                try {
                    boolean ok = fetchAndSave(app, loc[0], loc[1]);
                    if (ok) {
                        ClassWidget.updateAll(app);
                        if (onUiUpdated != null) {
                            new Handler(Looper.getMainLooper()).post(onUiUpdated);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    refreshing.set(false);
                }
            }, "rain-weather").start();
        });
    }

    /** 后台 Worker 调用：已满 3 小时且有权限时静默更新一次缓存。 */
    public static void refreshInWorkerIfNeeded(Context ctx) {
        if (ctx == null || !active(ctx)) return;
        Context app = ctx.getApplicationContext();
        RainForecast cur = current(app);
        if (cur != null && !cur.needsRefresh(System.currentTimeMillis())) return;
        double[] loc = lastKnownOrSavedLocation(app);
        if (loc == null) return;
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            fetchAndSave(app, loc[0], loc[1]);
        } catch (Throwable ignored) {
        } finally {
            refreshing.set(false);
        }
    }

    private interface LocationCallback {
        void onLocation(@Nullable double[] latLon);
    }

    @Nullable
    private static double[] lastKnownOrSavedLocation(Context app) {
        if (!hasPermission(app)) return null;
        try {
            LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location best = null;
                String[] providers = {
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER,
                        LocationManager.GPS_PROVIDER
                };
                for (String p : providers) {
                    try {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (best == null || l.getTime() > best.getTime())) {
                            best = l;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (best != null) {
                    double lat = best.getLatitude(), lon = best.getLongitude();
                    Prefs.setRainLocation(app, lat, lon);
                    return new double[]{lat, lon};
                }
            }
        } catch (Throwable ignored) {
        }
        return Prefs.rainLocation(app);
    }

    private static void resolveLocation(Context app, LocationCallback cb) {
        double[] known = lastKnownOrSavedLocation(app);
        if (known != null) {
            cb.onLocation(known);
            return;
        }
        if (!hasPermission(app)) {
            cb.onLocation(null);
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            if (!hasPermission(app)) {
                cb.onLocation(null);
                return;
            }
            LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                cb.onLocation(null);
                return;
            }
            String provider = null;
            try {
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                } else if (lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                    provider = LocationManager.PASSIVE_PROVIDER;
                }
            } catch (Throwable ignored) {
            }
            if (provider == null) {
                cb.onLocation(Prefs.rainLocation(app));
                return;
            }
            final AtomicBoolean done = new AtomicBoolean(false);
            final LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (!done.compareAndSet(false, true)) return;
                    try { lm.removeUpdates(this); } catch (Throwable ignored) {}
                    if (location != null) {
                        double lat = location.getLatitude(), lon = location.getLongitude();
                        Prefs.setRainLocation(app, lat, lon);
                        cb.onLocation(new double[]{lat, lon});
                    } else {
                        cb.onLocation(Prefs.rainLocation(app));
                    }
                }
                @Override public void onStatusChanged(String p, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
                main.postDelayed(() -> {
                    if (!done.compareAndSet(false, true)) return;
                    try { lm.removeUpdates(listener); } catch (Throwable ignored) {}
                    cb.onLocation(Prefs.rainLocation(app));
                }, 8_000L);
            } catch (Throwable e) {
                if (done.compareAndSet(false, true)) {
                    cb.onLocation(Prefs.rainLocation(app));
                }
            }
        });
    }

    private static boolean fetchAndSave(Context app, double lat, double lon) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(RainForecast.url(lat, lon));
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return false;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            String raw = bos.toString("UTF-8");
            long now = System.currentTimeMillis();
            RainForecast parsed = RainForecast.parse(raw, now);
            if (parsed == null) return false;

            File dst = cacheFile(app);
            File tmp = new File(dst.getPath() + ".tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                fo.write(raw.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(dst)) {
                dst.delete();
                if (!tmp.renameTo(dst)) return false;
            }
            Prefs.setRainFetchedAt(app, now);
            memoryCache = parsed;
            memoryLoadedAt = now;
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 生成桌面小组件单条课程的静态雨效背景位图（外圈静态雨蓝光晕 + 左侧课程色竖条 + 顶部淡冷色水汽 + 右侧短雨丝）。
     * 避开左侧课名、中间时间教室和右侧倒计时文字。
     */
    public static Bitmap widgetChipBitmap(Context c, int bgColor, int fgColor, boolean night,
                                          int rainLevel, int chipAlpha, boolean hasRightText, int seed) {
        float screenDensity = c != null ? c.getResources().getDisplayMetrics().density : 1f;
        float d = RainForecast.WIDGET_CHIP_DENSITY;
        int[] wh = RainForecast.widgetChipSizePx(screenDensity);
        int W = wh[0];
        int H = wh[1];
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float glow = RainForecast.glowStrength(rainLevel);
        int rainBlue = night ? RainForecast.RAIN_BLUE_NIGHT : RainForecast.RAIN_BLUE_DAY;
        float pad = 2.6f * d;
        RectF card = new RectF(pad, pad, W - pad, H - pad);
        float radius = 10f * d;

        // 1. 外圈静态雨蓝光晕
        float[] haloSteps = {2.6f, 2.0f, 1.4f, 0.8f};
        int baseGlowAlpha = Math.round(42 * glow * (night ? 0.95f : 0.72f));
        p.setStyle(Paint.Style.FILL);
        for (float st : haloSteps) {
            float ex = st * d;
            p.setColor(G.alpha(rainBlue, baseGlowAlpha));
            cv.drawRoundRect(new RectF(card.left - ex, card.top - ex, card.right + ex, card.bottom + ex),
                    radius + ex, radius + ex, p);
        }

        // 2. 课程条底色
        p.setColor(G.alpha(bgColor, Math.max(0, Math.min(255, chipAlpha))));
        cv.drawRoundRect(card, radius, radius, p);

        // 3. 裁剪进课程条内部：画顶部水汽 + 左侧色条 + 右侧短雨丝
        cv.save();
        Path clip = new Path();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        cv.clipPath(clip);

        int mistAlpha = Math.round((night ? 56 : 46) * glow);
        Paint mistP = new Paint(Paint.ANTI_ALIAS_FLAG);
        mistP.setShader(new LinearGradient(0, card.top, 0, card.top + card.height() * 0.55f,
                new int[]{G.alpha(rainBlue, mistAlpha), 0x00000000}, null, Shader.TileMode.CLAMP));
        cv.drawRect(card.left, card.top, card.right, card.top + card.height() * 0.55f, mistP);

        // 左侧课程色竖条
        p.setColor(G.alpha(fgColor, Math.max(180, chipAlpha)));
        cv.drawRect(card.left, card.top, card.left + 3.2f * d, card.bottom, p);

        // 右侧短雨丝（避开左侧课名时间教室；如果最右边有「进行中 / 倒计时」，雨丝收在中间偏右空白段）
        int n = Math.max(3, RainForecast.streakCount(rainLevel) / 2);
        float ln = RainForecast.streakLenDp(rainLevel);
        int al = RainForecast.streakAlpha(rainLevel);
        if (hasRightText) {
            n = Math.max(2, n - 2);
            al = Math.round(al * 0.78f);
        }
        float xStartRatio = hasRightText ? 0.54f : 0.66f;
        float xSpanRatio = hasRightText ? 0.20f : 0.30f;
        Random rnd = new Random(seed);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(Math.max(1f, 1.0f * d));
        sp.setColor(G.alpha(fgColor, al));
        float cw = card.width(), ch = card.height();
        for (int j = 0; j < n; j++) {
            float sx = card.left + cw * (xStartRatio + xSpanRatio * (j + 0.5f * rnd.nextFloat()) / n);
            float sy = card.top + ch * (0.18f + 0.36f * rnd.nextFloat());
            float Lh = 6.5f * d * (0.7f + ln / 14f);
            cv.drawLine(sx, sy, sx - Lh * 0.34f, sy + Lh, sp);
        }
        cv.restore();

        // 外圈一层细微雨蓝描边让小组件光晕更干净
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(Math.max(1f, 0.9f * d));
        bp.setColor(G.alpha(rainBlue, Math.round((night ? 140 : 105) * glow)));
        cv.drawRoundRect(card, radius, radius, bp);
        return bmp;
    }
}
