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

import android.content.Context;

import com.cyanogenmod.eleven.loaders.WrappedAsyncTaskLoader;
import com.cyanogenmod.eleven.utils.SectionCreatorUtils;

import java.util.List;
import java.util.TreeMap;

/**
 * This class wraps a SimpleListLoader and creates header sections for the sections
 * @param <T> The type of item that is loaded
 */
public class SectionCreator<T> extends WrappedAsyncTaskLoader<SectionListContainer<T>> {
    /**
     * Simple list loader class that exposes a load method
     * @param <T> type of item to load
     */
    public static abstract class SimpleListLoader<T> extends WrappedAsyncTaskLoader<List<T>> {
        protected Context mContext;

        public SimpleListLoader(Context context) {
            super(context);
            mContext = context;
        }

        public Context getContext() {
            return mContext;
        }
    }

    private SimpleListLoader<T> mLoader;
    private SectionCreatorUtils.IItemCompare<T> mComparator;

    /**
     * Creates a SectionCreator object which loads @loader
     * @param context The {@link Context} to use.
     * @param loader loader to wrap
     * @param comparator the comparison object to run to create the sections
     */
    public SectionCreator(Context context, SimpleListLoader<T> loader,
                          SectionCreatorUtils.IItemCompare<T> comparator) {
        super(context);
        mLoader = loader;
        mComparator = comparator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SectionListContainer<T> loadInBackground() {
        List<T> results = mLoader.loadInBackground();
        TreeMap<Integer, SectionCreatorUtils.Section> sections = null;

        if (mComparator != null) {
            sections = SectionCreatorUtils.createSections(results, mComparator);
        }

        return new SectionListContainer<T>(sections, results);
    }
}
