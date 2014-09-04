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
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.ImageView;

import com.cyngn.eleven.R;
import com.cyngn.eleven.slidinguppanel.SlidingUpPanelLayout;
import com.cyngn.eleven.slidinguppanel.SlidingUpPanelLayout.SimplePanelSlideListener;
import com.cyngn.eleven.ui.HeaderBar;
import com.cyngn.eleven.ui.fragments.AudioPlayerFragment;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.BlurScrimImage;

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
        None,
    }

    private SlidingUpPanelLayout mFirstPanel;
    private HeaderBar mFirstHeaderBar;
    private SlidingUpPanelLayout mSecondPanel;
    private HeaderBar mSecondHeaderBar;
    private Panel mTargetNavigatePanel;

    // this is the blurred image that goes behind the now playing and queue fragments
    private BlurScrimImage mBlurScrimImage;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.activity_base_content, new MusicBrowserPhoneFragment()).commit();
        }

        mTargetNavigatePanel = Panel.None;

        setupFirstPanel();
        setupSecondPanel();

        // get the blur scrim image
        findViewById(R.id.bottom_action_bar_parent).setBackgroundColor(Color.TRANSPARENT);
        mBlurScrimImage = (BlurScrimImage)findViewById(R.id.blurScrimImage);

        // if we've been launched by an intent, parse it
        Intent launchIntent = getIntent();
        if (launchIntent != null) {
            parseIntent(launchIntent);
        }
    }

    private void setupFirstPanel() {
        mFirstPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout);
        mFirstPanel.setPanelSlideListener(new SimplePanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                if (slideOffset > 0.8f) {
                    getActionBar().hide();
                } else if (slideOffset < 0.75f) {
                    getActionBar().show();
                }
            }

            @Override
            public void onPanelExpanded(View panel) {
                checkTargetNavigation();
            }

            @Override
            public void onPanelCollapsed(View panel) {
                checkTargetNavigation();
            }
        });

        // setup the header bar
        mFirstHeaderBar = setupHeaderBar(R.id.firstHeaderBar, R.string.page_now_playing,
                R.drawable.btn_queue_icon,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPanel(Panel.Queue);
                    }
                });
    }

    private void setupSecondPanel() {
        mSecondPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout2);
        mSecondPanel.setPanelSlideListener(new SimplePanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                mFirstPanel.setSlidingEnabled(false);
            }

            @Override
            public void onPanelExpanded(View panel) {
                checkTargetNavigation();
            }

            @Override
            public void onPanelCollapsed(View panel) {
                mFirstPanel.setSlidingEnabled(true);
                checkTargetNavigation();
            }
        });

        // setup the header bar
        mSecondHeaderBar = setupHeaderBar(R.id.secondHeaderBar, R.string.page_play_queue,
                R.drawable.btn_playback_icon,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPanel(Panel.MusicPlayer);
                    }
                });

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
                // if we are two panels over, we need special logic to jump twice
                mTargetNavigatePanel = panel;
                mSecondPanel.collapsePanel();
                mFirstPanel.collapsePanel();
                break;
            case MusicPlayer:
                mSecondPanel.collapsePanel();
                mFirstPanel.expandPanel();
                break;
            case Queue:
                // if we are two panels over, we need special logic to jump twice
                mTargetNavigatePanel = panel;
                mSecondPanel.expandPanel();
                mFirstPanel.expandPanel();
                break;
        }
    }

    /**
     * This checks if we are at our target panel, and if not, continues the motion
     */
    protected void checkTargetNavigation() {
        if (mTargetNavigatePanel != Panel.None) {
            if (mTargetNavigatePanel == getCurrentPanel()) {
                mTargetNavigatePanel = Panel.None;
            } else {
                showPanel(mTargetNavigatePanel);
            }
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

    @Override
    protected void updateMetaInfo() {
        super.updateMetaInfo();

        // load the blurred image
        mBlurScrimImage.loadBlurImage(ApolloUtils.getImageFetcher(this));

        // Set the artist name
        mFirstHeaderBar.setTitleText(MusicUtils.getArtistName());
    }

    protected AudioPlayerFragment getAudioPlayerFragment() {
        return (AudioPlayerFragment)getSupportFragmentManager().findFragmentById(R.id.audioPlayerFragment);
    }

    protected HeaderBar setupHeaderBar(final int containerId, final int textId,
                                       final int customIconId, final View.OnClickListener listener) {
        final HeaderBar headerBar = (HeaderBar) findViewById(containerId);
        headerBar.setTitleText(textId);
        headerBar.setupCustomButton(customIconId, listener);
        headerBar.setBackgroundColor(Color.TRANSPARENT);
        headerBar.setBackListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(Panel.Browse);
            }
        });

        return headerBar;
    }
}
