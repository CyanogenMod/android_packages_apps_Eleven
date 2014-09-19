/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */

package com.cyngn.eleven.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;

import com.cyngn.eleven.provider.SongPlayCount;
import com.cyngn.eleven.provider.SongPlayCount.SongPlayCountColumns;

/**
 * Used to query {@link android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the top Songs for a user based on number of times listened and time passed
 */
public class TopTracksLoader extends SongLoader {
    public static final int NUMBER_OF_SONGS = 99;

    public TopTracksLoader(final Context context) {
        super(context);
    }

    @Override
    protected Cursor getCursor() {
        return makeTopTracksCursor(mContext);
    }

    public static final Cursor makeTopTracksCursor(final Context context) {
        // first get the top results ids from the internal database
        Cursor songs = SongPlayCount.getInstance(context).getTopPlayedResults(NUMBER_OF_SONGS);

        if (songs != null && songs.moveToFirst()) {
            final int idColumnIndex = songs.getColumnIndex(SongPlayCountColumns.ID);

            // create the list of ids to select against
            StringBuilder selection = new StringBuilder();
            selection.append(BaseColumns._ID);
            selection.append(" IN (");

            selection.append(songs.getString(idColumnIndex));

            // this tracks the order of the ids
            long[] order = new long[songs.getCount()];

            do {
                selection.append(",");

                long id = songs.getLong(idColumnIndex);
                order[songs.getPosition()] = id;
                selection.append(String.valueOf(id));
            } while (songs.moveToNext());

            selection.append(")");

            // get a list of songs with the data given the selection statment
            Cursor songCursor = makeSongCursor(context, selection.toString());
            if (songCursor != null) {
                // now return the wrapped TopTracksCursor to handle sorting given order
                return new SortedCursor(songCursor, order, BaseColumns._ID);
            }
        }

        return null;
    }
}
