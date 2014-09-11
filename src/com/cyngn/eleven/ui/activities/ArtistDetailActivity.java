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
import com.cyngn.eleven.cache.ImageFetcher;

public class ArtistDetailActivity extends DetailActivity {
    private ImageView mHero;
    private View mHeader;

    private ListView mSongs;
    private ArtistDetailSongAdapter mSongAdapter;

    private RecyclerView mAlbums;
    private ArtistDetailAlbumAdapter mAlbumAdapter;

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

        setupSongList(root);
        setupAlbumList();
        setupHero(artistName);

        LoaderManager lm = getSupportLoaderManager();
        lm.initLoader(0, arguments, mAlbumAdapter);
        lm.initLoader(1, arguments, mSongAdapter);
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
        mAlbums.setAdapter(mAlbumAdapter);
    }

    private void setupSongList(ViewGroup root) {
        mSongs = (ListView)root.findViewById(R.id.songs);
        mHeader = (ViewGroup)LayoutInflater.from(this).
                inflate(R.layout.artist_detail_header, mSongs, false);
        mSongs.addHeaderView(mHeader);
        mSongs.setOnScrollListener(this);
        mSongAdapter = new ArtistDetailSongAdapter(this);
        mSongs.setAdapter(mSongAdapter);
    }

    // TODO: change this class to use the same header strategy as PlaylistDetail
    protected int getHeaderHeight() { return 0; }

    protected void setHeaderPosition(float y) {  }
}