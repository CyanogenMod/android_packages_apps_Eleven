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
import android.provider.MediaStore.Audio.AudioColumns;

import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.utils.Lists;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.PreferenceUtils;

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
    protected final ArrayList<Song> mSongList = Lists.newArrayList();

    /**
     * The {@link Cursor} used to run the query.
     */
    protected Cursor mCursor;

    /**
     * Constructor of <code>SongLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public SongLoader(final Context context) {
        super(context);
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
                final Song song = new Song(id, songName, artist, album, albumId, durationInSecs, year);

                // Add everything up
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
        return makeSongCursor(mContext, null);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @param selection Additional selection statement to use
     * @return The {@link Cursor} used to run the song query.
     */
    public static final Cursor makeSongCursor(final Context context, final String selection) {
        String selectionStatement = MusicUtils.MUSIC_ONLY_SELECTION;
        if (selection != null && !selection.isEmpty()) {
            selectionStatement += " AND " + selection;
        }

        return context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        AudioColumns.TITLE,
                        /* 2 */
                        AudioColumns.ARTIST,
                        /* 3 */
                        AudioColumns.ALBUM_ID,
                        /* 4 */
                        AudioColumns.ALBUM,
                        /* 5 */
                        AudioColumns.DURATION,
                        /* 6 */
                        AudioColumns.YEAR,
                }, selectionStatement, null,
                PreferenceUtils.getInstance(context).getSongSortOrder());
    }
}
