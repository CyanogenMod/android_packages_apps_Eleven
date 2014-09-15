package com.cyngn.eleven.ui.activities;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.ProfileSongAdapter;
import com.cyngn.eleven.dragdrop.DragSortListView;
import com.cyngn.eleven.dragdrop.DragSortListView.DragScrollProfile;
import com.cyngn.eleven.dragdrop.DragSortListView.DropListener;
import com.cyngn.eleven.dragdrop.DragSortListView.RemoveListener;
import com.cyngn.eleven.loaders.PlaylistSongLoader;
import com.cyngn.eleven.menu.CreateNewPlaylist;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.widgets.NoResultsContainer;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailActivity extends DetailActivity implements
        LoaderCallbacks<List<Song>>, OnItemClickListener, DropListener,
        RemoveListener, DragScrollProfile {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 8;

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
     * The Id of the playlist the songs belong to
     */
    private long mPlaylistId;

    @Override
    protected int getLayoutToInflate() {
        return R.layout.playlist_detail;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
    }

    private void setupHero() {
        mPlaylistImageView = (ImageView)findViewById(R.id.image);
        mHeaderContainer = findViewById(R.id.playlist_header);
        mNumberOfSongs = (TextView)findViewById(R.id.number_of_songs_text);
        mDurationOfPlaylist = (TextView)findViewById(R.id.duration_text);

        // TODO: Get the top artist image - do this in the next patch
        // ImageFetcher.getInstance(this).loadCurrentArtwork(mPlaylistImageView);
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
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        mSelectedPosition = info.position - 1;
        // Creat a new song
        mSong = mAdapter.getItem(mSelectedPosition);
        mSelectedId = mSong.mSongId;

        // Play the song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Play next
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE,
                getString(R.string.context_menu_play_next));

        // Add the song to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the song to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(this, GROUP_ID, subMenu);

        // View more content by the song artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
                getString(R.string.context_menu_more_by_artist));

        // Make the song a ringtone
        menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE,
                getString(R.string.context_menu_use_as_ringtone));

        // Remove the song from playlist
        menu.add(GROUP_ID, FragmentMenuItems.REMOVE_FROM_PLAYLIST, Menu.NONE,
                getString(R.string.context_menu_remove_from_playlist));

        // Delete the song
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(this, new long[]{
                            mSelectedId
                    }, 0, false);
                    return true;
                case FragmentMenuItems.PLAY_NEXT:
                    MusicUtils.playNext(new long[]{
                            mSelectedId
                    });
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(this, new long[]{
                            mSelectedId
                    });
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(new long[]{
                            mSelectedId
                    }).show(getSupportFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long playlistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, new long[]{
                            mSelectedId
                    }, playlistId);
                    return true;
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(this, mSong.mArtistName);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(this, mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    DeleteDialog.newInstance(mSong.mSongName, new long[]{
                            mSelectedId
                    }, null).show(getSupportFragmentManager(), "DeleteDialog");
                    SystemClock.sleep(10);
                    mAdapter.notifyDataSetChanged();
                    getSupportLoaderManager().restartLoader(LOADER, null, this);
                    return true;
                case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                    mAdapter.remove(mSong);
                    mAdapter.notifyDataSetChanged();
                    MusicUtils.removeFromPlaylist(this, mSong.mSongId, mPlaylistId);
                    getSupportLoaderManager().restartLoader(LOADER, null, this);
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
        mSong = mAdapter.getItem(which - 1);
        mAdapter.remove(mSong);
        mAdapter.notifyDataSetChanged();
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistId);
        getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + mSong.mSongId,
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
        mSong = mAdapter.getItem(realFrom);
        mAdapter.remove(mSong);
        mAdapter.insert(mSong, realTo);
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

            // Start fresh
            mAdapter.unload();
            // Return the correct count
            mAdapter.setCount(new ArrayList<Song>());
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
}