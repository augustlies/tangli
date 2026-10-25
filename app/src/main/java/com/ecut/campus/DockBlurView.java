package com.ecut.campus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Android 12+ Dock 真模糊层。
 *
 * 从主内容底部截取一条快照，用 RenderEffect 模糊后垫在 Dock 下；
 * 只在页面切换或纵向滚动后重算，横向切周不逐帧重绘。
 */
public class DockBlurView extends View {

    private View source;
    private Bitmap snapshot;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean dirty = true;
    private final int extraTopPx;

    public DockBlurView(Context context) {
        this(context, null);
    }

    public DockBlurView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        extraTopPx = Math.round(20 * getResources().getDisplayMetrics().density);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
        }
    }

    public void setSource(View source) {
        this.source = source;
        markDirty();
    }

    public void markDirty() {
        dirty = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int scale = 3;
        int bw = Math.max(1, w / scale);
        int bh = Math.max(1, (h + extraTopPx) / scale);
        if (dirty || snapshot == null || snapshot.getWidth() != bw || snapshot.getHeight() != bh) {
            if (snapshot != null) snapshot.recycle();
            snapshot = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(snapshot);
            c.scale(1f / scale, 1f / scale);
            int srcTop = Math.max(0, source.getHeight() - h - extraTopPx);
            c.translate(0, -srcTop);
            source.draw(c);
            dirty = false;
        }

        canvas.drawBitmap(snapshot,
                new Rect(0, 0, snapshot.getWidth(), snapshot.getHeight()),
                new RectF(0, 0, w, h), paint);
    }
}
