/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */

package com.cyngn.eleven.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

public class PopupMenuButton extends ImageView implements IPopupMenuCallback,
        View.OnClickListener {
    protected int mPosition = -1;
    protected IListener mClickListener = null;

    public PopupMenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnClickListener(this);
    }

    public void setPosition(final int position) {
        mPosition = position;
    }

    @Override
    public void setPopupMenuClickedListener(final IListener listener) {
        mClickListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mClickListener != null) {
            mClickListener.onPopupMenuClicked(v, mPosition);
        }
    }
}
