package com.ecut.campus.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** 极简流式布局：胶囊放不下就换行。 */
public class FlowLayout extends ViewGroup {

    private int lineSpacing;

    public FlowLayout(Context context) {
        super(context);
        lineSpacing = Math.round(10 * context.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop(), lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            if (x > 0 && x + child.getMeasuredWidth() > maxWidth) {
                x = 0;
                y += lineHeight + lineSpacing;
                lineHeight = 0;
            }
            x += child.getMeasuredWidth();
            lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
        }
        y += lineHeight + getPaddingBottom();
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(y, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > getPaddingLeft() + maxWidth) {
                x = getPaddingLeft();
                y += lineHeight + lineSpacing;
                lineHeight = 0;
            }
            child.layout(x, y, x + w, y + h);
            x += w;
            lineHeight = Math.max(lineHeight, h);
        }
    }
}
