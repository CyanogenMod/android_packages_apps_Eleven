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

package com.cyanogenmod.eleven.ui.fragments.profile;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.MusicStateListener;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.SongAdapter;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.recycler.RecycleHolder;
import com.cyanogenmod.eleven.sectionadapter.SectionAdapter;
import com.cyanogenmod.eleven.sectionadapter.SectionListContainer;
import com.cyanogenmod.eleven.service.MusicPlaybackTrack;
import com.cyanogenmod.eleven.ui.activities.BaseActivity;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.utils.SongPopupMenuHelper;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;
import com.cyanogenmod.eleven.widgets.NoResultsContainer;

import java.util.TreeSet;

/**
 * This class is used to display all of the songs
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BasicSongFragment extends Fragment implements
        LoaderCallbacks<SectionListContainer<Song>>, OnItemClickListener, MusicStateListener {

    /**
     * Fragment UI
     */
    protected ViewGroup mRootView;

    /**
     * The adapter for the list
     */
    protected SectionAdapter<Song, SongAdapter> mAdapter;

    /**
     * The list view
     */
    protected ListView mListView;

    /**
     * Pop up menu helper
     */
    protected PopupMenuHelper mPopupMenuHelper;

    /**
     * This holds the loading progress bar as well as the no results message
     */
    protected LoadingEmptyContainer mLoadingEmptyContainer;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public BasicSongFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPopupMenuHelper = new SongPopupMenuHelper(getActivity(), getFragmentManager()) {
            @Override
            public Song getSong(int position) {
                return mAdapter.getTItem(position);
            }

            @Override
            protected long getSourceId() {
                return getFragmentSourceId();
            }

            @Override
            protected Config.IdType getSourceType() {
                return getFragmentSourceType();
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);
                BasicSongFragment.this.updateMenuIds(set);
            }
        };

        // Create the adapter
        mAdapter = new SectionAdapter<Song, SongAdapter>(getActivity(), createAdapter());
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    protected long getFragmentSourceId() {
        return -1;
    }

    protected Config.IdType getFragmentSourceType() {
        return Config.IdType.NA;
    }

    protected void updateMenuIds(TreeSet<Integer> set) {
        // do nothing - let subclasses override
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup) inflater.inflate(R.layout.list_base, null);
        // set the background on the root view
        mRootView.setBackgroundColor(getResources().getColor(R.color.background_color));
        // Initialize the list
        mListView = (ListView) mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Play the selected song
        mListView.setOnItemClickListener(this);
        // To help make scrolling smooth
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // Pause disk cache access to ensure smoother scrolling
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mAdapter.getUnderlyingAdapter().setPauseDiskCache(true);
                } else {
                    mAdapter.getUnderlyingAdapter().setPauseDiskCache(false);
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });

        // Show progress bar
        mLoadingEmptyContainer = (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        // Setup the container strings
        setupNoResultsContainer(mLoadingEmptyContainer.getNoResultsContainer());
        mListView.setEmptyView(mLoadingEmptyContainer);

        // Register the music status listener
        ((BaseActivity)getActivity()).setMusicStateListenerListener(this);

        return mRootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ((BaseActivity)getActivity()).removeMusicStateListenerListener(this);
    }

    /**
     * This allows subclasses to customize the look and feel of the no results container
     * @param empty NoResultsContainer class
     */
    public void setupNoResultsContainer(final NoResultsContainer empty) {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Start the loader
        getFragmentLoaderManager().initLoader(getLoaderId(), null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        playAll(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<SectionListContainer<Song>> loader,
                               final SectionListContainer<Song> data) {
        if (data.mListResults.isEmpty()) {
            mAdapter.unload();
            mLoadingEmptyContainer.showNoResults();
            return;
        }

        mAdapter.setData(data);
    }

    /**
     * @return Gets the list of song ids from the adapter, or null if none
     */
    protected long[] getSongIdsFromAdapter() {
        if (mAdapter != null) {
            final SongAdapter adapter = mAdapter.getUnderlyingAdapter();
            if (adapter != null) {
                return adapter.getSongIds();
            }
        }

        return null;
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
    public void restartLoader() {
        // Update the list when the user deletes any items
        getFragmentLoaderManager().restartLoader(getLoaderId(), null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<SectionListContainer<Song>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * If the subclasses want to use a customized SongAdapter they can override this method
     * @return the Song adapter
     */
    protected SongAdapter createAdapter() {
        return new SongAdapter(
            getActivity(),
            R.layout.list_item_normal,
            getFragmentSourceId(),
            getFragmentSourceType()
        );
    }

    /**
     * Allow subclasses to specify a different loader manager
     * @return Loader Manager to use
     */
    public LoaderManager getFragmentLoaderManager() {
        return getLoaderManager();
    }

    @Override
    public void onMetaChanged() {
        MusicPlaybackTrack currentTrack = MusicUtils.getCurrentTrack();
        if (mAdapter.getUnderlyingAdapter().setCurrentlyPlayingTrack(currentTrack)) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPlaylistChanged() {
        // Nothing to do
    }

    /**
     * LoaderCallbacks identifier
     */
    public abstract int getLoaderId();

    /**
     * If the user clisk play all
     *
     * @param position the position of the item clicked or -1 if shuffle all
     */
    public abstract void playAll(int position);

}
