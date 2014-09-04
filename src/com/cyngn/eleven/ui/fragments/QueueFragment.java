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

package com.cyngn.eleven.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.SongAdapter;
import com.cyngn.eleven.dragdrop.DragSortListView;
import com.cyngn.eleven.dragdrop.DragSortListView.DragScrollProfile;
import com.cyngn.eleven.dragdrop.DragSortListView.DropListener;
import com.cyngn.eleven.dragdrop.DragSortListView.RemoveListener;
import com.cyngn.eleven.loaders.NowPlayingCursor;
import com.cyngn.eleven.loaders.QueueLoader;
import com.cyngn.eleven.menu.CreateNewPlaylist;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.widgets.PlayPauseProgressButton;
import com.viewpagerindicator.TitlePageIndicator;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.cyngn.eleven.utils.MusicUtils.mService;

/**
 * This class is used to display all of the songs in the queue.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class QueueFragment extends Fragment implements LoaderCallbacks<List<Song>>,
        OnItemClickListener, DropListener, RemoveListener, DragScrollProfile, ServiceConnection {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 13;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * Service token for binding to the music service
     */
    private MusicUtils.ServiceToken mToken;

    /**
     * The listener to the playback service that will trigger updates to the ui
     */
    private QueueUpdateListener mQueueUpdateListener;

    /**
     * The adapter for the list
     */
    private SongAdapter mAdapter;

    /**
     * The list view
     */
    private DragSortListView mListView;

    /**
     * Represents a song
     */
    private Song mSong;

    /**
     * Position of a context menu item
     */
    private int mSelectedPosition;

    /**
     * Id of a context menu item
     */
    private long mSelectedId;

    /**
     * Song, album, and artist name used in the context menu
     */
    private String mSongName, mAlbumName, mArtistName;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public QueueFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adpater
        mAdapter = new SongAdapter(getActivity(), R.layout.edit_queue_list_item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.list_base_nopadding, null);
        // Initialize the list
        mListView = (DragSortListView)rootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        mListView.setOnCreateContextMenuListener(this);
        // Play the selected song
        mListView.setOnItemClickListener(this);
        // Set the drop listener
        mListView.setDropListener(this);
        // Set the swipe to remove listener
        mListView.setRemoveListener(this);
        // Quick scroll while dragging
        mListView.setDragScrollProfile(this);
        // Enable fast scroll bars
        mListView.setFastScrollEnabled(true);
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Initialize the broadcast receiver
        mQueueUpdateListener = new QueueUpdateListener(this);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(getActivity(), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        refreshQueue();
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
        // Queue changes
        filter.addAction(MusicPlaybackService.QUEUE_CHANGED);
        // Track changes
        filter.addAction(MusicPlaybackService.META_CHANGED);

        getActivity().registerReceiver(mQueueUpdateListener, filter);

        // resume the progress listeners
        setPlayPauseProgressButtonStates(false);
    }

    @Override
    public void onStop() {
        super.onStop();

        // stops the progress listeners
        setPlayPauseProgressButtonStates(true);
    }

    /**
     * Sets the state for any play pause progress buttons under the listview
     * This is neede because the buttons update themselves so if the activity
     * is hidden, we want to pause those handlers
     * @param pause the state to set it to
     */
    public void setPlayPauseProgressButtonStates(boolean pause) {
        if (mListView != null) {
            // walk through the visible list items
            for (int i = mListView.getFirstVisiblePosition();
                 i <= mListView.getLastVisiblePosition(); i++) {
                View childView = mListView.getChildAt(i);
                if (childView != null) {
                    PlayPauseProgressButton button = (PlayPauseProgressButton) childView.findViewById(R.id.playPauseProgressButton);

                    // pause or resume based on the flag
                    if (pause) {
                        button.pause();
                    } else {
                        button.resume();
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            getActivity().unregisterReceiver(mQueueUpdateListener);
        } catch (final Throwable e) {
            //$FALL-THROUGH$
        }

        if (mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        mSelectedPosition = info.position;
        // Creat a new song
        mSong = mAdapter.getItem(mSelectedPosition);
        mSelectedId = mSong.mSongId;
        mSongName = mSong.mSongName;
        mAlbumName = mSong.mAlbumName;
        mArtistName = mSong.mArtistName;

        // Play the song next
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE,
                getString(R.string.context_menu_play_next));

        // Add the song to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu);

        // Remove the song from the queue
        menu.add(GROUP_ID, FragmentMenuItems.REMOVE_FROM_QUEUE, Menu.NONE,
                getString(R.string.remove_from_queue));

        // View more content by the song artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
                getString(R.string.context_menu_more_by_artist));

        // Make the song a ringtone
        menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE,
                getString(R.string.context_menu_use_as_ringtone));

        // Delete the song
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_NEXT:
                    NowPlayingCursor queue = (NowPlayingCursor)QueueLoader
                            .makeQueueCursor(getActivity());
                    queue.removeItem(mSelectedPosition);
                    queue.close();
                    queue = null;
                    MusicUtils.playNext(new long[] {
                        mSelectedId
                    });
                    refreshQueue();
                    return true;
                case FragmentMenuItems.REMOVE_FROM_QUEUE:
                    MusicUtils.removeTrack(mSelectedId);
                    refreshQueue();
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(new long[] {
                        mSelectedId
                    }).show(getFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long mPlaylistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), new long[] {
                        mSelectedId
                    }, mPlaylistId);
                    return true;
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(getActivity(), mArtistName);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(getActivity(), mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    DeleteDialog.newInstance(mSong.mSongName, new long[] {
                        mSelectedId
                    }, null).show(getFragmentManager(), "DeleteDialog");
                    return true;
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        // When selecting a track from the queue, just jump there instead of
        // reloading the queue. This is both faster, and prevents accidentally
        // dropping out of party shuffle.
        MusicUtils.setQueuePosition(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new QueueLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
        // Check for any errors
        if (data.isEmpty()) {
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Add the data to the adpater
        for (final Song song : data) {
            mAdapter.add(song);
        }
        // Build the cache
        mAdapter.buildCache();

        // Set the currently playing audio
        mAdapter.setCurrentlyPlayingSongId(MusicUtils.getCurrentAudioId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Song>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getSpeed(final float w, final long t) {
        if (w > 0.8f) {
            return mAdapter.getCount() / 0.001f;
        } else {
            return 10.0f * w;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final int which) {
        mSong = mAdapter.getItem(which);
        mAdapter.remove(mSong);
        mAdapter.notifyDataSetChanged();
        MusicUtils.removeTrack(mSong.mSongId);
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drop(final int from, final int to) {
        mSong = mAdapter.getItem(from);
        mAdapter.remove(mSong);
        mAdapter.insert(mSong, to);
        mAdapter.notifyDataSetChanged();
        MusicUtils.moveQueueItem(from, to);
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * Scrolls the list to the currently playing song when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentSong() {
        final int currentSongPosition = getItemPositionBySong();

        if (currentSongPosition != 0) {
            mListView.setSelection(currentSongPosition);
        }
    }

    /**
     * @return The position of an item in the list based on the name of the
     *         currently playing song.
     */
    private int getItemPositionBySong() {
        final long trackId = MusicUtils.getCurrentAudioId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (mAdapter.getItem(i).mSongId == trackId) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Called to restart the loader callbacks
     */
    public void refreshQueue() {
        if (isAdded()) {
            getLoaderManager().restartLoader(LOADER, null, this);
        }
    }

    /**
     * Used to monitor the state of playback
     */
    private static final class QueueUpdateListener extends BroadcastReceiver {

        private final WeakReference<QueueFragment> mReference;

        /**
         * Constructor of <code>PlaybackStatus</code>
         */
        public QueueUpdateListener(final QueueFragment fragment) {
            mReference = new WeakReference<QueueFragment>(fragment);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // TODO: Invalid options menu if opened?
            final String action = intent.getAction();
            if (action.equals(MusicPlaybackService.META_CHANGED)) {
                mReference.get().mAdapter.setCurrentlyPlayingSongId(MusicUtils.getCurrentAudioId());
            } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                mReference.get().mAdapter.notifyDataSetChanged();
            } else if (action.equals(MusicPlaybackService.QUEUE_CHANGED)) {
                mReference.get().refreshQueue();
            }
        }
    }
}
