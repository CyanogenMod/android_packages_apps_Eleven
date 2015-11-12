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

package com.cyanogenmod.eleven.ui.activities;

import android.app.ActionBar;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.IElevenService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.SummarySearchAdapter;
import com.cyanogenmod.eleven.loaders.WrappedAsyncTaskLoader;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.model.AlbumArtistDetails;
import com.cyanogenmod.eleven.model.SearchResult;
import com.cyanogenmod.eleven.model.SearchResult.ResultType;
import com.cyanogenmod.eleven.provider.SearchHistory;
import com.cyanogenmod.eleven.recycler.RecycleHolder;
import com.cyanogenmod.eleven.sectionadapter.SectionAdapter;
import com.cyanogenmod.eleven.sectionadapter.SectionCreator;
import com.cyanogenmod.eleven.sectionadapter.SectionCreator.SimpleListLoader;
import com.cyanogenmod.eleven.sectionadapter.SectionListContainer;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.MusicUtils.ServiceToken;
import com.cyanogenmod.eleven.utils.NavUtils;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.utils.SectionCreatorUtils;
import com.cyanogenmod.eleven.utils.SectionCreatorUtils.IItemCompare;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;
import com.cyanogenmod.eleven.widgets.NoResultsContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static android.view.View.OnTouchListener;
import static com.cyanogenmod.eleven.utils.MusicUtils.mService;

/**
 * Provides the search interface for Apollo.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SearchActivity extends FragmentActivity implements
        LoaderCallbacks<SectionListContainer<SearchResult>>,
        OnScrollListener, OnQueryTextListener, OnItemClickListener, ServiceConnection,
        OnTouchListener {
    /**
     * Loading delay of 500ms so we don't flash the screen too much when loading new searches
     */
    private static int LOADING_DELAY = 500;

    /**
     * Identifier for the search loader
     */
    private static int SEARCH_LOADER = 0;

    /**
     * Identifier for the search history loader
     */
    private static int HISTORY_LOADER = 1;

    /**
     * The service token
     */
    private ServiceToken mToken;

    /**
     * The query
     */
    private String mFilterString;

    /**
     * List view
     */
    private ListView mListView;

    /**
     * Used the filter the user's music
     */
    private SearchView mSearchView;

    /**
     * IME manager
     */
    private InputMethodManager mImm;

    /**
     * The view that container the no search results text and the loading progress bar
     */
    private LoadingEmptyContainer mLoadingEmptyContainer;

    /**
     * List view adapter
     */
    private SectionAdapter<SearchResult, SummarySearchAdapter> mAdapter;

    /**
     * boolean tracking whether this is the search level when the user first enters search
     * or if the user has clicked show all
     */
    private boolean mTopLevelSearch;

    /**
     * If the user has clicked show all, this tells us what type (Artist, Album, etc)
     */
    private ResultType mSearchType;

    /**
     * Search History loader callback
     */
    private SearchHistoryCallback mSearchHistoryCallback;

    /**
     * List view
     */
    private ListView mSearchHistoryListView;

    /**
     * This tracks our current visible state between the different views
      */
    enum VisibleState {
        SearchHistory,
        Empty,
        SearchResults,
        Loading,
    }

    private VisibleState mCurrentState;

    /**
     * Handler for posting runnables
     */
    private Handler mHandler;

    /**
     * A runnable to show the loading view that will be posted with a delay to prevent flashing
     */
    private Runnable mLoadingRunnable;

    /**
     * Flag used to track if we are quitting so we don't flash loaders while finishing the activity
     */
    private boolean mQuitting = false;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPopupMenuHelper = new PopupMenuHelper(this, getSupportFragmentManager()) {
            private SearchResult mSelectedItem;

            @Override
            public PopupMenuType onPreparePopupMenu(int position) {
                mSelectedItem = mAdapter.getTItem(position);

                return PopupMenuType.SearchResult;
            }

            @Override
            protected long[] getIdList() {
                switch (mSelectedItem.mType) {
                    case Artist:
                        return MusicUtils.getSongListForArtist(SearchActivity.this,
                                mSelectedItem.mId);
                    case Album:
                        return MusicUtils.getSongListForAlbum(SearchActivity.this,
                                mSelectedItem.mId);
                    case Song:
                        return new long[] { mSelectedItem.mId };
                    case Playlist:
                        return MusicUtils.getSongListForPlaylist(SearchActivity.this,
                                mSelectedItem.mId);
                    default:
                        return null;
                }
            }

            @Override
            protected long getSourceId() {
                return mSelectedItem.mId;
            }

            @Override
            protected Config.IdType getSourceType() {
                return mSelectedItem.mType.getSourceType();
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);

                if (mSelectedItem.mType == ResultType.Album) {
                    set.add(FragmentMenuItems.MORE_BY_ARTIST);
                }
            }

            @Override
            protected String getArtistName() {
                return mSelectedItem.mArtist;
            }
        };

        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Set the layout
        setContentView(R.layout.activity_search);

        // get the input method manager
        mImm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Initialize the adapter
        SummarySearchAdapter adapter = new SummarySearchAdapter(this);
        mAdapter = new SectionAdapter<SearchResult, SummarySearchAdapter>(this, adapter);
        // Set the prefix
        mAdapter.getUnderlyingAdapter().setPrefix(mFilterString);
        mAdapter.setupHeaderParameters(R.layout.list_search_header, false);
        mAdapter.setupFooterParameters(R.layout.list_search_footer, true);
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });

        mLoadingEmptyContainer = (LoadingEmptyContainer) findViewById(R.id.loading_empty_container);
        // setup the no results container
        NoResultsContainer noResults = mLoadingEmptyContainer.getNoResultsContainer();
        noResults.setMainText(R.string.empty_search);
        noResults.setSecondaryText(R.string.empty_search_check);

        initListView();

        // setup handler and runnable
        mHandler = new Handler();
        mLoadingRunnable = new Runnable() {
            @Override
            public void run() {
                setState(VisibleState.Loading);
            }
        };

        // Theme the action bar
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Get the query String
        mFilterString = getIntent().getStringExtra(SearchManager.QUERY);

        // if we have a non-empty search string, this is a 2nd lvl search
        if (!TextUtils.isEmpty(mFilterString)) {
            mTopLevelSearch = false;

            // get the search type to filter by
            int type = getIntent().getIntExtra(SearchManager.SEARCH_MODE, -1);
            if (type >= 0 && type < ResultType.values().length) {
                mSearchType = ResultType.values()[type];
            }

            int resourceId = 0;
            switch (mSearchType) {
                case Artist:
                    resourceId = R.string.search_title_artists;
                    break;
                case Album:
                    resourceId = R.string.search_title_albums;
                    break;
                case Playlist:
                    resourceId = R.string.search_title_playlists;
                    break;
                case Song:
                    resourceId = R.string.search_title_songs;
                    break;
            }
            actionBar.setTitle(getString(resourceId, mFilterString).toUpperCase());
            actionBar.setDisplayHomeAsUpEnabled(true);

            // Set the prefix
            mAdapter.getUnderlyingAdapter().setPrefix(mFilterString);

            // Start the loader for the query
            getSupportLoaderManager().initLoader(SEARCH_LOADER, null, this);
        } else {
            mTopLevelSearch = true;
            mSearchHistoryCallback = new SearchHistoryCallback();

            // Start the loader for the search history
            getSupportLoaderManager().initLoader(HISTORY_LOADER, null, mSearchHistoryCallback);
        }
    }

    /**
     * Sets up the list view
     */
    private void initListView() {
        // Initialize the grid
        mListView = (ListView)findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Show the albums and songs from the selected artist
        mListView.setOnItemClickListener(this);
        // To help make scrolling smooth
        mListView.setOnScrollListener(this);
        // sets the touch listener
        mListView.setOnTouchListener(this);
        // If we setEmptyView with mLoadingEmptyContainer it causes a crash in DragSortListView
        // when updating the search.  For now let's manually toggle visibility and come back
        // to this later
        //mListView.setEmptyView(mLoadingEmptyContainer);

        // load the search history list view
        mSearchHistoryListView = (ListView)findViewById(R.id.list_search_history);
        mSearchHistoryListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String searchItem = (String)mSearchHistoryListView.getAdapter().getItem(position);
                mSearchView.setQuery(searchItem, true);
            }
        });
        mSearchHistoryListView.setOnTouchListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<SearchResult>> onCreateLoader(final int id,
                                                                     final Bundle args) {
        IItemCompare<SearchResult> comparator = null;

        // prep the loader in case the query takes a long time
        setLoading();

        // if we are at the top level, create a comparator to separate the different types into
        // their own sections (artists, albums, etc)
        if (mTopLevelSearch) {
            comparator = SectionCreatorUtils.createSearchResultComparison(this);
        }

        return new SectionCreator<SearchResult>(this,
                new SummarySearchLoader(this, mFilterString, mSearchType),
                comparator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // if we are not a top level search view, we do not need to create the search fields
        if (!mTopLevelSearch) {
            return super.onCreateOptionsMenu(menu);
        }

        // Search view
        getMenuInflater().inflate(R.menu.search, menu);

        // Filter the list the user is looking it via SearchView
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView)searchItem.getActionView();
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setQueryHint(getString(R.string.searchHint).toUpperCase());

        // The SearchView has no way for you to customize or get access to the search icon in a
        // normal fashion, so we need to manually look for the icon and change the
        // layout params to hide it
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setIconified(false);
        int searchButtonId = getResources().getIdentifier("android:id/search_mag_icon", null, null);
        ImageView searchIcon = (ImageView)mSearchView.findViewById(searchButtonId);
        searchIcon.setLayoutParams(new LinearLayout.LayoutParams(0, 0));

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                quit();
                return false;
            }
        });

        menu.findItem(R.id.menu_search).expandActionView();

        return super.onCreateOptionsMenu(menu);
    }

    private void quit() {
        mQuitting = true;
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                quit();
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
    public void onLoadFinished(final Loader<SectionListContainer<SearchResult>> loader,
                               final SectionListContainer<SearchResult> data) {
        // Check for any errors
        if (data.mListResults.isEmpty()) {
            // clear the adapter
            mAdapter.clear();
            // show the empty state
            setState(VisibleState.Empty);
        } else {
            // Set the data
            mAdapter.setData(data);
            // show the search results
            setState(VisibleState.SearchResults);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<SectionListContainer<SearchResult>> loader) {
        mAdapter.unload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
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
    public boolean onQueryTextSubmit(final String query) {
        // simulate an on query text change
        onQueryTextChange(query);
        // hide the input manager
        hideInputManager();

        return true;
    }

    public void hideInputManager() {
        // When the search is "committed" by the user, then hide the keyboard so
        // the user can more easily browse the list of results.
        if (mSearchView != null) {
            if (mImm != null) {
                mImm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();

            // add our search string
            SearchHistory.getInstance(this).addSearchString(mFilterString);
        }
    }

    /**
     * This posts a delayed for showing the loading screen.  The reason for the delayed is we
     * don't want to flash the loading icon very often since searches usually are pretty fast
     */
    public void setLoading() {
        if (mCurrentState != VisibleState.Loading) {
            if (!mHandler.hasCallbacks(mLoadingRunnable)) {
                mHandler.postDelayed(mLoadingRunnable, LOADING_DELAY);
            }
        }
    }

    /**
     * Sets the currently visible view
     * @param state the current visible state
     */
    public void setState(VisibleState state) {
        // remove any delayed runnables.  This has to be before mCurrentState == state
        // in case the state doesn't change but we've created a loading runnable
        mHandler.removeCallbacks(mLoadingRunnable);

        // if we are already looking at view already, just quit
        if (mCurrentState == state) {
            return;
        }

        mCurrentState = state;

        mSearchHistoryListView.setVisibility(View.INVISIBLE);
        mListView.setVisibility(View.INVISIBLE);
        mLoadingEmptyContainer.setVisibility(View.INVISIBLE);

        switch (mCurrentState) {
            case SearchHistory:
                mSearchHistoryListView.setVisibility(View.VISIBLE);
                break;
            case SearchResults:
                mListView.setVisibility(View.VISIBLE);
                break;
            case Empty:
                mLoadingEmptyContainer.setVisibility(View.VISIBLE);
                mLoadingEmptyContainer.showNoResults();
                break;
            case Loading:
                mLoadingEmptyContainer.setVisibility(View.VISIBLE);
                mLoadingEmptyContainer.showLoading();
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onQueryTextChange(final String newText) {
        if (mQuitting) {
            return true;
        }

        if (TextUtils.isEmpty(newText)) {
            if (!TextUtils.isEmpty(mFilterString)) {
                mFilterString = "";
                getSupportLoaderManager().restartLoader(HISTORY_LOADER, null,
                        mSearchHistoryCallback);
                getSupportLoaderManager().destroyLoader(SEARCH_LOADER);
            }

            return true;
        }

        // if the strings are the same, return
        if (newText.equals(mFilterString)) {
            return true;
        }

        // Called when the action bar search text has changed. Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        mFilterString = newText;
        // Set the prefix
        mAdapter.getUnderlyingAdapter().setPrefix(mFilterString);
        getSupportLoaderManager().restartLoader(SEARCH_LOADER, null, this);
        getSupportLoaderManager().destroyLoader(HISTORY_LOADER);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        if (mAdapter.isSectionFooter(position)) {
            // since a footer should be after a list item by definition, let's look up the type
            // of the previous item
            SearchResult item = mAdapter.getTItem(position - 1);
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchManager.QUERY, mFilterString);
            intent.putExtra(SearchManager.SEARCH_MODE, item.mType.ordinal());
            startActivity(intent);
        } else {
            SearchResult item = mAdapter.getTItem(position);
            switch (item.mType) {
                case Artist:
                    NavUtils.openArtistProfile(this, item.mArtist);
                    break;
                case Album:
                    NavUtils.openAlbumProfile(this, item.mAlbum, item.mArtist, item.mId);
                    break;
                case Playlist:
                    NavUtils.openPlaylist(this, item.mId, item.mTitle);
                    break;
                case Song:
                    // If it's a song, play it and leave
                    final long[] list = new long[]{
                            item.mId
                    };
                    MusicUtils.playAll(this, list, 0, -1, Config.IdType.NA, false);
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = IElevenService.Stub.asInterface(service);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
    }

    /**
     * This class loads a search result summary of items
     */
    private static final class SummarySearchLoader extends SimpleListLoader<SearchResult> {
        private final String mQuery;
        private final ResultType mSearchType;

        public SummarySearchLoader(final Context context, final String query,
                                   final ResultType searchType) {
            super(context);
            mQuery = query;
            mSearchType = searchType;
        }

        /**
         * This creates a search result given the data at the cursor position
         * @param cursor at the position for the item
         * @param type the type of item to create
         * @return the search result
         */
        protected SearchResult createSearchResult(final Cursor cursor, ResultType type) {
            SearchResult item = null;

            switch (type) {
                case Playlist:
                    item = SearchResult.createPlaylistResult(cursor);
                    item.mSongCount = MusicUtils.getSongCountForPlaylist(getContext(), item.mId);
                    break;
                case Song:
                    item = SearchResult.createSearchResult(cursor);
                    if (item != null) {
                        AlbumArtistDetails details = MusicUtils.getAlbumArtDetails(getContext(),
                                item.mId);
                        if (details != null) {
                            item.mArtist = details.mArtistName;
                            item.mAlbum = details.mAlbumName;
                            item.mAlbumId = details.mAlbumId;
                        }
                    }
                    break;
                case Album:
                case Artist:
                default:
                    item = SearchResult.createSearchResult(cursor);
                    break;
            }

            return item;
        }

        @Override
        public List<SearchResult> loadInBackground() {
            // if we are doing a specific type search, run that one
            if (mSearchType != null && mSearchType != ResultType.Unknown) {
                return runSearchForType();
            }

            return runGenericSearch();
        }

        /**
         * This creates a search for a specific type given a filter string.  This will return the
         * full list of results that matches those two requirements
         * @return the results for that search
         */
        protected List<SearchResult> runSearchForType() {
            ArrayList<SearchResult> results = new ArrayList<SearchResult>();
            Cursor cursor = null;
            try {
                if (mSearchType == ResultType.Playlist) {
                    cursor = makePlaylistSearchCursor(getContext(), mQuery);
                } else {
                    cursor = ApolloUtils.createSearchQueryCursor(getContext(), mQuery);
                }

                // pre-cache this index
                final int mimeTypeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        boolean addResult = true;

                        if (mSearchType != ResultType.Playlist) {
                            // get the result type
                            ResultType type = ResultType.getResultType(cursor, mimeTypeIndex);
                            if (type != mSearchType) {
                                addResult = false;
                            }
                        }

                        if (addResult) {
                            results.add(createSearchResult(cursor, mSearchType));
                        }
                    } while (cursor.moveToNext());
                }

            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            return results;
        }

        /**
         * This will run a search given a filter string and return the top NUM_RESULTS_TO_GET per
         * type
         * @return the results for that search
         */
        public List<SearchResult> runGenericSearch() {
            ArrayList<SearchResult> results = new ArrayList<SearchResult>();
            // number of types to query for
            final int numTypes = ResultType.getNumTypes();

            // number of results we want
            final int numResultsNeeded = Config.SEARCH_NUM_RESULTS_TO_GET * numTypes;

            // current number of results we have
            int numResultsAdded = 0;

            // count for each result type
            int[] numOfEachType = new int[numTypes];

            // search playlists first
            Cursor playlistCursor = makePlaylistSearchCursor(getContext(), mQuery);
            if (playlistCursor != null && playlistCursor.moveToFirst()) {
                do {
                    // create the item
                    SearchResult item = createSearchResult(playlistCursor, ResultType.Playlist);
                    /// add the results
                    numResultsAdded++;
                    results.add(item);
                } while (playlistCursor.moveToNext()
                        && numResultsAdded < Config.SEARCH_NUM_RESULTS_TO_GET);

                // because we deal with playlists separately,
                // just mark that we have the full # of playlists
                // so that logic later can quit out early if full
                numResultsAdded = Config.SEARCH_NUM_RESULTS_TO_GET;

                // close the cursor
                playlistCursor.close();
                playlistCursor = null;
            }

            // do fancy audio search
            Cursor cursor = ApolloUtils.createSearchQueryCursor(getContext(), mQuery);

            // pre-cache this index
            final int mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);

            // walk through the cursor
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    // get the result type
                    ResultType type = ResultType.getResultType(cursor, mimeTypeIndex);

                    // if we still need this type
                    if (numOfEachType[type.ordinal()] < Config.SEARCH_NUM_RESULTS_TO_GET) {
                        // get the search result
                        SearchResult item = createSearchResult(cursor, type);

                        if (item != null) {
                            // add it
                            results.add(item);
                            numOfEachType[type.ordinal()]++;
                            numResultsAdded++;

                            // if we have enough then quit
                            if (numResultsAdded >= numResultsNeeded) {
                                break;
                            }
                        }
                    }
                } while (cursor.moveToNext());

                cursor.close();
                cursor = null;
            }

            // sort our results
            Collections.sort(results, SearchResult.COMPARATOR);

            return results;
        }

        public static Cursor makePlaylistSearchCursor(final Context context,
                                                      final String searchTerms) {
            if (TextUtils.isEmpty(searchTerms)) {
                return null;
            }

            // trim out special characters like % or \ as well as things like "a" "and" etc
            String trimmedSearchTerms = MusicUtils.getTrimmedName(searchTerms);

            if (TextUtils.isEmpty(trimmedSearchTerms)) {
                return null;
            }

            String[] keywords = trimmedSearchTerms.split(" ");

            // prep the keyword for like search
            for (int i = 0; i < keywords.length; i++) {
                keywords[i] = "%" + keywords[i] + "%";
            }

            String where = "";

            // make the where clause
            for (int i = 0; i < keywords.length; i++) {
                if (i == 0) {
                    where = "name LIKE ?";
                } else {
                    where += " AND name LIKE ?";
                }
            }

            return context.getContentResolver().query(
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    new String[]{
                        /* 0 */
                            BaseColumns._ID,
                        /* 1 */
                            MediaStore.Audio.PlaylistsColumns.NAME
                    }, where, keywords, MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER);
        }
    }

    /**
     * Loads the search history in the background and creates an array adapter
     */
    public static class SearchHistoryLoader extends WrappedAsyncTaskLoader<ArrayAdapter<String>> {
        public SearchHistoryLoader(Context context) {
            super(context);
        }

        @Override
        public ArrayAdapter<String> loadInBackground() {
            ArrayList<String> strings = SearchHistory.getInstance(getContext()).getRecentSearches();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                    R.layout.list_item_search_history, R.id.line_one);
            adapter.addAll(strings);
            return adapter;
        }
    }

    /**
     * This handles the Loader callbacks for the search history
     */
    public class SearchHistoryCallback implements LoaderCallbacks<ArrayAdapter<String>> {
        @Override
        public Loader<ArrayAdapter<String>> onCreateLoader(int i, Bundle bundle) {
            // prep the loader in case the query takes a long time
            setLoading();

            return new SearchHistoryLoader(SearchActivity.this);
        }

        @Override
        public void onLoadFinished(Loader<ArrayAdapter<String>> searchHistoryAdapterLoader,
                                   ArrayAdapter<String> searchHistoryAdapter) {
            // show the search history
            setState(VisibleState.SearchHistory);

            mSearchHistoryListView.setAdapter(searchHistoryAdapter);
        }

        @Override
        public void onLoaderReset(Loader<ArrayAdapter<String>> cursorAdapterLoader) {
            ((ArrayAdapter)mSearchHistoryListView.getAdapter()).clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        hideInputManager();
        return false;
    }
}
