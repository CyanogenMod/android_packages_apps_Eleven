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

import static com.cyngn.eleven.utils.MusicUtils.mService;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.SongAdapter;
import com.cyngn.eleven.dragdrop.DragSortListView;
import com.cyngn.eleven.dragdrop.DragSortListView.DragScrollProfile;
import com.cyngn.eleven.dragdrop.DragSortListView.DropListener;
import com.cyngn.eleven.dragdrop.DragSortListView.RemoveListener;
import com.cyngn.eleven.loaders.NowPlayingCursor;
import com.cyngn.eleven.loaders.QueueLoader;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.service.MusicPlaybackTrack;
import com.cyngn.eleven.ui.activities.SlidingPanelActivity;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.NoResultsContainer;
import com.cyngn.eleven.widgets.LoadingEmptyContainer;
import com.cyngn.eleven.widgets.PlayPauseProgressButton;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * This class is used to display all of the songs in the queue.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class QueueFragment extends Fragment implements LoaderCallbacks<List<Song>>,
        OnItemClickListener, DropListener, RemoveListener, DragScrollProfile, ServiceConnection {

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
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * Root view
     */
    private ViewGroup mRootView;

    /**
     * This holds the loading progress bar as well as the no results message
     */
    private LoadingEmptyContainer mLoadingEmptyContainer;

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
        mPopupMenuHelper = new PopupMenuHelper(getActivity(), getFragmentManager()) {
            private Song mSong;
            private int mSelectedPosition;
            private MusicPlaybackTrack mSelectedTrack;

            @Override
            public PopupMenuType onPreparePopupMenu(int position) {
                mSelectedPosition = position;
                mSong = mAdapter.getItem(mSelectedPosition);
                mSelectedTrack = MusicUtils.getTrack(mSelectedPosition);

                return PopupMenuType.Queue;
            }

            @Override
            protected long[] getIdList() {
                return new long[] { mSong.mSongId };
            }

            @Override
            protected long getSourceId() {
                if (mSelectedTrack == null) {
                    return -1;
                }

                return mSelectedTrack.mSourceId;
            }

            @Override
            protected Config.IdType getSourceType() {
                if (mSelectedTrack == null) {
                    return Config.IdType.NA;
                }

                return mSelectedTrack.mSourceType;
            }

            @Override
            protected String getArtistName() {
                return mSong.mArtistName;
            }

            @Override
            protected void onDeleteClicked() {
                DeleteDialog.newInstance(mSong.mSongName,
                        new long[] { getId() }, null).show(getFragmentManager(), "DeleteDialog");
            }

            @Override
            protected void playNext() {
                NowPlayingCursor queue = (NowPlayingCursor)QueueLoader
                        .makeQueueCursor(getActivity());
                queue.removeItem(mSelectedPosition);
                queue.close();
                queue = null;
                MusicUtils.playNext(getIdList(), getSourceId(), getSourceType());
                refreshQueue();
            }

            @Override
            protected void removeFromQueue() {
                MusicUtils.removeTrack(getId());
                refreshQueue();
            }
        };

        // Create the adapter
        mAdapter = new SongAdapter(getActivity(), R.layout.edit_queue_list_item,
                -1, Config.IdType.NA);
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        // Initialize the list
        mListView = (DragSortListView)mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
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
        // Setup the loading and empty state
        mLoadingEmptyContainer =
                (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        // Setup the container strings
        setupNoResultsContainer(mLoadingEmptyContainer.getNoResultsContainer());
        mListView.setEmptyView(mLoadingEmptyContainer);
        return mRootView;
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
                    PlayPauseProgressButton button =
                            (PlayPauseProgressButton) childView.findViewById(R.id.playPauseProgressButton);
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
        mLoadingEmptyContainer.showLoading();
        return new QueueLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
        mAdapter.unload(); // Start fresh

        if (data.isEmpty()) {
            mLoadingEmptyContainer.showNoResults();
            mAdapter.setCurrentQueuePosition(SongAdapter.NOTHING_PLAYING);
            ((SlidingPanelActivity)getActivity()).clearMetaInfo();
        } else {
            // Add the songs found to the adapter
            for (final Song song : data) { mAdapter.add(song); }
            // Build the cache
            mAdapter.buildCache();
            // Set the currently playing audio
            mAdapter.setCurrentQueuePosition(MusicUtils.getQueuePosition());
        }
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
        Song song = mAdapter.getItem(which);
        mAdapter.remove(song);
        mAdapter.notifyDataSetChanged();
        MusicUtils.removeTrack(song.mSongId);
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drop(final int from, final int to) {
        Song song = mAdapter.getItem(from);
        mAdapter.remove(song);
        mAdapter.insert(song, to);
        mAdapter.notifyDataSetChanged();
        MusicUtils.moveQueueItem(from, to);
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * Called to restart the loader callbacks
     */
    public void refreshQueue() {
        if (isAdded()) {
            getLoaderManager().restartLoader(LOADER, null, this);
        }
    }

    private void setupNoResultsContainer(NoResultsContainer empty) {
        int color = getResources().getColor(R.color.no_results_light);
        empty.setTextColor(color);
        empty.setMainText(R.string.empty_queue_main);
        empty.setSecondaryText(R.string.empty_queue_secondary);
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
                mReference.get().mAdapter.setCurrentQueuePosition(MusicUtils.getQueuePosition());
            } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                mReference.get().mAdapter.notifyDataSetChanged();
            } else if (action.equals(MusicPlaybackService.QUEUE_CHANGED)) {
                mReference.get().refreshQueue();
            }
        }
    }
}
