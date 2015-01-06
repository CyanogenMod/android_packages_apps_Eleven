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
package com.cyanogenmod.eleven.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MusicDB extends SQLiteOpenHelper {
    /**
     * Version History
     * v1 Sept 22 2014  Initial Merge of tables
     *                  Has PlaylistArtworkstore, RecentStore, SearchHistory, SongPlayCount
     * v2 Oct 7 2014    Added a new class MusicPlaybackState - need to bump version so the new
     *                  tables are created, but need to remove all drops from other classes to
     *                  maintain data
     * v3 Dec 4 2014    Add Sorting tables similar to Contacts to enable other languages like
     *                  Chinese to properly sort as they would expect
     * v4 Jan 6 2015    Missed Collate keyword on the LocalizedSongSortTable
     */


    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 4;

    /* Name of database file */
    public static final String DATABASENAME = "musicdb.db";

    private static MusicDB sInstance = null;

    private final Context mContext;

    /**
     * @param context The {@link android.content.Context} to use
     * @return A new instance of this class.
     */
    public static final synchronized MusicDB getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new MusicDB(context.getApplicationContext());
        }
        return sInstance;
    }

    public MusicDB(final Context context) {
        super(context, DATABASENAME, null, VERSION);

        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        PropertiesStore.getInstance(mContext).onCreate(db);
        PlaylistArtworkStore.getInstance(mContext).onCreate(db);
        RecentStore.getInstance(mContext).onCreate(db);
        SongPlayCount.getInstance(mContext).onCreate(db);
        SearchHistory.getInstance(mContext).onCreate(db);
        MusicPlaybackState.getInstance(mContext).onCreate(db);
        LocalizedStore.getInstance(mContext).onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        PropertiesStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        PlaylistArtworkStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        RecentStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        SongPlayCount.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        SearchHistory.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        MusicPlaybackState.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        LocalizedStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(MusicDB.class.getSimpleName(),
                "Downgrading from: " + oldVersion + " to " + newVersion + ". Dropping tables");
        PropertiesStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        PlaylistArtworkStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        RecentStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        SongPlayCount.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        SearchHistory.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        MusicPlaybackState.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        LocalizedStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
    }
}
