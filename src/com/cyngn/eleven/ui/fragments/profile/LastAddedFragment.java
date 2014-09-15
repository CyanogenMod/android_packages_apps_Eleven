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

package com.cyngn.eleven.ui.fragments.profile;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;

import com.cyngn.eleven.R;
import com.cyngn.eleven.loaders.LastAddedLoader;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.NoResultsContainer;

import java.util.List;

/**
 * This class is used to display all of the songs the user put on their device
 * within the last four weeks.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class LastAddedFragment extends BasicSongFragment {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 7;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new LastAddedLoader(getActivity());
    }

    @Override
    public int getGroupId() {
        return GROUP_ID;
    }

    @Override
    public int getLoaderId() {
        return LOADER;
    }

    @Override
    public void playAll(int position) {
        Cursor cursor = LastAddedLoader.makeLastAddedCursor(getActivity());
        final long[] list = MusicUtils.getSongListForCursor(cursor);
        MusicUtils.playAll(getActivity(), list, position, false);
        cursor.close();
        cursor = null;
    }

    @Override
    public void setupNoResultsContainer(NoResultsContainer empty) {
        super.setupNoResultsContainer(empty);

        empty.setMainText(R.string.empty_last_added_main);
        empty.setSecondaryText(R.string.empty_last_added);
    }
}
