/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.ui.fragments.profile;

import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.Config.SmartPlaylistType;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.loaders.LastAddedLoader;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.sectionadapter.SectionCreator;
import com.cyanogenmod.eleven.sectionadapter.SectionListContainer;
import com.cyanogenmod.eleven.ui.activities.BaseActivity;
import com.cyanogenmod.eleven.ui.fragments.ISetupActionBar;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.NoResultsContainer;

/**
 * This class is used to display all of the songs the user put on their device
 * within the last four weeks.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class LastAddedFragment extends SmartPlaylistFragment implements ISetupActionBar {

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Song>> onCreateLoader(final int id, final Bundle args) {
        // show the loading progress bar
        mLoadingEmptyContainer.showLoading();

        LastAddedLoader loader = new LastAddedLoader(getActivity());
        return new SectionCreator<Song>(getActivity(), loader, null);
    }

    @Override
    public void setupNoResultsContainer(NoResultsContainer empty) {
        super.setupNoResultsContainer(empty);

        empty.setMainText(R.string.empty_last_added_main);
        empty.setSecondaryText(R.string.empty_last_added);
    }

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        setupActionBar();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void setupActionBar() {
        ((BaseActivity)getActivity()).setupActionBar(R.string.playlist_last_added);
        ((BaseActivity)getActivity()).setActionBarElevation(true);
    }

    @Override
    protected long getFragmentSourceId() {
        return Config.SmartPlaylistType.LastAdded.mId;
    }

    protected SmartPlaylistType getSmartPlaylistType() {
        return Config.SmartPlaylistType.LastAdded;
    }

    @Override
    protected int getShuffleTitleId() { return R.string.menu_shuffle_last_added; }

    @Override
    protected int getClearTitleId() { return R.string.clear_last_added; }

    @Override
    protected void clearList() { MusicUtils.clearLastAdded(getActivity()); }
}