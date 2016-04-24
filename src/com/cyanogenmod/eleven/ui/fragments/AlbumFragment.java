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

package com.cyanogenmod.eleven.ui.fragments;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import com.cyanogenmod.eleven.MusicStateListener;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.AlbumAdapter;
import com.cyanogenmod.eleven.adapters.PagerAdapter;
import com.cyanogenmod.eleven.loaders.AlbumLoader;
import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.recycler.RecycleHolder;
import com.cyanogenmod.eleven.sectionadapter.SectionCreator;
import com.cyanogenmod.eleven.sectionadapter.SectionListContainer;
import com.cyanogenmod.eleven.ui.activities.BaseActivity;
import com.cyanogenmod.eleven.ui.fragments.phone.MusicBrowserFragment;
import com.cyanogenmod.eleven.utils.AlbumPopupMenuHelper;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.NavUtils;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;

/**
 * This class is used to display all of the albums on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumFragment extends MusicBrowserFragment implements
        LoaderCallbacks<SectionListContainer<Album>>, OnScrollListener,
        OnItemClickListener, MusicStateListener {

    /**
     * Grid view column count. ONE - list, TWO - normal grid, FOUR - landscape
     */
    private static final int TWO = 2, FOUR = 4;

    /**
     * Fragment UI
     */
    private ViewGroup mRootView;

    /**
     * The adapter for the grid
     */
    private AlbumAdapter mAdapter;

    /**
     * The grid view
     */
    private GridView mGridView;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * This holds the loading progress bar as well as the no results message
     */
    private LoadingEmptyContainer mLoadingEmptyContainer;

    @Override
    public int getLoaderId() {
        return PagerAdapter.MusicFragments.ALBUM.ordinal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPopupMenuHelper = new AlbumPopupMenuHelper(getActivity(), getFragmentManager()) {
            public Album getAlbum(int position) {
                return mAdapter.getItem(position);
            }
        };

        int layout = R.layout.grid_items_normal;

        mAdapter = new AlbumAdapter(getActivity(), layout);
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
        mRootView = (ViewGroup)inflater.inflate(R.layout.grid_base, null);
        initGridView();

        // Register the music status listener
        ((BaseActivity)getActivity()).setMusicStateListenerListener(this);

        return mRootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        initLoader(null, this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ((BaseActivity)getActivity()).removeMusicStateListenerListener(this);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        mAdapter.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        Album album = mAdapter.getItem(position);
        NavUtils.openAlbumProfile(getActivity(), album.mAlbumName, album.mArtistName, album.mAlbumId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Album>> onCreateLoader(final int id, final Bundle args) {
        mLoadingEmptyContainer.showLoading();
        // if we ever decide to add section headers for grid items, we can pass a compartor
        // instead of null
        return new SectionCreator<Album>(getActivity(), new AlbumLoader(getActivity()), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<SectionListContainer<Album>> loader,
                               final SectionListContainer<Album> data) {
        if (data.mListResults.isEmpty()) {
            mAdapter.unload();
            mLoadingEmptyContainer.showNoResults();
            return;
        }

        mAdapter.setData(data.mListResults);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<SectionListContainer<Album>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * Scrolls the list to the currently playing album when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentAlbum() {
        final int currentAlbumPosition = getItemPositionByAlbum();

        if (currentAlbumPosition != 0) {
            mGridView.setSelection(currentAlbumPosition);
        }
    }

    /**
     * @return The position of an item in the list or grid based on the id of
     *         the currently playing album.
     */
    private int getItemPositionByAlbum() {
        final long albumId = MusicUtils.getCurrentAlbumId();
        if (mAdapter == null) {
            return 0;
        }

        int position = mAdapter.getItemPosition(albumId);

        // if for some reason we don't find the item, just jump to the top
        if (position < 0) {
            return 0;
        }

        return position;
    }

    /**
     * Restarts the loader.
     */
    public void refresh() {
        // Wait a moment for the preference to change.
        SystemClock.sleep(10);
        restartLoader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        // Update the list when the user deletes any items
        restartLoader(null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // Nothing to do
    }

    @Override
    public void onPlaylistChanged() {
        // Nothing to do
    }

    /**
     * Sets up various helpers for both the list and grid
     *
     * @param list The list or grid
     */
    private void initAbsListView(final AbsListView list) {
        // Release any references to the recycled Views
        list.setRecyclerListener(new RecycleHolder());
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);
        // To help make scrolling smooth
        list.setOnScrollListener(this);
    }

    /**
     * Sets up the grid view
     */
    private void initGridView() {
        int columns = ApolloUtils.isLandscape(getActivity()) ? FOUR : TWO;
        mAdapter.setNumColumns(columns);
        // Initialize the grid
        mGridView = (GridView)mRootView.findViewById(R.id.grid_base);
        // Set the data behind the grid
        mGridView.setAdapter(mAdapter);
        // Set up the helpers
        initAbsListView(mGridView);
        mGridView.setNumColumns(columns);

        // Show progress bar
        mLoadingEmptyContainer = (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        mGridView.setEmptyView(mLoadingEmptyContainer);
    }
}
