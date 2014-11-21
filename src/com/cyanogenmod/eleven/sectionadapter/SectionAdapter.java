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
package com.cyanogenmod.eleven.sectionadapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.MusicHolder;
import com.cyanogenmod.eleven.utils.SectionCreatorUtils.Section;
import com.cyanogenmod.eleven.utils.SectionCreatorUtils.SectionType;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;

import java.util.TreeMap;

/**
 * This class wraps an ArrayAdapter that implements BasicAdapter and allows Sections to be inserted
 * into the list.  This wraps the methods for getting the view/indices and returns the section
 * heads and if it is an underlying item it flows it through the underlying adapter
 * @param <TItem> The underlying item that is in the array adapter
 * @param <TArrayAdapter> the arrayadapter that contains TItem and implements BasicAdapter
 */
public class SectionAdapter<TItem,
        TArrayAdapter extends ArrayAdapter<TItem> & SectionAdapter.BasicAdapter & IPopupMenuCallback>
        extends BaseAdapter implements IPopupMenuCallback, IPopupMenuCallback.IListener {
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
     * A map of external position to the Section type and Identifier
     */
    protected TreeMap<Integer, Section> mSections;

    protected int mHeaderLayoutId;
    protected boolean mHeaderEnabled;

    protected int mFooterLayoutId;
    protected boolean mFooterEnabled;

    /**
     * Popup menu click listener
     */
    protected IListener mListener;

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
        mUnderlyingAdapter.setPopupMenuClickedListener(this);
        mSections = new TreeMap<Integer, Section>();
        setupHeaderParameters(R.layout.list_header, false);
        // since we have no good default footer, just re-use the header layout
        setupFooterParameters(R.layout.list_header, false);
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
        if (isSection(position)) {
            if (convertView == null) {
                int layoutId = mHeaderLayoutId;
                if (isSectionFooter(position)) {
                    layoutId = mFooterLayoutId;
                }

                convertView = LayoutInflater.from(mContext).inflate(layoutId, parent, false);
            }

            TextView title = (TextView)convertView.findViewById(R.id.title);
            title.setText(mSections.get(position).mIdentifier);
        } else {
            convertView = mUnderlyingAdapter.getView(
                    getInternalPosition(position), convertView, parent);

            Object tag = convertView.getTag();
            if (tag instanceof MusicHolder) {
                MusicHolder holder = (MusicHolder)tag;
                View divider = holder.mDivider.get();
                if (divider != null) {
                    // if it is the last item in the list, or it is an item before a section divider
                    // then hide the divider, otherwise show it
                    if (position == getCount() - 1 || isSection(position + 1)) {
                        divider.setVisibility(View.INVISIBLE);
                    } else {
                        divider.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        return convertView;
    }

    /**
     * Setup the header parameters
     * @param layoutId the layout id used to inflate
     * @param enabled whether clicking is enabled on the header
     */
    public void setupHeaderParameters(int layoutId, boolean enabled) {
        mHeaderLayoutId = layoutId;
        mHeaderEnabled = enabled;
    }

    /**
     * Setup the footer parameters
     * @param layoutId the layout id used to inflate
     * @param enabled whether clicking is enabled on the footer
     */
    public void setupFooterParameters(int layoutId, boolean enabled) {
        mFooterLayoutId = layoutId;
        mFooterEnabled = enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mSections.size() + mUnderlyingAdapter.getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getItem(int position) {
        if (isSection(position)) {
            return mSections.get(position);
        }

        return mUnderlyingAdapter.getItem(getInternalPosition(position));
    }

    /**
     * Gets the underlying adapter's item
     * @param position position to query for
     * @return the underlying item or null if a section header is queried
     */
    public TItem getTItem(int position) {
        if (isSection(position)) {
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
        } else if (isSectionFooter(position)) {
            // use the last view type id as the section header
            return getViewTypeCount() - 2;
        }

        return mUnderlyingAdapter.getItemViewType(getInternalPosition(position));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        // increment view type count by 2 for section headers and section footers
        return mUnderlyingAdapter.getViewTypeCount() + 2;
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
        if (isSectionHeader(position)) {
            return mHeaderEnabled;
        } else if (isSectionFooter(position)) {
            return mFooterEnabled;
        }

        return true;
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
    public boolean isSectionHeader(int position) {
        return mSections.containsKey(position) && mSections.get(position).mType == SectionType.Header;
    }

    /**
     * Determines whether the item at the position is a section footer
     * @param position position in the overall lis
     * @return true if a section footer
     */
    public boolean isSectionFooter(int position) {
        return mSections.containsKey(position) && mSections.get(position).mType == SectionType.Footer;
    }

    /**
     * Determines whether the item at the position is a section of some type
     * @param position position in the overall lis
     * @return true if the item is a section
     */
    public boolean isSection(int position) {
        return mSections.containsKey(position);
    }

    /**
     * Converts the external position to the internal position.  This is needed to determine
     * the position to pass into the underlying adapter
     * @param position external position
     * @return the internal position
     */
    public int getInternalPosition(int position) {
        if (isSection(position)) {
            return -1;
        }

        int countSectionHeaders = 0;

        for (Integer sectionPosition : mSections.keySet()) {
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
        for (Integer sectionPosition : mSections.keySet()) {
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

        if (data.mSections == null) {
            mSections.clear();
        } else {
            mSections = data.mSections;
        }

        mUnderlyingAdapter.addAll(data.mListResults);

        mUnderlyingAdapter.buildCache();

        notifyDataSetChanged();
    }

    /**
     * unloads the underlying adapter
     */
    public void unload() {
        mSections.clear();
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
        mSections.clear();
        mUnderlyingAdapter.clear();
        mSections.clear();
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

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }

    @Override
    public void onPopupMenuClicked(View v, int position) {
        if (mListener != null) {
            mListener.onPopupMenuClicked(v, getExternalPosition(position));
        }
    }
}
