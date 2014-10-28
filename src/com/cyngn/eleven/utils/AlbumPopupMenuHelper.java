/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.utils;

import android.app.Activity;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;

import android.view.MenuItem;
import com.cyngn.eleven.Config;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.menu.FragmentMenuItems;
import com.cyngn.eleven.menu.PhotoSelectionDialog;
import com.cyngn.eleven.model.Album;

import java.util.TreeSet;

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

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        boolean handled = super.onMenuItemClick(item);
        if (!handled && item.getGroupId() == getGroupId()) {
            switch (item.getItemId()) {
                case FragmentMenuItems.CHANGE_IMAGE:
                    String key = ImageFetcher.generateAlbumCacheKey(mAlbum.mAlbumName,
                            getArtistName());
                    PhotoSelectionDialog.newInstance(mAlbum.mAlbumName,
                            PhotoSelectionDialog.ProfileType.ALBUM, key)
                            .show(mFragmentManager, "PhotoSelectionDialog");
                    return true;
            }
        }

        return handled;
    }

    @Override
    protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
        super.updateMenuIds(type, set);

        // Don't show more by artist if it is an unknown artist
        if (MediaStore.UNKNOWN_STRING.equals(mAlbum.mArtistName)) {
            set.remove(FragmentMenuItems.MORE_BY_ARTIST);
        }
    }
}
