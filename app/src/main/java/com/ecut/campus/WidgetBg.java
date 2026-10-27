package com.ecut.campus;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import com.ecut.campus.data.WidgetLook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 小组件「今日课程」的自选背景图：选图（系统照片选择器 / 文件选择器，不要存储权限）→ 按 EXIF 摆正、
 * 长边 ≤ 2048 存进 App 私有目录；渲染时按每个小组件的真实像素从存图居中裁切（采样解码 + 高质量缩放）、盖遮罩、
 * 最后在成品上切圆角，交给 RemoteViews。照片全程不模糊；字的清楚靠遮罩和半透明课程条。深字浅字的判断在 data/WidgetLook。
 */
final class WidgetBg {

    static final int REQ_PICK = 2003;
    private static final String FILE = "widget_bg.jpg";

    /** 遮罩色 = 小组件卡片底色（日间纸色 / 夜间墨色），和默认样子同一家族。 */
    static final int MASK_DAY = 0xF8F7F3, MASK_NIGHT = 0x1C1C1A;

    private WidgetBg() {}

    static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), FILE);
    }

    /** 设置里选了图片、文件也还在。 */
    static boolean active(Context c) {
        return Prefs.widgetBgImage(c) && file(c).isFile();
    }

    /** 打开系统照片选择器（Android 13+），没有就用文件选择器，都不需要存储权限。 */
    static void pick(Activity a) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                a.startActivityForResult(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), REQ_PICK);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        try {
            a.startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(a, "没找到能选图片的应用", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 把选中的图复制进来：按 EXIF 摆正（含镜像），长边缩到 ≤ 2048，存 JPEG。成功返回 true（在后台线程调）。 */
    static boolean importFrom(Context c, Uri uri) {
        if (uri == null) return false;
        Context app = c.getApplicationContext();
        Bitmap bmp = null, out = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int sample = WidgetLook.sampleSize(bounds.outWidth, bounds.outHeight, WidgetLook.STORE_LONG_SIDE);
            for (int attempt = 0; attempt < 3 && bmp == null; attempt++, sample *= 2) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                    if (in == null) return false;
                    bmp = BitmapFactory.decodeStream(in, null, o);
                } catch (OutOfMemoryError oom) {
                    bmp = null; // 超大图：采样再翻倍试
                }
            }
            if (bmp == null) return false;
            int ori = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in != null) ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) {
            }
            int[] fit = WidgetLook.fitLongSide(bmp.getWidth(), bmp.getHeight(), WidgetLook.STORE_LONG_SIDE);
            Matrix mx = new Matrix();
            mx.postScale(fit[0] / (float) bmp.getWidth(), fit[1] / (float) bmp.getHeight());
            exifMatrix(mx, ori);
            out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
            File dst = file(app);
            File tmp = new File(dst.getPath() + ".tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                if (!out.compress(Bitmap.CompressFormat.JPEG, 92, fo)) return false;
            }
            if (!tmp.renameTo(dst)) {
                dst.delete();
                if (!tmp.renameTo(dst)) return false;
            }
            Prefs.setWidgetBgImage(app, true);
            return true;
        } catch (Throwable e) { // 包括超大图 OOM：不让设置页崩
            DiagLog.error(app, "widget", "bg import failed", e);
            return false;
        } finally {
            if (out != null && out != bmp) out.recycle();
            if (bmp != null) bmp.recycle();
        }
    }

    /** EXIF 方向 → 旋转 / 镜像（接在缩放后面）。 */
    static void exifMatrix(Matrix mx, int ori) {
        switch (ori) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: mx.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: mx.postRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: mx.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: mx.postRotate(90); mx.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: mx.postRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: mx.postRotate(-90); mx.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: mx.postRotate(270); break;
            default: break;
        }
    }

    /** 恢复默认：删图；resetMask 时遮罩也回到 35%。 */
    static void clear(Context c, boolean resetMask) {
        file(c).delete();
        Prefs.setWidgetBgImage(c, false);
        if (resetMask) Prefs.setWidgetMask(c, WidgetLook.MASK_DEFAULT);
    }

    /** 存图的宽高（不解码像素）；读不到返回 null。 */
    static int[] sourceSize(Context c) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file(c).getPath(), o);
        return o.outWidth > 0 && o.outHeight > 0 ? new int[]{o.outWidth, o.outHeight} : null;
    }

    /** 给 W×H 的小组件解码存图：采样只降到裁出的那块仍 ≥ W×H。用完由调用方 recycle，读不到返回 null。 */
    static Bitmap loadSource(Context c, int W, int H) {
        try {
            int[] sz = sourceSize(c);
            if (sz == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = WidgetLook.cropSampleSize(sz[0], sz[1], W, H);
            return BitmapFactory.decodeFile(file(c).getPath(), o);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 标题区域（上 ~1/3、左 ~7/8）的平均灰度，按小组件比例 w:h 裁切后取。 */
    static double headerGray(Bitmap src, int w, int h) {
        return headerGray(src, w, h, BAND_WIDE_W, BAND_WIDE_H);
    }

    /** 标题字集中在上 1/3（拉成 2×2 也一样）。 */
    static final float BAND_WIDE_W = 0.88f, BAND_WIDE_H = 0.34f;

    /** 文字区域（从左上角起 bandW × bandH）的平均灰度，按小组件比例 w:h 裁切后取。 */
    static double headerGray(Bitmap src, int w, int h, float bandW, float bandH) {
        int[] r = WidgetLook.centerCrop(src.getWidth(), src.getHeight(), w, h);
        int bw = Math.max(1, Math.round(r[2] * bandW)), bh = Math.max(1, Math.round(r[3] * bandH));
        Bitmap band = null, small = null;
        try {
            band = Bitmap.createBitmap(src, r[0], r[1], bw, bh);
            small = Bitmap.createScaledBitmap(band, 32, 10, true);
            int[] px = new int[32 * 10];
            small.getPixels(px, 0, 32, 0, 0, 32, 10);
            return WidgetLook.meanGray(px);
        } catch (Throwable e) {
            return 0.5;
        } finally {
            if (small != null && small != band && small != src) small.recycle();
            if (band != null && band != src) band.recycle();
        }
    }

    /** 设置页用：按默认 4×2 比例估一下标题区灰度。没图返回 -1。 */
    static double headerGray(Context c) {
        if (!active(c)) return -1;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 4;
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeFile(file(c).getPath(), o);
            return b == null ? -1 : headerGray(b, 250, 165);
        } catch (Throwable e) {
            return -1;
        } finally {
            if (b != null) b.recycle();
        }
    }

    static int maskColor(boolean night) {
        return night ? MASK_NIGHT : MASK_DAY;
    }

    /** 一个小组件的背景位图 + 标题该用深字还是浅字。 */
    static final class Look {
        Bitmap bitmap;
        boolean darkText;
        /** 课程条那片图（原图、未盖遮罩）的平均灰度和花的程度；没有课程条时为 -1 / 0。 */
        double chipGray = -1, chipSpread;
    }

    /** 课程条所在的竖向范围（占小组件高度的比例）：标题下面整片。只用来量图，不再磨砂。 */
    static final float[] CHIPS_WIDE = {0.31f, 1f};

    /** compose 的 maxLong 取这个 = 不限长边，只受系统位图内存上限约束。 */
    static final int FULL = Integer.MAX_VALUE;

    /** 一片区域（比例坐标）的 {平均灰度, 标准差}，取 32×10 小图。 */
    static double[] regionStats(Bitmap b, float x0, float y0, float x1, float y1) {
        int W = b.getWidth(), H = b.getHeight();
        int l = Math.max(0, Math.round(W * x0)), t = Math.max(0, Math.round(H * y0));
        int w = Math.max(1, Math.min(W - l, Math.round(W * (x1 - x0)))), h = Math.max(1, Math.min(H - t, Math.round(H * (y1 - y0))));
        Bitmap part = null, small = null;
        try {
            part = Bitmap.createBitmap(b, l, t, w, h);
            small = Bitmap.createScaledBitmap(part, 32, 10, true);
            int[] px = new int[32 * 10];
            small.getPixels(px, 0, 32, 0, 0, 32, 10);
            return new double[]{WidgetLook.meanGray(px), WidgetLook.spread(px)};
        } catch (Throwable e) {
            return new double[]{0.5, 0.15};
        } finally {
            if (small != null && small != part && small != b) small.recycle();
            if (part != null && part != b) part.recycle();
        }
    }

    /**
     * 小组件竖屏下的真实尺寸（dp）：宽取 MIN_WIDTH，高取 MAX_HEIGHT（系统约定的竖屏宽高；ImageView 是 fitXY，比例必须和
     * 竖屏一致，不然会被拉伸）；缺哪个用 MAX_WIDTH / MIN_HEIGHT 补，都拿不到按 4×2 默认 250×165。
     */
    static int[] sizeDp(AppWidgetManager m, int id) {
        return sizeDp(m, id, 250, 165);
    }

    static int[] sizeDp(AppWidgetManager m, int id, int defW, int defH) {
        int w = 0, h = 0;
        try {
            Bundle o = m.getAppWidgetOptions(id);
            if (o != null) {
                w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
                if (w <= 0) w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
                if (h <= 0) h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            }
        } catch (Exception ignored) {
        }
        return new int[]{w > 0 ? w : defW, h > 0 ? h : defH};
    }

    /**
     * 合成：按小组件的真实像素（dp × density）从存图居中裁切、高质量缩放，超过系统位图上限才等比缩小；
     * 盖一层卡片色遮罩，最后在成品上切圆角、描发丝边。照片不做任何模糊。
     */
    static Look compose(Context c, int[] dp, boolean night, int maskPercent, int maxLong, float[] chips) {
        return compose(c, dp, night, maskPercent, maxLong, BAND_WIDE_W, BAND_WIDE_H, chips);
    }

    /** chips：课程条所在的竖向范围 {上, 下}（比例），量那块图的亮度 / 花的程度；null = 没有课程条。 */
    static Look compose(Context c, int[] dp, boolean night, int maskPercent, int maxLong,
                        float bandW, float bandH, float[] chips) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float d = dm.density;
        int wpx = Math.max(1, Math.round(dp[0] * d)), hpx = Math.max(1, Math.round(dp[1] * d));
        int[] cap = WidgetLook.fitBitmapCap(wpx, hpx, dm.widthPixels, dm.heightPixels);
        int[] fit = WidgetLook.fitLongSide(cap[0], cap[1], maxLong);
        int W = fit[0], H = fit[1];
        float scale = W / (float) wpx;

        Bitmap src = loadSource(c, W, H);
        if (src == null) return null;
        Bitmap crop = null, sized = null;
        try {
            int[] r = WidgetLook.centerCrop(src.getWidth(), src.getHeight(), W, H);
            crop = Bitmap.createBitmap(src, r[0], r[1], r[2], r[3]);
            sized = (crop.getWidth() == W && crop.getHeight() == H) ? crop : Bitmap.createScaledBitmap(crop, W, H, true);

            int mask = maskColor(night);
            Look look = new Look();
            look.darkText = WidgetLook.darkText(headerGray(sized, W, H, bandW, bandH), WidgetLook.gray(mask), maskPercent);
            if (chips != null) {
                double[] st = regionStats(sized, 0.04f, chips[0], 0.96f, chips[1]);
                look.chipGray = st[0];
                look.chipSpread = st[1];
            }

            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            float radius = cornerPx(c) * scale;
            RectF rect = new RectF(0, 0, W, H);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(sized, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)); // 1:1，不再缩放
            cv.drawRoundRect(rect, radius, radius, p);
            Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
            mp.setColor(mask | (Math.round(255 * (float) WidgetLook.maskAlpha(maskPercent)) << 24));
            cv.drawRoundRect(rect, radius, radius, mp);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);
            float sw = Math.max(1f, d * scale);
            sp.setStrokeWidth(sw);
            sp.setColor(night ? 0x1AFFFFFF : 0x14000000);
            RectF in = new RectF(sw / 2, sw / 2, W - sw / 2, H - sw / 2);
            cv.drawRoundRect(in, Math.max(0, radius - sw / 2), Math.max(0, radius - sw / 2), sp);
            look.bitmap = out;
            return look;
        } catch (Throwable e) {
            DiagLog.error(c, "widget", "bg compose failed", e);
            return null;
        } finally {
            if (sized != null && sized != crop && sized != src) sized.recycle();
            if (crop != null && crop != src) crop.recycle();
            src.recycle();
        }
    }

    /** 卡片圆角：Android 12+ 用系统小组件圆角，之前 20dp（和 w_card 一致）。 */
    static float cornerPx(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                return c.getResources().getDimension(android.R.dimen.system_app_widget_background_radius);
            } catch (Exception ignored) {
            }
        }
        return 20 * c.getResources().getDisplayMetrics().density;
    }
}
