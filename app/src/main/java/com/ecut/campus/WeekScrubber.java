package com.ecut.campus;

import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * 拨动周数：按住标题里的大号周数左右拖，像里程表一样一格一格滚，每过一周震一下，
 * 课表跟着实时换；松手停在那一周。单击立刻打开周菜单（不再等双击判定，没有半拍延迟）；
 * 回到本周用底部「课表」再点一下，或周菜单里的「本周」。
 */
public final class WeekScrubber implements View.OnTouchListener {

    public interface Host {
        int viewedWeek();
        int actualWeek();
        int totalWeeks();
        /** 拖动中预览（只换标题和网格，不落盘）。 */
        void previewWeek(int week);
        /** 松手：真正切到这一周。 */
        void commitWeek(int week);
        void onTitleClick(View v);
    }

    private final Host host;
    private final GestureDetector detector;
    private final float stepPx;
    private final int slop;
    private float downX;
    private boolean scrubbing;
    private int baseWeek, shownWeek;
    private boolean atEdge;

    public WeekScrubber(View title, Host host) {
        this.host = host;
        float d = title.getResources().getDisplayMetrics().density;
        stepPx = 26 * d;
        slop = ViewConfiguration.get(title.getContext()).getScaledTouchSlop();
        detector = new GestureDetector(title.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                host.onTitleClick(title);
                return true;
            }
        });
    }

    /** 拖动距离换算成相对周数：每 stepPx 一周，向右为下一周。 */
    static int stepsFor(float dx, float stepPx) {
        if (stepPx <= 0) return 0;
        return (int) (dx / stepPx);
    }

    static int clamp(int w, int total) {
        return Math.max(1, Math.min(Math.max(1, total), w));
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                scrubbing = false;
                baseWeek = host.viewedWeek();
                shownWeek = baseWeek;
                atEdge = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downX;
                if (!scrubbing && Math.abs(dx) > slop) {
                    scrubbing = true;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    detector.onTouchEvent(cancel);
                    cancel.recycle();
                }
                if (scrubbing) {
                    int raw = baseWeek + stepsFor(dx, stepPx);
                    int w = clamp(raw, host.totalWeeks());
                    // 拖过第 1 周或最后一周：重震一下、数字顶一下，告诉你到头了
                    boolean edge = raw != w;
                    if (edge && !atEdge) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        v.animate().cancel();
                        v.setTranslationX(raw > w ? -6f : 6f);
                        v.animate().translationX(0).setDuration(160).start();
                    }
                    atEdge = edge;
                    if (w != shownWeek) {
                        int dir = w > shownWeek ? 1 : -1;
                        shownWeek = w;
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        host.previewWeek(w);
                        roll(v, dir);
                    }
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                if (scrubbing) {
                    scrubbing = false;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (shownWeek != baseWeek) host.commitWeek(shownWeek);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                // 被系统打断（来电、手势被抢）不算数：退回拖动前那一周
                if (scrubbing) {
                    scrubbing = false;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (shownWeek != baseWeek) host.previewWeek(baseWeek);
                    shownWeek = baseWeek;
                    return true;
                }
                break;
        }
        return detector.onTouchEvent(e);
    }

    /** 里程表式滚动：新数字从拖动方向滑进来。 */
    private static void roll(View v, int dir) {
        if (!(v instanceof TextView)) return;
        float h = v.getHeight() * 0.35f;
        v.animate().cancel();
        v.setTranslationY(dir * h);
        v.setAlpha(0.35f);
        v.animate().translationY(0).alpha(1f).setDuration(140).start();
    }
}
