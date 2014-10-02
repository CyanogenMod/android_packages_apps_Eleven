package com.cyngn.eleven.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.Config.SmartPlaylistType;
import com.cyngn.eleven.R;
import com.cyngn.eleven.model.Playlist;

public abstract class PlaylistPopupMenuHelper extends PopupMenuHelper {
    private Playlist mPlaylist;

    public PlaylistPopupMenuHelper(Activity activity, FragmentManager fragmentManager, PopupMenuType type) {
        super(activity, fragmentManager);
        mType = type;
    }

    public abstract Playlist getPlaylist(int position);

    @Override
    public PopupMenuType onPreparePopupMenu(int position) {
        mPlaylist = getPlaylist(position);
        return mPlaylist.isSmartPlaylist() ?
                PopupMenuType.SmartPlaylist : PopupMenuType.Playlist;
    }

    public void updateName(String name) {
        if(mPlaylist != null) { mPlaylist.mPlaylistName = name; }
    }

    @Override
    protected long getSourceId() {
        return mPlaylist.mPlaylistId;
    }

    @Override
    protected Config.IdType getSourceType() {
        return Config.IdType.Playlist;
    }

    @Override
    protected long[] getIdList() {
        if (mPlaylist.isSmartPlaylist()) {
            return MusicUtils.getSongListForSmartPlaylist(mActivity,
                    SmartPlaylistType.getTypeById(getSourceId()));
        } else {
            return MusicUtils.getSongListForPlaylist(mActivity, getSourceId());
        }
    }

    @Override
    protected void onDeleteClicked() {
        // TODO: do this with a proper DialogFragment
        buildDeleteDialog(getId(), mPlaylist.mPlaylistName).show();
    }

    @Override // FIXME: is this really the right thing?
    protected long getId() {
        return mPlaylist.mPlaylistId;
    }

    /**
     * Create a new {@link AlertDialog} for easy playlist deletion
     * 
     * @param playlistName The title of the playlist being deleted
     * @param playlistId The ID of the playlist being deleted
     * @return A new {@link AlertDialog} used to delete playlists
     */
    private final AlertDialog buildDeleteDialog(final long playlistId, final String playlistName) {
        return new AlertDialog.Builder(mActivity)
                .setTitle(mActivity.getString(R.string.delete_dialog_title, playlistName))
                .setPositiveButton(R.string.context_menu_delete, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Uri mUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                playlistId);
                        mActivity.getContentResolver().delete(mUri, null, null);
                        MusicUtils.refresh();
                    }
                }).setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).setMessage(R.string.cannot_be_undone).create();
    }
}