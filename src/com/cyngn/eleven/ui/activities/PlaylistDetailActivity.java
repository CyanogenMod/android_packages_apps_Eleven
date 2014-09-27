package com.cyngn.eleven.ui.activities;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.ProfileSongAdapter;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.dragdrop.DragSortListView;
import com.cyngn.eleven.dragdrop.DragSortListView.DragScrollProfile;
import com.cyngn.eleven.dragdrop.DragSortListView.DropListener;
import com.cyngn.eleven.dragdrop.DragSortListView.RemoveListener;
import com.cyngn.eleven.loaders.PlaylistSongLoader;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.utils.SongPopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.NoResultsContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class PlaylistDetailActivity extends DetailActivity implements
        LoaderCallbacks<List<Song>>, OnItemClickListener, DropListener,
        RemoveListener, DragScrollProfile, MusicStateListener {

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    private DragSortListView mListView;
    private ProfileSongAdapter mAdapter;

    private View mHeaderContainer;
    private ImageView mPlaylistImageView;

    private NoResultsContainer mNoResultsContainer;

    private TextView mNumberOfSongs;
    private TextView mDurationOfPlaylist;

    /**
     * The Id of the playlist the songs belong to
     */
    private long mPlaylistId;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    @Override
    protected int getLayoutToInflate() {
        return R.layout.playlist_detail;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPopupMenuHelper = new SongPopupMenuHelper(this, getSupportFragmentManager()) {
            @Override
            public Song getSong(int position) {
                if (position == 0) {
                    return null;
                }

                return mAdapter.getItem(position - 1);
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);

                set.add(FragmentMenuItems.REMOVE_FROM_PLAYLIST);
            }

            @Override
            protected void removeFromPlaylist() {
                mAdapter.remove(mSong);
                mAdapter.notifyDataSetChanged();
                MusicUtils.removeFromPlaylist(PlaylistDetailActivity.this, mSong.mSongId, mPlaylistId);
                getSupportLoaderManager().restartLoader(LOADER, null, PlaylistDetailActivity.this);
            }
        };

        Bundle arguments = getIntent().getExtras();
        String playlistName = arguments.getString(Config.NAME);
        mPlaylistId = arguments.getLong(Config.ID);

        setupActionBar(playlistName);

        ViewGroup root = (ViewGroup) findViewById(R.id.activity_base_content);
        root.setPadding(0, 0, 0, 0); // clear default padding

        setupHero();
        setupSongList(root);
        setupNoResultsContainer();

        LoaderManager lm = getSupportLoaderManager();
        lm.initLoader(0, arguments, this);

        // listen to music state changes
        setMusicStateListenerListener(this);
    }

    private void setupHero() {
        mPlaylistImageView = (ImageView)findViewById(R.id.image);
        mHeaderContainer = findViewById(R.id.playlist_header);
        mNumberOfSongs = (TextView)findViewById(R.id.number_of_songs_text);
        mDurationOfPlaylist = (TextView)findViewById(R.id.duration_text);

        ImageFetcher.getInstance(this).loadPlaylistArtistImage(mPlaylistId, mPlaylistImageView);
    }

    private void setupSongList(ViewGroup root) {
        mListView = (DragSortListView) root.findViewById(R.id.list_base);
        mListView.setOnScrollListener(this);
        mAdapter = new ProfileSongAdapter(
                this,
                R.layout.edit_track_list_item,
                R.layout.faux_playlist_header,
                ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING
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
        // Remove the scrollbars and padding for the fast scroll
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setFastScrollEnabled(false);
        mListView.setPadding(0, 0, 0, 0);
    }

    private void setupNoResultsContainer() {
        mNoResultsContainer = (NoResultsContainer)findViewById(R.id.no_results_container);
        mNoResultsContainer.setMainText(R.string.empty_playlist_main);
        mNoResultsContainer.setSecondaryText(R.string.empty_playlist_secondary);
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
        Song song = mAdapter.getItem(which - 1);
        mAdapter.remove(song);
        mAdapter.notifyDataSetChanged();
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistId);
        getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + song.mSongId,
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drop(final int from, final int to) {
        if (from == 0 || to == 0) {
            mAdapter.notifyDataSetChanged();
            return;
        }
        final int realFrom = from - 1;
        final int realTo = to - 1;
        Song song = mAdapter.getItem(realFrom);
        mAdapter.remove(song);
        mAdapter.insert(song, realTo);
        mAdapter.notifyDataSetChanged();
        MediaStore.Audio.Playlists.Members.moveItem(getContentResolver(),
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
        Cursor cursor = PlaylistSongLoader.makePlaylistSongCursor(this,
                mPlaylistId);
        final long[] list = MusicUtils.getSongListForCursor(cursor);
        MusicUtils.playAll(this, list, position - 1, false);
        cursor.close();
        cursor = null;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    protected int getHeaderHeight() { return mHeaderContainer.getHeight(); }

    protected void setHeaderPosition(float y) { mHeaderContainer.setY(y); }

    @Override
    public Loader<List<Song>> onCreateLoader(int i, Bundle bundle) {
        return new PlaylistSongLoader(this, mPlaylistId);
    }

    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
        if (data.isEmpty()) {
            // set the empty view - only do it here so we don't see the empty view by default
            // while the list is loading
            mListView.setEmptyView(mNoResultsContainer);

            // hide the header container
            mHeaderContainer.setVisibility(View.GONE);

            // Return the correct count
            mAdapter.setCount(new ArrayList<Song>());
            // Start fresh
            mAdapter.unload();
        } else {
            // show the header container
            mHeaderContainer.setVisibility(View.VISIBLE);

            // Start fresh
            mAdapter.unload();
            // Return the correct count
            mAdapter.setCount(data);
            // set the number of songs
            String numberOfSongs = MusicUtils.makeLabel(this, R.plurals.Nsongs, data.size());
            mNumberOfSongs.setText(numberOfSongs);

            long duration = 0;

            // Add the data to the adapter
            for (final Song song : data) {
                mAdapter.add(song);
                duration += song.mDuration;
            }

            // set the duration
            String durationString = MusicUtils.makeLongTimeString(this, duration);
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
        getSupportLoaderManager().restartLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public void onMetaChanged() {

    }
}