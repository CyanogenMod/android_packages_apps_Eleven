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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Outline;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.AlbumArtPagerAdapter;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.loaders.NowPlayingCursor;
import com.cyanogenmod.eleven.loaders.QueueLoader;
import com.cyanogenmod.eleven.menu.CreateNewPlaylist;
import com.cyanogenmod.eleven.menu.DeleteDialog;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.ui.activities.HomeActivity;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.NavUtils;
import com.cyanogenmod.eleven.utils.PreferenceUtils;
import com.cyanogenmod.eleven.widgets.BrowseButton;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;
import com.cyanogenmod.eleven.widgets.NoResultsContainer;
import com.cyanogenmod.eleven.widgets.PlayPauseProgressButton;
import com.cyanogenmod.eleven.widgets.QueueButton;
import com.cyanogenmod.eleven.widgets.RepeatButton;
import com.cyanogenmod.eleven.widgets.RepeatingImageButton;
import com.cyanogenmod.eleven.widgets.ShuffleButton;
import com.cyanogenmod.eleven.widgets.VisualizerView;

import java.lang.ref.WeakReference;

import static com.cyanogenmod.eleven.utils.MusicUtils.mService;

public class AudioPlayerFragment extends Fragment implements ServiceConnection {
    private static final String TAG = AudioPlayerFragment.class.getSimpleName();

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 15;

    // fragment view
    private ViewGroup mRootView;

    // Header views
    private TextView mSongTitle;
    private TextView mArtistName;

    // Playlist Button
    private ImageView mAddToPlaylistButton;

    // Menu Button
    private ImageView mMenuButton;

    // Message to refresh the time
    private static final int REFRESH_TIME = 1;

    // The service token
    private MusicUtils.ServiceToken mToken;

    // Play pause and progress button
    private PlayPauseProgressButton mPlayPauseProgressButton;

    // Repeat button
    private RepeatButton mRepeatButton;

    // Shuffle button
    private ShuffleButton mShuffleButton;

    // Previous button
    private RepeatingImageButton mPreviousButton;

    // Next button
    private RepeatingImageButton mNextButton;

    // Album art ListView
    private ViewPager mAlbumArtViewPager;
    private LoadingEmptyContainer mQueueEmpty;

    private AlbumArtPagerAdapter mAlbumArtPagerAdapter;

    // Current time
    private TextView mCurrentTime;

    // Total time
    private TextView mTotalTime;

    // Visualizer View
    private VisualizerView mVisualizerView;

    // Broadcast receiver
    private PlaybackStatus mPlaybackStatus;

    // Handler used to update the current time
    private TimeHandler mTimeHandler;

    // Image cache
    private ImageFetcher mImageFetcher;

    // popup menu for pressing the menu icon
    private PopupMenu mPopupMenu;

    // Lyrics text view
    private TextView mLyricsText;

    private long mSelectedId = -1;

    private boolean mIsPaused = false;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Control the media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(getActivity(), this);

        // Initialize the image fetcher/cache
        mImageFetcher = ApolloUtils.getImageFetcher(getActivity());

        // Initialize the handler used to update the current time
        mTimeHandler = new TimeHandler(this);

        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup) inflater.inflate(R.layout.activity_player_fragment, null);

        // Header title values
        initHeaderBar();

        initPlaybackControls();

        mVisualizerView = (VisualizerView) mRootView.findViewById(R.id.visualizerView);
        mVisualizerView.initialize(getActivity());
        updateVisualizerPowerSaveMode();

        mLyricsText = (TextView) mRootView.findViewById(R.id.audio_player_lyrics);

        return mRootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        // Set the playback drawables
        updatePlaybackControls();
        // Setup the adapter
        createAndSetAdapter();
        // Current info
        updateNowPlayingInfo();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    @Override
    public void onStart() {
        super.onStart();

        final IntentFilter filter = new IntentFilter();
        // Play and pause changes
        filter.addAction(MusicPlaybackService.PLAYSTATE_CHANGED);
        // Shuffle and repeat changes
        filter.addAction(MusicPlaybackService.SHUFFLEMODE_CHANGED);
        filter.addAction(MusicPlaybackService.REPEATMODE_CHANGED);
        // Track changes
        filter.addAction(MusicPlaybackService.META_CHANGED);
        // Update a list, probably the playlist fragment's
        filter.addAction(MusicPlaybackService.REFRESH);
        // Listen to changes to the entire queue
        filter.addAction(MusicPlaybackService.QUEUE_CHANGED);
        // Listen for lyrics text for the audio track
        filter.addAction(MusicPlaybackService.NEW_LYRICS);
        // Listen for power save mode changed
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        // Register the intent filters
        getActivity().registerReceiver(mPlaybackStatus, filter);
        // Refresh the current time
        final long next = refreshCurrentTime();
        queueNextRefresh(next);

        // resumes the update callback for the play pause progress button
        mPlayPauseProgressButton.resume();
    }

    @Override
    public void onStop() {
        super.onStop();

        // pause the update callback for the play pause progress button
        mPlayPauseProgressButton.pause();

        mImageFetcher.flush();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mIsPaused = false;
        mTimeHandler.removeMessages(REFRESH_TIME);
        // Unbind from the service
        if (mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }

        // Unregister the receiver
        try {
            getActivity().unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable e) {
            //$FALL-THROUGH$
        }
    }

    /**
     * Initializes the header bar
     */
    private void initHeaderBar() {
        View headerBar = mRootView.findViewById(R.id.audio_player_header);
        final int bottomActionBarHeight =
                getResources().getDimensionPixelSize(R.dimen.bottom_action_bar_height);

        headerBar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // since we only want the top and bottom shadows, pad the horizontal width
                // to hide the shadows. Can't seem to find a better way to do this
                int padWidth = (int)(0.2f * view.getWidth());
                outline.setRect(-padWidth, -bottomActionBarHeight, view.getWidth() + padWidth,
                        view.getHeight());
            }
        });

        // Title text
        mSongTitle = (TextView) mRootView.findViewById(R.id.header_bar_song_title);
        mArtistName = (TextView) mRootView.findViewById(R.id.header_bar_artist_title);

        // Buttons
        // Search Button
        View v = mRootView.findViewById(R.id.header_bar_search_button);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavUtils.openSearch(getActivity(), "");
            }
        });

        // Add to Playlist Button
        // Setup the playlist button - add a click listener to show the context
        mAddToPlaylistButton = (ImageView) mRootView.findViewById(R.id.header_bar_add_button);

        // Create the context menu when requested
        mAddToPlaylistButton.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, menu);
                menu.setHeaderTitle(R.string.add_to_playlist);
            }
        });

        // add a click listener to show the context
        mAddToPlaylistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // save the current track id
                mSelectedId = MusicUtils.getCurrentAudioId();
                mAddToPlaylistButton.showContextMenu();
            }
        });

        // Add the menu button
        // menu button
        mMenuButton = (ImageView) mRootView.findViewById(R.id.header_bar_menu_button);
        mMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopupMenu();
            }
        });
    }

    /**
     * Initializes the items in the now playing screen
     */
    private void initPlaybackControls() {
        mPlayPauseProgressButton = (PlayPauseProgressButton)mRootView.findViewById(R.id.playPauseProgressButton);
        mPlayPauseProgressButton.setDragEnabled(true);
        mShuffleButton = (ShuffleButton)mRootView.findViewById(R.id.action_button_shuffle);
        mRepeatButton = (RepeatButton)mRootView.findViewById(R.id.action_button_repeat);
        mPreviousButton = (RepeatingImageButton)mRootView.findViewById(R.id.action_button_previous);
        mNextButton = (RepeatingImageButton)mRootView.findViewById(R.id.action_button_next);

        // Album art view pager
        mAlbumArtViewPager = (ViewPager)mRootView.findViewById(R.id.audio_player_album_art_viewpager);
        mAlbumArtViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                int currentPosition = 0;
                if (MusicUtils.getShuffleMode() == MusicPlaybackService.SHUFFLE_NONE) {
                    // if we aren't shuffling, base the position on the queue position
                    currentPosition = MusicUtils.getQueuePosition();
                } else {
                    // if we are shuffling, use the history size as the position
                    currentPosition = MusicUtils.getQueueHistorySize();
                }

                // check if we are going to next or previous track
                if (position - currentPosition == 1) {
                    MusicUtils.asyncNext(getActivity());
                } else if (position - currentPosition == -1) {
                    MusicUtils.previous(getActivity(), true);
                } else if (currentPosition != position) {
                    Log.w(TAG, "Unexpected page position of " + position
                            + " when current is: " + currentPosition);
                }
            }
        });
        // view to show in place of album art if queue is empty
        mQueueEmpty = (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        setupNoResultsContainer(mQueueEmpty.getNoResultsContainer());

        // Current time
        mCurrentTime = (TextView)mRootView.findViewById(R.id.audio_player_current_time);
        // Total time
        mTotalTime = (TextView)mRootView.findViewById(R.id.audio_player_total_time);

        // Set the repeat listener for the previous button
        mPreviousButton.setRepeatListener(mRewindListener);
        // Set the repeat listener for the next button
        mNextButton.setRepeatListener(mFastForwardListener);

        mPlayPauseProgressButton.enableAndShow();
    }

    private void setupNoResultsContainer(NoResultsContainer empty) {
        int color = getResources().getColor(R.color.no_results_light);
        empty.setTextColor(color);
        empty.setMainText(R.string.empty_queue_main);
        empty.setSecondaryText(R.string.empty_queue_secondary);
    }

    /**
     * Sets the track name, album name, and album art.
     */
    private void updateNowPlayingInfo() {
        // Set the track name
        mSongTitle.setText(MusicUtils.getTrackName());
        mArtistName.setText(MusicUtils.getArtistName());

        // Set the total time
        String totalTime = MusicUtils.makeShortTimeString(getActivity(), MusicUtils.duration() / 1000);
        if (!mTotalTime.getText().equals(totalTime)) {
            mTotalTime.setText(totalTime);
        }

        if (MusicUtils.getRepeatMode() == MusicPlaybackService.REPEAT_CURRENT) {
            // we are repeating 1 so just jump to the 1st and only item
            mAlbumArtViewPager.setCurrentItem(0, false);
        } else if (MusicUtils.getShuffleMode() == MusicPlaybackService.SHUFFLE_NONE) {
            // we are playing in-order, base the position on the queue position
            mAlbumArtViewPager.setCurrentItem(MusicUtils.getQueuePosition(), true);
        } else {
            // if we are shuffling, just based our index based on the history
            mAlbumArtViewPager.setCurrentItem(MusicUtils.getQueueHistorySize(), true);
        }

        // Update the current time
        queueNextRefresh(1);
    }

    /**
     * This creates the adapter based on the repeat and shuffle configuration and sets it into the
     * page adapter
     */
    private void createAndSetAdapter() {
        mAlbumArtPagerAdapter = new AlbumArtPagerAdapter(getChildFragmentManager());

        int repeatMode = MusicUtils.getRepeatMode();
        int targetSize = 0;
        int targetIndex = 0;
        int queueSize = MusicUtils.getQueueSize();

        if (repeatMode == MusicPlaybackService.REPEAT_CURRENT) {
            targetSize = 1;
            targetIndex = 0;
        } else if (MusicUtils.getShuffleMode() == MusicPlaybackService.SHUFFLE_NONE) {
            // if we aren't shuffling, use the queue to determine where we are
            targetSize = queueSize;
            targetIndex = MusicUtils.getQueuePosition();
        } else {
            // otherwise, set it to the max history size
            targetSize = MusicPlaybackService.MAX_HISTORY_SIZE;
            targetIndex = MusicUtils.getQueueHistorySize();
        }

        mAlbumArtPagerAdapter.setPlaylistLength(targetSize);
        mAlbumArtViewPager.setAdapter(mAlbumArtPagerAdapter);
        mAlbumArtViewPager.setCurrentItem(targetIndex);

        if(queueSize == 0) {
            mAlbumArtViewPager.setVisibility(View.GONE);
            mQueueEmpty.showNoResults();
            mAddToPlaylistButton.setVisibility(View.GONE);
        } else {
            mAlbumArtViewPager.setVisibility(View.VISIBLE);
            mQueueEmpty.hideAll();
            mAddToPlaylistButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the correct drawable states for the playback controls.
     */
    private void updatePlaybackControls() {
        // Set the play and pause image
        mPlayPauseProgressButton.getPlayPauseButton().updateState();
        // Set the shuffle image
        mShuffleButton.updateShuffleState();
        // Set the repeat image
        mRepeatButton.updateRepeatState();
    }

    /**
     * @param delay When to update
     */
    private void queueNextRefresh(final long delay) {
        if (!mIsPaused) {
            final Message message = mTimeHandler.obtainMessage(REFRESH_TIME);
            mTimeHandler.removeMessages(REFRESH_TIME);
            mTimeHandler.sendMessageDelayed(message, delay);
        }
    }

    /**
     * Used to seek forwards or backwards in time through the current track
     *
     * @param repcnt The repeat count
     * @param delta The long press duration
     * @param forwards Whether it was seeking forwards or backwards
     */
    private void seekRelative(final int repcnt, long delta, boolean forwards) {
        if (mService == null) {
            return;
        }
        if (repcnt > 0) {
            final long EXTRA_FAST_CUTOFF = 10000;
            if (delta < EXTRA_FAST_CUTOFF) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = EXTRA_FAST_CUTOFF * 10 + (delta - EXTRA_FAST_CUTOFF) * 40;
            }

            MusicUtils.seekRelative(forwards ? delta : -delta);

            refreshCurrentTime();
        }
    }

    private void refreshCurrentTimeText(final long pos) {
        if (mPlayPauseProgressButton.isDragging()) {
            mCurrentTime.setText(MusicUtils.makeShortTimeString(getActivity(),
                    mPlayPauseProgressButton.getDragProgressInMs() / 1000));
        } else {
            mCurrentTime.setText(MusicUtils.makeShortTimeString(getActivity(), pos / 1000));
        }
    }

    /* Used to update the current time string */
    private long refreshCurrentTime() {
        if (mService == null) {
            return MusicUtils.UPDATE_FREQUENCY_MS;
        }
        try {
            final long pos = MusicUtils.position();
            if (pos >= 0 && MusicUtils.duration() > 0) {
                refreshCurrentTimeText(pos);

                if (mPlayPauseProgressButton.isDragging()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                    return MusicUtils.UPDATE_FREQUENCY_FAST_MS;
                } else if (MusicUtils.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);

                    // calculate the number of milliseconds until the next full second,
                    // so the counter can be updated at just the right time
                    return Math.max(20, 1000 - pos % 1000);
                } else {
                    // blink the counter
                    final int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE
                            : View.INVISIBLE);
                }
            } else {
                mCurrentTime.setText("--:--");
            }
        } catch (final Exception ignored) {
            if (ignored.getMessage() != null) {
                Log.e(TAG, ignored.getMessage());
            }
        }
        return MusicUtils.UPDATE_FREQUENCY_MS;
    }

    /**
     * Used to scan backwards through the track
     */
    private final RepeatingImageButton.RepeatListener mRewindListener = new RepeatingImageButton.RepeatListener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onRepeat(final View v, final long howlong, final int repcnt) {
            seekRelative(repcnt, howlong, false);
        }
    };

    /**
     * Used to scan ahead through the track
     */
    private final RepeatingImageButton.RepeatListener mFastForwardListener = new RepeatingImageButton.RepeatListener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onRepeat(final View v, final long howlong, final int repcnt) {
            seekRelative(repcnt, howlong, true);
        }
    };

    public void showPopupMenu() {
        // create the popup menu
        if (mPopupMenu == null) {
            mPopupMenu = new PopupMenu(getActivity(), mMenuButton);
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
            // ringtone, and equalizer
            inflater.inflate(R.menu.audio_player, menu);

            if (!NavUtils.hasEffectsPanel(getActivity())) {
                menu.removeItem(R.id.menu_audio_player_equalizer);
            }

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
                MusicUtils.shuffleAll(getActivity());
                return true;
            case R.id.menu_audio_player_ringtone:
                // Set the current track as a ringtone
                MusicUtils.setRingtone(getActivity(), MusicUtils.getCurrentAudioId());
                return true;
            case R.id.menu_audio_player_equalizer:
                // Sound effects
                NavUtils.openEffectsPanel(getActivity(), HomeActivity.EQUALIZER);
                return true;
            case R.id.menu_settings:
                // Settings
                NavUtils.openSettings(getActivity());
                return true;
            case R.id.menu_audio_player_more_by_artist:
                NavUtils.openArtistProfile(getActivity(), MusicUtils.getArtistName());
                return true;
            case R.id.menu_audio_player_delete:
                // Delete current song
                DeleteDialog.newInstance(MusicUtils.getTrackName(), new long[]{
                        MusicUtils.getCurrentAudioId()
                }, null).show(getActivity().getSupportFragmentManager(), "DeleteDialog");
                return true;
            case R.id.menu_save_queue:
                NowPlayingCursor queue = (NowPlayingCursor) QueueLoader
                        .makeQueueCursor(getActivity());
                CreateNewPlaylist.getInstance(MusicUtils.getSongListForCursor(queue)).show(
                        getFragmentManager(), "CreatePlaylist");
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

    public void dismissPopupMenu() {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(new long[]{
                            mSelectedId
                    }).show(getFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long mPlaylistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), new long[]{
                            mSelectedId
                    }, mPlaylistId);
                    return true;
                default:
                    break;
            }
        }

        return super.onContextItemSelected(item);
    }

    public void onLyrics(String lyrics) {
        if (TextUtils.isEmpty(lyrics)
                || !PreferenceUtils.getInstance(getActivity()).getShowLyrics()) {
            mLyricsText.animate().alpha(0).setDuration(200);
        } else {
            lyrics = lyrics.replace("\n", "<br/>");
            Spanned span = Html.fromHtml(lyrics);
            mLyricsText.setText(span);

            mLyricsText.animate().alpha(1).setDuration(200);
        }
    }

    public void setVisualizerVisible(boolean visible) {
        if (visible && PreferenceUtils.getInstance(getActivity()).getShowVisualizer()) {
            mVisualizerView.setVisible(true);
        } else {
            mVisualizerView.setVisible(false);
        }
    }

    public void updateVisualizerPowerSaveMode() {
        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        mVisualizerView.setPowerSaveMode(pm.isPowerSaveMode());
    }

    public void setVisualizerColor(int color) {
        mVisualizerView.setColor(color);
    }

    /**
     * Used to update the current time string
     */
    private static final class TimeHandler extends Handler {

        private final WeakReference<AudioPlayerFragment> mAudioPlayer;

        /**
         * Constructor of <code>TimeHandler</code>
         */
        public TimeHandler(final AudioPlayerFragment player) {
            mAudioPlayer = new WeakReference<AudioPlayerFragment>(player);
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case REFRESH_TIME:
                    final long next = mAudioPlayer.get().refreshCurrentTime();
                    mAudioPlayer.get().queueNextRefresh(next);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Used to monitor the state of playback
     */
    private static final class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<AudioPlayerFragment> mReference;

        /**
         * Constructor of <code>PlaybackStatus</code>
         */
        public PlaybackStatus(final AudioPlayerFragment fragment) {
            mReference = new WeakReference<AudioPlayerFragment>(fragment);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final AudioPlayerFragment audioPlayerFragment = mReference.get();
            final String action = intent.getAction();
            if (action.equals(MusicPlaybackService.META_CHANGED)) {
                // if we are repeating current and the track has changed, re-create the adapter
                if (MusicUtils.getRepeatMode() == MusicPlaybackService.REPEAT_CURRENT) {
                    mReference.get().createAndSetAdapter();
                }

                // Current info
                audioPlayerFragment.updateNowPlayingInfo();
                audioPlayerFragment.dismissPopupMenu();
            } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                // Set the play and pause image
                audioPlayerFragment.mPlayPauseProgressButton.getPlayPauseButton().updateState();
                audioPlayerFragment.mVisualizerView.setPlaying(MusicUtils.isPlaying());
            } else if (action.equals(MusicPlaybackService.REPEATMODE_CHANGED)
                    || action.equals(MusicPlaybackService.SHUFFLEMODE_CHANGED)) {
                // Set the repeat image
                audioPlayerFragment.mRepeatButton.updateRepeatState();
                // Set the shuffle image
                audioPlayerFragment.mShuffleButton.updateShuffleState();

                // Update the queue
                audioPlayerFragment.createAndSetAdapter();
            } else if (action.equals(MusicPlaybackService.QUEUE_CHANGED)) {
                audioPlayerFragment.createAndSetAdapter();
            } else if (action.equals(MusicPlaybackService.NEW_LYRICS)) {
                audioPlayerFragment.onLyrics(intent.getStringExtra("lyrics"));
            } else if (action.equals(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) {
                audioPlayerFragment.updateVisualizerPowerSaveMode();
            }
        }
    }
}
