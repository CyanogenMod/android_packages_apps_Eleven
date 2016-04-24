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
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.ui.MusicHolder;
import com.cyanogenmod.eleven.ui.MusicHolder.DataHolder;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;

import java.util.Collections;
import java.util.List;

/**
 * This {@link ArrayAdapter} is used to display all of the albums on a user's
 * device for {@link RecentsFragment} and {@link AlbumsFragment}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumAdapter extends BaseAdapter implements IPopupMenuCallback {
    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Image cache and image fetcher
     */
    private final ImageFetcher mImageFetcher;

    /**
     * Used to cache the album info
     */
    private DataHolder[] mData = new DataHolder[0];
    private List<Album> mAlbums = Collections.emptyList();

    /**
     * Used to listen to the pop up menu callbacks
     */
    private IPopupMenuCallback.IListener mListener;

    /** number of columns of containing grid view,
     *  used to determine which views to pad */
    private int mColumns;
    private int mPadding;

    private Context mContext;

    /**
     * Constructor of <code>AlbumAdapter</code>
     *
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     * @param style Determines which layout to use and therefore which items to
     *            load.
     */
    public AlbumAdapter(final Activity context, final int layoutId) {
        mContext = context;
        // Get the layout Id
        mLayoutId = layoutId;
        // Initialize the cache & image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(context);
        mPadding = context.getResources().getDimensionPixelSize(R.dimen.list_item_general_margin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(mLayoutId, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
            // set the pop up menu listener
            holder.mPopupMenuButton.get().setPopupMenuClickedListener(mListener);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        adjustPadding(position, convertView);

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Sets the position each time because of recycling
        holder.mPopupMenuButton.get().setPosition(position);
        // Set each album name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        // Set the artist name (line two)
        holder.mLineTwo.get().setText(dataHolder.mLineTwo);
        // Asynchronously load the album images into the adapter
        mImageFetcher.loadAlbumImage(
                dataHolder.mLineTwo, dataHolder.mLineOne,
                dataHolder.mItemId, holder.mImage.get());

        return convertView;
    }

    private void adjustPadding(final int position, View convertView) {
        if (position < mColumns) {
            // first row
            convertView.setPadding(0, mPadding, 0, 0);
            return;
        }
        int count = getCount();
        int footers = count % mColumns;
        if (footers == 0) { footers = mColumns; }
        if (position >= (count-footers)) {
            // last row
            convertView.setPadding(0, 0, 0, mPadding);
        } else {
            // middle rows
            convertView.setPadding(0, 0 ,0, 0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getCount() {
        return mAlbums.size();
    }

    @Override
    public Album getItem(int pos) {
        return mAlbums.get(pos);
    }

    @Override
    public long getItemId(int pos) { return pos; }

    /**
     * Method used to cache the data used to populate the list or grid. The idea
     * is to cache everything before {@code #getView(int, View, ViewGroup)} is
     * called.
     */
    public void buildCache() {
        mData = new DataHolder[mAlbums.size()];
        int i = 0;
        for (Album album : mAlbums) {
            mData[i] = new DataHolder();
            mData[i].mItemId = album.mAlbumId;
            mData[i].mLineOne = album.mAlbumName;
            mData[i].mLineTwo = album.mArtistName;
            i++;
        }
    }

    public void setData(List<Album> albums) {
        mAlbums = albums;
        buildCache();
        notifyDataSetChanged();
    }

    public void setNumColumns(int columns) {
        mColumns = columns;
    }

    public void unload() {
        setData(Collections.<Album>emptyList());
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
    public int getItemPosition(long id) {
        int i = 0;
        for (Album album : mAlbums) {
            if (album.mAlbumId == id) {
                return i;
            }
            i++;
        }

        return -1;
    }

    @Override
    public void setPopupMenuClickedListener(IPopupMenuCallback.IListener listener) {
        mListener = listener;
    }
}