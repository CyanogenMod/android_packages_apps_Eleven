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

package com.cyngn.eleven.ui.fragments.profile;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.SongAdapter;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.ui.activities.BaseActivity;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.NoResultsContainer;

import java.util.ArrayList;

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
     * True if the list should execute {@code #restartLoader()}.
     */
    protected boolean mShouldRefresh = false;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public BasicSongFragment() {
    }

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
        mPopupMenuHelper = new PopupMenuHelper(getActivity(), getFragmentManager()) {
            /**
             * Represents a song
             */
            protected Song mSong;

            @Override
            protected PopupMenuHelper.PopupMenuType onPreparePopupMenu(int position) {
                // Create a new song
                mSong = mAdapter.getTItem(position);

                return PopupMenuType.Song;
            }

            @Override
            protected void getAdditionalIds(PopupMenuType type, ArrayList<Integer> list) {
                super.getAdditionalIds(type, list);
                BasicSongFragment.this.getAdditionaIdsForType(list);
            }

            @Override
            protected long[] getIdList() {
                return new long[] { mSong.mSongId };
            }

            @Override
            protected int getGroupId() {
                return BasicSongFragment.this.getGroupId();
            }

            @Override
            protected void onDeleteClicked() {
                mShouldRefresh = true;
                DeleteDialog.newInstance(mSong.mSongName, getIdList(), null).show(
                        getFragmentManager(), "DeleteDialog");
            }

            @Override
            protected void setShouldRefresh() {
                mShouldRefresh = true;
            }

            @Override
            protected long getId() {
                return mSong.mSongId;
            }

            @Override
            protected String getArtistName() {
                return mSong.mArtistName;
            }
        };

        // Create the adapter
        mAdapter = createAdapter();
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    protected void getAdditionaIdsForType(ArrayList<Integer> list) {
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
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                        || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
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

        return mRootView;
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
        getLoaderManager().initLoader(getLoaderId(), null, this);
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
        // Check for any errors
        if (data.mListResults.isEmpty()) {
            // Set the empty text
            final NoResultsContainer empty =
                    (NoResultsContainer)mRootView.findViewById(R.id.no_results_container);
            // Setup the container strings
            setupNoResultsContainer(empty);
            // set the empty view into the list view
            mListView.setEmptyView(empty);
            return;
        }

        // Start fresh
        mAdapter.setData(data);
    }

    /**
     * Restarts the loader.
     */
    public void refresh() {
        // Wait a moment for the preference to change.
        SystemClock.sleep(10);
        getLoaderManager().restartLoader(getLoaderId(), null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        // Update the list when the user deletes any items
        if (mShouldRefresh) {
            getLoaderManager().restartLoader(getLoaderId(), null, this);
        }
        mShouldRefresh = false;
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
    protected SectionAdapter<Song, SongAdapter> createAdapter() {
        return new SectionAdapter(getActivity(),
                new SongAdapter(
                    getActivity(),
                    R.layout.list_item_normal
                )
        );
    }

    @Override
    public void onMetaChanged() {
        // do nothing
    }

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    public abstract int getGroupId();

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
