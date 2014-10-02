package com.cyngn.eleven.utils;

import android.app.Activity;
import android.support.v4.app.FragmentManager;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Artist;

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
}