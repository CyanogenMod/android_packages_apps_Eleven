package com.cyngn.eleven.ui.activities;

import android.app.ActionBar;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.ArtistDetailAlbumAdapter;
import com.cyngn.eleven.adapters.ArtistDetailSongAdapter;
import com.cyngn.eleven.cache.ImageFetcher;

import java.util.Locale;

public class ArtistDetailActivity extends SlidingPanelActivity
implements AbsListView.OnScrollListener {
    private static final int ACTION_BAR_DEFAULT_OPACITY = 65;
    private ImageView mHero;
    private View mHeader;
    private Drawable mActionBarBackground;

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

    private void setupActionBar(String artistName) {
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(artistName.toUpperCase(Locale.getDefault()));
        actionBar.setIcon(R.drawable.ic_action_back);
        actionBar.setHomeButtonEnabled(true);
        // change action bar background to a drawable we can control
        mActionBarBackground = new ColorDrawable(getResources().getColor(R.color.header_action_bar_color));
        mActionBarBackground.setAlpha(ACTION_BAR_DEFAULT_OPACITY);
        actionBar.setBackgroundDrawable(mActionBarBackground);
    }

    /** cause action bar icon tap to act like back -- boo-urns! */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override // OnScrollListener
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        View child = view.getChildAt(0);
        if(child == null) { return; }

        float y = -child.getY();
        if(y < 0) { return; }

        float alpha = 255f;
        // when the list view hits the top of the screen, y start counting up from 0
        // again, so we check to see if enough items are on screen that the hero image
        // is no longer visible, and assume opaque from there on out
        if(visibleItemCount < 6) {
            alpha = ACTION_BAR_DEFAULT_OPACITY +
                    ((255 - ACTION_BAR_DEFAULT_OPACITY) * y/(float)mHero.getHeight());
            if(alpha > 255f) { alpha = 255f; }
        }
        mActionBarBackground.setAlpha((int)alpha);
    }

    @Override // OnScrollListener
    public void onScrollStateChanged(AbsListView view, int scrollState) {}
}