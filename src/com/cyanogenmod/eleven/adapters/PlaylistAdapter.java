/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.cyanogenmod.eleven.Config.SmartPlaylistType;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.model.Playlist;
import com.cyanogenmod.eleven.ui.MusicHolder;
import com.cyanogenmod.eleven.ui.MusicHolder.DataHolder;
import com.cyanogenmod.eleven.ui.fragments.PlaylistFragment;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;

/**
 * This {@link ArrayAdapter} is used to display all of the playlists on a user's
 * device for {@link PlaylistFragment}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistAdapter extends ArrayAdapter<Playlist> implements IPopupMenuCallback {

    /**
     * Smart playlists and normal playlists
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * Used to identify the view type
     */
    private static final int SMART_PLAYLIST_VIEW_TYPE = 1;

    /**
     * Used to cache the playlist info
     */
    private DataHolder[] mData;

    /**
     * Used to listen to the pop up menu callbacks
     */
    protected IListener mListener;

    /**
     * Constructor of <code>PlaylistAdapter</code>
     *
     * @param context The {@link Context} to use.
     */
    public PlaylistAdapter(final Context context) {
        super(context, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicHolder holder;
        if (convertView == null) {
            int layoutId = R.layout.list_item_normal;

            if (getItemViewType(position) == SMART_PLAYLIST_VIEW_TYPE) {
                layoutId = R.layout.list_item_smart_playlist;
            }

            convertView = LayoutInflater.from(getContext()).inflate(layoutId, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);

            // set the pop up menu listener
            holder.mPopupMenuButton.get().setPopupMenuClickedListener(mListener);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // because of recycling, we need to set the position each time
        holder.mPopupMenuButton.get().setPosition(position);

        // Set each playlist name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);

        if (dataHolder.mLineTwo == null) {
            holder.mLineTwo.get().setVisibility(View.GONE);
        } else {
            holder.mLineTwo.get().setVisibility(View.VISIBLE);
            holder.mLineTwo.get().setText(dataHolder.mLineTwo);
        }

        SmartPlaylistType type = SmartPlaylistType.getTypeById(dataHolder.mItemId);
        if (type != null) {
            // Set the image resource based on the icon
            switch (type) {
                case LastAdded:
                    holder.mImage.get().setImageResource(R.drawable.recently_added);
                    break;
                case RecentlyPlayed:
                    holder.mImage.get().setImageResource(R.drawable.recent_icon);
                    break;
                case TopTracks:
                default:
                    holder.mImage.get().setImageResource(R.drawable.top_tracks_icon);
                    break;
            }
        } else {
            // load the image
            ImageFetcher.getInstance(getContext()).loadPlaylistCoverArtImage(
                    dataHolder.mItemId, holder.mImage.get());
        }



        return convertView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getItemViewType(int position) {
        if (getItem(position).isSmartPlaylist()) {
            return SMART_PLAYLIST_VIEW_TYPE;
        }

        return 0;
    }

    /**
     * Method used to cache the data used to populate the list or grid. The idea
     * is to cache everything before {@code #getView(int, View, ViewGroup)} is
     * called.
     */
    public void buildCache() {
        mData = new DataHolder[getCount()];
        for (int i = 0; i < getCount(); i++) {
            // Build the artist
            final Playlist playlist = getItem(i);

            // Build the data holder
            mData[i] = new DataHolder();
            // Playlist Id
            mData[i].mItemId = playlist.mPlaylistId;
            // Playlist names (line one)
            mData[i].mLineOne = playlist.mPlaylistName;
            // # of songs
            if (playlist.mSongCount >= 0) {
                mData[i].mLineTwo = MusicUtils.makeLabel(getContext(),
                        R.plurals.Nsongs, playlist.mSongCount);
            }
        }
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        clear();
        mData = null;
    }

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }
}
