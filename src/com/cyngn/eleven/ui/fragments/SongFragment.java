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

package com.cyngn.eleven.ui.fragments;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import com.cyngn.eleven.adapters.PagerAdapter;
import com.cyngn.eleven.loaders.SongLoader;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.ui.fragments.profile.BasicSongFragment;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.SectionCreatorUtils;
import com.viewpagerindicator.TitlePageIndicator;

/**
 * This class is used to display all of the songs on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SongFragment extends BasicSongFragment {

    /**
     * {@inheritDoc}
     */
    public void playAll(int position) {
        int internalPosition = mAdapter.getInternalPosition(position);
        Cursor cursor = SongLoader.makeSongCursor(getActivity(), null);
        final long[] list = MusicUtils.getSongListForCursor(cursor);
        MusicUtils.playAll(getActivity(), list, internalPosition, false);
        cursor.close();
        cursor = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Song>> onCreateLoader(final int id, final Bundle args) {
        // get the context
        Context context = getActivity();

        // create the underlying song loader
        SongLoader songLoader = new SongLoader(context);

        // get the song comparison method to create the headers with
        SectionCreatorUtils.IItemCompare<Song> songComparison = SectionCreatorUtils.createSongComparison(context);

        // return the wrapped section creator
        return new SectionCreator<Song>(context, songLoader, songComparison);
    }


    @Override
    public int getLoaderId() {
        return PagerAdapter.MusicFragments.SONG.ordinal();
    }

    /**
     * Scrolls the list to the currently playing song when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentSong() {
        final int currentSongPosition = getItemPositionBySong();

        if (currentSongPosition != 0) {
            mListView.setSelection(currentSongPosition);
        }
    }

    /**
     * @return The position of an item in the list based on the name of the
     * currently playing song.
     */
    private int getItemPositionBySong() {
        final long trackId = MusicUtils.getCurrentAudioId();
        if (mAdapter == null) {
            return 0;
        }

        int position = mAdapter.getItemPosition(trackId);

        // if for some reason we don't find the item, just jump to the top
        if (position < 0) {
            return 0;
        }

        return position;
    }

    @Override
    public LoaderManager getFragmentLoaderManager() {
        return getParentFragment().getLoaderManager();
    }
}
