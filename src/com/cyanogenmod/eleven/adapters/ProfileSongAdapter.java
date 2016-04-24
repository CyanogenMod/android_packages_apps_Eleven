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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.model.Song;

import java.util.Collection;

/**
 * This {@link ArrayAdapter} is used to display the songs for a particular playlist
 * {@link com.cyanogenmod.eleven.ui.fragments.PlaylistDetailFragment}
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ProfileSongAdapter extends SongAdapter {
    /**
     * Instead of having random +1 and -1 sprinkled around, this variable will show what is really
     * related to the header
     */
    public static final int NUM_HEADERS = 1;

    /**
     * Fake header layout Id
     */
    private final int mHeaderId;

    /**
     * Constructor of <code>ProfileSongAdapter</code>
     *
     * @param activity The {@link Activity} to use
     * @param layoutId The resource Id of the view to inflate.
     */
    public ProfileSongAdapter(final long playlistId, final Activity activity, final int layoutId,
                              final int headerId) {
        super(activity, layoutId, playlistId, Config.IdType.Playlist);
        // Cache the header
        mHeaderId = headerId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {

        // Return a faux header at position 0
        if (position == 0) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(mHeaderId, parent, false);
            }

            return convertView;
        }

        return super.getView(position, convertView, parent);
    }

    /**
     * {@inheritDoc}
     */
    protected boolean showNowPlayingIndicator(final Song song, final int position) {
        return super.showNowPlayingIndicator(song, position)
                && mCurrentlyPlayingTrack.mSourcePosition == position - NUM_HEADERS;
    }

    @Override
    public boolean isEnabled(int position) {
        if (position == 0) {
            return false;
        }

        return super.isEnabled(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        return super.getViewTypeCount() + NUM_HEADERS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getItemViewType(final int position) {
        if (position == 0) {
            // since our view type count adds 1 to the super class, we can return viewtypecount - 1
            return getViewTypeCount() - 1;
        }
        return super.getItemViewType(position);
    }

    @Override
    public void addAll(Collection<? extends Song> collection) {
        // insert a header if one is needed
        insertHeader();
        super.addAll(collection);
    }

    @Override
    public void addAll(Song... items) {
        // insert a header if one is needed
        insertHeader();
        super.addAll(items);
    }

    /**
     * Make sure we insert our header when we add items
     */
    private void insertHeader() {
        if (getCount() == 0) {
            // add a dummy entry to the underlying adapter.  This is needed otherwise the
            // underlying adapter could crash because getCount() doesn't match up
            add(new Song(-1, null, null, null, -1, -1, -1));
        }
    }
}
