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

package com.cyanogenmod.eleven.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;

import com.cyanogenmod.eleven.provider.RecentStore;
import com.cyanogenmod.eleven.provider.SongPlayCount;
import com.cyanogenmod.eleven.provider.SongPlayCount.SongPlayCountColumns;

import java.util.ArrayList;

/**
 * Used to query {@link android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * a sorted list of songs based on either the TopTracks or the RecentSongs
 */
public class TopTracksLoader extends SongLoader {
    // used for the top played results
    public static final int NUMBER_OF_SONGS = 99;

    public enum QueryType {
        TopTracks,
        RecentSongs,
    }

    protected QueryType mQueryType;

    public TopTracksLoader(final Context context, QueryType type) {
        super(context);

        mQueryType = type;
    }

    @Override
    protected Cursor getCursor() {
        SortedCursor retCursor = null;
        if (mQueryType == QueryType.TopTracks) {
            retCursor = makeTopTracksCursor(mContext);
        } else if (mQueryType == QueryType.RecentSongs) {
            retCursor = makeRecentTracksCursor(mContext);
        }

        // clean up the databases with any ids not found
        if (retCursor != null) {
            ArrayList<Long> missingIds = retCursor.getMissingIds();
            if (missingIds != null && missingIds.size() > 0) {
                // for each unfound id, remove it from the database
                // this codepath should only really be hit if the user removes songs
                // outside of the Eleven app
                for (long id : missingIds) {
                    if (mQueryType == QueryType.TopTracks) {
                        SongPlayCount.getInstance(mContext).removeItem(id);
                    } else if (mQueryType == QueryType.RecentSongs) {
                        RecentStore.getInstance(mContext).removeItem(id);
                    }
                }
            }
        }

        return retCursor;
    }

    /**
     * This creates a sorted cursor based on the top played results
     * @param context Android context
     * @return sorted cursor
     */
    public static final SortedCursor makeTopTracksCursor(final Context context) {
        // first get the top results ids from the internal database
        Cursor songs = SongPlayCount.getInstance(context).getTopPlayedResults(NUMBER_OF_SONGS);

        try {
            return makeSortedCursor(context, songs,
                    songs.getColumnIndex(SongPlayCountColumns.ID));
        } finally {
            if (songs != null) {
                songs.close();
                songs = null;
            }
        }
    }

    /**
     * This creates a sorted cursor based on the recently played tracks
     * @param context Android context
     * @return sorted cursor
     */
    public static final SortedCursor makeRecentTracksCursor(final Context context) {
        // first get the top results ids from the internal database
        Cursor songs = RecentStore.getInstance(context).queryRecentIds(null);

        try {
            return makeSortedCursor(context, songs,
                    songs.getColumnIndex(SongPlayCountColumns.ID));
        } finally {
            if (songs != null) {
                songs.close();
                songs = null;
            }
        }
    }

    /**
     * This creates a sorted song cursor given a cursor that contains the sort order
     * @param context Android context
     * @param cursor This is the cursor used to determine the order of the ids
     * @param idColumn the id column index of the cursor
     * @return a Sorted Cursor of songs
     */
    public static final SortedCursor makeSortedCursor(final Context context, final Cursor cursor,
                                                      final int idColumn) {
        if (cursor != null && cursor.moveToFirst()) {
            // create the list of ids to select against
            StringBuilder selection = new StringBuilder();
            selection.append(BaseColumns._ID);
            selection.append(" IN (");

            // this tracks the order of the ids
            long[] order = new long[cursor.getCount()];

            long id = cursor.getLong(idColumn);
            selection.append(id);
            order[cursor.getPosition()] = id;

            while (cursor.moveToNext()) {
                selection.append(",");

                id = cursor.getLong(idColumn);
                order[cursor.getPosition()] = id;
                selection.append(String.valueOf(id));
            }

            selection.append(")");

            // get a list of songs with the data given the selection statement
            Cursor songCursor = makeSongCursor(context, selection.toString(), false);
            if (songCursor != null) {
                // now return the wrapped TopTracksCursor to handle sorting given order
                return new SortedCursor(songCursor, order, BaseColumns._ID, null);
            }
        }

        return null;
    }
}
