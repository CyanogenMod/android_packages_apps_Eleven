/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.widgets;

import android.view.View;

public interface IPopupMenuCallback {
    public static interface IListener {
        void onPopupMenuClicked(final View v, final int position);
    };

    public void setPopupMenuClickedListener(final IListener listener);
}
