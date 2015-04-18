/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.LinearLayout;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.slidinguppanel.SlidingUpPanelLayout;
import com.cyanogenmod.eleven.slidinguppanel.SlidingUpPanelLayout.SimplePanelSlideListener;
import com.cyanogenmod.eleven.ui.HeaderBar;
import com.cyanogenmod.eleven.ui.fragments.AudioPlayerFragment;
import com.cyanogenmod.eleven.ui.fragments.QueueFragment;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.BlurScrimImage;

import java.util.HashSet;

/**
 * This class is used to display the {@link ViewPager} used to swipe between the
 * main {@link Fragment}s used to browse the user's music.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class SlidingPanelActivity extends BaseActivity {

    public enum Panel {
        Browse,
        MusicPlayer,
        Queue,
        None,
    }

    public static interface ISlidingPanelListener {
        public void onBeginSlide();
        public void onFinishSlide(SlidingPanelActivity.Panel visiblePanel);
    }

    private static final String STATE_KEY_CURRENT_PANEL = "CurrentPanel";

    private SlidingUpPanelLayout mFirstPanel;
    private SlidingUpPanelLayout mSecondPanel;
    protected Panel mTargetNavigatePanel;
    private HashSet<ISlidingPanelListener> mSlidingPanelListeners
            = new HashSet<ISlidingPanelListener>();

    private final ShowPanelClickListener mShowBrowse = new ShowPanelClickListener(Panel.Browse);
    private final ShowPanelClickListener mShowMusicPlayer = new ShowPanelClickListener(Panel.MusicPlayer);

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

        if (savedInstanceState != null) {
            int panelIndex = savedInstanceState.getInt(STATE_KEY_CURRENT_PANEL,
                    Panel.Browse.ordinal());
            Panel targetPanel = Panel.values()[panelIndex];

            showPanel(targetPanel);
            mTargetNavigatePanel = Panel.None;

            if (targetPanel == Panel.Queue) {
                mFirstPanel.setSlidingEnabled(false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(STATE_KEY_CURRENT_PANEL, getCurrentPanel().ordinal());
    }

    private void setupFirstPanel() {
        mFirstPanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout);
        mFirstPanel.setPanelSlideListener(new SimplePanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                onSlide(slideOffset);
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

                onSlide(slideOffset);
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
        setupHeaderBar(R.id.secondHeaderBar, R.string.page_play_queue, mShowMusicPlayer);

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

    public void showPanel(Panel panel) {
        // if we are already at our target panel, then don't do anything
        if (panel == getCurrentPanel()) {
            return;
        }

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

    protected void onSlide(float slideOffset) {
        for (ISlidingPanelListener listener : mSlidingPanelListeners) {
            listener.onBeginSlide();
        }
    }

    /**
     * This checks if we are at our target panel and resets our flag if we are there
     */
    protected void checkTargetNavigation() {
        final Panel currentPanel = getCurrentPanel();
        // This checks if we are at our target panel and resets our flag if we are there
        if (mTargetNavigatePanel == currentPanel) {
            mTargetNavigatePanel = Panel.None;
        }

        // if we are at the target panel
        if (mTargetNavigatePanel == Panel.None) {
            for (ISlidingPanelListener listener : mSlidingPanelListeners) {
                listener.onFinishSlide(currentPanel);
            }
        }
    }

    public Panel getCurrentPanel() {
        if (mSecondPanel.isPanelExpanded()) {
            return Panel.Queue;
        } else if (mFirstPanel.isPanelExpanded()) {
            return Panel.MusicPlayer;
        } else {
            return Panel.Browse;
        }
    }

    public void clearMetaInfo() {
        super.clearMetaInfo();
        mBlurScrimImage.transitionToDefaultState();
    }

    @Override
    public void onMetaChanged() {
        super.onMetaChanged();

        // load the blurred image
        mBlurScrimImage.loadBlurImage(ApolloUtils.getImageFetcher(this));
    }

    @Override
    public void onCacheUnpaused() {
        super.onCacheUnpaused();

        // load the blurred image
        mBlurScrimImage.loadBlurImage(ApolloUtils.getImageFetcher(this));
    }

    protected AudioPlayerFragment getAudioPlayerFragment() {
        return (AudioPlayerFragment)getSupportFragmentManager().findFragmentById(R.id.audioPlayerFragment);
    }

    protected QueueFragment getQueueFragment() {
        return (QueueFragment)getSupportFragmentManager().findFragmentById(R.id.queueFragment);
    }

    protected HeaderBar setupHeaderBar(final int containerId, final int textId,
                                       final View.OnClickListener headerClickListener) {
        final HeaderBar headerBar = (HeaderBar) findViewById(containerId);
        headerBar.setFragment(getQueueFragment());
        headerBar.setTitleText(textId);
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

    public void addSlidingPanelListener(final ISlidingPanelListener listener) {
        mSlidingPanelListeners.add(listener);
    }

    public void removeSlidingPanelListener(final ISlidingPanelListener listener) {
        mSlidingPanelListeners.remove(listener);
    }
}
