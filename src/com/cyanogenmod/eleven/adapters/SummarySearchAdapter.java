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
package com.cyanogenmod.eleven.adapters;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.format.PrefixHighlighter;
import com.cyanogenmod.eleven.model.SearchResult;
import com.cyanogenmod.eleven.sectionadapter.SectionAdapter;
import com.cyanogenmod.eleven.ui.MusicHolder;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;

import java.util.Locale;

/**
 * Used to populate the list view with the search results.
 */
public final class SummarySearchAdapter extends ArrayAdapter<SearchResult>
        implements SectionAdapter.BasicAdapter, IPopupMenuCallback {

    /**
     * Image cache and image fetcher
     */
    private final ImageFetcher mImageFetcher;

    /**
     * Highlights the query
     */
    private final PrefixHighlighter mHighlighter;

    /**
     * The prefix that's highlighted
     */
    private char[] mPrefix;

    /**
     * Used to listen to the pop up menu callbacks
     */
    private IListener mListener;

    /**
     * Constructor for <code>SearchAdapter</code>
     *
     * @param context The {@link Activity} to use.
     */
    public SummarySearchAdapter(final Activity context) {
        super(context, 0);
        // Initialize the cache & image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(context);
        // Create the prefix highlighter
        mHighlighter = new PrefixHighlighter(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
            /* Recycle ViewHolder's items */
        MusicHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item_normal, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
            // set the pop up menu listener
            holder.mPopupMenuButton.get().setPopupMenuClickedListener(mListener);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Sets the position each time because of recycling
        holder.mPopupMenuButton.get().setPosition(position);

        final SearchResult item = getItem(position);

        switch (item.mType) {
            case Artist:
                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(item.mArtist, holder.mImage.get());

                setText(holder.mLineOne.get(), item.mArtist);

                String songCount = MusicUtils.makeLabel(getContext(), R.plurals.Nsongs, item.mSongCount);
                String albumCount = MusicUtils.makeLabel(getContext(), R.plurals.Nalbums, item.mAlbumCount);
                // Album Name | Artist Name (line two)
                holder.mLineTwo.get().setText(MusicUtils.makeCombinedString(getContext(), songCount, albumCount));
                break;
            case Album:
                // Asynchronously load the album images into the adapter
                mImageFetcher.loadAlbumImage(item.mArtist, item.mAlbum,
                        item.mId, holder.mImage.get());

                setText(holder.mLineOne.get(), item.mAlbum);
                setText(holder.mLineTwo.get(), item.mArtist);
                break;
            case Song:
                // Asynchronously load the album images into the adapter
                mImageFetcher.loadAlbumImage(item.mArtist, item.mAlbum,
                        item.mAlbumId, holder.mImage.get());

                setText(holder.mLineOne.get(), item.mTitle);
                setText(holder.mLineTwo.get(),
                        MusicUtils.makeCombinedString(getContext(), item.mArtist, item.mAlbum));
                break;
            case Playlist:
                // Asynchronously load the playlist images into the adapter
                ImageFetcher.getInstance(getContext()).loadPlaylistCoverArtImage(
                        item.mId, holder.mImage.get());

                setText(holder.mLineOne.get(), item.mTitle);
                String songs = MusicUtils.makeLabel(getContext(), R.plurals.Nsongs, item.mSongCount);
                holder.mLineTwo.get().setText(songs);
                break;
        }

        return convertView;
    }

    /**
     * Sets the text onto the textview with highlighting if a prefix is defined
     * @param textView
     * @param text
     */
    private void setText(final TextView textView, final String text) {
        if (mPrefix == null) {
            textView.setText(text);
        } else {
            mHighlighter.setText(textView, text, mPrefix);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * @param pause True to temporarily pause the disk cache, false
     *            otherwise.
     */
    public void setPauseDiskCache(final boolean pause) {
        if (mImageFetcher != null) {
            mImageFetcher.setPauseDiskCache(pause);
        }
    }

    /**
     * @param prefix The query to filter.
     */
    public void setPrefix(final CharSequence prefix) {
        if (!TextUtils.isEmpty(prefix)) {
            mPrefix = prefix.toString().toUpperCase(Locale.getDefault()).toCharArray();
        } else {
            mPrefix = null;
        }
    }

    @Override
    public void unload() {
        clear();
    }

    @Override
    public void buildCache() {

    }

    @Override
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
            if (getItem(i).mId == id) {
                return i;
            }
        }

        return  -1;
    }

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }
}