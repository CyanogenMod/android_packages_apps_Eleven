/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyngn.eleven.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.cyngn.eleven.Config.SmartPlaylistType;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Playlist;
import com.cyngn.eleven.ui.MusicHolder;
import com.cyngn.eleven.ui.MusicHolder.DataHolder;
import com.cyngn.eleven.ui.fragments.PlaylistFragment;
import com.cyngn.eleven.utils.MusicUtils;

/**
 * This {@link ArrayAdapter} is used to display all of the playlists on a user's
 * device for {@link PlaylistFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistAdapter extends ArrayAdapter<Playlist> {

    /**
     * Number of views (TextView)
     */
    private static final int VIEW_TYPE_COUNT = 1;

    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Used to cache the playlist info
     */
    private DataHolder[] mData;

    /**
     * Constructor of <code>PlaylistAdapter</code>
     * 
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public PlaylistAdapter(final Context context, final int layoutId) {
        super(context, 0);
        // Get the layout Id
        mLayoutId = layoutId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mLayoutId, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

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
            // Clear any drawables
            holder.mImage.get().setBackground(null);

            // Set the image resource based on the icon
            switch (type) {
                case LastAdded:
                    holder.mImage.get().setImageResource(R.drawable.recently_added);
                    break;
                case TopTracks:
                default:
                    holder.mImage.get().setImageResource(R.drawable.top_tracks_icon);
                    break;
            }

            // set the special background color
            convertView.setBackgroundColor(getContext().getResources().
                    getColor(R.color.smart_playlist_item_background));
        } else {
            // load the image
            ImageFetcher.getInstance(getContext()).loadPlaylistCoverArtImage(
                    dataHolder.mItemId, holder.mImage.get());

            // clear the background color
            convertView.setBackgroundColor(Color.TRANSPARENT);
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

}
