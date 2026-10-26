package com.ecut.campus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * 换主题 / 深浅色的过渡：切换前截一张旧界面，Activity 重建后盖在最上面，
 * 再从手指点的位置挖出一个越来越大的圆，新主题从圆里扩散开。
 */
public final class ThemeReveal {
    private ThemeReveal() {}

    private static Bitmap pending;
    private static float pendingX, pendingY;
    private static long pendingAt;
    /** 重建后要回到的底部页签和设置子页（-1 表示不处理）。 */
    public static int restoreTab = -1, restorePage = -1;

    /** 在 recreate / 切深色之前调用。 */
    public static void capture(Activity a, View touched, int tab, int page) {
        try {
            View root = a.getWindow().getDecorView();
            if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
            Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bmp));
            int[] loc = new int[2];
            if (touched != null) {
                touched.getLocationInWindow(loc);
                pendingX = loc[0] + touched.getWidth() / 2f;
                pendingY = loc[1] + touched.getHeight() / 2f;
            } else {
                pendingX = root.getWidth() / 2f;
                pendingY = root.getHeight() / 2f;
            }
            pending = bmp;
            pendingAt = android.os.SystemClock.uptimeMillis();
            restoreTab = tab;
            restorePage = page;
        } catch (Throwable t) {
            pending = null;
        }
    }

    /** 在新 Activity 的 setContentView 之后调用；没有截图就什么都不做。 */
    public static void play(Activity a) {
        Bitmap bmp = pending;
        pending = null;
        if (bmp == null) return;
        // 截图超过 3 秒还没用上（重建被打断、进程被回收后又回来）：旧画面不再盖上去
        if (android.os.SystemClock.uptimeMillis() - pendingAt > 3000) {
            bmp.recycle();
            restoreTab = restorePage = -1;
            return;
        }
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        HoleView hole = new HoleView(a, bmp, pendingX, pendingY);
        decor.addView(hole, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        float far = (float) Math.hypot(Math.max(pendingX, bmp.getWidth() - pendingX),
                Math.max(pendingY, bmp.getHeight() - pendingY));
        ValueAnimator anim = ValueAnimator.ofFloat(0f, far);
        anim.setDuration(380);
        anim.setStartDelay(30);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(v -> hole.setRadius((float) v.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                decor.removeView(hole);
                bmp.recycle();
            }
        });
        anim.start();
    }

    static float revealRadius(float x, float y, float w, float h) {
        return (float) Math.hypot(Math.max(x, w - x), Math.max(y, h - y));
    }

    private static final class HoleView extends View {
        private final Bitmap bmp;
        private final float cx, cy;
        private float radius;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();

        HoleView(Context c, Bitmap bmp, float cx, float cy) {
            super(c);
            this.bmp = bmp;
            this.cx = cx;
            this.cy = cy;
            setClickable(true); // 动画期间挡住点击
        }

        void setRadius(float r) {
            radius = r;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bmp.isRecycled()) return;
            canvas.save();
            path.reset();
            path.setFillType(Path.FillType.INVERSE_WINDING);
            path.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.clipPath(path);
            canvas.drawBitmap(bmp, 0, 0, paint);
            canvas.restore();
        }
    }
}
