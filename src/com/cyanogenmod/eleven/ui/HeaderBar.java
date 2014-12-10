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
package com.cyanogenmod.eleven.ui;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.loaders.NowPlayingCursor;
import com.cyanogenmod.eleven.loaders.QueueLoader;
import com.cyanogenmod.eleven.menu.CreateNewPlaylist;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.NavUtils;

/**
 * Simple Header bar wrapper class that also has its own menu bar button.
 * It can collect a list of popup menu creators and create a pop up menu
 * from the list
 */
public class HeaderBar extends LinearLayout {

    private ImageView mMenuButton;
    private ImageView mSearchButton;
    private ImageView mBackButton;
    private TextView mTitleText;
    private PopupMenu mPopupMenu;
    private Fragment mFragment;

    public HeaderBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFragment(Fragment activity) {
        mFragment = activity;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mMenuButton = (ImageView)findViewById(R.id.header_bar_menu_button);
        mMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopupMenu();
            }
        });

        mSearchButton = (ImageView)findViewById(R.id.header_bar_search_button);
        mSearchButton.setOnClickListener(new View.OnClickListener() {
        @Override
            public void onClick(View v) {
                NavUtils.openSearch(mFragment.getActivity(), "");
            }
        });


        mBackButton = (ImageView)findViewById(R.id.header_bar_up);

        mTitleText = (TextView)findViewById(R.id.header_bar_title);
    }

    /**
     * @param resId set the title text
     */
    public void setTitleText(int resId) {
        mTitleText.setText(resId);
    }

    /**
     * @param text set the title text
     */
    public void setTitleText(String text) {
        mTitleText.setText(text);
    }

    /**
     * Sets the back button listener
     * @param listener listener
     */
    public void setBackListener(final OnClickListener listener) {
        mBackButton.setOnClickListener(listener);
        setOnClickListener(listener);
    }

    /**
     * Sets the header bar listener
     * @param listener listener
     */
    public void setHeaderClickListener(final OnClickListener listener) {
        setOnClickListener(listener);
    }

    public void showPopupMenu() {
        // create the popup menu
        if (mPopupMenu == null) {
            mPopupMenu = new PopupMenu(mFragment.getActivity(), mMenuButton);
            mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return onPopupMenuItemClick(item);
                }
            });
        }

        final Menu menu = mPopupMenu.getMenu();
        final MenuInflater inflater = mPopupMenu.getMenuInflater();

        menu.clear();

        // Shuffle all
        inflater.inflate(R.menu.shuffle_all, menu);
        if (MusicUtils.getQueueSize() > 0) {
            // save queue/clear queue
            inflater.inflate(R.menu.queue, menu);
        }
        // Settings
        inflater.inflate(R.menu.activity_base, menu);

        // show the popup
        mPopupMenu.show();
    }

    public boolean onPopupMenuItemClick(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_shuffle_all:
                // Shuffle all the songs
                MusicUtils.shuffleAll(mFragment.getActivity());
                return true;
            case R.id.menu_settings:
                // Settings
                NavUtils.openSettings(mFragment.getActivity());
                return true;
            case R.id.menu_save_queue:
                NowPlayingCursor queue = (NowPlayingCursor) QueueLoader
                        .makeQueueCursor(mFragment.getActivity());
                CreateNewPlaylist.getInstance(MusicUtils.getSongListForCursor(queue)).show(
                        mFragment.getFragmentManager(), "CreatePlaylist");
                queue.close();
                return true;
            case R.id.menu_clear_queue:
                MusicUtils.clearQueue();
                return true;
            default:
                break;
        }

        return false;
    }
}
