package com.cyngn.eleven.ui.fragments.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.cyngn.eleven.Config.SmartPlaylistType;
import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.SongAdapter;
import com.cyngn.eleven.adapters.PagerAdapter;
import com.cyngn.eleven.menu.ConfirmDialog;
import com.cyngn.eleven.model.Playlist;
import com.cyngn.eleven.ui.fragments.IChildFragment;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.PlaylistPopupMenuHelper;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.utils.PopupMenuHelper.PopupMenuType;

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
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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