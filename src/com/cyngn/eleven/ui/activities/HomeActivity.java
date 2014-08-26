/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyngn.eleven.ui.activities;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.cyngn.eleven.R;
import com.cyngn.eleven.slidinguppanel.SlidingUpPanelLayout;
import com.cyngn.eleven.ui.fragments.AudioPlayerFragment;
import com.cyngn.eleven.ui.fragments.QueueFragment;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.widgets.theme.BottomActionBar;

/**
 * This class is used to display the {@link ViewPager} used to swipe between the
 * main {@link Fragment}s used to browse the user's music.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class HomeActivity extends BaseActivity {
    public static final String ACTION_VIEW_BROWSE = "com.cyngn.eleven.ui.activities.HomeActivity.view.Browse";
    public static final String ACTION_VIEW_MUSIC_PLAYER = "com.cyngn.eleven.ui.activities.HomeActivity.view.MusicPlayer";
    public static final String ACTION_VIEW_QUEUE = "com.cyngn.eleven.ui.activities.HomeActivity.view.Queue";

    enum Panel {
        Browse,
        MusicPlayer,
        Queue,
    }

    private SlidingUpPanelLayout mFirstPanel;
    private SlidingUpPanelLayout mSecondPanel;
    private int mActionBarColor;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.activity_base_content, new MusicBrowserPhoneFragment()).commit();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.audioPlayerFragment, new AudioPlayerFragment()).commit();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.queueFragment, new QueueFragment()).commit();

        // set the action bar background color to be the background theme color
        mActionBarColor = getResources().getColor(R.color.header_action_bar_color);
        getActionBar().setBackgroundDrawable(new ColorDrawable(mActionBarColor));

        setupFirstPanel();
        setupSecondPanel();

        // if we've been launched by an intent, parse it
        Intent launchIntent = getIntent();
        if (launchIntent != null) {
            parseIntent(launchIntent);
        }
    }

    private void setupFirstPanel() {
        mFirstPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout);
        setHeaderText(R.id.firstHeaderBar, R.string.page_now_playing);
        mFirstPanel.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                if (slideOffset > 0.8f) {
                    getActionBar().hide();
                } else if (slideOffset < 0.75f) {
                    getActionBar().show();
                }
            }

            @Override
            public void onPanelCollapsed(View panel) {

            }

            @Override
            public void onPanelExpanded(View panel) {

            }

            @Override
            public void onPanelAnchored(View panel) {

            }

            @Override
            public void onPanelHidden(View panel) {

            }
        });
    }

    private void setupSecondPanel() {
        mSecondPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout2);
        mSecondPanel.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                mFirstPanel.setSlidingEnabled(false);
            }

            @Override
            public void onPanelCollapsed(View panel) {
                mFirstPanel.setSlidingEnabled(true);
            }

            @Override
            public void onPanelExpanded(View panel) {

            }

            @Override
            public void onPanelAnchored(View panel) {

            }

            @Override
            public void onPanelHidden(View panel) {

            }
        });

        // set the text of the second header
        setHeaderText(R.id.secondHeaderBar, R.string.page_play_queue);

        // set the drag view offset to allow the panel to go past the top of the viewport
        // since the previous view's is hiding the slide offset, we need to subtract that
        // from action bat height
        int slideOffset = getResources().getDimensionPixelOffset(R.dimen.sliding_panel_indicator_height);
        slideOffset -= ApolloUtils.getActionBarHeight(this);
        mSecondPanel.setSlidePanelOffset(slideOffset);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        parseIntent(intent);
    }

    private void parseIntent(Intent intent) {
        Panel targetPanel = null;

        if (intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals(ACTION_VIEW_BROWSE)) {
                targetPanel = Panel.Browse;
            } else if (action.equals(ACTION_VIEW_MUSIC_PLAYER)) {
                targetPanel = Panel.MusicPlayer;
            } if (action.equals(ACTION_VIEW_QUEUE)) {
                targetPanel = Panel.Queue;
            }
        } else {
            AudioPlayerFragment player = getAudioPlayerFragment();
            if (player != null && player.startPlayback()) {
                targetPanel = Panel.MusicPlayer;
            }
        }

        if (targetPanel != null) {
            showPanel(targetPanel);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int setContentView() {
        return R.layout.activity_base;
    }

    @Override
    public void onBackPressed() {
        Panel panel = getCurrentPanel();
        switch (panel) {
            case Browse:
                super.onBackPressed();
                break;
            case MusicPlayer:
                showPanel(Panel.Browse);
                break;
            case Queue:
                showPanel(Panel.MusicPlayer);
                break;
        }
    }

    protected void showPanel(Panel panel) {
        // TODO: Add ability to do this instantaneously as opposed to animate
        switch (panel) {
            case Browse:
                mSecondPanel.collapsePanel();
                mFirstPanel.collapsePanel();
                break;
            case MusicPlayer:
                mSecondPanel.collapsePanel();
                mFirstPanel.expandPanel();
                break;
            case Queue:
                mSecondPanel.expandPanel();
                mFirstPanel.expandPanel();
                break;
        }
    }

    protected Panel getCurrentPanel() {
        if (mSecondPanel.isPanelExpanded()) {
            return Panel.Queue;
        } else if (mFirstPanel.isPanelExpanded()) {
            return Panel.MusicPlayer;
        } else {
            return Panel.Browse;
        }
    }

    protected AudioPlayerFragment getAudioPlayerFragment() {
        return (AudioPlayerFragment)getSupportFragmentManager().findFragmentById(R.id.audioPlayerFragment);
    }

    protected void setHeaderText(int containerId, int textId) {
        View container = findViewById(containerId);
        TextView textView = (TextView)container.findViewById(R.id.header_bar_title);
        textView.setText(textId);
    }
}
