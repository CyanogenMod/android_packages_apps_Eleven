package com.cyngn.eleven.ui.activities;

import android.app.ActionBar;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.cyngn.eleven.R;

import java.util.Locale;

public abstract class DetailActivity extends SlidingPanelActivity implements
OnScrollListener {
    protected static final int ACTION_BAR_DEFAULT_OPACITY = 65;
    protected Drawable mActionBarBackground;

    protected void setupActionBar(String name) {
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(name.toUpperCase(Locale.getDefault()));
        actionBar.setIcon(R.drawable.ic_action_back);
        actionBar.setHomeButtonEnabled(true);
        // change action bar background to a drawable we can control
        mActionBarBackground = new ColorDrawable(getResources().getColor(R.color.header_action_bar_color));
        mActionBarBackground.setAlpha(ACTION_BAR_DEFAULT_OPACITY);
        actionBar.setBackgroundDrawable(mActionBarBackground);
    }

    /** cause action bar icon tap to act like back -- boo-urns! */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
                            (float)getHeaderHeight());
            if(alpha > 255) { alpha = 255; }
        } else {
            // header off screen
            setHeaderPosition(-getHeaderHeight());
        }

        mActionBarBackground.setAlpha(alpha);
    }

    @Override // OnScrollListener
    public void onScrollStateChanged(AbsListView view, int scrollState) {}
}