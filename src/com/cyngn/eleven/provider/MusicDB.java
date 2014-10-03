/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.provider;

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
     *
     */


    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 2;

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
        PlaylistArtworkStore.getInstance(mContext).onCreate(db);
        RecentStore.getInstance(mContext).onCreate(db);
        SongPlayCount.getInstance(mContext).onCreate(db);
        SearchHistory.getInstance(mContext).onCreate(db);
        MusicPlaybackState.getInstance(mContext).onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        PlaylistArtworkStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        RecentStore.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        SongPlayCount.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        SearchHistory.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        MusicPlaybackState.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(MusicDB.class.getSimpleName(),
                "Downgrading from: " + oldVersion + " to " + newVersion + ". Dropping tables");
        PlaylistArtworkStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        RecentStore.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        SongPlayCount.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        SearchHistory.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
        MusicPlaybackState.getInstance(mContext).onDowngrade(db, oldVersion, newVersion);
    }
}
