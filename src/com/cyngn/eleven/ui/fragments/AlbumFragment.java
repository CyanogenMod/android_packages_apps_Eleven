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

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.AlbumAdapter;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.AlbumLoader;
import com.cyngn.eleven.menu.CreateNewPlaylist;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.ui.activities.BaseActivity;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.utils.PreferenceUtils;
import com.cyngn.eleven.utils.SectionCreatorUtils;
import com.cyngn.eleven.widgets.NoResultsContainer;
import com.viewpagerindicator.TitlePageIndicator;

/**
 * This class is used to display all of the albums on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumFragment extends Fragment implements LoaderCallbacks<SectionListContainer<Album>>,
        OnScrollListener, OnItemClickListener, MusicStateListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 3;

    /**
     * Grid view column count. ONE - list, TWO - normal grid, FOUR - landscape
     */
    private static final int ONE = 1, TWO = 2, FOUR = 4;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * Fragment UI
     */
    private ViewGroup mRootView;

    /**
     * The adapter for the grid
     */
    private SectionAdapter<Album, AlbumAdapter> mAdapter;

    /**
     * The grid view
     */
    private GridView mGridView;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Album song list
     */
    private long[] mAlbumList;

    /**
     * Represents an album
     */
    private Album mAlbum;

    /**
     * True if the list should execute {@code #restartLoader()}.
     */
    private boolean mShouldRefresh = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        // Register the music status listener
        ((BaseActivity)activity).setMusicStateListenerListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int layout = R.layout.grid_items_normal;

        AlbumAdapter adapter = new AlbumAdapter(getActivity(), layout);
        mAdapter = new SectionAdapter<Album, AlbumAdapter>(getActivity(), adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        mRootView = (ViewGroup)inflater.inflate(R.layout.grid_base, null);
        initGridView();
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
        getLoaderManager().initLoader(LOADER, null, this);
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
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        // Create a new album
        mAlbum = mAdapter.getTItem(info.position);
        // Create a list of the album's songs
        mAlbumList = MusicUtils.getSongListForAlbum(getActivity(), mAlbum.mAlbumId);

        // Play the album
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Add the album to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the album to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu);

        // View more content by the album artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
                getString(R.string.context_menu_more_by_artist));

        // Remove the album from the list
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        // Avoid leaking context menu selections
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(getActivity(), mAlbumList, 0, false);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), mAlbumList);
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(mAlbumList).show(getFragmentManager(),
                            "CreatePlaylist");
                    return true;
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(getActivity(), mAlbum.mArtistName);
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long id = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), mAlbumList, id);
                    return true;
                case FragmentMenuItems.DELETE:
                    mShouldRefresh = true;
                    final String album = mAlbum.mAlbumName;
                    DeleteDialog.newInstance(album, mAlbumList,
                            ImageFetcher.generateAlbumCacheKey(album,mAlbum.mArtistName))
                            .show(getFragmentManager(), "DeleteDialog");
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
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.getUnderlyingAdapter().setPauseDiskCache(true);
        } else {
            mAdapter.getUnderlyingAdapter().setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        mAlbum = mAdapter.getTItem(position);
        NavUtils.openAlbumProfile(getActivity(), mAlbum.mAlbumName, mAlbum.mArtistName, mAlbum.mAlbumId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Album>> onCreateLoader(final int id, final Bundle args) {
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
        // Check for any errors
        if (data.mListResults.isEmpty()) {
            // Set the empty text
            final NoResultsContainer empty = (NoResultsContainer)mRootView.findViewById(R.id.no_results_container);
            mGridView.setEmptyView(empty);
            return;
        }

        // Set the data
        mAdapter.setData(data);
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
        getLoaderManager().restartLoader(LOADER, null, this);
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
        if (mShouldRefresh) {
            getLoaderManager().restartLoader(LOADER, null, this);
        }
        mShouldRefresh = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
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
        // Listen for ContextMenus to be created
        list.setOnCreateContextMenuListener(this);
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);
        // To help make scrolling smooth
        list.setOnScrollListener(this);
    }

    /**
     * Sets up the grid view
     */
    private void initGridView() {
        // Initialize the grid
        mGridView = (GridView)mRootView.findViewById(R.id.grid_base);
        // Set the data behind the grid
        mGridView.setAdapter(mAdapter);
        // Set up the helpers
        initAbsListView(mGridView);
        if (ApolloUtils.isLandscape(getActivity())) {
            mGridView.setNumColumns(FOUR);
        } else {
            mGridView.setNumColumns(TWO);
        }
    }
}
