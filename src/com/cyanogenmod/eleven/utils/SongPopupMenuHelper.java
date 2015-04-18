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
package com.cyanogenmod.eleven.utils;


import android.app.Activity;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;

import com.cyanogenmod.eleven.menu.DeleteDialog;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.model.Song;

import java.util.TreeSet;

public abstract class SongPopupMenuHelper extends PopupMenuHelper {
    protected Song mSong;

    public SongPopupMenuHelper(Activity activity, FragmentManager fragmentManager) {
        super(activity, fragmentManager);
    }

    public abstract Song getSong(int position);

    @Override
    public PopupMenuHelper.PopupMenuType onPreparePopupMenu(int position) {
        mSong = getSong(position);

        if (mSong == null) {
            return null;
        }

        return PopupMenuType.Song;
    }

    @Override
    protected void playAlbum() {
        MusicUtils.playAlbum(mActivity, mSong.mAlbumId, 0, false);
    }

    @Override
    protected long[] getIdList() {
        return new long[] { mSong.mSongId };
    }

    @Override
    protected String getArtistName() {
        return mSong.mArtistName;
    }

    @Override
    protected void onDeleteClicked() {
        DeleteDialog.newInstance(mSong.mSongName, getIdList(), null)
                .show(mFragmentManager, "DeleteDialog");
    }

    @Override
    protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
        super.updateMenuIds(type, set);

        // Don't show more by artist if it is an unknown artist
        if (MediaStore.UNKNOWN_STRING.equals(mSong.mArtistName)) {
            set.remove(FragmentMenuItems.MORE_BY_ARTIST);
        }
    }
}
