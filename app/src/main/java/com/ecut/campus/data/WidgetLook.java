package com.ecut.campus.data;

/**
 * 小组件「自选背景图」的纯计算部分（无 Android 依赖，便于单测）：
 * 居中裁切、限制长边、遮罩强度，以及按「图片亮度 + 遮罩」决定标题用深字还是浅字。
 *
 * 亮度一律用 0..1 的 sRGB 灰度（Rec.709 加权）；比较对比度时再换成线性相对亮度（WCAG）。
 */
public final class WidgetLook {

    private WidgetLook() {}

    /** 遮罩滑杆范围（%）和默认值。100% 会把图完全盖住，所以封顶 85%。 */
    public static final int MASK_MIN = 0, MASK_MAX = 85, MASK_DEFAULT = 35;

    /** 存到 App 私有目录的原图长边上限（px）：渲染时按每个小组件的真实像素从它裁，大屏高密度也够清楚。 */
    public static final int STORE_LONG_SIDE = 2048;
    /** 系统给一个小组件全部位图的内存上限 ≈ 屏幕宽 × 高 × 4 字节 × 1.5。 */
    public static final double REMOTE_BITMAP_SCREENS = 1.5;

    /** 深色字 #111111、浅色字 #F5F4EF 的线性相对亮度。 */
    static final double INK_DARK_LUM = relLum(0x11 / 255.0);
    static final double INK_LIGHT_LUM = relLum(0xF4 / 255.0);

    public static int clampMask(int percent) {
        return Math.max(MASK_MIN, Math.min(MASK_MAX, percent));
    }

    /** 遮罩不透明度 0..0.85。 */
    public static double maskAlpha(int percent) {
        return clampMask(percent) / 100.0;
    }

    /** 把 src 居中裁成 dstW:dstH 的比例，返回 {left, top, width, height}。 */
    public static int[] centerCrop(int srcW, int srcH, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0) return new int[]{0, 0, Math.max(0, srcW), Math.max(0, srcH)};
        if (dstW <= 0 || dstH <= 0) return new int[]{0, 0, srcW, srcH};
        // src 比目标更「宽」→ 裁左右；否则裁上下。用 long 避免大图相乘溢出
        if ((long) srcW * dstH > (long) dstW * srcH) {
            int w = (int) Math.max(1, Math.round((double) srcH * dstW / dstH));
            w = Math.min(w, srcW);
            return new int[]{(srcW - w) / 2, 0, w, srcH};
        } else {
            int h = (int) Math.max(1, Math.round((double) srcW * dstH / dstW));
            h = Math.min(h, srcH);
            return new int[]{0, (srcH - h) / 2, srcW, h};
        }
    }

    /** 等比缩小到长边 ≤ maxLong（不放大），返回 {w, h}，每边至少 1。 */
    public static int[] fitLongSide(int w, int h, int maxLong) {
        if (w <= 0 || h <= 0) return new int[]{Math.max(1, w), Math.max(1, h)};
        int lng = Math.max(w, h);
        if (lng <= maxLong) return new int[]{w, h};
        double s = (double) maxLong / lng;
        return new int[]{Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(h * s))};
    }

    /** 解码时用的 2 的幂采样率：解出来的长边仍 ≥ want。 */
    public static int sampleSize(int w, int h, int want) {
        int s = 1;
        int lng = Math.max(w, h);
        while (want > 0 && lng / (s * 2) >= want) s *= 2;
        return s;
    }

    /**
     * 渲染时的采样率（2 的幂）：从 srcW×srcH 居中裁出 dstW:dstH 那块，按此采样解码后，裁出的那块仍 ≥ dstW×dstH，
     * 剩下最多 2 倍的缩小交给高质量缩放。
     */
    public static int cropSampleSize(int srcW, int srcH, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return 1;
        int[] r = centerCrop(srcW, srcH, dstW, dstH);
        int s = 1;
        while (r[2] / (s * 2) >= dstW && r[3] / (s * 2) >= dstH) s *= 2;
        return s;
    }

    /**
     * 小组件位图的最终尺寸：就是小组件的真实像素 w×h；只有 ARGB_8888 超过系统上限（屏幕像素 × 4 × 1.5 字节）时才等比缩小。
     */
    public static int[] fitBitmapCap(int w, int h, int screenW, int screenH) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (screenW <= 0 || screenH <= 0) return new int[]{w, h};
        double capPx = (double) screenW * screenH * REMOTE_BITMAP_SCREENS;
        double px = (double) w * h;
        if (px <= capPx) return new int[]{w, h};
        double s = Math.sqrt(capPx / px);
        int nw = Math.max(1, (int) Math.floor(w * s)), nh = Math.max(1, (int) Math.floor(h * s));
        return new int[]{nw, nh};
    }

    /** 一个颜色的 sRGB 灰度 0..1（忽略 alpha）。 */
    public static double gray(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    /** 一组像素的平均灰度；全透明像素跳过；空数组当作中灰 0.5。 */
    public static double meanGray(int[] argb) {
        if (argb == null || argb.length == 0) return 0.5;
        double sum = 0;
        int n = 0;
        for (int p : argb) {
            if ((p >>> 24) == 0) continue;
            sum += gray(p);
            n++;
        }
        return n == 0 ? 0.5 : sum / n;
    }

    /** sRGB 灰度 → 线性相对亮度。 */
    public static double relLum(double g) {
        g = Math.max(0, Math.min(1, g));
        return g <= 0.04045 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
    }

    /** WCAG 对比度（≥ 1）。 */
    public static double contrast(double lumA, double lumB) {
        double hi = Math.max(lumA, lumB), lo = Math.min(lumA, lumB);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** 图片上盖一层遮罩后的灰度（和 Canvas 一样在 sRGB 里按 alpha 混合）。 */
    public static double composite(double imageGray, double maskGray, double alpha) {
        alpha = Math.max(0, Math.min(1, alpha));
        return imageGray * (1 - alpha) + maskGray * alpha;
    }

    /** 标题区域用深色字吗？哪种字和「图片 + 遮罩」对比度更高就用哪种。 */
    public static boolean darkText(double imageGray, double maskGray, int maskPercent) {
        double bg = relLum(composite(imageGray, maskGray, maskAlpha(maskPercent)));
        return contrast(bg, INK_DARK_LUM) >= contrast(bg, INK_LIGHT_LUM);
    }

    /** 当前设置下标题文字能拿到的对比度（给设置页提示「遮罩再调高一点」用）。 */
    public static double textContrast(double imageGray, double maskGray, int maskPercent) {
        double bg = relLum(composite(imageGray, maskGray, maskAlpha(maskPercent)));
        return Math.max(contrast(bg, INK_DARK_LUM), contrast(bg, INK_LIGHT_LUM));
    }

    /** 对比度不到 4.5（正文可读线）时建议把遮罩调到多少；已经够了返回当前值。 */
    public static int suggestedMask(double imageGray, double maskGray, int maskPercent) {
        int p = clampMask(maskPercent);
        while (p < MASK_MAX && textContrast(imageGray, maskGray, p) < 4.5) p++;
        return p;
    }

    // ===== 有背景图时的半透明课程条 =====

    /** 课程条底色的基础不透明度（让图透出来一点）和上限。 */
    public static final double CHIP_ALPHA_BASE = 0.72, CHIP_ALPHA_MAX = 0.94;

    /** 一组像素灰度的标准差（0..0.5），衡量图「花不花」；全透明像素跳过，空数组为 0。 */
    public static double spread(int[] argb) {
        if (argb == null || argb.length == 0) return 0;
        double sum = 0, sq = 0;
        int n = 0;
        for (int p : argb) {
            if ((p >>> 24) == 0) continue;
            double g = gray(p);
            sum += g;
            sq += g * g;
            n++;
        }
        if (n == 0) return 0;
        double m = sum / n;
        return Math.sqrt(Math.max(0, sq / n - m * m));
    }

    /**
     * 课程条底色（课程色）的不透明度 0..1：基础 0.72；遮罩低于默认、图越花就再加一点；
     * 然后对每个课程色、在「图 ± 花的程度」两头都检查课名（≥ 4.5）和小字（≥ 3）的对比度，不够就继续加，封顶 0.94。
     *
     * @param imageGray   课程条下面那片图的平均灰度（未盖遮罩）
     * @param imageSpread 那片图的灰度标准差
     * @param maskGray    遮罩色灰度
     * @param inkGray     课名颜色灰度；subGray 时间教室颜色灰度
     * @param pigGrays    这套深浅色下全部课程色的灰度
     */
    public static double chipAlpha(double imageGray, double imageSpread, double maskGray, int maskPercent,
                                   double inkGray, double subGray, double[] pigGrays) {
        double ma = maskAlpha(maskPercent);
        double bg = composite(imageGray, maskGray, ma);
        double sp = Math.max(0, imageSpread) * (1 - ma);
        double a = CHIP_ALPHA_BASE;
        if (maskPercent < MASK_DEFAULT) a += (MASK_DEFAULT - clampMask(maskPercent)) / (double) MASK_DEFAULT * 0.08;
        a += Math.min(0.10, sp * 0.5);
        double inkL = relLum(inkGray), subL = relLum(subGray);
        double[] bgs = {Math.max(0, bg - sp), Math.min(1, bg + sp)};
        if (pigGrays != null) {
            for (double pig : pigGrays) {
                for (double b : bgs) {
                    while (a < CHIP_ALPHA_MAX) {
                        double eff = relLum(composite(b, pig, a));
                        if (contrast(eff, inkL) >= 4.5 && contrast(eff, subL) >= 3.0) break;
                        a += 0.01;
                    }
                }
            }
        }
        return Math.min(CHIP_ALPHA_MAX, a);
    }

    /** 0..1 → 0..255。 */
    public static int alpha255(double a) {
        return (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
    }
}
