package com.cyanogenmod.eleven.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class SquareFrame extends FrameLayout {
    public SquareFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onMeasure(final int widthSpec, final int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        final int mSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(mSize, mSize);
    }
}