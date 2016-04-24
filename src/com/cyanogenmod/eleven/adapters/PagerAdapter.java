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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.fragments.AlbumFragment;
import com.cyanogenmod.eleven.ui.fragments.ArtistFragment;
import com.cyanogenmod.eleven.ui.fragments.PlaylistFragment;
import com.cyanogenmod.eleven.ui.fragments.SongFragment;
import com.cyanogenmod.eleven.utils.Lists;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/**
 * A {@link FragmentPagerAdapter} class for swiping between playlists, recent,
 * artists, albums, songs, and genre {@link Fragment}s on phones.<br/>
 */
public class PagerAdapter extends FragmentPagerAdapter {

    private final SparseArray<WeakReference<Fragment>> mFragmentArray = new SparseArray<WeakReference<Fragment>>();

    private final List<Holder> mHolderList = Lists.newArrayList();

    private final Context mContext;

    private int mCurrentPage;

    /**
     * Constructor of <code>PagerAdatper<code>
     *
     * @param fragmentManager The supporting fragment manager
     */
    public PagerAdapter(final Context context, final FragmentManager fragmentManager) {
        super(fragmentManager);
        mContext = context;
    }

    /**
     * Method that adds a new fragment class to the viewer (the fragment is
     * internally instantiate)
     *
     * @param className The full qualified name of fragment class.
     * @param params The instantiate params.
     */
    @SuppressWarnings("synthetic-access")
    public void add(final Class<? extends Fragment> className, final Bundle params) {
        final Holder mHolder = new Holder();
        mHolder.mClassName = className.getName();
        mHolder.mParams = params;

        final int mPosition = mHolderList.size();
        mHolderList.add(mPosition, mHolder);
        notifyDataSetChanged();
    }

    /**
     * Method that returns the {@link Fragment} in the argument
     * position.
     *
     * @param position The position of the fragment to return.
     * @return Fragment The {@link Fragment} in the argument position.
     */
    public Fragment getFragment(final int position) {
        final WeakReference<Fragment> mWeakFragment = mFragmentArray.get(position);
        if (mWeakFragment != null && mWeakFragment.get() != null) {
            return mWeakFragment.get();
        }
        return getItem(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object instantiateItem(final ViewGroup container, final int position) {
        final Fragment mFragment = (Fragment)super.instantiateItem(container, position);
        final WeakReference<Fragment> mWeakFragment = mFragmentArray.get(position);
        if (mWeakFragment != null) {
            mWeakFragment.clear();
        }
        mFragmentArray.put(position, new WeakReference<Fragment>(mFragment));
        return mFragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Fragment getItem(final int position) {
        final Holder mCurrentHolder = mHolderList.get(position);
        final Fragment mFragment = Fragment.instantiate(mContext,
                mCurrentHolder.mClassName, mCurrentHolder.mParams);
        return mFragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyItem(final ViewGroup container, final int position, final Object object) {
        super.destroyItem(container, position, object);
        final WeakReference<Fragment> mWeakFragment = mFragmentArray.get(position);
        if (mWeakFragment != null) {
            mWeakFragment.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mHolderList.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence getPageTitle(final int position) {
        return mContext.getResources().getStringArray(R.array.page_titles)[position]
                .toUpperCase(Locale.getDefault());
    }

    /**
     * Method that returns the current page position.
     *
     * @return int The current page.
     */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    /**
     * Method that sets the current page position.
     *
     * @param currentPage The current page.
     */
    protected void setCurrentPage(final int currentPage) {
        mCurrentPage = currentPage;
    }

    /**
     * An enumeration of all the main fragments supported.
     */
    public enum MusicFragments {
        /**
         * The artist fragment
         */
        ARTIST(ArtistFragment.class),
        /**
         * The album fragment
         */
        ALBUM(AlbumFragment.class),
        /**
         * The song fragment
         */
        SONG(SongFragment.class),
        /**
         * The playlist fragment
         */
        PLAYLIST(PlaylistFragment.class);

        private Class<? extends Fragment> mFragmentClass;

        /**
         * Constructor of <code>MusicFragments</code>
         *
         * @param fragmentClass The fragment class
         */
        private MusicFragments(final Class<? extends Fragment> fragmentClass) {
            mFragmentClass = fragmentClass;
        }

        /**
         * Method that returns the fragment class.
         *
         * @return Class<? extends Fragment> The fragment class.
         */
        public Class<? extends Fragment> getFragmentClass() {
            return mFragmentClass;
        }

    }

    /**
     * A private class with information about fragment initialization
     */
    private final static class Holder {
        String mClassName;

        Bundle mParams;
    }
}
