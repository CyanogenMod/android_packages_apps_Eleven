/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Artists;
import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.AlbumArtPagerAdapter;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.NowPlayingCursor;
import com.cyngn.eleven.loaders.QueueLoader;
import com.cyngn.eleven.menu.CreateNewPlaylist;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.widgets.PlayPauseProgressButton;
import com.cyngn.eleven.widgets.RepeatButton;
import com.cyngn.eleven.widgets.RepeatingImageButton;
import com.cyngn.eleven.widgets.ShuffleButton;
import com.cyngn.eleven.widgets.theme.HoloSelector;

import java.lang.ref.WeakReference;

import static com.cyngn.eleven.utils.MusicUtils.mService;

public class AudioPlayerFragment extends Fragment implements ServiceConnection {
    private static final String TAG = AudioPlayerFragment.class.getSimpleName();

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 15;

    // fragment view
    private ViewGroup mRootView;

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

    // Playlist button
    private ImageView mAddToPlaylistButton;

    // Menu button
    private ImageView mMenuButton;

    // Track name
    private TextView mTrackName;

    // Album art ListView
    private ViewPager mAlbumArtViewPager;

    private AlbumArtPagerAdapter mAlbumArtPagerAdapter;

    // Current time
    private TextView mCurrentTime;

    // Total time
    private TextView mTotalTime;

    // Broadcast receiver
    private PlaybackStatus mPlaybackStatus;

    // Handler used to update the current time
    private TimeHandler mTimeHandler;

    // Image cache
    private ImageFetcher mImageFetcher;

    // popup menu for pressing the menu icon
    private PopupMenu mPopupMenu;

    private long mPosOverride = -1;

    private long mStartSeekPos = 0;

    private long mLastSeekEventTime;

    private long mSelectedId = -1;

    private boolean mIsPaused = false;

    private boolean mFromTouch = false;

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
        initPlaybackControls();
        return mRootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        // Check whether we were asked to start any playback
        startPlayback();
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

    public void onDelete(long[] ids) {
        if (MusicUtils.getQueue().length == 0) {
            NavUtils.goHome(getActivity());
        }
    }

    public void onVisible() {
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        updateNowPlayingInfo();
    }

    public void onHidden() {

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
     * Initializes the items in the now playing screen
     */
    @SuppressWarnings("deprecation")
    private void initPlaybackControls() {
        // Play and pause button
        mPlayPauseProgressButton = (PlayPauseProgressButton)mRootView.findViewById(R.id.playPauseProgressButton);
        // Shuffle button
        mShuffleButton = (ShuffleButton)mRootView.findViewById(R.id.action_button_shuffle);
        // Repeat button
        mRepeatButton = (RepeatButton)mRootView.findViewById(R.id.action_button_repeat);
        // Previous button
        mPreviousButton = (RepeatingImageButton)mRootView.findViewById(R.id.action_button_previous);
        // Next button
        mNextButton = (RepeatingImageButton)mRootView.findViewById(R.id.action_button_next);
        // Track name
        mTrackName = (TextView)mRootView.findViewById(R.id.audio_player_track_name);
        mTrackName.setSelected(true);
        mTrackName.setOnClickListener(mOpenAlbumProfile);

        // Setup the playlist button - add a click listener to show the context
        mAddToPlaylistButton = (ImageView)mRootView.findViewById(R.id.action_button_add_to_playlist);
        mAddToPlaylistButton.setBackground(new HoloSelector(getActivity()));

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

        // menu button
        mMenuButton = (ImageView)mRootView.findViewById(R.id.action_button_menu);
        mMenuButton.setBackground(new HoloSelector(getActivity()));
        mMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopupMenu();
            }
        });


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

        // Current time
        mCurrentTime = (TextView)mRootView.findViewById(R.id.audio_player_current_time);
        // Total time
        mTotalTime = (TextView)mRootView.findViewById(R.id.audio_player_total_time);

        // Set the repeat listner for the previous button
        mPreviousButton.setRepeatListener(mRewindListener);
        // Set the repeat listner for the next button
        mNextButton.setRepeatListener(mFastForwardListener);

        mPlayPauseProgressButton.enableAndShow();
    }

    /**
     * Sets the track name, album name, and album art.
     */
    private void updateNowPlayingInfo() {
        // Set the track name
        mTrackName.setText(MusicUtils.getTrackName());

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

        if (repeatMode == MusicPlaybackService.REPEAT_CURRENT) {
            targetSize = 1;
            targetIndex = 0;
        } else if (MusicUtils.getShuffleMode() == MusicPlaybackService.SHUFFLE_NONE) {
            // if we aren't shuffling, use the queue to determine where we are
            targetSize = MusicUtils.getQueue().length;
            targetIndex = MusicUtils.getQueuePosition();
        } else {
            // otherwise, set it to the max history size
            targetSize = MusicPlaybackService.MAX_HISTORY_SIZE;
            targetIndex = MusicUtils.getQueueHistorySize();
        }

        mAlbumArtPagerAdapter.setPlaylistLength(targetSize);
        mAlbumArtViewPager.setAdapter(mAlbumArtPagerAdapter);
        mAlbumArtViewPager.setCurrentItem(targetIndex);
    }

    private long parseIdFromIntent(Intent intent, String longKey,
                                   String stringKey, long defaultId) {
        long id = intent.getLongExtra(longKey, -1);
        if (id < 0) {
            String idString = intent.getStringExtra(stringKey);
            if (idString != null) {
                try {
                    id = Long.parseLong(idString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return id;
    }

    /**
     * Checks whether the passed intent contains a playback request,
     * and starts playback if that's the case
     * @return true if the intent was consumed
     */
    public boolean startPlayback() {
        Intent intent = getActivity().getIntent();

        if (intent == null || mService == null || getActivity() == null) {
            return false;
        }

        Uri uri = intent.getData();
        String mimeType = intent.getType();
        boolean handled = false;

        if (uri != null && uri.toString().length() > 0) {
            MusicUtils.playFile(getActivity(), uri);
            handled = true;
        } else if (Playlists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "playlistId", "playlist", -1);
            if (id >= 0) {
                MusicUtils.playPlaylist(getActivity(), id, false);
                handled = true;
            }
        } else if (Albums.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "albumId", "album", -1);
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playAlbum(getActivity(), id, position, false);
                handled = true;
            }
        } else if (Artists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "artistId", "artist", -1);
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playArtist(getActivity(), id, position, false);
                handled = true;
            }
        }

        if (handled) {
            // Make sure to process intent only once
            getActivity().setIntent(new Intent());
            return true;
        }

        return false;
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
     * Used to scan backwards in time through the curren track
     *
     * @param repcnt The repeat count
     * @param delta The long press duration
     */
    private void scanBackward(final int repcnt, long delta) {
        if (mService == null) {
            return;
        }
        if (repcnt == 0) {
            mStartSeekPos = MusicUtils.position();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos - delta;
            if (newpos < 0) {
                // move to previous track
                MusicUtils.previous(getActivity(), true);
                final long duration = MusicUtils.duration();
                mStartSeekPos += duration;
                newpos += duration;
            }
            if (delta - mLastSeekEventTime > 250 || repcnt < 0) {
                MusicUtils.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshCurrentTime();
        }
    }

    /**
     * Used to scan forwards in time through the curren track
     *
     * @param repcnt The repeat count
     * @param delta The long press duration
     */
    private void scanForward(final int repcnt, long delta) {
        if (mService == null) {
            return;
        }
        if (repcnt == 0) {
            mStartSeekPos = MusicUtils.position();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos + delta;
            final long duration = MusicUtils.duration();
            if (newpos >= duration) {
                // move to next track
                MusicUtils.next();
                mStartSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (delta - mLastSeekEventTime > 250 || repcnt < 0) {
                MusicUtils.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshCurrentTime();
        }
    }

    private void refreshCurrentTimeText(final long pos) {
        mCurrentTime.setText(MusicUtils.makeShortTimeString(getActivity(), pos / 1000));
    }

    /* Used to update the current time string */
    private long refreshCurrentTime() {
        if (mService == null) {
            return 500;
        }
        try {
            final long pos = mPosOverride < 0 ? MusicUtils.position() : mPosOverride;
            if (pos >= 0 && MusicUtils.duration() > 0) {
                refreshCurrentTimeText(pos);
                final int progress = (int)(1000 * pos / MusicUtils.duration());

                if (mFromTouch) {
                    return 500;
                } else if (MusicUtils.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    final int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE
                            : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTime.setText("--:--");
            }

            // calculate the number of milliseconds until the next full second,
            // so
            // the counter can be updated at just the right time
            final long remaining = 1000 - pos % 1000;
            if (remaining < 20) {
                return 20;
            }

            return remaining;
        } catch (final Exception ignored) {
            if (ignored.getMessage() != null) {
                Log.e(TAG, ignored.getMessage());
            }
        }
        return 500;
    }

    /**
     * /** Used to shared what the user is currently listening to
     */
    private void shareCurrentTrack() {
        if (MusicUtils.getTrackName() == null || MusicUtils.getArtistName() == null) {
            return;
        }
        final Intent shareIntent = new Intent();
        final String shareMessage = getString(R.string.now_listening_to,
                MusicUtils.getTrackName(), MusicUtils.getArtistName());

        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_track_using)));
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
            scanBackward(repcnt, howlong);
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
            scanForward(repcnt, howlong);
        }
    };

    /**
     * Opens to the current album profile
     */
    private final View.OnClickListener mOpenAlbumProfile = new View.OnClickListener() {

        @Override
        public void onClick(final View v) {
            NavUtils.openAlbumProfile(getActivity(), MusicUtils.getAlbumName(),
                    MusicUtils.getArtistName(), MusicUtils.getCurrentAlbumId());
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

            final Menu menu = mPopupMenu.getMenu();
            final MenuInflater inflater = mPopupMenu.getMenuInflater();

            // Shuffle all
            inflater.inflate(R.menu.shuffle_all, menu);
            // Share, ringtone, and equalizer
            inflater.inflate(R.menu.audio_player, menu);
            // save queue/clear queue
            inflater.inflate(R.menu.queue, menu);
            // Settings
            inflater.inflate(R.menu.activity_base, menu);
        }

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
            case R.id.menu_audio_player_share:
                // Share the current meta data
                shareCurrentTrack();
                return true;
            case R.id.menu_audio_player_equalizer:
                // Sound effects
                NavUtils.openEffectsPanel(getActivity());
                return true;
            case R.id.menu_settings:
                // Settings
                NavUtils.openSettings(getActivity());
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
                NavUtils.goHome(getActivity());
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
            final String action = intent.getAction();
            if (action.equals(MusicPlaybackService.META_CHANGED)) {
                // Current info
                mReference.get().updateNowPlayingInfo();
                mReference.get().dismissPopupMenu();
            } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                // Set the play and pause image
                mReference.get().mPlayPauseProgressButton.getPlayPauseButton().updateState();
            } else if (action.equals(MusicPlaybackService.REPEATMODE_CHANGED)
                    || action.equals(MusicPlaybackService.SHUFFLEMODE_CHANGED)) {
                // Set the repeat image
                mReference.get().mRepeatButton.updateRepeatState();
                // Set the shuffle image
                mReference.get().mShuffleButton.updateShuffleState();

                // Update the queue
                mReference.get().createAndSetAdapter();
            } else if (action.equals(MusicPlaybackService.QUEUE_CHANGED)) {
                mReference.get().createAndSetAdapter();
            }
        }
    }
}
