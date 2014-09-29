package com.cyngn.eleven.ui.activities;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.ArtistDetailAlbumAdapter;
import com.cyngn.eleven.adapters.ArtistDetailSongAdapter;
import com.cyngn.eleven.adapters.IEmptyAdapterCallback;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.utils.AlbumPopupMenuHelper;
import com.cyngn.eleven.utils.SongPopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;

import java.util.TreeSet;

public class ArtistDetailActivity extends DetailActivity {
    private final int ALBUM_LOADER_ID = 0;
    private final int SONG_LOADER_ID = 1;
    private ImageView mHero;
    private View mHeader;

    private ListView mSongs;
    private ArtistDetailSongAdapter mSongAdapter;

    private RecyclerView mAlbums;
    private ArtistDetailAlbumAdapter mAlbumAdapter;

    private SongPopupMenuHelper mSongPopupMenuHelper;
    private AlbumPopupMenuHelper mAlbumPopupMenuHelper;

    @Override
    protected int getLayoutToInflate() { return R.layout.activity_artist_detail; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getIntent().getExtras();
        String artistName = arguments.getString(Config.ARTIST_NAME);

        setupActionBar(artistName);

        ViewGroup root = (ViewGroup)findViewById(R.id.activity_base_content);
        root.setPadding(0, 0, 0, 0); // clear default padding

        setupPopupMenuHelpers();
        setupSongList(root);
        setupAlbumList();
        setupHero(artistName);

        LoaderManager lm = getSupportLoaderManager();
        lm.initLoader(ALBUM_LOADER_ID, arguments, mAlbumAdapter);
        lm.initLoader(SONG_LOADER_ID, arguments, mSongAdapter);
    }

    private void setupHero(String artistName) {
        mHero = (ImageView)mHeader.findViewById(R.id.hero);
        mHero.setContentDescription(artistName);
        ImageFetcher.getInstance(this).loadArtistImage(artistName, mHero);
    }

    private void setupAlbumList() {
        mAlbums = (RecyclerView) mHeader.findViewById(R.id.albums);
        mAlbums.setHasFixedSize(true);
        mAlbums.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mAlbumAdapter = new ArtistDetailAlbumAdapter(this);
        mAlbumAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mAlbumPopupMenuHelper.showPopupMenu(v, position);
            }
        });
        mAlbums.setAdapter(mAlbumAdapter);
    }

    private void setupSongList(ViewGroup root) {
        mSongs = (ListView)root.findViewById(R.id.songs);
        mHeader = (ViewGroup)LayoutInflater.from(this).
                inflate(R.layout.artist_detail_header, mSongs, false);
        mSongs.addHeaderView(mHeader);
        mSongs.setOnScrollListener(this);
        mSongAdapter = new ArtistDetailSongAdapter(this);
        mSongAdapter.setOnEmptyAdapterListener(new IEmptyAdapterCallback() {
            @Override
            public void onEmptyAdapter() {
                // no results - because the user deleted the last item
                finish();
            }
        });
        mSongAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mSongPopupMenuHelper.showPopupMenu(v, position);
            }
        });
        mSongs.setAdapter(mSongAdapter);
        mSongs.setOnItemClickListener(mSongAdapter);
    }

    private void setupPopupMenuHelpers() {
        mSongPopupMenuHelper = new SongPopupMenuHelper(this, getSupportFragmentManager()) {
            @Override
            public Song getSong(int position) {
                return mSongAdapter.getItem(position);
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);

                // since we are already on the artist page, this item doesn't make sense
                set.remove(FragmentMenuItems.MORE_BY_ARTIST);
            }
        };

        mAlbumPopupMenuHelper = new AlbumPopupMenuHelper(this, getSupportFragmentManager()) {
            @Override
            public Album getAlbum(int position) {
                return mAlbumAdapter.getItem(position);
            }

            @Override
            protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
                super.updateMenuIds(type, set);

                // since we are already on the artist page, this item doesn't make sense
                set.remove(FragmentMenuItems.MORE_BY_ARTIST);
            }
        };
    }

    // TODO: change this class to use the same header strategy as PlaylistDetail
    protected int getHeaderHeight() { return 0; }

    protected void setHeaderPosition(float y) {  }

    @Override
    public void restartLoader() {
        super.restartLoader();

        Bundle arguments = getIntent().getExtras();
        LoaderManager lm = getSupportLoaderManager();
        lm.restartLoader(ALBUM_LOADER_ID, arguments, mAlbumAdapter);
        lm.restartLoader(SONG_LOADER_ID, arguments, mSongAdapter);
    }
}