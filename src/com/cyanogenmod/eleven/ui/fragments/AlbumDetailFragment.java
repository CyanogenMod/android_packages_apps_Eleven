/*
* Copyright (C) 2014 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.cyanogenmod.eleven.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.AlbumDetailSongAdapter;
import com.cyanogenmod.eleven.adapters.DetailSongAdapter;
import com.cyanogenmod.eleven.adapters.PagerAdapter;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.utils.AlbumPopupMenuHelper;
import com.cyanogenmod.eleven.utils.GenreFetcher;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.utils.SongPopupMenuHelper;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.LoadingEmptyContainer;

import java.util.List;

public class AlbumDetailFragment extends DetailFragment implements IChildFragment {
    private static final int LOADER_ID = 1;

    private ListView mSongs;
    private DetailSongAdapter mSongAdapter;
    private TextView mAlbumDuration;
    private TextView mGenre;
    private ImageView mAlbumArt;
    private PopupMenuHelper mSongMenuHelper;
    private long mAlbumId;
    private String mArtistName;
    private String mAlbumName;
    private LoadingEmptyContainer mLoadingEmptyContainer;

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

    @Override // DetailFragment
    protected PopupMenuHelper createActionMenuHelper() {
        return new AlbumPopupMenuHelper(getActivity(), getChildFragmentManager()) {
            public Album getAlbum(int position) {
                return new Album(mAlbumId, mAlbumName, mArtistName, -1, null);
            }
        };
    }

    @Override // DetailFragment
    protected int getShuffleTitleId() { return R.string.menu_shuffle_album; }

    @Override // DetailFragment
    protected void playShuffled() {
        MusicUtils.playAlbum(getActivity(), mAlbumId, -1, true);
    }

    private void setupHeader(String artist, Bundle arguments) {
        mAlbumId = arguments.getLong(Config.ID);
        mArtistName = artist;
        mAlbumName = arguments.getString(Config.NAME);
        String year = arguments.getString(Config.ALBUM_YEAR);
        int songCount = arguments.getInt(Config.SONG_COUNT);

        mAlbumArt = (ImageView)mRootView.findViewById(R.id.album_art);
        mAlbumArt.setContentDescription(mAlbumName);
        ImageFetcher.getInstance(getActivity()).loadAlbumImage(artist, mAlbumName, mAlbumId, mAlbumArt);

        TextView title = (TextView)mRootView.findViewById(R.id.title);
        title.setText(mAlbumName);

        setupCountAndYear(mRootView, year, songCount);

        // will be updated once we have song data
        mAlbumDuration = (TextView)mRootView.findViewById(R.id.duration);
        mGenre = (TextView)mRootView.findViewById(R.id.genre);
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
        mSongMenuHelper = new SongPopupMenuHelper(getActivity(), getChildFragmentManager()) {
            @Override
            public Song getSong(int position) {
                return mSongAdapter.getItem(position);
            }

            @Override
            protected long getSourceId() {
                return mAlbumId;
            }

            @Override
            protected Config.IdType getSourceType() {
                return Config.IdType.Album;
            }
        };
    }

    private void setupSongList() {
        mSongs = (ListView)mRootView.findViewById(R.id.songs);
        mSongAdapter = new AlbumDetailSongAdapter(getActivity(), this) {
            @Override
            protected void onLoading() {
                mLoadingEmptyContainer.showLoading();
            }

            @Override
            protected void onNoResults() {
                getContainingActivity().postRemoveFragment(AlbumDetailFragment.this);
            }
        };
        mSongAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mSongMenuHelper.showPopupMenu(v, position);
            }
        });
        mSongs.setAdapter(mSongAdapter);
        mSongs.setOnItemClickListener(mSongAdapter);
        mLoadingEmptyContainer =
                (LoadingEmptyContainer)mRootView.findViewById(R.id.loading_empty_container);
        mSongs.setEmptyView(mLoadingEmptyContainer);
    }

    /** called back by song loader */
    public void update(List<Song> songs) {
        /** compute total run time for album */
        int duration = 0;
        for(Song s : songs) { duration += s.mDuration; }
        mAlbumDuration.setText(MusicUtils.makeLongTimeString(getActivity(), duration));

        /** use the first song on the album to get a genre */
        if(!songs.isEmpty()) {
            GenreFetcher.fetch(getActivity(), (int) songs.get(0).mSongId, mGenre);
        }
    }

    @Override
    public void restartLoader() {
        getLoaderManager().restartLoader(LOADER_ID, getArguments(), mSongAdapter);
        ImageFetcher.getInstance(getActivity()).loadAlbumImage(mArtistName, mAlbumName, mAlbumId,
                mAlbumArt);
    }

    @Override
    public void onMetaChanged() {
        super.onMetaChanged();

        mSongAdapter.setCurrentlyPlayingTrack(MusicUtils.getCurrentTrack());
    }

    @Override
    public PagerAdapter.MusicFragments getMusicFragmentParent() {
        return PagerAdapter.MusicFragments.ALBUM;
    }
}
