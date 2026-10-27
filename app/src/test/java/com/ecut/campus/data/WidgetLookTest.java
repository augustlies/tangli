package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class WidgetLookTest {

    private static final double DAY_MASK = WidgetLook.gray(0xF8F7F3);
    private static final double NIGHT_MASK = WidgetLook.gray(0x1C1C1A);

    @Test
    public void centerCropWideAndTall() {
        // 4000×3000 横图裁成 250:165 → 裁上下
        int[] r = WidgetLook.centerCrop(4000, 3000, 250, 165);
        assertEquals(0, r[0]);
        assertEquals(4000, r[2]);
        assertEquals(2640, r[3]);
        assertEquals((3000 - 2640) / 2, r[1]);
        // 竖图 1080×2400 裁成同比例 → 裁上下，宽度不变
        r = WidgetLook.centerCrop(1080, 2400, 250, 165);
        assertEquals(1080, r[2]);
        assertEquals(713, r[3]);
        assertEquals((2400 - 713) / 2, r[1]);
        // 很宽的全景图裁成接近方形 → 裁左右
        r = WidgetLook.centerCrop(3000, 600, 200, 200);
        assertArrayEquals(new int[]{1200, 0, 600, 600}, r);
    }

    @Test
    public void centerCropDegenerate() {
        assertArrayEquals(new int[]{0, 0, 100, 50}, WidgetLook.centerCrop(100, 50, 0, 10));
        assertArrayEquals(new int[]{0, 0, 0, 0}, WidgetLook.centerCrop(0, 0, 10, 10));
        int[] r = WidgetLook.centerCrop(100, 100, 100, 100);
        assertArrayEquals(new int[]{0, 0, 100, 100}, r);
    }

    @Test
    public void fitLongSideNeverUpscales() {
        assertArrayEquals(new int[]{800, 528}, WidgetLook.fitLongSide(1000, 660, 800));
        assertArrayEquals(new int[]{363, 800}, WidgetLook.fitLongSide(1089, 2400, 800));
        assertArrayEquals(new int[]{300, 200}, WidgetLook.fitLongSide(300, 200, 800));
        int[] tiny = WidgetLook.fitLongSide(10000, 1, 800);
        assertEquals(800, tiny[0]);
        assertEquals(1, tiny[1]);
    }

    @Test
    public void sampleSizeKeepsAtLeastWanted() {
        assertEquals(2, WidgetLook.sampleSize(4000, 3000, 1280));
        assertEquals(1, WidgetLook.sampleSize(1200, 900, 1280));
        assertEquals(4, WidgetLook.sampleSize(8000, 6000, 1280));
        assertTrue(8000 / WidgetLook.sampleSize(8000, 6000, 1280) >= 1280);
    }

    @Test
    public void maskClamped() {
        assertEquals(0, WidgetLook.clampMask(-5));
        assertEquals(85, WidgetLook.clampMask(100));
        assertEquals(0.35, WidgetLook.maskAlpha(35), 1e-9);
        assertEquals(0.85, WidgetLook.maskAlpha(99), 1e-9);
    }

    @Test
    public void grayAndMean() {
        assertEquals(1.0, WidgetLook.gray(0xFFFFFF), 1e-9);
        assertEquals(0.0, WidgetLook.gray(0x000000), 1e-9);
        // 绿色比蓝色「亮」得多
        assertTrue(WidgetLook.gray(0x00FF00) > WidgetLook.gray(0x0000FF) * 5);
        // 全透明像素不算
        int[] px = {0xFFFFFFFF, 0xFF000000, 0x00FFFFFF};
        assertEquals(0.5, WidgetLook.meanGray(px), 1e-9);
        assertEquals(0.5, WidgetLook.meanGray(new int[0]), 1e-9);
        assertEquals(0.5, WidgetLook.meanGray(null), 1e-9);
    }

    @Test
    public void contrastBasics() {
        assertEquals(21.0, WidgetLook.contrast(1.0, 0.0), 1e-9);
        assertEquals(1.0, WidgetLook.contrast(0.3, 0.3), 1e-9);
        assertEquals(0.5, WidgetLook.composite(0.0, 1.0, 0.5), 1e-9);
        assertEquals(0.2, WidgetLook.composite(0.2, 1.0, 0.0), 1e-9);
    }

    @Test
    public void darkPhotoNoMaskGetsLightText() {
        assertFalse(WidgetLook.darkText(0.10, DAY_MASK, 0));   // 夜景照片，不加遮罩
        assertFalse(WidgetLook.darkText(0.10, NIGHT_MASK, 60)); // 夜间黑遮罩更暗
    }

    @Test
    public void brightPhotoGetsDarkText() {
        assertTrue(WidgetLook.darkText(0.90, DAY_MASK, 0));    // 雪地 / 天空
        assertTrue(WidgetLook.darkText(0.90, NIGHT_MASK, 20)); // 黑遮罩不够厚，照片仍亮
    }

    @Test
    public void strongMaskWinsOverPhoto() {
        // 暗照片 + 日间白遮罩 80% → 背景已经很浅，换深色字
        assertTrue(WidgetLook.darkText(0.10, DAY_MASK, 80));
        // 亮照片 + 夜间黑遮罩 80% → 换浅色字
        assertFalse(WidgetLook.darkText(0.90, NIGHT_MASK, 80));
    }

    @Test
    public void chosenInkAlwaysBeatsTheOther() {
        for (int g = 0; g <= 100; g += 5) {
            for (int m = 0; m <= 85; m += 17) {
                double img = g / 100.0;
                boolean dark = WidgetLook.darkText(img, DAY_MASK, m);
                double bg = WidgetLook.relLum(WidgetLook.composite(img, DAY_MASK, WidgetLook.maskAlpha(m)));
                double cd = WidgetLook.contrast(bg, WidgetLook.INK_DARK_LUM);
                double cl = WidgetLook.contrast(bg, WidgetLook.INK_LIGHT_LUM);
                assertEquals(dark, cd >= cl);
                // 二选一总能拿到至少 ~4:1（中灰是最差情况）
                assertTrue(Math.max(cd, cl) > 4.0);
            }
        }
    }

    @Test
    public void suggestedMaskReachesReadable() {
        // 中灰最难读；建议值要么已达 4.5，要么封顶
        assertTrue(WidgetLook.textContrast(0.46, DAY_MASK, 0) < 4.5);
        int s = WidgetLook.suggestedMask(0.46, DAY_MASK, 0);
        assertTrue(s > 0);
        assertTrue(WidgetLook.textContrast(0.46, DAY_MASK, s) >= 4.5);
        // 已经够清楚就不改
        assertEquals(10, WidgetLook.suggestedMask(0.95, DAY_MASK, 10));
    }

    // ===== 半透明课程条 =====
    private static final double[] DAY_PIG = {WidgetLook.gray(0xF5ECDE), WidgetLook.gray(0xCFCFED), WidgetLook.gray(0xEDCFCF)};
    private static final double[] NIGHT_PIG = {WidgetLook.gray(0x3C2E1C), WidgetLook.gray(0x22223B), WidgetLook.gray(0x223C3B)};
    private static final double DAY_INK = WidgetLook.gray(0x111111), DAY_SUB = WidgetLook.gray(0x55554F);
    private static final double NIGHT_INK = WidgetLook.gray(0xECEAE4), NIGHT_SUB = WidgetLook.gray(0xB3B1AA);

    @Test
    public void chipAlphaCalmPhotoStaysNearBase() {
        double a = WidgetLook.chipAlpha(0.6, 0.02, DAY_MASK, 35, DAY_INK, DAY_SUB, DAY_PIG);
        assertTrue(a >= WidgetLook.CHIP_ALPHA_BASE && a < 0.75);
        double n = WidgetLook.chipAlpha(0.3, 0.02, NIGHT_MASK, 35, NIGHT_INK, NIGHT_SUB, NIGHT_PIG);
        assertTrue(n >= WidgetLook.CHIP_ALPHA_BASE && n < 0.75);
    }

    @Test
    public void chipAlphaRisesWithLowMaskAndBusyPhoto() {
        double calm = WidgetLook.chipAlpha(0.6, 0.02, DAY_MASK, 35, DAY_INK, DAY_SUB, DAY_PIG);
        double lowMask = WidgetLook.chipAlpha(0.6, 0.02, DAY_MASK, 0, DAY_INK, DAY_SUB, DAY_PIG);
        double busy = WidgetLook.chipAlpha(0.6, 0.25, DAY_MASK, 35, DAY_INK, DAY_SUB, DAY_PIG);
        assertTrue(lowMask > calm);
        assertTrue(busy > calm);
        assertTrue(WidgetLook.chipAlpha(0.5, 0.5, DAY_MASK, 0, DAY_INK, DAY_SUB, DAY_PIG) <= WidgetLook.CHIP_ALPHA_MAX);
    }

    @Test
    public void chipAlphaKeepsTextReadableOnHostilePhoto() {
        // 夜间深色课程条 + 浅字，压在没遮罩的亮白图上：要加到字仍 ≥ 4.5
        double a = WidgetLook.chipAlpha(0.98, 0.0, NIGHT_MASK, 0, NIGHT_INK, NIGHT_SUB, NIGHT_PIG);
        for (double pig : NIGHT_PIG) {
            double eff = WidgetLook.relLum(WidgetLook.composite(0.98, pig, a));
            assertTrue(a >= WidgetLook.CHIP_ALPHA_MAX - 1e-9
                    || WidgetLook.contrast(eff, WidgetLook.relLum(NIGHT_INK)) >= 4.5);
        }
        assertTrue(a > WidgetLook.CHIP_ALPHA_BASE);
    }

    @Test
    public void spreadAndAlpha255() {
        assertEquals(0, WidgetLook.spread(new int[]{0xFF808080, 0xFF808080}), 1e-9);
        assertEquals(0.5, WidgetLook.spread(new int[]{0xFF000000, 0xFFFFFFFF}), 1e-6);
        assertEquals(0, WidgetLook.spread(new int[0]), 1e-9);
        assertEquals(184, WidgetLook.alpha255(0.72));
        assertEquals(255, WidgetLook.alpha255(1.5));
    }

    @Test
    public void cropSampleSizeKeepsCropAtLeastTarget() {
        // 2048×1536 存图裁给 1000×500 的 4×2（3x 屏）：裁出 2048×1024，采样 2 → 1024×512 ≥ 1000×500
        assertEquals(2, WidgetLook.cropSampleSize(2048, 1536, 1000, 500));
        // 目标比裁出来还大：不采样（不放大，也不再丢像素）
        assertEquals(1, WidgetLook.cropSampleSize(1280, 960, 1100, 700));
        // 竖图裁给 2×2 方块 450×450：裁出 1536×1536 → 采样 2 (768) 可以，4 (384) 不够
        assertEquals(2, WidgetLook.cropSampleSize(1536, 2048, 450, 450));
        int s = WidgetLook.cropSampleSize(4000, 3000, 640, 400);
        int[] r = WidgetLook.centerCrop(4000, 3000, 640, 400);
        assertTrue(r[2] / s >= 640 && r[3] / s >= 400);
        assertTrue(r[2] / (s * 2) < 640 || r[3] / (s * 2) < 400);
        assertEquals(1, WidgetLook.cropSampleSize(0, 0, 10, 10));
    }

    @Test
    public void bitmapCapOnlyShrinksWhenOver() {
        // 真实大小在上限内：原样
        assertArrayEquals(new int[]{1035, 540}, WidgetLook.fitBitmapCap(1035, 540, 1080, 2400));
        // 超过 1080×2400×1.5 像素：等比缩到上限以内
        int[] f = WidgetLook.fitBitmapCap(4000, 3000, 1080, 2400);
        assertTrue((double) f[0] * f[1] <= 1080.0 * 2400 * 1.5);
        assertEquals(4000.0 / 3000, (double) f[0] / f[1], 0.01);
        // 拿不到屏幕尺寸：不动
        assertArrayEquals(new int[]{900, 600}, WidgetLook.fitBitmapCap(900, 600, 0, 0));
    }
}
