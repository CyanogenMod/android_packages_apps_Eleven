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

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.PagerAdapter;
import com.cyanogenmod.eleven.adapters.ProfileSongAdapter;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.dragdrop.DragSortListView;
import com.cyanogenmod.eleven.dragdrop.DragSortListView.DragScrollProfile;
import com.cyanogenmod.eleven.dragdrop.DragSortListView.DropListener;
import com.cyanogenmod.eleven.dragdrop.DragSortListView.RemoveListener;
import com.cyanogenmod.eleven.loaders.PlaylistSongLoader;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.model.Playlist;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.recycler.RecycleHolder;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PlaylistPopupMenuHelper;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.utils.PopupMenuHelper.PopupMenuType;
import com.cyanogenmod.eleven.utils.SongPopupMenuHelper;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;
import com.cyanogenmod.eleven.widgets.NoResultsContainer;

import java.util.List;
import java.util.TreeSet;

public class PlaylistDetailFragment extends FadingBarFragment implements
        LoaderCallbacks<List<Song>>, OnItemClickListener, DropListener,
        RemoveListener, DragScrollProfile, IChildFragment {

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    private DragSortListView mListView;
    private ProfileSongAdapter mAdapter;

    private View mHeaderContainer;
    private ImageView mPlaylistImageView;

    private LoadingEmptyContainer mLoadingEmptyContainer;

    private TextView mNumberOfSongs;
    private TextView mDurationOfPlaylist;

    /**
     * The Id of the playlist the songs belong to
     */
    private long mPlaylistId;
    private String mPlaylistName;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    @Override
    protected String getTitle() { return mPlaylistName; }

    @Override
    protected int getLayoutToInflate() {
        return R.layout.playlist_detail;
    }

    @Override
    protected void onViewCreated() {
        super.onViewCreated();
        setupHero();
        setupSongList();
    }

    private void lookupName() {
        mPlaylistName = MusicUtils.getNameForPlaylist(getActivity(), mPlaylistId);
    }

    @Override // DetailFragment
    protected PopupMenuHelper createActionMenuHelper() {
        return new PlaylistPopupMenuHelper(
                getActivity(), getChildFragmentManager(), PopupMenuType.Playlist) {
            public Playlist getPlaylist(int position) {
                return new Playlist(mPlaylistId, getTitle(), 0);
            }
        };
    }

    @Override // DetailFragment
    protected int getShuffleTitleId() { return R.string.menu_shuffle_playlist; }

    @Override // DetailFragment
    protected void playShuffled() {
        MusicUtils.playPlaylist(getActivity(), mPlaylistId, true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LoaderManager lm = getLoaderManager();
        lm.initLoader(0, getArguments(), this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPopupMenuHelper = new SongPopupMenuHelper(getActivity(), getFragmentManager()) {
            @Override
            public Song getSong(int position) {
                if (position == 0) {
                    return null;
                }

                return mAdapter.getItem(position);
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);

                set.add(FragmentMenuItems.REMOVE_FROM_PLAYLIST);
                set.remove(FragmentMenuItems.DELETE);
            }

            @Override
            protected long getSourceId() {
                return mPlaylistId;
            }

            @Override
            protected Config.IdType getSourceType() {
                return Config.IdType.Playlist;
            }

            @Override
            protected void removeFromPlaylist() {
                mAdapter.remove(mSong);
                mAdapter.buildCache();
                mAdapter.notifyDataSetChanged();
                MusicUtils.removeFromPlaylist(getActivity(), mSong.mSongId, mPlaylistId);
                getLoaderManager().restartLoader(LOADER, null, PlaylistDetailFragment.this);
            }
        };

        mPlaylistId = getArguments().getLong(Config.ID);
        lookupName();
    }

    private void setupHero() {
        mPlaylistImageView = (ImageView)mRootView.findViewById(R.id.image);
        mHeaderContainer = mRootView.findViewById(R.id.playlist_header);
        mNumberOfSongs = (TextView)mRootView.findViewById(R.id.number_of_songs_text);
        mDurationOfPlaylist = (TextView)mRootView.findViewById(R.id.duration_text);

        final ImageFetcher imageFetcher = ImageFetcher.getInstance(getActivity());
        imageFetcher.loadPlaylistArtistImage(mPlaylistId, mPlaylistImageView);
    }

    private void setupSongList() {
        mListView = (DragSortListView) mRootView.findViewById(R.id.list_base);
        mListView.setOnScrollListener(PlaylistDetailFragment.this);

        mAdapter = new ProfileSongAdapter(
                mPlaylistId,
                getActivity(),
                R.layout.edit_track_list_item,
                R.layout.faux_playlist_header
        );
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
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

        // Adjust the progress bar padding to account for the header
        int padTop = getResources().getDimensionPixelSize(R.dimen.playlist_detail_header_height);
        mRootView.findViewById(R.id.progressbar).setPadding(0, padTop, 0, 0);

        // set the loading and empty view container
        mLoadingEmptyContainer =
                (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        setupNoResultsContainer(mLoadingEmptyContainer.getNoResultsContainer());
        mListView.setEmptyView(mLoadingEmptyContainer);
    }

    private void setupNoResultsContainer(final NoResultsContainer container) {
        container.setMainText(R.string.empty_playlist_main);
        container.setSecondaryText(R.string.empty_playlist_secondary);
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
        if (which == 0) {
            return;
        }

        Song song = mAdapter.getItem(which);
        mAdapter.remove(song);
        mAdapter.buildCache();
        mAdapter.notifyDataSetChanged();
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistId);
        getActivity().getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + song.mSongId,
                null);

        MusicUtils.refresh();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drop(int from, int to) {
        from = Math.max(ProfileSongAdapter.NUM_HEADERS, from);
        to = Math.max(ProfileSongAdapter.NUM_HEADERS, to);

        Song song = mAdapter.getItem(from);
        mAdapter.remove(song);
        mAdapter.insert(song, to);
        mAdapter.buildCache();
        mAdapter.notifyDataSetChanged();

        final int realFrom = from - ProfileSongAdapter.NUM_HEADERS;
        final int realTo = to - ProfileSongAdapter.NUM_HEADERS;
        MediaStore.Audio.Playlists.Members.moveItem(getActivity().getContentResolver(),
                mPlaylistId, realFrom, realTo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        if (position == 0) {
            return;
        }
        Cursor cursor = PlaylistSongLoader.makePlaylistSongCursor(getActivity(),
                mPlaylistId);
        final long[] list = MusicUtils.getSongListForCursor(cursor);
        MusicUtils.playAll(getActivity(), list, position - ProfileSongAdapter.NUM_HEADERS,
                mPlaylistId, Config.IdType.Playlist, false);
        cursor.close();
        cursor = null;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        super.onScrollStateChanged(view, scrollState);

        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    protected int getHeaderHeight() { return mHeaderContainer.getHeight(); }

    protected void setHeaderPosition(float y) {
        // Offset the header height to account for the faux header
        y = y - getResources().getDimension(R.dimen.header_bar_height);
        mHeaderContainer.setY(y);
    }

    @Override
    public Loader<List<Song>> onCreateLoader(int i, Bundle bundle) {
        mLoadingEmptyContainer.showLoading();

        return new PlaylistSongLoader(getActivity(), mPlaylistId);
    }

    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
        if (data.isEmpty()) {
            mLoadingEmptyContainer.showNoResults();

            // hide the header container
            mHeaderContainer.setVisibility(View.INVISIBLE);

            // Start fresh
            mAdapter.unload();
        } else {
            // show the header container
            mHeaderContainer.setVisibility(View.VISIBLE);

            // pause notifying the adapter and make changes before re-enabling it so that the list
            // view doesn't reset to the top of the list
            mAdapter.setNotifyOnChange(false);
            // Start fresh
            mAdapter.unload();
            // Return the correct count
            mAdapter.addAll(data);
            // build the cache
            mAdapter.buildCache();
            // re-enable the notify by calling notify dataset changes
            mAdapter.notifyDataSetChanged();
            // set the number of songs
            String numberOfSongs = MusicUtils.makeLabel(getActivity(), R.plurals.Nsongs,
                    data.size());
            mNumberOfSongs.setText(numberOfSongs);

            long duration = 0;

            // Add the data to the adapter
            for (final Song song : data) {
                duration += song.mDuration;
            }

            // set the duration
            String durationString = MusicUtils.makeLongTimeString(getActivity(), duration);
            mDurationOfPlaylist.setText(durationString);
        }
    }

    @Override
    public void onLoaderReset(final Loader<List<Song>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    @Override
    public void restartLoader() {
        lookupName(); // playlist name may have changed
        if(mPlaylistName == null) {
            // if name is null, we've been deleted, so close the this fragment
            getContainingActivity().postRemoveFragment(this);
            return;
        }

        // since onCreateOptionsMenu can be called after onCreate it is possible for
        // mActionMenuHelper to be null.  In this case, don't bother updating the Name since when
        // it does create it, it will use the updated name anyways
        if (mActionMenuHelper != null) {
            // update action bar title and popup menu handler
            ((PlaylistPopupMenuHelper) mActionMenuHelper).updateName(mPlaylistName);
        }

        getContainingActivity().setActionBarTitle(mPlaylistName);
        // and reload the song list
        getLoaderManager().restartLoader(0, getArguments(), this);
    }

    @Override
    public void onMetaChanged() {
        super.onMetaChanged();

        mAdapter.setCurrentlyPlayingTrack(MusicUtils.getCurrentTrack());
    }

    @Override
    public void onPlaylistChanged() {
        super.onPlaylistChanged();

        restartLoader();
    }

    @Override
    public PagerAdapter.MusicFragments getMusicFragmentParent() {
        return PagerAdapter.MusicFragments.PLAYLIST;
    }
}
