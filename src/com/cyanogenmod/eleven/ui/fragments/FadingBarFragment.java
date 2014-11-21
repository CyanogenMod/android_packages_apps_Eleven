/*
* Copyright (C) 2014 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.cyanogenmod.eleven.ui.fragments;

import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.cyanogenmod.eleven.ui.activities.HomeActivity;

public abstract class FadingBarFragment extends DetailFragment implements OnScrollListener {
    protected static final int ACTION_BAR_DEFAULT_OPACITY = 100;

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

        HomeActivity home = getContainingActivity();
        if (home != null && home.getTopFragment() == this) {
            home.setActionBarAlpha(alpha);
        }
    }

    @Override // OnScrollListener
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }
}
