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
import android.support.v4.app.FragmentManager;

import android.view.MenuItem;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.menu.DeleteDialog;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.menu.PhotoSelectionDialog;
import com.cyanogenmod.eleven.model.Artist;

public abstract class ArtistPopupMenuHelper extends PopupMenuHelper {
    private Artist mArtist;

    public ArtistPopupMenuHelper(Activity activity, FragmentManager fragmentManager) {
        super(activity, fragmentManager);
        mType = PopupMenuType.Artist;
    }

    public abstract Artist getArtist(int position);

    @Override
    public PopupMenuType onPreparePopupMenu(int position) {
        mArtist = getArtist(position);
        return mArtist == null ? null : PopupMenuType.Artist;
    }

    @Override
    protected long getSourceId() {
        return mArtist.mArtistId;
    }

    @Override
    protected Config.IdType getSourceType() {
        return Config.IdType.Artist;
    }

    @Override
    protected long[] getIdList() {
        return MusicUtils.getSongListForArtist(mActivity, mArtist.mArtistId);
    }

    @Override
    protected void onDeleteClicked() {
        final String artist = mArtist.mArtistName;
        DeleteDialog.newInstance(artist, getIdList(), artist)
            .show(mFragmentManager, "DeleteDialog");
    }

    @Override
    protected String getArtistName() {
        return mArtist.mArtistName;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        boolean handled = super.onMenuItemClick(item);
        if (!handled && item.getGroupId() == getGroupId()) {
            switch (item.getItemId()) {
                case FragmentMenuItems.CHANGE_IMAGE:
                    PhotoSelectionDialog.newInstance(getArtistName(),
                            PhotoSelectionDialog.ProfileType.ARTIST, getArtistName())
                            .show(mFragmentManager, "PhotoSelectionDialog");
                    return true;
            }
        }

        return handled;
    }
}