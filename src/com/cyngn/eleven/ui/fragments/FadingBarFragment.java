package com.cyngn.eleven.ui.fragments;

import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

public abstract class FadingBarFragment extends DetailFragment implements OnScrollListener {
    protected static final int ACTION_BAR_DEFAULT_OPACITY = 65;

    @Override
    public void setupActionBar() {
        super.setupActionBar();

        getContainingActivity().setActionBarAlpha(ACTION_BAR_DEFAULT_OPACITY);
        getContainingActivity().setFragmentPadding(false);
    }

    protected abstract int getHeaderHeight();

    protected abstract void setHeaderPosition(float y);

    @Override // OnScrollListener
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        View firstChild = view.getChildAt(0);
        if (firstChild == null) {
            return;
        }

        float firstChildY = firstChild.getY();

        int alpha = 255;
        if (firstVisibleItem == 0) {
            // move header to current top of list
            setHeaderPosition(firstChildY);
            // calculate alpha for the action bar
            alpha = ACTION_BAR_DEFAULT_OPACITY +
                    (int)((255 - ACTION_BAR_DEFAULT_OPACITY) * -firstChildY /
                            (float)(firstChild.getHeight()));
            if(alpha > 255) { alpha = 255; }
        } else {
            // header off screen
            setHeaderPosition(-getHeaderHeight());
        }

        if (getContainingActivity().getTopFragment() == this) {
            getContainingActivity().setActionBarAlpha(alpha);
        }
    }

    @Override // OnScrollListener
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }
}
