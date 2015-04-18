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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.android.internal.view.menu.ContextMenuBuilder;
import com.android.internal.view.menu.MenuBuilder;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.menu.CreateNewPlaylist;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.menu.RenamePlaylist;
import com.cyanogenmod.eleven.provider.RecentStore;

import java.util.TreeSet;

/**
 * Simple helper class that does most of the popup menu inflating and handling
 * It has a few hooks around so that if the class wants customization they can add it on
 * without changing this class too much
 */
public abstract class PopupMenuHelper implements PopupMenu.OnMenuItemClickListener {
    // the different types of pop up menus
    public static enum PopupMenuType {
        Artist,
        Album,
        Song,
        Playlist,
        SmartPlaylist,
        SearchResult,
        Queue,
    }

    protected Activity mActivity;
    protected PopupMenuType mType;
    protected FragmentManager mFragmentManager;

    public PopupMenuHelper(final Activity activity, final FragmentManager fragmentManager) {
        mActivity = activity;
        mFragmentManager = fragmentManager;
    }

    /**
     * Call this to inflate and show the pop up menu
     * @param view the view to anchor the popup menu against
     * @param position the item that was clicked in the popup menu (or -1 if not relevant)
     */
    public void showPopupMenu(final View view, final int position) {
        // create the popup menu
        PopupMenu popupMenu = new PopupMenu(mActivity, view);
        final Menu menu = popupMenu.getMenu();

        // hook up the click listener
        popupMenu.setOnMenuItemClickListener(this);

        // figure what type of pop up menu it is
        mType = onPreparePopupMenu(position);
        if (mType != null) {
            // inflate the menu
            createPopupMenu(menu);
            // show it
            popupMenu.show();
        }
    }

    /**
     * This function allows classes to setup any variables before showing the popup menu
     * @param position the position passed in from showPopupMenu
     * @return the pop up menu type, or null if we shouldn't show a pop up menu
     */
    public abstract PopupMenuType onPreparePopupMenu(final int position);

    /**
     * @return the list of ids needed for some menu actions like playing a list of songs
     */
    protected abstract long[] getIdList();

    protected abstract long getSourceId();
    protected abstract Config.IdType getSourceType();

    /**
     * @return the group id to be used for pop up menu inflating
     */
    protected int getGroupId() {
        return 0;
    }

    /**
     * called when the delete item is pressed.
     */
    protected void onDeleteClicked() {
        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * @return the artist name (when needed) for "more by this artist"
     */
    protected String getArtistName() {
        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * @return the single id that is needed for the "set as my ringtone"
     */
    protected long getId() {
        long[] idList = getIdList();
        if (idList.length == 1) {
            return idList[0];
        }

        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * Called when the user clicks "remove from playlist"
     */
    protected void removeFromPlaylist() {
        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * Called when the user clicks "remove from queue"
     */
    protected void removeFromQueue() {
        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * Called when the user clicks "play next".  Has a default implementation
     */
    protected void playNext() {
        MusicUtils.playNext(getIdList(), getSourceId(), getSourceType());
    }

    /**
     * Called when the user clicks "play album".
     */
    protected void playAlbum() {
        throw new UnsupportedOperationException("Method Not Implemented!");
    }

    /**
     * Creates the pop up menu by inflating the menu items
     * @param menu Menu to use for adding to
     */
    public void createPopupMenu(final Menu menu) {
        TreeSet<Integer> menuItems = new TreeSet<Integer>();

        // get the default items and add them
        int[] defaultItems = getIdsForType(mType);
        if (defaultItems != null) {
            for (int id : defaultItems) {
                menuItems.add(id);
            }
        }

        updateMenuIds(mType, menuItems);

        for (int id : menuItems) {
            addToMenu(menu, id, getAdditionalStringResourceForId(id));
        }
    }

    /**
     * Gets the default menu items for the specified type
     * @param type of pop up menu to create
     * @return list of menu items to inflate
     */
    private static int[] getIdsForType(PopupMenuType type) {
        switch (type) {
            case Artist:
                return new int[] {
                    FragmentMenuItems.PLAY_SELECTION,
                    FragmentMenuItems.ADD_TO_QUEUE,
                    FragmentMenuItems.ADD_TO_PLAYLIST,
                    FragmentMenuItems.DELETE,
                    FragmentMenuItems.CHANGE_IMAGE,
                };
            case Album:
                return new int[] {
                        FragmentMenuItems.PLAY_SELECTION,
                        FragmentMenuItems.ADD_TO_QUEUE,
                        FragmentMenuItems.ADD_TO_PLAYLIST,
                        FragmentMenuItems.MORE_BY_ARTIST,
                        FragmentMenuItems.DELETE,
                        FragmentMenuItems.CHANGE_IMAGE,
                };
            case Song:
                return new int[] {
                        FragmentMenuItems.PLAY_SELECTION,
                        FragmentMenuItems.PLAY_NEXT,
                        FragmentMenuItems.PLAY_ALBUM,
                        FragmentMenuItems.ADD_TO_QUEUE,
                        FragmentMenuItems.ADD_TO_PLAYLIST,
                        FragmentMenuItems.MORE_BY_ARTIST,
                        FragmentMenuItems.USE_AS_RINGTONE,
                        FragmentMenuItems.DELETE,
                };
            case Playlist:
                return new int[] {
                        FragmentMenuItems.PLAY_SELECTION,
                        FragmentMenuItems.ADD_TO_QUEUE,
                        FragmentMenuItems.RENAME_PLAYLIST,
                        FragmentMenuItems.DELETE,
                };
            case SmartPlaylist:
                return new int[] {
                        FragmentMenuItems.PLAY_SELECTION,
                        FragmentMenuItems.ADD_TO_QUEUE,
                };
            case SearchResult:
                return new int[] {
                        FragmentMenuItems.PLAY_SELECTION,
                        FragmentMenuItems.ADD_TO_QUEUE,
                        FragmentMenuItems.ADD_TO_PLAYLIST,
                };
            case Queue:
                return new int[] {
                        FragmentMenuItems.PLAY_NEXT,
                        FragmentMenuItems.ADD_TO_PLAYLIST,
                        FragmentMenuItems.REMOVE_FROM_QUEUE,
                        FragmentMenuItems.MORE_BY_ARTIST,
                        FragmentMenuItems.USE_AS_RINGTONE,
                        FragmentMenuItems.DELETE,
                };
        }

        return null;
    }

    /**
     * Allows containing classes to add/remove ids to the menu
     * @param type the pop up menu type
     * @param set the treeset to add/remove menu items
     */
    protected void updateMenuIds(PopupMenuType type, TreeSet<Integer> set) {
        // do nothing
    }

    /**
     * Gets the string resource for an id - if the string resource doesn't exist in this class
     * the containing class can override this method
     * @param id the menu id
     * @return string resource id
     */
    protected int getAdditionalStringResourceForId(final int id) {
        return getStringResourceForId(id);
    }

    /**
     * Gets the string resource for an id
     * @param id the menu id
     * @return string resource id
     */
    public static int getStringResourceForId(final int id) {
        switch (id) {
            case FragmentMenuItems.REMOVE_FROM_RECENT:
                return R.string.context_menu_remove_from_recent;
            case FragmentMenuItems.PLAY_SELECTION:
                return R.string.context_menu_play_selection;
            case FragmentMenuItems.ADD_TO_QUEUE:
                return R.string.add_to_queue;
            case FragmentMenuItems.ADD_TO_PLAYLIST:
                return R.string.add_to_playlist;
            case FragmentMenuItems.NEW_PLAYLIST:
                return R.string.new_playlist;
            case FragmentMenuItems.RENAME_PLAYLIST:
                return R.string.context_menu_rename_playlist;
            case FragmentMenuItems.PLAYLIST_SELECTED:
                return 0; // no string here expected
            case FragmentMenuItems.MORE_BY_ARTIST:
                return R.string.context_menu_more_by_artist;
            case FragmentMenuItems.DELETE:
                return R.string.context_menu_delete;
            case FragmentMenuItems.FETCH_ARTIST_IMAGE:
                return R.string.context_menu_fetch_artist_image;
            case FragmentMenuItems.FETCH_ALBUM_ART:
                return R.string.context_menu_fetch_album_art;
            case FragmentMenuItems.USE_AS_RINGTONE:
                return R.string.context_menu_use_as_ringtone;
            case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                return R.string.context_menu_remove_from_playlist;
            case FragmentMenuItems.REMOVE_FROM_QUEUE:
                return R.string.remove_from_queue;
            case FragmentMenuItems.PLAY_NEXT:
                return R.string.context_menu_play_next;
            case FragmentMenuItems.PLAY_ALBUM:
                return R.string.context_menu_play_album;
            case FragmentMenuItems.CHANGE_IMAGE:
                return R.string.context_menu_change_image;
        }

        return 0;
    }

    /**
     * Simple helper function for adding an item to the menu
     */
    public void addToMenu(final Menu menu, final int id, final int resourceId) {
        menu.add(getGroupId(), id, id /*as order*/, mActivity.getString(resourceId));
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getGroupId() == getGroupId()) {
            switch (item.getItemId()) {
                case FragmentMenuItems.REMOVE_FROM_RECENT:
                    RecentStore.getInstance(mActivity).removeItem(getId());
                    MusicUtils.refresh();
                    return true;
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(mActivity, getIdList(), 0, getSourceId(), getSourceType(),
                            false);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(mActivity, getIdList(), getSourceId(), getSourceType());
                    return true;
                case FragmentMenuItems.ADD_TO_PLAYLIST:
                    ContextMenuBuilder builder = new ContextMenuBuilder(mActivity);
                    MusicUtils.makePlaylistMenu(mActivity, getGroupId(), builder);
                    builder.setHeaderTitle(R.string.add_to_playlist);
                    builder.setCallback(new MenuBuilder.Callback() {
                        @Override
                        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                            return onMenuItemClick(item);
                        }

                        @Override
                        public void onMenuModeChange(MenuBuilder menu) {
                            // do nothing
                        }
                    });
                    builder.show(null, null);
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(getIdList()).show(
                            mFragmentManager, "CreatePlaylist");
                    return true;
                case FragmentMenuItems.RENAME_PLAYLIST:
                    RenamePlaylist.getInstance(getId()).show(
                            mFragmentManager, "RenameDialog");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long mPlaylistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(mActivity, getIdList(), mPlaylistId);
                    return true;
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(mActivity, getArtistName());
                    return true;
                case FragmentMenuItems.DELETE:
                    onDeleteClicked();
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(mActivity, getId());
                    return true;
                case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                    removeFromPlaylist();
                    return true;
                case FragmentMenuItems.REMOVE_FROM_QUEUE:
                    removeFromQueue();
                    return true;
                case FragmentMenuItems.PLAY_NEXT:
                    playNext();
                    return true;
                case FragmentMenuItems.PLAY_ALBUM:
                    playAlbum();
                    return true;
                default:
                    break;
            }
        }

        return false;
    }
}
