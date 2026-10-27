package com.ecut.campus.fun;

import android.graphics.Typeface;
import android.widget.TextView;

import com.ecut.campus.G;
import com.ecut.campus.data.GradeVerdict;
import com.ecut.campus.data.Grades;

import java.util.Objects;

/** 翻牌批语的界面部分：第一下把副标题翻成一句批语，第二下才翻分数（翻分数仍由成绩页自己做）。 */
public final class GradeFlip {
    private GradeFlip() {}

    /** 这门成绩的一句批语（按课名+分数定，每次进来都是同一句）；关了开关或认不出分数段返回空串。 */
    public static String verdictFor(android.content.Context c, Grades.CourseGrade g) {
        if (!FunPrefs.verdictOn(c) || g == null) return "";
        return GradeVerdict.pick(GradeVerdict.band(g.score, g.scoreValue, g.isPassed),
                Objects.hash(g.name, g.score));
    }

    /** 副标题显示批语（墨色加粗），盖着的分数牌改成「再点」。 */
    public static void applyVerdict(TextView meta, TextView cover, String verdict) {
        meta.setText(verdict);
        meta.setTextColor(G.INK);
        meta.setTypeface(Typeface.DEFAULT_BOLD);
        meta.setTextSize(12.5f);
        if (cover != null) cover.setText("再点");
    }

    /** 带动画：副标题沿横轴翻过去，换成批语再翻回来。 */
    public static void flipToVerdict(TextView meta, TextView cover, String verdict) {
        meta.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        meta.animate().rotationX(90f).setDuration(140)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    applyVerdict(meta, cover, verdict);
                    meta.setRotationX(-90f);
                    meta.animate().rotationX(0f).setDuration(200)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
                }).start();
    }
}
