package com.ecut.campus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * 悬浮胶囊 Dock：三个入口画在一颗胶囊里，选中块是一颗小胶囊。
 *
 * 点一下：选中块带一点回弹滑过去；按住左右拖：选中块跟着手指走，
 * 经过一个入口轻震一下，松手吸附到最近的那个并切过去。
 * 纸墨 = 墨色胶囊 + 纸色选中块；磨砂玻璃 = 半透明白胶囊 + 墨色选中块。
 */
public class CapsuleDock extends View {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"课表", "拦截", "设置"};
    private static final int[] ICONS = {R.drawable.ic_nav_timetable, R.drawable.ic_nav_shield, R.drawable.ic_nav_settings};

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable[] icons = new Drawable[ICONS.length];
    private final RectF tmp = new RectF();
    private final float density;

    private int selected = 0;
    /** 选中块中心所在的位置，单位「格」（0..n-1，可以是小数）。 */
    private float pos = 0f;
    private ValueAnimator anim;
    private OnTabSelectedListener listener;

    private boolean dragging;
    private float downX, downY;
    /** 按下时预览要切去的那一格（-1 = 没按着），松手才真正切。 */
    private int pressedSlot = -1;
    private int lastHoverTab = -1;

    public CapsuleDock(Context context) {
        this(context, null);
    }

    public CapsuleDock(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, density * 0.8f));
        for (int i = 0; i < ICONS.length; i++) {
            Drawable d = ContextCompat.getDrawable(context, ICONS[i]);
            icons[i] = d == null ? null : d.mutate();
        }
        setClickable(true);
        setElevation(G.GLASS ? 0f : 8f * density);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
        setContentDescription("底部导航：课表、拦截、设置");
    }

    public void setOnTabSelectedListener(OnTabSelectedListener l) {
        this.listener = l;
    }

    /** 点已经选中的那一项（比如在课表页再点「课表」= 回到本周）。 */
    public void setOnTabReselectedListener(OnTabSelectedListener l) {
        this.reselect = l;
    }

    private OnTabSelectedListener reselect;

    /** 外部切页时同步选中块（animate=false 或还没布局时直接就位）。 */
    public void setSelected(int index, boolean animate) {
        index = Math.max(0, Math.min(LABELS.length - 1, index));
        selected = index;
        updateDescription();
        if (!animate || getWidth() == 0) {
            if (anim != null) anim.cancel();
            pos = index;
            invalidate();
            return;
        }
        animateTo(index);
    }

    private void updateDescription() {
        setContentDescription("底部导航，当前：" + LABELS[selected] + "。左右拖动或点按切换：课表、拦截、设置");
    }

    public int selectedTab() {
        return selected;
    }

    private void animateTo(int index) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(320);
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private float pad() {
        return 5f * density;
    }

    private float cellWidth() {
        return (getWidth() - pad() * 2f) / LABELS.length;
    }

    /** 手指 x → 格位置（小数，夹在 0..n-1）。 */
    static float slotAt(float x, float left, float cell, int n) {
        if (cell <= 0) return 0f;
        float p = (x - left) / cell - 0.5f;
        return Math.max(0f, Math.min(n - 1, p));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float cell = cellWidth();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                lastHoverTab = selected;
                pressedSlot = Math.round(slotAt(e.getX(), pad(), cell, LABELS.length));
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                // 明显是横着拖（横向 > 12dp 且比纵向大）才接管；轻微抖动仍算点按
                if (!dragging && dx > 12f * density && dx > dy * 1.5f) {
                    dragging = true;
                    pressedSlot = -1;
                    if (anim != null) anim.cancel();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    pos = slotAt(e.getX(), pad(), cell, LABELS.length);
                    int hover = Math.round(pos);
                    if (hover != lastHoverTab) {
                        lastHoverTab = hover;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean wasDrag = dragging;
                int target = wasDrag ? Math.round(pos)
                        : (pressedSlot >= 0 ? pressedSlot : Math.round(slotAt(e.getX(), pad(), cell, LABELS.length)));
                dragging = false;
                pressedSlot = -1;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                animateTo(target);
                if (target != selected) {
                    selected = target;
                    updateDescription();
                    if (!wasDrag) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) listener.onTabSelected(target);
                } else {
                    performClick();
                    if (!wasDrag && reselect != null) reselect.onTabSelected(target);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                pressedSlot = -1;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                animateTo(selected);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = h / 2f;

        int pillColor, onPill, offColor;
        if (G.GLASS) {
            bgPaint.setColor(G.NIGHT ? 0x8C1C1C20 : 0x99FFFFFF);
            strokePaint.setColor(G.NIGHT ? 0x2EFFFFFF : 0xE6FFFFFF);
            pillColor = G.INK;
            onPill = G.ON_INK;
            offColor = G.SUB;
        } else {
            bgPaint.setColor(G.INK);
            pillColor = G.ON_INK;
            onPill = G.INK;
            offColor = G.alpha(G.ON_INK, 0x99);
        }
        tmp.set(0, 0, w, h);
        canvas.drawRoundRect(tmp, r, r, bgPaint);
        if (G.GLASS) {
            float d = strokePaint.getStrokeWidth() / 2f;
            tmp.set(d, d, w - d, h - d);
            canvas.drawRoundRect(tmp, r, r, strokePaint);
        }

        float cell = cellWidth();
        float p = pad();
        // 选中块：拖动时略微拉长，像被手指拽着
        float stretch = dragging ? 1.06f : 1f;
        float cx = p + cell * (pos + 0.5f);
        float halfW = cell / 2f * stretch;
        tmp.set(cx - halfW, p, cx + halfW, h - p);
        pillPaint.setColor(pillColor);
        float pr = tmp.height() / 2f;
        canvas.drawRoundRect(tmp, pr, pr, pillPaint);
        if (pressedSlot >= 0 && pressedSlot != Math.round(pos)) {
            // 按着别的格：先在那里浮出一颗淡胶囊，告诉你松手会去哪
            float pcx = p + cell * (pressedSlot + 0.5f);
            tmp.set(pcx - cell / 2f, p, pcx + cell / 2f, h - p);
            pillPaint.setColor(G.alpha(pillColor, 0x40));
            canvas.drawRoundRect(tmp, pr, pr, pillPaint);
        }

        float iconSize = 18f * density;
        float gap = 6f * density;
        int offAlpha = (offColor >>> 24) & 0xFF;
        for (int i = 0; i < LABELS.length; i++) {
            float icx = p + cell * (i + 0.5f);
            // 离选中块越近越接近「选中色」，拖动过程中颜色平滑过渡
            float near = Math.max(0f, 1f - Math.abs(pos - i));
            int color = G.alpha(G.mix(offColor | 0xFF000000, onPill, near),
                    Math.round(offAlpha + (255 - offAlpha) * near));
            textPaint.setColor(color);
            textPaint.setTypeface(near > 0.5f ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float textW = textPaint.measureText(LABELS[i]);
            float total = iconSize + gap + textW;
            float startX = icx - total / 2f;
            Drawable d = icons[i];
            if (d != null) {
                int top = Math.round(h / 2f - iconSize / 2f);
                d.setBounds(Math.round(startX), top, Math.round(startX + iconSize), Math.round(top + iconSize));
                d.setTint(color);
                d.draw(canvas);
            }
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(LABELS[i], startX + iconSize + gap + textW / 2f, baseline, textPaint);
        }
    }
}
