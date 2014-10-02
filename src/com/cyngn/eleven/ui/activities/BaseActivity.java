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

import static com.cyngn.eleven.utils.MusicUtils.mService;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.IElevenService;
import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.Lists;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.MusicUtils.ServiceToken;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.widgets.PlayPauseProgressButton;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * A base {@link FragmentActivity} used to update the bottom bar and
 * bind to Apollo's service.
 * <p>
 * {@link SlidingPanelActivity} extends from this skeleton.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BaseActivity extends FragmentActivity implements ServiceConnection,
        MusicStateListener {

    /**
     * Playstate and meta change listener
     */
    private final ArrayList<MusicStateListener> mMusicStateListener = Lists.newArrayList();

    private int mActionBarHeight;

    /**
     * The service token
     */
    private ServiceToken mToken;

    /**
     * Play pause progress button
     */
    private PlayPauseProgressButton mPlayPauseProgressButton;

    /**
     * Track name (BAB)
     */
    private TextView mTrackName;

    /**
     * Artist name (BAB)
     */
    private TextView mArtistName;

    /**
     * Album art (BAB)
     */
    private ImageView mAlbumArt;

    /**
     * Broadcast receiver
     */
    private PlaybackStatus mPlaybackStatus;

    private Drawable mActionBarBackground;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);

        getActionBar().setTitle(getString(R.string.app_name).toUpperCase());

        // Calculate ActionBar height
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, value, true))
        {
            mActionBarHeight = TypedValue.complexToDimensionPixelSize(value.data,
                    getResources().getDisplayMetrics());
        }

        // Set the layout
        setContentView(setContentView());

        // set the background on the root view
        getWindow().getDecorView().getRootView().setBackgroundColor(
                getResources().getColor(R.color.background_color));
        // Initialze the bottom action bar
        initBottomActionBar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = IElevenService.Stub.asInterface(service);
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        onMetaChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        getMenuInflater().inflate(R.menu.search_btn, menu);
        // Settings
        getMenuInflater().inflate(R.menu.activity_base, menu);

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                // Settings
                NavUtils.openSettings(this);
                return true;

            case R.id.menu_search:
                NavUtils.openSearch(BaseActivity.this, "");
                return true;

            case android.R.id.home:
                getSupportFragmentManager().popBackStack();
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        onMetaChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();
        final IntentFilter filter = new IntentFilter();
        // Play and pause changes
        filter.addAction(MusicPlaybackService.PLAYSTATE_CHANGED);
        // Track changes
        filter.addAction(MusicPlaybackService.META_CHANGED);
        // Update a list, probably the playlist fragment's
        filter.addAction(MusicPlaybackService.REFRESH);
        // If a playlist has changed, notify us
        filter.addAction(MusicPlaybackService.PLAYLIST_CHANGED);
        registerReceiver(mPlaybackStatus, filter);

        mPlayPauseProgressButton.resume();

        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();

        mPlayPauseProgressButton.pause();

        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mToken != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }

        // Unregister the receiver
        try {
            unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable e) {
            //$FALL-THROUGH$
        }

        // Remove any music status listeners
        mMusicStateListener.clear();
    }

    public void setupActionBar(int resId) {
        setupActionBar(getString(resId));
    }

    public void setupActionBar(String title) {
        setActionBarTitle(title);

        if (mActionBarBackground == null) {
            final int actionBarColor = getResources().getColor(R.color.header_action_bar_color);
            mActionBarBackground = new ColorDrawable(actionBarColor);
            getActionBar().setBackgroundDrawable(mActionBarBackground);
        }
    }

    public void setActionBarTitle(String title) {
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(title.toUpperCase());
    }

    public void setActionBarAlpha(int alpha) {
        mActionBarBackground.setAlpha(alpha);
    }

    public void setFragmentPadding(boolean enablePadding) {
        final int height = enablePadding ? mActionBarHeight : 0;
        findViewById(R.id.activity_base_content).setPadding(0, height, 0, 0);
    }

    /**
     * Initializes the items in the bottom action bar.
     */
    protected void initBottomActionBar() {
        // Play and pause button
        mPlayPauseProgressButton = (PlayPauseProgressButton)findViewById(R.id.playPauseProgressButton);
        mPlayPauseProgressButton.enableAndShow();

        // Track name
        mTrackName = (TextView)findViewById(R.id.bottom_action_bar_line_one);
        // Artist name
        mArtistName = (TextView)findViewById(R.id.bottom_action_bar_line_two);
        // Album art
        mAlbumArt = (ImageView)findViewById(R.id.bottom_action_bar_album_art);
        // Open to the currently playing album profile
        mAlbumArt.setOnClickListener(mOpenCurrentAlbumProfile);
    }

    protected void clearMetaInfo() {
        mAlbumArt.setImageResource(R.drawable.default_artwork);
    }

    /**
     * Sets the track name, album name, and album art.
     */
    private void updateBottomActionBarInfo() {
        // Set the track name
        mTrackName.setText(MusicUtils.getTrackName());
        // Set the artist name
        mArtistName.setText(MusicUtils.getArtistName());
        // Set the album art
        ApolloUtils.getImageFetcher(this).loadCurrentArtwork(mAlbumArt);
    }

    /**
     * Sets the correct drawable states for the playback controls.
     */
    private void updatePlaybackControls() {
        // Set the play and pause image
        mPlayPauseProgressButton.getPlayPauseButton().updateState();
    }

    /**
     * Opens the album profile of the currently playing album
     */
    private final View.OnClickListener mOpenCurrentAlbumProfile = new View.OnClickListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(final View v) {
            if (MusicUtils.getCurrentAudioId() != -1) {
                NavUtils.openAlbumProfile(BaseActivity.this, MusicUtils.getAlbumName(),
                        MusicUtils.getArtistName(), MusicUtils.getCurrentAlbumId());
            } else {
                MusicUtils.shuffleAll(BaseActivity.this);
            }
        }
    };

    /**
     * Used to monitor the state of playback
     */
    private final static class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<BaseActivity> mReference;

        /**
         * Constructor of <code>PlaybackStatus</code>
         */
        public PlaybackStatus(final BaseActivity activity) {
            mReference = new WeakReference<BaseActivity>(activity);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            BaseActivity baseActivity = mReference.get();
            if (baseActivity != null) {
                if (action.equals(MusicPlaybackService.META_CHANGED)) {
                    baseActivity.onMetaChanged();
                } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                    // Set the play and pause image
                    baseActivity.mPlayPauseProgressButton.getPlayPauseButton().updateState();
                } else if (action.equals(MusicPlaybackService.REFRESH)) {
                    baseActivity.restartLoader();
                } else if (action.equals(MusicPlaybackService.PLAYLIST_CHANGED)) {
                    baseActivity.onPlaylistChanged();
                }
            }
        }
    }

    @Override
    public void onMetaChanged() {
        // update action bar info
        updateBottomActionBarInfo();

        // Let the listener know to the meta chnaged
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.onMetaChanged();
            }
        }
    }

    @Override
    public void restartLoader() {
        // Let the listener know to update a list
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.restartLoader();
            }
        }
    }

    @Override
    public void onPlaylistChanged() {
        // Let the listener know to update a list
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.onPlaylistChanged();
            }
        }
    }

    /**
     * @param status The {@link MusicStateListener} to use
     */
    public void setMusicStateListenerListener(final MusicStateListener status) {
        if (status == this) {
            throw new UnsupportedOperationException("Override the method, don't add a listener");
        }

        if (status != null) {
            mMusicStateListener.add(status);
        }
    }

    /**
     * @param status The {@link MusicStateListener} to use
     */
    public void removeMusicStateListenerListener(final MusicStateListener status) {
        if (status != null) {
            mMusicStateListener.remove(status);
        }
    }

    /**
     * @return The resource ID to be inflated.
     */
    public abstract int setContentView();
}
