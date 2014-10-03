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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.ui.MusicHolder;
import com.cyngn.eleven.ui.MusicHolder.DataHolder;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.IPopupMenuCallback;

/**
 * This {@link ArrayAdapter} is used to display all of the albums on a user's
 * device for {@link RecentsFragment} and {@link AlbumsFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumAdapter extends ArrayAdapter<Album>
        implements SectionAdapter.BasicAdapter, IPopupMenuCallback {

    /**
     * Number of views (ImageView and TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Image cache and image fetcher
     */
    private final ImageFetcher mImageFetcher;

    /**
     * Semi-transparent overlay
     */
    private final int mOverlay;

    /**
     * Sets the album art on click listener to start playing them album when
     * touched.
     */
    private boolean mTouchPlay = false;

    /**
     * Used to cache the album info
     */
    private DataHolder[] mData;

    /**
     * Used to listen to the pop up menu callbacks
     */
    private IPopupMenuCallback.IListener mListener;

    /**
     * Constructor of <code>AlbumAdapter</code>
     * 
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     * @param style Determines which layout to use and therefore which items to
     *            load.
     */
    public AlbumAdapter(final Activity context, final int layoutId) {
        super(context, 0);
        // Get the layout Id
        mLayoutId = layoutId;
        // Initialize the cache & image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(context);
        // Cache the transparent overlay
        mOverlay = context.getResources().getColor(R.color.list_item_background);
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
            // set the pop up menu listener
            holder.mPopupMenuButton.get().setPopupMenuClickedListener(mListener);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Sets the position each time because of recycling
        holder.mPopupMenuButton.get().setPosition(position);
        // Set each album name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        // Set the artist name (line two)
        holder.mLineTwo.get().setText(dataHolder.mLineTwo);
        // Asynchronously load the album images into the adapter
        mImageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, dataHolder.mItemId,
                holder.mImage.get());

        if (mTouchPlay) {
            // Play the album when the artwork is touched
            playAlbum(holder.mImage.get(), position);
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
            // Build the album
            final Album album = getItem(i);

            // Build the data holder
            mData[i] = new DataHolder();
            // Album Id
            mData[i].mItemId = album.mAlbumId;
            // Album names (line one)
            mData[i].mLineOne = album.mAlbumName;
            // Album artist names (line two)
            mData[i].mLineTwo = album.mArtistName;
        }
    }

    /**
     * Starts playing an album if the user touches the artwork in the list.
     * 
     * @param album The {@link ImageView} holding the album
     * @param position The position of the album to play.
     */
    private void playAlbum(final ImageView album, final int position) {
        album.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                final long id = getItem(position).mAlbumId;
                final long[] list = MusicUtils.getSongListForAlbum(getContext(), id);
                MusicUtils.playAll(getContext(), list, 0, id, Config.IdType.Album, false);
            }
        });
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        clear();
        mData = null;
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
     * @param album The key used to find the cached album to remove
     */
    public void removeFromCache(final Album album) {
        if (mImageFetcher != null) {
            mImageFetcher.removeFromCache(
                    ImageFetcher.generateAlbumCacheKey(album.mAlbumName, album.mArtistName));
        }
    }

    /**
     * Flushes the disk cache.
     */
    public void flush() {
        mImageFetcher.flush();
    }

    /**
     * Gets the item position for a given id
     * @param id identifies the object
     * @return the position if found, -1 otherwise
     */
    @Override
    public int getItemPosition(long id) {
        for (int i = 0; i < getCount(); i++) {
            if (getItem(i).mAlbumId == id) {
                return i;
            }
        }

        return  -1;
    }

    /**
     * @param play True to play the album when the artwork is touched, false
     *            otherwise.
     */
    public void setTouchPlay(final boolean play) {
        mTouchPlay = play;
    }

    @Override
    public void setPopupMenuClickedListener(IPopupMenuCallback.IListener listener) {
        mListener = listener;
    }
}
