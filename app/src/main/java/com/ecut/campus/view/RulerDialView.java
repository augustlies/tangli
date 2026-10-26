package com.ecut.campus.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.ecut.campus.G;

/**
 * 刻度拨盘：横向刻度尺，中央指针读数。左右拖动跟手、带惯性、松手吸附到整数刻度，
 * 每过一格轻震一下。用于「几分钟」这类数字设置，替代手打数字。
 */
public class RulerDialView extends View {

    public interface OnValueChanged { void onChanged(int value); }

    private int min = 0, max = 100, value = 45;
    private String unit = "分钟";
    private float pos;           // 浮点读数（拖动中）
    private float lastX;
    private boolean dragging;
    private VelocityTracker tracker;
    private ValueAnimator anim;
    private OnValueChanged listener;
    private int lastTick;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointer = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RulerDialView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.MONOSPACE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void configure(int min, int max, int value, String unit) {
        this.min = min;
        this.max = Math.max(min, max);
        this.unit = unit == null ? "" : unit;
        this.value = clamp(value, this.min, this.max);
        pos = this.value;
        lastTick = this.value;
        invalidate();
    }

    public void setOnValueChanged(OnValueChanged l) { listener = l; }

    public int value() { return value; }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** 读数四舍五入并夹在范围内（纯计算，便于单测）。 */
    static int snap(float p, int lo, int hi) { return clamp(Math.round(p), lo, hi); }

    private float spacing() { return 11 * getResources().getDisplayMetrics().density; }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(dp(132)));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float base = h - dp(26);
        float sp = spacing();

        big.setColor(G.INK);
        big.setTextSize(dp(40));
        String num = String.valueOf(snap(pos, min, max));
        c.drawText(num, cx, dp(46), big);
        text.setTextSize(dp(11));
        text.setColor(G.MUTE);
        float nw = big.measureText(num);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(unit, cx + nw / 2f + dp(5), dp(46), text);
        text.setTextAlign(Paint.Align.CENTER);

        int first = (int) Math.floor(pos - cx / sp) - 1;
        int last = (int) Math.ceil(pos + cx / sp) + 1;
        for (int v = Math.max(min, first); v <= Math.min(max, last); v++) {
            float x = cx + (v - pos) * sp;
            float dist = Math.abs(x - cx) / cx;
            int alpha = (int) (255 * Math.max(0.12f, 1f - dist * dist));
            boolean major = v % 5 == 0;
            tick.setColor(G.alpha(G.INK, major ? alpha : alpha / 2));
            tick.setStrokeWidth(dp(major ? 1.4f : 1f));
            float len = major ? dp(22) : dp(12);
            c.drawLine(x, base - len, x, base, tick);
            if (v % 10 == 0) {
                text.setColor(G.alpha(G.MUTE, alpha));
                c.drawText(String.valueOf(v), x, base + dp(16), text);
            }
        }
        pointer.setColor(G.GLASS ? G.INK : G.ACCENT);
        RectF p = new RectF(cx - dp(1.2f), base - dp(34), cx + dp(1.2f), base + dp(2));
        c.drawRoundRect(p, dp(1.2f), dp(1.2f), pointer);
        c.drawCircle(cx, base - dp(36), dp(3.2f), pointer);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (tracker == null) tracker = VelocityTracker.obtain();
        tracker.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                lastX = e.getX();
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastX;
                lastX = e.getX();
                moveTo(pos - dx / spacing());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                tracker.computeCurrentVelocity(1000);
                float vx = tracker.getXVelocity();
                tracker.recycle();
                tracker = null;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                float target = snap(pos - vx / spacing() * 0.25f, min, max);
                animateTo(target);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void moveTo(float p) {
        pos = Math.max(min - 0.4f, Math.min(max + 0.4f, p));
        int v = snap(pos, min, max);
        if (v != lastTick) {
            lastTick = v;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            value = v;
            if (listener != null) listener.onChanged(v);
        }
        invalidate();
    }

    private void animateTo(float target) {
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration((long) Math.min(520, 120 + Math.abs(target - pos) * 18));
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> moveTo((float) a.getAnimatedValue()));
        anim.start();
    }
}
