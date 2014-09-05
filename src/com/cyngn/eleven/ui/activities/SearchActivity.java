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

package com.cyngn.eleven.ui.activities;

import android.app.ActionBar;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
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
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.cyngn.eleven.IElevenService;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.SummarySearchAdapter;
import com.cyngn.eleven.model.AlbumArtistDetails;
import com.cyngn.eleven.model.SearchResult;
import com.cyngn.eleven.model.SearchResult.ResultType;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.sectionadapter.SectionCreator.SimpleListLoader;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.MusicUtils.ServiceToken;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.utils.SectionCreatorUtils;
import com.cyngn.eleven.utils.SectionCreatorUtils.IItemCompare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.view.View.OnTouchListener;
import static com.cyngn.eleven.utils.MusicUtils.mService;

/**
 * Provides the search interface for Apollo.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SearchActivity extends FragmentActivity implements LoaderCallbacks<SectionListContainer<SearchResult>>,
        OnScrollListener, OnQueryTextListener, OnItemClickListener, ServiceConnection,
        OnTouchListener {
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
     * The view that container the no search results text
     */
    private View mNoSearchResultsView;

    /**
     * List view adapter
     */
    private SectionAdapter<SearchResult, SummarySearchAdapter> mAdapter;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Theme the action bar
        final ActionBar actionBar = getActionBar();
        actionBar.setTitle(getString(R.string.app_name_uppercase));
        actionBar.setHomeButtonEnabled(true);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Set the layout
        setContentView(R.layout.search_layout);

        // get the input method manager
        mImm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Get the query String
        mFilterString = getIntent().getStringExtra(SearchManager.QUERY);

        // Initialize the adapter
        SummarySearchAdapter adapter = new SummarySearchAdapter(this);
        mAdapter = new SectionAdapter<SearchResult, SummarySearchAdapter>(this, adapter);
        // Set the prefix
        mAdapter.getUnderlyingAdapter().setPrefix(mFilterString);

        initListView();

        mNoSearchResultsView = findViewById(R.id.no_search_results);
        mNoSearchResultsView.setVisibility(View.INVISIBLE);

        if (mFilterString != null && !mFilterString.isEmpty()) {
            // Prepare the loader. Either re-connect with an existing one,
            // or start a new one.
            getSupportLoaderManager().initLoader(0, null, this);

            // Action bar subtitle
            getActionBar().setSubtitle("\"" + mFilterString + "\"");
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
        mListView.setOnTouchListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<SearchResult>> onCreateLoader(final int id, final Bundle args) {
        IItemCompare<SearchResult> comparator = SectionCreatorUtils.createSearchResultComparison(this);
        return new SectionCreator<SearchResult>(this, new SummarySearchLoader(this, mFilterString),
                comparator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        getMenuInflater().inflate(R.menu.search, menu);

        // Filter the list the user is looking it via SearchView
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView)searchItem.getActionView();
        mSearchView.setOnQueryTextListener(this);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                finish();
                return true;
            }
        });

        if (mFilterString == null || mFilterString.isEmpty()) {
            menu.findItem(R.id.menu_search).expandActionView();
        }

        // Add voice search
        final SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
        mSearchView.setSearchableInfo(searchableInfo);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
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
                finish();
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
            // Set the empty text
            final TextView empty = (TextView)findViewById(R.id.no_search_results_text);
            empty.setText("\"" + mFilterString + "\"");

            // clear the adapter
            mAdapter.clear();

            // hide listview, show no results text
            mListView.setVisibility(View.INVISIBLE);
            mNoSearchResultsView.setVisibility(View.VISIBLE);
        } else {
            // Set the data
            mAdapter.setData(data);

            // show listview, hide no results text
            mListView.setVisibility(View.VISIBLE);
            mNoSearchResultsView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<SectionListContainer<SearchResult>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
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
    public boolean onQueryTextSubmit(final String query) {
        if (TextUtils.isEmpty(query)) {
            mFilterString = "";
            getActionBar().setSubtitle("");
            return false;
        }

        hideInputManager();

        // Action bar subtitle
        getActionBar().setSubtitle("\"" + mFilterString + "\"");
        return true;
    }

    public void hideInputManager() {
        // When the search is "committed" by the user, then hide the keyboard so
        // the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            if (mImm != null && mImm.isImeShowing()) {
                mImm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onQueryTextChange(final String newText) {
        if (TextUtils.isEmpty(newText)) {
            mListView.setVisibility(View.INVISIBLE);
            mNoSearchResultsView.setVisibility(View.INVISIBLE);
            mFilterString = "";
            return false;
        }
        // Called when the action bar search text has changed. Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        mFilterString = !TextUtils.isEmpty(newText) ? newText : null;
        // Set the prefix
        mAdapter.getUnderlyingAdapter().setPrefix(mFilterString);
        getSupportLoaderManager().restartLoader(0, null, this);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        SearchResult item = mAdapter.getTItem(position);
        switch (item.mType) {
            case Artist:
                NavUtils.openArtistProfile(this, item.mArtist);
                break;
            case Album:
                NavUtils.openAlbumProfile(this, item.mAlbum, item.mArtist, item.mId);
                break;
            case Playlist:
                NavUtils.openPlaylist(this, item.mId, null, item.mTitle);
                break;
            case Song:
                // If it's a song, play it and leave
                final long[] list = new long[]{
                        item.mId
                };
                MusicUtils.playAll(this, list, 0, false);
                break;
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
        private static final int NUM_RESULTS_TO_GET = 3;

        private final String mQuery;

        public SummarySearchLoader(final Context context, final String query) {
            super(context);
            mQuery = query;
        }

        @Override
        public List<SearchResult> loadInBackground() {
            ArrayList<SearchResult> results = new ArrayList<SearchResult>();
            // number of types to query for
            final int numTypes = ResultType.getNumTypes();

            // number of results we want
            final int numResultsNeeded = NUM_RESULTS_TO_GET * numTypes;

            // current number of results we have
            int numResultsAdded = 0;

            // count for each result type
            int[] numOfEachType = new int[numTypes];

            // search playlists first
            Cursor playlistCursor = makePlaylistSearchCursor(getContext(), mQuery);
            if (playlistCursor != null && playlistCursor.moveToFirst()) {
                do {
                    // create the item
                    SearchResult item = SearchResult.createPlaylistResult(playlistCursor);
                    if (item != null) {
                        // get the song count
                        item.mSongCount = MusicUtils.getSongCountForPlaylist(getContext(), item.mId);

                        /// add the results
                        numResultsAdded++;
                        results.add(item);
                    }
                } while (playlistCursor.moveToNext() && numResultsAdded < NUM_RESULTS_TO_GET);

                // because we deal with playlists separately,
                // just mark that we have the full # of playlists
                // so that logic later can quit out early if full
                numResultsAdded = NUM_RESULTS_TO_GET;

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
                    if (numOfEachType[type.ordinal()] < NUM_RESULTS_TO_GET) {
                        // get the search result
                        SearchResult item = SearchResult.createSearchResult(cursor);
                        if (item != null) {
                            if (item.mType == ResultType.Song) {
                                // get the album art details
                                AlbumArtistDetails details = MusicUtils.getAlbumArtDetails(getContext(), item.mId);
                                if (details != null) {
                                    item.mArtist = details.mArtistName;
                                    item.mAlbum = details.mAlbumName;
                                    item.mAlbumId = details.mAlbumId;
                                }
                            }

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
            if (searchTerms == null || searchTerms.isEmpty()) {
                return null;
            }

            // trim out special characters like % or \ as well as things like "a" "and" etc
            String trimmedSearchTerms = MusicUtils.getTrimmedName(searchTerms);

            if (trimmedSearchTerms == null || trimmedSearchTerms.isEmpty()) {
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

            return context.getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    new String[]{
                        /* 0 */
                            BaseColumns._ID,
                        /* 1 */
                            MediaStore.Audio.PlaylistsColumns.NAME
                    }, where, keywords, MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER);
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
