package com.cyngn.eleven.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.AlbumDetailSongAdapter;
import com.cyngn.eleven.adapters.DetailSongAdapter;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.utils.AlbumPopupMenuHelper;
import com.cyngn.eleven.utils.GenreFetcher;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.utils.SongPopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.PopupMenuButton;

import java.util.List;

public class AlbumDetailFragment extends BaseFragment {
    private static final int LOADER_ID = 1;

    private ListView mSongs;
    private DetailSongAdapter mSongAdapter;
    private TextView mAlbumDuration;
    private TextView mGenre;
    private PopupMenuHelper mPopupMenuHelper;
    private PopupMenuButton mPopupMenuButton;
    private PopupMenuHelper mHeaderPopupMenuHelper;
    private long mAlbumId;
    private String mArtistName;
    private String mAlbumName;

    @Override
    protected int getLayoutToInflate() {
        return R.layout.activity_album_detail;
    }

    @Override
    protected String getTitle() {
        return getArguments().getString(Config.ARTIST_NAME);
    }

    @Override
    protected void onViewCreated() {
        super.onViewCreated();

        Bundle arguments = getArguments();
        String artistName = arguments.getString(Config.ARTIST_NAME);

        setupPopupMenuHelper();
        setupHeader(artistName, arguments);
        setupSongList();

        LoaderManager lm = getLoaderManager();
        lm.initLoader(LOADER_ID, arguments, mSongAdapter);
    }

    private void setupHeader(String artist, Bundle arguments) {
        mAlbumId = arguments.getLong(Config.ID);
        mArtistName = artist;
        mAlbumName = arguments.getString(Config.NAME);
        String year = arguments.getString(Config.ALBUM_YEAR);
        int songCount = arguments.getInt(Config.SONG_COUNT);

        ImageView albumArt = (ImageView)mRootView.findViewById(R.id.album_art);
        albumArt.setContentDescription(mAlbumName);
        ImageFetcher.getInstance(getActivity()).loadAlbumImage(artist, mAlbumName, mAlbumId, albumArt);

        TextView title = (TextView)mRootView.findViewById(R.id.title);
        title.setText(mAlbumName);

        setupCountAndYear(mRootView, year, songCount);

        // will be updated once we have song data
        mAlbumDuration = (TextView)mRootView.findViewById(R.id.duration);
        mGenre = (TextView)mRootView.findViewById(R.id.genre);

        mPopupMenuButton = (PopupMenuButton)mRootView.findViewById(R.id.overflow);
        mPopupMenuButton.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mHeaderPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    private void setupCountAndYear(View root, String year, int songCount) {
        TextView songCountAndYear = (TextView)root.findViewById(R.id.song_count_and_year);
        if(songCount > 0) {
            String countText = getResources().
                    getQuantityString(R.plurals.Nsongs, songCount, songCount);
            if(year == null) {
                songCountAndYear.setText(countText);
            } else {
                songCountAndYear.setText(getString(R.string.combine_two_strings, countText, year));
            }
        } else if(year != null) {
            songCountAndYear.setText(year);
        }
    }

    private void setupPopupMenuHelper() {
        mPopupMenuHelper = new SongPopupMenuHelper(getActivity(), getChildFragmentManager()) {
            @Override
            public Song getSong(int position) {
                return mSongAdapter.getItem(position);
            }
        };

        mHeaderPopupMenuHelper = new AlbumPopupMenuHelper(getActivity(), getChildFragmentManager()) {
            public Album getAlbum(int position) {
                return new Album(mAlbumId, mAlbumName, mArtistName, -1, null);
            }
        };
    }

    private void setupSongList() {
        mSongs = (ListView)mRootView.findViewById(R.id.songs);
        mSongAdapter = new AlbumDetailSongAdapter(getActivity(), this);
        mSongAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
        mSongs.setAdapter(mSongAdapter);
        mSongs.setOnItemClickListener(mSongAdapter);
    }

    /** called back by song loader */
    public void update(List<Song> songs) {
        /** compute total run time for album */
        int duration = 0;
        for(Song s : songs) { duration += s.mDuration; }
        updateDuration(duration);

        /** use the first song on the album to get a genre */
        if(!songs.isEmpty()) {
            GenreFetcher.fetch(getActivity(), (int)songs.get(0).mSongId, mGenre);
        } else {
            // no songs, remove from stack
            getContainingActivity().postRemoveFragment(this);
        }
    }

    public void updateDuration(int duration) {
        int mins = Math.round(duration/60);
        int hours = mins/60;
        mins %= 60;

        String durationText = (hours == 0)
            ? getString(R.string.duration_album_mins_only, mins)
            : getString(R.string.duration_album_hour_mins, hours, mins);

        mAlbumDuration.setText(durationText);
    }

    @Override
    public void restartLoader() {
        getLoaderManager().restartLoader(LOADER_ID, getArguments(), mSongAdapter);
    }
}