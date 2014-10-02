/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.utils;


import android.app.Activity;
import android.support.v4.app.FragmentManager;

import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Song;

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
}
