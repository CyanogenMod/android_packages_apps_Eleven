/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.sectionadapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.cyngn.eleven.R;

import java.util.TreeMap;

/**
 * This class wraps an ArrayAdapter that implements BasicAdapter and allows Sections to be inserted
 * into the list.  This wraps the methods for getting the view/indices and returns the section
 * heads and if it is an underlying item it flows it through the underlying adapter
 * @param <TItem> The underlying item that is in the array adapter
 * @param <TArrayAdapter> the arrayadapter that contains TItem and implements BasicAdapter
 */
public class SectionAdapter<TItem,
        TArrayAdapter extends ArrayAdapter<TItem> & SectionAdapter.BasicAdapter>
        extends BaseAdapter {
    /**
     * Basic interface that the adapters implement
     */
    public interface BasicAdapter {
        public void unload();
        public void buildCache();
        public void flush();
        public int getItemPosition(long id);
    }

    /**
     * The underlying adapter to wrap
     */
    protected TArrayAdapter mUnderlyingAdapter;

    /**
     * A map of external position to the String to use as the header
     */
    protected TreeMap<Integer, String> mSectionHeaders;

    /**
     * {@link Context}
     */
    protected final Context mContext;

    /**
     * Creates a SectionAdapter
     * @param context The {@link Context} to use.
     * @param underlyingAdapter the underlying adapter to wrap
     */
    public SectionAdapter(final Activity context, final TArrayAdapter underlyingAdapter) {
        mContext = context;
        mUnderlyingAdapter = underlyingAdapter;
        mSectionHeaders = new TreeMap<Integer, String>();
    }

    /**
     * Gets the underlying array adapter
     * @return the underlying array adapter
     */
    public TArrayAdapter getUnderlyingAdapter() {
        return mUnderlyingAdapter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        if (isSectionHeader(position)) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.list_header, parent, false);
            }

            TextView header = (TextView)convertView.findViewById(R.id.header);
            header.setText(mSectionHeaders.get(position));
        } else {
            convertView = mUnderlyingAdapter.getView(getInternalPosition(position), convertView, parent);
        }

        return convertView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mSectionHeaders.size() + mUnderlyingAdapter.getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getItem(int position) {
        if (isSectionHeader(position)) {
            return mSectionHeaders.get(position);
        }

        return mUnderlyingAdapter.getItem(getInternalPosition(position));
    }

    /**
     * Gets the underlying adapter's item
     * @param position position to query for
     * @return the underlying item or null if a section header is queried
     */
    public TItem getTItem(int position) {
        if (isSectionHeader(position)) {
            return null;
        }

        return mUnderlyingAdapter.getItem(getInternalPosition(position));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        return position;
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
    public int getItemViewType(int position) {
        if (isSectionHeader(position)) {
            // use the last view type id as the section header
            return getViewTypeCount() - 1;
        }

        return mUnderlyingAdapter.getItemViewType(getInternalPosition(position));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        // increment view type count by 1 for section headers
        return mUnderlyingAdapter.getViewTypeCount() + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();

        mUnderlyingAdapter.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetInvalidated() {
        super.notifyDataSetInvalidated();

        mUnderlyingAdapter.notifyDataSetInvalidated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(int position) {
        // don't enable clicking/long press for section headers
        return !isSectionHeader(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    /**
     * Determines whether the item at the position is a section header
     * @param position position in the overall lis
     * @return true if a section header
     */
    private boolean isSectionHeader(int position) {
        return mSectionHeaders.containsKey(position);
    }

    /**
     * Converts the external position to the internal position.  This is needed to determine
     * the position to pass into the underlying adapter
     * @param position external position
     * @return the internal position
     */
    public int getInternalPosition(int position) {
        if (isSectionHeader(position)) {
            return -1;
        }

        int countSectionHeaders = 0;

        for (Integer sectionPosition : mSectionHeaders.keySet()) {
            if (sectionPosition <= position) {
                countSectionHeaders++;
            } else {
                break;
            }
        }

        return position - countSectionHeaders;
    }

    /**
     * Converts the underlaying adapter position to wrapped adapter position
     * @param internalPosition the position of the underlying adapter
     * @return the position of the wrapped adapter
     */
    public int getExternalPosition(int internalPosition) {
        int externalPosition = internalPosition;
        for (Integer sectionPosition : mSectionHeaders.keySet()) {
            // because the section headers are tracking the 'merged' lists, we need to keep bumping
            // our position for each found section header
            if (sectionPosition <= externalPosition) {
                externalPosition++;
            } else {
                break;
            }
        }

        return externalPosition;
    }

    /**
     * Sets the data on the adapter
     * @param data data to set
     */
    public void setData(SectionListContainer<TItem> data) {
        mUnderlyingAdapter.unload();

        if (data.mSectionIndices == null) {
            mSectionHeaders.clear();
        } else {
            mSectionHeaders = data.mSectionIndices;
        }

        mUnderlyingAdapter.addAll(data.mListResults);

        mUnderlyingAdapter.buildCache();

        notifyDataSetChanged();
    }

    /**
     * unloads the underlying adapter
     */
    public void unload() {
        mUnderlyingAdapter.unload();
        notifyDataSetChanged();
    }

    /**
     * flushes the underlying adapter
     */
    public void flush() {
        mUnderlyingAdapter.flush();
        notifyDataSetChanged();
    }

    public void clear() {
        mUnderlyingAdapter.clear();
        mSectionHeaders.clear();
    }

    /**
     * Gets the item position for the given identifier
     * @param identifier used to identify the object
     * @return item position, or -1 if not found
     */
    public int getItemPosition(long identifier) {
        int internalPosition = mUnderlyingAdapter.getItemPosition(identifier);
        if (internalPosition >= 0) {
            return getExternalPosition(internalPosition);
        }

        return -1;
    }
}
