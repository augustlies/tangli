package com.ecut.campus.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 按厂商选课表来源；厂商未知时对当前页面跑所有 detect，取最高分。 */
public final class TimetableSources {

    /** 低于这个分数就当作认不出来。 */
    public static final int MIN_CONFIDENCE = 40;

    private static final ZhengfangSource ZHENGFANG = new ZhengfangSource();
    private static final QiangzhiSource QIANGZHI = new QiangzhiSource();
    private static final QiangzhiH5Source QIANGZHI_H5 = new QiangzhiH5Source();
    private static final WiseduSource WISEDU = new WiseduSource();
    private static final List<TimetableSource> ALL =
            Collections.unmodifiableList(Arrays.asList(ZHENGFANG, QIANGZHI, QIANGZHI_H5, WISEDU));

    private TimetableSources() {}

    public static List<TimetableSource> all() {
        return ALL;
    }

    /** OTHER 返回 null。 */
    public static TimetableSource forVendor(SchoolProfile.Vendor v) {
        if (v == SchoolProfile.Vendor.ZHENGFANG) return ZHENGFANG;
        if (v == SchoolProfile.Vendor.QIANGZHI) return QIANGZHI;
        return null;
    }

    /** 当前页面最像哪一家；都不像返回 null。 */
    public static TimetableSource detect(String url, String html) {
        TimetableSource best = null;
        int bestScore = MIN_CONFIDENCE - 1;
        for (TimetableSource s : ALL) {
            int score = s.detect(url, html);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    public static SchoolProfile.Vendor detectVendor(String url, String html) {
        TimetableSource s = detect(url, html);
        return s == null ? SchoolProfile.Vendor.OTHER : s.vendor();
    }
}
