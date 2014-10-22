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

package com.cyngn.eleven.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;

import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.utils.Lists;
import com.cyngn.eleven.utils.PreferenceUtils;
import com.cyngn.eleven.utils.SortOrder;
import com.cyngn.eleven.utils.SortUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query {@link MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI} and return
 * the albums on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumLoader extends SectionCreator.SimpleListLoader<Album> {

    /**
     * The result
     */
    private ArrayList<Album> mAlbumsList = Lists.newArrayList();

    /**
     * The {@link Cursor} used to run the query.
     */
    private Cursor mCursor;

    /**
     * Constructor of <code>AlbumLoader</code>
     *
     * @param context The {@link Context} to use
     */
    public AlbumLoader(final Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Album> loadInBackground() {
        // Create the Cursor
        mCursor = makeAlbumCursor(getContext());
        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the album id
                final long id = mCursor.getLong(0);

                // Copy the album name
                final String albumName = mCursor.getString(1);

                // Copy the artist name
                final String artist = mCursor.getString(2);

                // Copy the number of songs
                final int songCount = mCursor.getInt(3);

                // Copy the release year
                final String year = mCursor.getString(4);

                // as per designer's request, don't show unknown albums
                if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    continue;
                }

                // Create a new album
                final Album album = new Album(id, albumName, artist, songCount, year);

                // Add everything up
                mAlbumsList.add(album);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        // requested album ordering
        String albumSortOrder = PreferenceUtils.getInstance(mContext).getAlbumSortOrder();

        // run a custom localized sort to try to fit items in to header buckets more nicely
        if (shouldEvokeCustomSortRoutine(albumSortOrder)) {
            mAlbumsList = SortUtils.localizeSortList(mAlbumsList, albumSortOrder);
        }

        return mAlbumsList;
    }

    /**
     * Evoke custom sorting routine if the sorting attribute is a String. MediaProvider's sort
     * can be trusted in other instances
     * @param sortOrder
     * @return
     */
    private boolean shouldEvokeCustomSortRoutine(String sortOrder) {
        return sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_A_Z) ||
               sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_Z_A) ||
               sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_ARTIST);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the album query.
     */
    public static final Cursor makeAlbumCursor(final Context context) {
        return context.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        AlbumColumns.ALBUM,
                        /* 2 */
                        AlbumColumns.ARTIST,
                        /* 3 */
                        AlbumColumns.NUMBER_OF_SONGS,
                        /* 4 */
                        AlbumColumns.FIRST_YEAR
                }, null, null, PreferenceUtils.getInstance(context).getAlbumSortOrder());
    }
}
