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

import android.os.Bundle;
import android.support.v4.content.Loader;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.loaders.TopTracksLoader;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.ui.activities.BaseActivity;
import com.cyngn.eleven.ui.fragments.profile.BasicSongFragment;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.NoResultsContainer;

import java.util.TreeSet;

/**
 * This class is used to display all of the recently listened to songs by the
 * user.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RecentFragment extends BasicSongFragment implements ISetupActionBar {

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    @Override
    protected void updateMenuIds(TreeSet<Integer> set) {
        set.add(FragmentMenuItems.REMOVE_FROM_RECENT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Song>> onCreateLoader(final int id, final Bundle args) {
        TopTracksLoader loader = new TopTracksLoader(getActivity(),
                TopTracksLoader.QueryType.RecentSongs);
        return new SectionCreator<Song>(getActivity(), loader, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // refresh the list since a track playing means it should be recently played
        getLoaderManager().restartLoader(LOADER, null, this);
    }

    @Override
    public int getLoaderId() {
        return LOADER;
    }

    @Override
    public void playAll(int position) {
        MusicUtils.playSmartPlaylist(getActivity(), position,
                Config.SmartPlaylistType.RecentlyPlayed);
    }

    @Override
    public void setupNoResultsContainer(NoResultsContainer empty) {
        super.setupNoResultsContainer(empty);

        empty.setMainText(R.string.empty_recent_main);
        empty.setSecondaryText(R.string.empty_recent);
    }

    @Override
    public void setupActionBar() {
        ((BaseActivity)getActivity()).setupActionBar(R.string.playlist_recently_played);
    }
}

