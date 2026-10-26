package com.ecut.campus.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ecut.campus.G;
import com.ecut.campus.R;

/**
 * 拦截页的大护盾开关：一圈 60 格刻度，中间盾牌。
 * 开启时刻度被一道亮弧缓慢扫过（只在可见时转，6 秒一圈）；关闭时刻度变淡、静止。
 */
public class ShieldRingView extends View {

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable shield;
    private final float density;

    private boolean on;
    private float sweep;
    private float press = 1f;
    private ValueAnimator spin;

    public ShieldRingView(Context context) {
        this(context, null);
    }

    public ShieldRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        tick.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setLetterSpacing(0.2f);
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_nav_shield);
        shield = d == null ? null : d.mutate();
        setClickable(true);
        setFocusable(true);
        setContentDescription("护盾已关闭，点按开启");
    }

    public void setOn(boolean on) {
        if (this.on == on) return;
        this.on = on;
        setContentDescription(on ? "护盾已开启，点按关闭" : "护盾已关闭，点按开启");
        updateSpin();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(184 * density);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSpin();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateSpin();
    }

    private void updateSpin() {
        boolean want = on && isAttachedToWindow() && isShown();
        if (want && spin == null) {
            spin = ValueAnimator.ofFloat(0f, 360f);
            spin.setDuration(6000);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(a -> {
                sweep = (float) a.getAnimatedValue();
                invalidate();
            });
            spin.start();
        } else if (!want) {
            stopSpin();
        }
    }

    private void stopSpin() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(0.94f);
                break;
            case MotionEvent.ACTION_UP:
                animatePress(1f);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                break;
            case MotionEvent.ACTION_CANCEL:
                animatePress(1f);
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    private void animatePress(float to) {
        ValueAnimator a = ValueAnimator.ofFloat(press, to);
        a.setDuration(140);
        a.addUpdateListener(v -> {
            press = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - 4f * density;
        canvas.save();
        canvas.scale(press, press, cx, cy);

        // 内圆：开 = 墨色实心，关 = 卡片底
        float inner = r - 22f * density;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(on ? G.INK : G.CARD);
        canvas.drawCircle(cx, cy, inner, fill);
        if (!on) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1f, density));
            fill.setColor(G.CARD_STROKE);
            canvas.drawCircle(cx, cy, inner, fill);
        }

        // 刻度：60 格，每 5 格一根长的
        for (int i = 0; i < 60; i++) {
            double ang = Math.toRadians(i * 6 - 90);
            boolean major = i % 5 == 0;
            float len = (major ? 12f : 7f) * density;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            int color;
            if (on) {
                // 亮弧拖在扫描角度后面，越近越实
                float d = ((i * 6 - sweep) % 360 + 360) % 360;
                float t = d > 300 ? (d - 300) / 60f : (d < 4 ? 1f : 0f);
                color = G.alpha(G.INK, Math.round(0x40 + (0xFF - 0x40) * t));
            } else {
                color = G.alpha(G.INK, major ? 0x40 : 0x22);
            }
            tick.setColor(color);
            tick.setStrokeWidth((major ? 2.2f : 1.4f) * density);
            canvas.drawLine(cx + cos * r, cy + sin * r, cx + cos * (r - len), cy + sin * (r - len), tick);
        }

        int fg = on ? G.ON_INK : G.SUB;
        float iconSize = 44f * density;
        if (shield != null) {
            int left = Math.round(cx - iconSize / 2f);
            int top = Math.round(cy - iconSize / 2f - 12f * density);
            shield.setBounds(left, top, Math.round(left + iconSize), Math.round(top + iconSize));
            shield.setTint(fg);
            shield.draw(canvas);
        }
        text.setColor(fg);
        text.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText(on ? "ON" : "OFF", cx, cy + iconSize / 2f + 12f * density, text);
        canvas.restore();
    }
}
