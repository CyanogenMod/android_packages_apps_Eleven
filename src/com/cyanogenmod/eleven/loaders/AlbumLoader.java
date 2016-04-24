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

package com.cyanogenmod.eleven.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;

import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.provider.LocalizedStore;
import com.cyanogenmod.eleven.provider.LocalizedStore.SortParameter;
import com.cyanogenmod.eleven.sectionadapter.SectionCreator;
import com.cyanogenmod.eleven.utils.Lists;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PreferenceUtils;
import com.cyanogenmod.eleven.utils.SortOrder;

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
     * Additional selection filter
     */
    protected Long mArtistId;

    /**
     * @param context The {@link Context} to use
     */
    public AlbumLoader(final Context context) {
        this(context, null);
    }

    /**
     * @param context The {@link Context} to use
     * @param artistId The artistId to filter against or null if none
     */
    public AlbumLoader(final Context context, final Long artistId) {
        super(context);

        mArtistId = artistId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Album> loadInBackground() {
        // Create the Cursor
        mCursor = makeAlbumCursor(getContext(), mArtistId);
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

                if (mCursor instanceof SortedCursor) {
                    album.mBucketLabel = (String)((SortedCursor)mCursor).getExtraData();
                }

                // Add everything up
                mAlbumsList.add(album);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        return mAlbumsList;
    }

    /**
     * For string-based sorts, return the localized store sort parameter, otherwise return null
     * @param sortOrder the song ordering preference selected by the user
     */
    private static LocalizedStore.SortParameter getSortParameter(String sortOrder) {
        if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_A_Z) ||
                sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_Z_A)) {
            return LocalizedStore.SortParameter.Album;
        } else if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_ARTIST)) {
            return LocalizedStore.SortParameter.Artist;
        }

        return null;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     *
     * @param context The {@link Context} to use.
     * @param artistId The artistId we want to find albums for or null if we want all albums
     * @return The {@link Cursor} used to run the album query.
     */
    public static final Cursor makeAlbumCursor(final Context context, final Long artistId) {
        // requested album ordering
        final String albumSortOrder = PreferenceUtils.getInstance(context).getAlbumSortOrder();
        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        if (artistId != null) {
            uri = MediaStore.Audio.Artists.Albums.getContentUri("external", artistId);
        }

        Cursor cursor = context.getContentResolver().query(uri,
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
                }, null, null, albumSortOrder);

        // if our sort is a localized-based sort, grab localized data from the store
        final SortParameter sortParameter = getSortParameter(albumSortOrder);
        if (sortParameter != null && cursor != null) {
            final boolean descending = MusicUtils.isSortOrderDesending(albumSortOrder);
            return LocalizedStore.getInstance(context).getLocalizedSort(cursor, BaseColumns._ID,
                    SortParameter.Album, sortParameter, descending, artistId == null);
        }

        return cursor;
    }
}
