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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;

import android.widget.LinearLayout;
import com.cyngn.eleven.R;
import com.cyngn.eleven.slidinguppanel.SlidingUpPanelLayout;
import com.cyngn.eleven.slidinguppanel.SlidingUpPanelLayout.SimplePanelSlideListener;
import com.cyngn.eleven.ui.HeaderBar;
import com.cyngn.eleven.ui.fragments.AudioPlayerFragment;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.widgets.BlurScrimImage;

/**
 * This class is used to display the {@link ViewPager} used to swipe between the
 * main {@link Fragment}s used to browse the user's music.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SlidingPanelActivity extends BaseActivity {

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

    private final ShowPanelClickListener mShowBrowse = new ShowPanelClickListener(Panel.Browse);
    private final ShowPanelClickListener mShowMusicPlayer = new ShowPanelClickListener(Panel.MusicPlayer);
    private final ShowPanelClickListener mShowQueue = new ShowPanelClickListener(Panel.Queue);

    // this is the blurred image that goes behind the now playing and queue fragments
    private BlurScrimImage mBlurScrimImage;

    /**
     * Opens the now playing screen
     */
    private final View.OnClickListener mOpenNowPlaying = new View.OnClickListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(final View v) {
            if (MusicUtils.getCurrentAudioId() != -1) {
                openAudioPlayer();
            } else {
                MusicUtils.shuffleAll(SlidingPanelActivity.this);
            }
        }
    };

    @Override
    protected void initBottomActionBar() {
        super.initBottomActionBar();
        // Bottom action bar
        final LinearLayout bottomActionBar = (LinearLayout)findViewById(R.id.bottom_action_bar);
        // Display the now playing screen or shuffle if this isn't anything
        // playing
        bottomActionBar.setOnClickListener(mOpenNowPlaying);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTargetNavigatePanel = Panel.None;

        setupFirstPanel();
        setupSecondPanel();

        // get the blur scrim image
        mBlurScrimImage = (BlurScrimImage)findViewById(R.id.blurScrimImage);

        if (getLayoutToInflate() != 0) {
            ViewStub contentStub = (ViewStub) findViewById(R.id.content_stub);
            if (contentStub != null) {
                contentStub.setLayoutResource(getLayoutToInflate());
                contentStub.inflate();
            }
        }
    }

    protected int getLayoutToInflate() {
        return 0;
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
                R.drawable.btn_queue_icon, mShowQueue, mShowBrowse);
    }

    private void setupSecondPanel() {
        mSecondPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout2);
        mSecondPanel.setPanelSlideListener(new SimplePanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                // if we are not going to a specific panel, then disable sliding to prevent
                // the two sliding panels from fighting for touch input
                if (mTargetNavigatePanel == Panel.None) {
                    mFirstPanel.setSlidingEnabled(false);
                }
            }

            @Override
            public void onPanelExpanded(View panel) {
                checkTargetNavigation();
            }

            @Override
            public void onPanelCollapsed(View panel) {
                // re-enable sliding when the second panel is collapsed
                mFirstPanel.setSlidingEnabled(true);
                checkTargetNavigation();
            }
        });

        // setup the header bar
        mSecondHeaderBar = setupHeaderBar(R.id.secondHeaderBar, R.string.page_play_queue,
                R.drawable.btn_playback_icon, mShowMusicPlayer, mShowMusicPlayer);

        // set the drag view offset to allow the panel to go past the top of the viewport
        // since the previous view's is hiding the slide offset, we need to subtract that
        // from action bat height
        int slideOffset = getResources().getDimensionPixelOffset(R.dimen.sliding_panel_indicator_height);
        slideOffset -= ApolloUtils.getActionBarHeight(this);
        mSecondPanel.setSlidePanelOffset(slideOffset);
    }



    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
            default:
            case MusicPlayer:
                showPanel(Panel.Browse);
                break;
            case Queue:
                showPanel(Panel.MusicPlayer);
                break;
        }
    }

    public void openAudioPlayer() {
        showPanel(Panel.MusicPlayer);
    }

    protected void showPanel(Panel panel) {
        // TODO: Add ability to do this instantaneously as opposed to animate
        switch (panel) {
            case Browse:
                // if we are two panels over, we need special logic to jump twice
                mTargetNavigatePanel = panel;
                mSecondPanel.collapsePanel();
                // re-enable sliding on first panel so we can collapse it
                mFirstPanel.setSlidingEnabled(true);
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
     * This checks if we are at our target panel and resets our flag if we are there
     */
    protected void checkTargetNavigation() {
        if (mTargetNavigatePanel == getCurrentPanel()) {
            mTargetNavigatePanel = Panel.None;
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
                                       final int customIconId, final View.OnClickListener customListener,
                                        final View.OnClickListener headerClickListener) {
        final HeaderBar headerBar = (HeaderBar) findViewById(containerId);
        headerBar.setTitleText(textId);
        headerBar.setupCustomButton(customIconId, customListener);
        headerBar.setBackgroundColor(Color.TRANSPARENT);
        headerBar.setBackListener(mShowBrowse);
        headerBar.setHeaderClickListener(headerClickListener);

        return headerBar;
    }

    private class ShowPanelClickListener implements View.OnClickListener {

        private Panel mTargetPanel;

        public ShowPanelClickListener(Panel targetPanel) {
            mTargetPanel = targetPanel;
        }

        @Override
        public void onClick(View v) {
            showPanel(mTargetPanel);
        }
    }
}
