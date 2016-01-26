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
package com.cyanogenmod.eleven.ui.fragments.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.cyanogenmod.eleven.Config.SmartPlaylistType;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.adapters.PagerAdapter;
import com.cyanogenmod.eleven.menu.ConfirmDialog;
import com.cyanogenmod.eleven.model.Playlist;
import com.cyanogenmod.eleven.ui.fragments.IChildFragment;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PlaylistPopupMenuHelper;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;
import com.cyanogenmod.eleven.utils.PopupMenuHelper.PopupMenuType;

public abstract class SmartPlaylistFragment extends BasicSongFragment
        implements ConfirmDialog.ConfirmCallback, IChildFragment {
    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;
    private static final int CLEAR_REQUEST = 1;
    private PopupMenuHelper mActionMenuHelper;

    @Override
    public int getLoaderId() { return LOADER; }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected Config.IdType getFragmentSourceType() {
        return Config.IdType.Playlist;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.shuffle_item, menu);
        menu.findItem(R.id.menu_shuffle_item).setTitle(getShuffleTitleId());

        // use the same popup menu to provide actions for smart playlist
        // as is used in the PlaylistFragment
        mActionMenuHelper = new PlaylistPopupMenuHelper(
                getActivity(), getChildFragmentManager(), PopupMenuType.SmartPlaylist) {
            public Playlist getPlaylist(int position) {
                SmartPlaylistType type = getSmartPlaylistType();
                return new Playlist(type.mId, getString(type.mTitleId), 0);
            }
        };
        mActionMenuHelper.onPreparePopupMenu(0);
        mActionMenuHelper.createPopupMenu(menu);

        inflater.inflate(R.menu.clear_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_shuffle_item:
                playAll(-1, true);
                return true;
            case R.id.clear_list:
                ConfirmDialog.show(
                    this, CLEAR_REQUEST, getClearTitleId(), R.string.clear);
                return true;
            default:
                if(mActionMenuHelper.onMenuItemClick(item)) { return true; }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void confirmOk(int requestCode) {
        if(requestCode == CLEAR_REQUEST) {
            mAdapter.unload();
            clearList();
            restartLoader();
        }
    }

    @Override
    public void playAll(int position) {
        playAll(position, false);
    }

    public void playAll(int position, boolean shuffle) {
        // we grab the song ids from the adapter instead of querying the cursor because the user
        // expects what they see to be what they play.  The counter argument of updating the list
        // could be made, but refreshing the smart playlists so often will be annoying and
        // confusing for the user so this is an intermediate compromise.  An example is the top
        // tracks list is based on the # of times you play a song, but near the beginning each
        // song being played will change the list and the compromise is to update only when you
        // enter the page.
        long[] songIds = getSongIdsFromAdapter();
        if (songIds != null) {
            MusicUtils.playAll(getActivity(), songIds, position, getSmartPlaylistType().mId,
                    Config.IdType.Playlist, shuffle);
        }
    }

    public PagerAdapter.MusicFragments getMusicFragmentParent() {
        return PagerAdapter.MusicFragments.PLAYLIST;
    }

    protected abstract SmartPlaylistType getSmartPlaylistType();

    /** text for menu item that shuffles items in this playlist */
    protected abstract int getShuffleTitleId();

    /** text for confirmation dialog that clears this playlist */
    protected abstract int getClearTitleId();

    /** action that clears this playlist */
    protected abstract void clearList();
}