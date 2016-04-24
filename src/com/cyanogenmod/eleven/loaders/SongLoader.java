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
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.text.TextUtils;

import com.cyanogenmod.eleven.model.Song;
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
 * Used to query {@link MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the songs on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SongLoader extends SectionCreator.SimpleListLoader<Song> {

    /**
     * The result
     */
    protected ArrayList<Song> mSongList = Lists.newArrayList();

    /**
     * The {@link Cursor} used to run the query.
     */
    protected Cursor mCursor;

    /**
     * Additional selection filter
     */
    protected String mSelection;

    /**
     * @param context The {@link Context} to use
     */
    public SongLoader(final Context context) {
        this(context, null);
    }

    /**
     * @param context The {@link Context} to use
     * @param selection Additional selection filter to apply to the loader
     */
    public SongLoader(final Context context, final String selection) {
        super(context);

        mSelection = selection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Song> loadInBackground() {
        // Create the Cursor
        mCursor = getCursor();

        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the song Id
                final long id = mCursor.getLong(0);

                // Copy the song name
                final String songName = mCursor.getString(1);

                // Copy the artist name
                final String artist = mCursor.getString(2);

                // Copy the album id
                final long albumId = mCursor.getLong(3);

                // Copy the album name
                final String album = mCursor.getString(4);

                // Copy the duration
                final long duration = mCursor.getLong(5);

                // Convert the duration into seconds
                final int durationInSecs = (int) duration / 1000;

                // Copy the Year
                final int year = mCursor.getInt(6);

                // Create a new song
                final Song song = new Song(id, songName, artist, album, albumId,
                                            durationInSecs, year);

                if (mCursor instanceof SortedCursor) {
                    song.mBucketLabel = (String)((SortedCursor)mCursor).getExtraData();
                }

                mSongList.add(song);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        return mSongList;
    }

    /**
     * Gets the cursor for the loader - can be overriden
     * @return cursor to load
     */
    protected Cursor getCursor() {
        return makeSongCursor(mContext, mSelection);
    }

    /**
     * For string-based sorts, return the localized store sort parameter, otherwise return null
     * @param sortOrder the song ordering preference selected by the user
     */
    private static LocalizedStore.SortParameter getSortParameter(String sortOrder) {
        if (sortOrder.equals(SortOrder.SongSortOrder.SONG_A_Z) ||
                sortOrder.equals(SortOrder.SongSortOrder.SONG_Z_A)) {
            return LocalizedStore.SortParameter.Song;
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_ALBUM)) {
            return LocalizedStore.SortParameter.Album;
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_ARTIST)) {
            return LocalizedStore.SortParameter.Artist;
        }

        return null;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     *
     * @param context The {@link Context} to use.
     * @param selection Additional selection statement to use
     * @return The {@link Cursor} used to run the song query.
     */
    public static final Cursor makeSongCursor(final Context context, final String selection) {
        return makeSongCursor(context, selection, true);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     *
     * @param context The {@link Context} to use.
     * @param selection Additional selection statement to use
     * @param runSort For localized sorts this can enable/disable the logic for running the
     *                additional localization sort.  Queries that apply their own sorts can pass
     *                in false for a boost in perf
     * @return The {@link Cursor} used to run the song query.
     */
    public static final Cursor makeSongCursor(final Context context, final String selection,
                                              final boolean runSort) {
        String selectionStatement = MusicUtils.MUSIC_ONLY_SELECTION;
        if (!TextUtils.isEmpty(selection)) {
            selectionStatement += " AND " + selection;
        }

        final String songSortOrder = PreferenceUtils.getInstance(context).getSongSortOrder();

        Cursor cursor = context.getContentResolver().query(Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        Audio.Media._ID,
                        /* 1 */
                        Audio.Media.TITLE,
                        /* 2 */
                        Audio.Media.ARTIST,
                        /* 3 */
                        Audio.Media.ALBUM_ID,
                        /* 4 */
                        Audio.Media.ALBUM,
                        /* 5 */
                        Audio.Media.DURATION,
                        /* 6 */
                        Audio.Media.YEAR,
                }, selectionStatement, null, songSortOrder);

        // if our sort is a localized-based sort, grab localized data from the store
        final SortParameter sortParameter = getSortParameter(songSortOrder);
        if (runSort && sortParameter != null && cursor != null) {
            final boolean descending = MusicUtils.isSortOrderDesending(songSortOrder);
            return LocalizedStore.getInstance(context).getLocalizedSort(cursor, Audio.Media._ID,
                    SortParameter.Song, sortParameter, descending, TextUtils.isEmpty(selection));
        }

        return cursor;
    }
}
