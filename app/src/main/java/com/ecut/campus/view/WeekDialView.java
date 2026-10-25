package com.ecut.campus.view;

import com.ecut.campus.G;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import com.ecut.campus.WeekMath;

/**
 * 周数旋钮：竖向刻度滚轮，中央指针选中。
 * 上下拖动跟手、带惯性、松手吸附到最近刻度；点击刻度直接跳到该周。
 */
public class WeekDialView extends View {

    public interface OnWeekPicked { void onPicked(int week); }
    public interface OnWeekChanged { void onChanged(int week); }

    private int totalWeeks = 20;
    private int week = 1;        // 当前指针对准的周（可为小数滚动中的浮点，用 scrollPos 表达）
    private int actualWeek = 1;
    private float scrollPos = 0; // 距第 1 周的浮点偏移，week = round(scrollPos) + 1
    private OnWeekPicked pickListener;
    private OnWeekChanged changeListener;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);

    private VelocityTracker velocityTracker;
    private float downY, lastY, velocity;
    private boolean dragging;
    private ValueAnimator fling;

    public WeekDialView(Context context) {
        super(context);
        text.setTextAlign(Paint.Align.CENTER);
        tick.setTextAlign(Paint.Align.CENTER);
        center.setColor(G.INK);
    }

    public void configure(int current, int total, int actual) {
        totalWeeks = Math.max(1, total);
        actualWeek = Math.max(1, Math.min(totalWeeks, actual));
        setWeek(current);
    }

    public void setWeek(int w) {
        week = Math.max(1, Math.min(totalWeeks, w));
        scrollPos = week - 1;
        notifyChanged();
        invalidate();
    }

    public int week() { return week; }

    public void setOnWeekPicked(OnWeekPicked l) { pickListener = l; }
    public void setOnWeekChanged(OnWeekChanged l) { changeListener = l; }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private void notifyChanged() {
        if (changeListener != null) changeListener.onChanged(week);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float rowH = dp(40);
        float centerY = h / 2f;

        // 中央高亮带
        center.setAlpha(28);
        canvas.drawRoundRect(new RectF(dp(8), centerY - rowH / 2, w - dp(8), centerY + rowH / 2),
                dp(14), dp(14), center);
        center.setAlpha(255);

        int first = (int) Math.floor(scrollPos - h / 2 / rowH - 1);
        int last = (int) Math.ceil(scrollPos + h / 2 / rowH + 1);
        for (int w1 = first; w1 <= last; w1++) {
            if (w1 < 0 || w1 >= totalWeeks) continue;
            float y = centerY + (w1 - scrollPos) * rowH;
            float dist = Math.abs(y - centerY) / (h / 2f);
            int n = w1 + 1;
            boolean isCurrent = n == week;
            boolean isActual = n == actualWeek;

            String label = "第 " + n + " 周";
            if (isActual) label += " · 本周";
            tick.setTextSize(dp(17));
            tick.setTypeface(isCurrent ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tick.setColor(isCurrent ? G.INK
                    : blend(G.alpha(G.SUB2, 0x8A), dist));
            canvas.drawText(label, w / 2f, y + dp(6), tick);
        }

        // 指针侧条
        center.setAlpha(255);
        canvas.drawRoundRect(new RectF(dp(6), centerY - dp(14), dp(10), centerY + dp(14)),
                dp(2), dp(2), center);

        fade.setShader(new android.graphics.LinearGradient(
                0, 0, 0, dp(26), G.CARD, G.CLEAR, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, dp(26), fade);
        fade.setShader(new android.graphics.LinearGradient(
                0, h, 0, h - dp(26), G.CARD, G.CLEAR, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, h - dp(26), w, h, fade);
        fade.setShader(null);
    }

    private static int blend(int color, float dist) {
        // dist 0..1 → 逐渐变浅
        int alpha = (int) (0x8A * Math.max(0f, 1f - dist * 0.8f));
        return (alpha << 24) | (color & G.CLEAR);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (fling != null) { fling.cancel(); fling = null; }
                dragging = true;
                downY = lastY = event.getY();
                velocity = 0;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                float y = event.getY();
                float dy = y - lastY;
                lastY = y;
                float rowH = dp(40);
                scrollPos = Math.max(0, Math.min(totalWeeks - 1, scrollPos - dy / rowH));
                updateWeek();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                dragging = false;
                velocityTracker.computeCurrentVelocity(1000);
                float vy = velocityTracker.getYVelocity();
                velocityTracker.recycle();
                velocityTracker = null;
                float rowH = dp(40);
                // 惯性：把速度折算成滚动距离（指数衰减）
                float flingPx = vy * 0.28f;
                float target = Math.max(0, Math.min(totalWeeks - 1,
                        scrollPos - flingPx / rowH));
                target = Math.round(target);
                animateTo(target);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
                animateTo(Math.round(scrollPos));
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateWeek() {
        int w = Math.max(1, Math.min(totalWeeks, Math.round(scrollPos) + 1));
        if (w != week) {
            week = w;
            notifyChanged();
        }
    }

    private void animateTo(float target) {
        if (fling != null) fling.cancel();
        final float start = scrollPos;
        float dist = Math.abs(target - start);
        long dur = dist < 0.01f ? 0 : Math.round(140 + 160 * Math.sqrt(Math.min(1f, dist / 6f)));
        fling = ValueAnimator.ofFloat(0f, 1f);
        fling.setDuration(dur);
        fling.addUpdateListener(a -> {
            scrollPos = start + (target - start) * (float) a.getAnimatedValue();
            updateWeek();
            invalidate();
        });
        fling.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                fling = null;
                scrollPos = target;
                updateWeek();
                invalidate();
            }
        });
        fling.start();
    }
}
