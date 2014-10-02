/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.utils;

import android.app.Activity;
import android.support.v4.app.FragmentManager;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Album;

public abstract class AlbumPopupMenuHelper extends PopupMenuHelper {
    protected Album mAlbum;

    public AlbumPopupMenuHelper(Activity activity, FragmentManager fragmentManager) {
        super(activity, fragmentManager);
        mType = PopupMenuType.Album;
    }

    public abstract Album getAlbum(int position);

    @Override
    public PopupMenuType onPreparePopupMenu(int position) {
        mAlbum = getAlbum(position);

        if (mAlbum == null) {
            return null;
        }

        return PopupMenuType.Album;
    }

    @Override
    protected long[] getIdList() {
        return MusicUtils.getSongListForAlbum(mActivity, mAlbum.mAlbumId);
    }

    @Override
    protected long getSourceId() {
        return mAlbum.mAlbumId;
    }

    @Override
    protected Config.IdType getSourceType() {
        return Config.IdType.Album;
    }

    @Override
    protected void onDeleteClicked() {
        final String album = mAlbum.mAlbumName;
        DeleteDialog.newInstance(album, getIdList(),
                ImageFetcher.generateAlbumCacheKey(album, mAlbum.mArtistName))
                .show(mFragmentManager, "DeleteDialog");
    }

    @Override
    protected String getArtistName() {
        return mAlbum.mArtistName;
    }
}
