/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.format.PrefixHighlighter;
import com.cyngn.eleven.model.SearchResult;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.ui.MusicHolder;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;

import java.util.Locale;

/**
 * Used to populate the list view with the search results.
 */
public final class SummarySearchAdapter extends ArrayAdapter<SearchResult> implements SectionAdapter.BasicAdapter {

    /**
     * Number of views (ImageView and TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

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
     * Combines two strings
     */
    private String mCombineString;

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

        mCombineString = getContext().getResources().getString(R.string.combine_two_strings);
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
                    getViewResourceId(position), parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        final SearchResult item = getItem(position);

        switch (item.mType) {
            case Artist:
                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(item.mArtist, holder.mImage.get());

                mHighlighter.setText(holder.mLineOne.get(), item.mArtist, mPrefix);

                String songCount = MusicUtils.makeLabel(getContext(), R.plurals.Nsongs, item.mSongCount);
                String albumCount = MusicUtils.makeLabel(getContext(), R.plurals.Nalbums, item.mAlbumCount);
                holder.mLineTwo.get().setText(String.format(mCombineString, songCount, albumCount));

                break;
            case Album:
                // Asynchronously load the album images into the adapter
                mImageFetcher.loadAlbumImage(item.mArtist, item.mAlbum,
                        item.mId, holder.mImage.get());

                mHighlighter.setText(holder.mLineOne.get(), item.mAlbum, mPrefix);
                mHighlighter.setText(holder.mLineTwo.get(), item.mArtist, mPrefix);
                break;
            case Song:
                // Asynchronously load the album images into the adapter
                mImageFetcher.loadAlbumImage(item.mArtist, item.mAlbum,
                        item.mAlbumId, holder.mImage.get());

                mHighlighter.setText(holder.mLineOne.get(), item.mTitle, mPrefix);
                mHighlighter.setText(holder.mLineTwo.get(),
                        String.format(mCombineString, item.mArtist, item.mAlbum), mPrefix);
                break;
            case Playlist:
                mHighlighter.setText(holder.mLineOne.get(), item.mTitle, mPrefix);
                String songs = MusicUtils.makeLabel(getContext(), R.plurals.Nsongs, item.mSongCount);
                holder.mLineTwo.get().setText(songs);
                holder.mLineThree.get().setVisibility(View.GONE);
                break;
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
     * This categorizes the view types we want for each item type
     * @param position of the item
     * @return categorization
     */
    @Override
    public int getItemViewType(int position) {
        switch (getItem(position).mType) {
            case Artist:
            case Album:
            case Song:
                return 0;
            default:
            case Playlist:
                return 1;
        }
    }

    /**
     * this returns the layout needed for the item
     * @param position of the item
     * @return layout id
     */
    public int getViewResourceId(int position) {
        switch (getItemViewType(position)) {
            case 0:
                return R.layout.list_item_normal;
            default:
                return R.layout.list_item_simple;
        }
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
}