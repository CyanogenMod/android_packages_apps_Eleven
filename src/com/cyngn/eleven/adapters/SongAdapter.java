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

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Artist;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.ui.MusicHolder;
import com.cyngn.eleven.ui.MusicHolder.DataHolder;
import com.cyngn.eleven.ui.fragments.QueueFragment;
import com.cyngn.eleven.ui.fragments.SongFragment;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.PlayPauseProgressButton;

/**
 * This {@link ArrayAdapter} is used to display all of the songs on a user's
 * device for {@link SongFragment}. It is also used to show the queue in
 * {@link QueueFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SongAdapter extends ArrayAdapter<Song>
        implements SectionAdapter.BasicAdapter, IPopupMenuCallback {

    /**
     * Number of views (TextView)
     */
    private static final int VIEW_TYPE_COUNT = 1;

    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Image cache and image fetcher
     */
    private final ImageFetcher mImageFetcher;

    /**
     * The index of the item that is currently playing
     */
    private long mCurrentlyPlayingSongId = -1;

    /**
     * Used to cache the song info
     */
    private DataHolder[] mData;

    /**
     * Used to listen to the pop up menu callbacks
     */
    private IPopupMenuCallback.IListener mListener;

    /**
     * Constructor of <code>SongAdapter</code>
     * 
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public SongAdapter(final Activity context, final int layoutId) {
        super(context, 0);
        // Get the layout Id
        mLayoutId = layoutId;
        // Initialize the cache & image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(context);
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

            holder.mPopupMenuButton.get().setPopupMenuClickedListener(mListener);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Sets the position each time because of recycling
        holder.mPopupMenuButton.get().setPosition(position);
        // Set each song name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        // Set the album name (line two)
        holder.mLineTwo.get().setText(dataHolder.mLineTwo);

        // Asynchronously load the artist image into the adapter
        Song item = getItem(position);
        if (item.mAlbumId >= 0) {
            mImageFetcher.loadAlbumImage(item.mArtistName, item.mAlbumName, item.mAlbumId,
                    holder.mImage.get());
        }

        // padding doesn't apply to included layouts, so we need
        // to wrap it in a container and show/hide with the container
        PlayPauseProgressButton playPauseProgressButton = holder.mPlayPauseProgressButton.get();
        if (playPauseProgressButton != null) {
            View playPauseContainer = holder.mPlayPauseProgressContainer.get();

            if (mCurrentlyPlayingSongId == dataHolder.mItemId) {
                // make it visible
                playPauseProgressButton.enableAndShow();
                playPauseContainer.setVisibility(View.VISIBLE);
            } else {
                // hide it
                playPauseProgressButton.disableAndHide();
                playPauseContainer.setVisibility(View.GONE);
            }
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
            // Build the song
            final Song song = getItem(i);

            // Build the data holder
            mData[i] = new DataHolder();
            // Song Id
            mData[i].mItemId = song.mSongId;
            // Song names (line one)
            mData[i].mLineOne = song.mSongName;
            // Song duration (line one, right)
            mData[i].mLineOneRight = MusicUtils.makeShortTimeString(getContext(), song.mDuration);

            // Artist Name | Album Name (line two)
            mData[i].mLineTwo = MusicUtils.makeCombinedString(getContext(), song.mArtistName,
                    song.mAlbumName);
        }
    }

    /**
     * @param pause True to temporarily pause the disk cache, false otherwise.
     */
    public void setPauseDiskCache(final boolean pause) {
        if (mImageFetcher != null) {
            mImageFetcher.setPauseDiskCache(pause);
        }
    }

    /**
     * @param artist The key used to find the cached artist to remove
     */
    public void removeFromCache(final Artist artist) {
        if (mImageFetcher != null) {
            mImageFetcher.removeFromCache(artist.mArtistName);
        }
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        clear();
        mData = null;
    }

    /**
     * Do nothing.
     */
    public void flush() {
    }

    /**
     * Gets the item position for a given id
     * @param id identifies the object
     * @return the position if found, -1 otherwise
     */
    @Override
    public int getItemPosition(long id) {
        for (int i = 0; i < getCount(); i++) {
            if (getItem(i).mSongId == id) {
                return i;
            }
        }

        return  -1;
    }

    public void setCurrentlyPlayingSongId(long songId) {
        if (mCurrentlyPlayingSongId != songId) {
            mCurrentlyPlayingSongId = songId;

            notifyDataSetChanged();
        }
    }

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }
}
