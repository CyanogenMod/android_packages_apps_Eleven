/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.cyanogenmod.eleven.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.eleven.loaders.SortedCursor;
import com.cyanogenmod.eleven.locale.LocaleSet;
import com.cyanogenmod.eleven.locale.LocaleSetManager;
import com.cyanogenmod.eleven.locale.LocaleUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import libcore.icu.ICU;

/**
 * Because sqlite localized collator isn't sufficient, we need to store more specialized logic
 * into our own db similar to contacts db.  This is most noticeable in languages like Chinese,
 * Japanese etc
 */
public class LocalizedStore {
    private static final String TAG = LocalizedStore.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static LocalizedStore sInstance = null;

    private static final int LOCALE_CHANGED = 0;

    private final MusicDB mMusicDatabase;
    private final Context mContext;
    private final ContentValues mContentValues = new ContentValues(10);
    private final LocaleSetManager mLocaleSetManager;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    public enum SortParameter {
        Song,
        Artist,
        Album,
    };

    private static class SortData {
        long[] ids;
        List<String> bucketLabels;
    }

    /**
     * @param context The {@link android.content.Context} to use
     * @return A new instance of this class.
     */
    public static final synchronized LocalizedStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new LocalizedStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private LocalizedStore(final Context context) {
        mMusicDatabase = MusicDB.getInstance(context);
        mContext = context;
        mLocaleSetManager = new LocaleSetManager(mContext);

        mHandlerThread = new HandlerThread("LocalizedStoreWorker",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == LOCALE_CHANGED && mLocaleSetManager.localeSetNeedsUpdate()) {
                    rebuildLocaleData(mLocaleSetManager.getSystemLocaleSet());
                }
            }
        };

        // check to see if locale has changed
        onLocaleChanged();
    }

    public void onCreate(final SQLiteDatabase db) {

        String[] tables = new String[]{
            "CREATE TABLE IF NOT EXISTS " + SongSortColumns.TABLE_NAME + "(" +
                    SongSortColumns.ID + " INTEGER PRIMARY KEY," +
                    SongSortColumns.ARTIST_ID + " INTEGER NOT NULL," +
                    SongSortColumns.ALBUM_ID + " INTEGER NOT NULL," +
                    SongSortColumns.NAME + " TEXT COLLATE LOCALIZED," +
                    SongSortColumns.NAME_LABEL + " TEXT," +
                    SongSortColumns.NAME_BUCKET + " INTEGER);",

            "CREATE TABLE IF NOT EXISTS " + AlbumSortColumns.TABLE_NAME + "(" +
                    AlbumSortColumns.ID + " INTEGER PRIMARY KEY," +
                    AlbumSortColumns.ARTIST_ID + " INTEGER NOT NULL," +
                    AlbumSortColumns.NAME + " TEXT COLLATE LOCALIZED," +
                    AlbumSortColumns.NAME_LABEL + " TEXT," +
                    AlbumSortColumns.NAME_BUCKET + " INTEGER);",

            "CREATE TABLE IF NOT EXISTS " + ArtistSortColumns.TABLE_NAME + "(" +
                    ArtistSortColumns.ID + " INTEGER PRIMARY KEY," +
                    ArtistSortColumns.NAME + " TEXT COLLATE LOCALIZED," +
                    ArtistSortColumns.NAME_LABEL + " TEXT," +
                    ArtistSortColumns.NAME_BUCKET + " INTEGER);",
        };

        for (String table : tables) {
            if (DEBUG) {
                Log.d(TAG, "Creating table: " + table);
            }
            db.execSQL(table);
        }
    }

    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // this table was created in version 3 so call the onCreate method if oldVersion <= 2
        // in version 4 we need to recreate the SongSortcolumns table so drop the table and call
        // onCreate if oldVersion <= 3
        if (oldVersion <= 3) {
            db.execSQL("DROP TABLE IF EXISTS " + SongSortColumns.TABLE_NAME);
            onCreate(db);
        }
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // If we ever have downgrade, drop the table to be safe
        db.execSQL("DROP TABLE IF EXISTS " + SongSortColumns.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + AlbumSortColumns.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ArtistSortColumns.TABLE_NAME);
        onCreate(db);
    }

    public void onLocaleChanged() {
        mHandler.obtainMessage(LOCALE_CHANGED).sendToTarget();
    }

    private void rebuildLocaleData(LocaleSet locales) {
        if (DEBUG) {
            Log.d(TAG, "Locale has changed, rebuilding sorting data");
        }

        final long start = SystemClock.elapsedRealtime();
        final SQLiteDatabase db = mMusicDatabase.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + SongSortColumns.TABLE_NAME);
            db.execSQL("DELETE FROM " + AlbumSortColumns.TABLE_NAME);
            db.execSQL("DELETE FROM " + ArtistSortColumns.TABLE_NAME);

            // prep the localization classes
            mLocaleSetManager.updateLocaleSet(locales);

            updateLocalizedStore(db, null);

            // Update the ICU version used to generate the locale derived data
            // so we can tell when we need to rebuild with new ICU versions.
            PropertiesStore.getInstance(mContext).storeProperty(
                    PropertiesStore.DbProperties.ICU_VERSION, ICU.getIcuVersion());
            PropertiesStore.getInstance(mContext).storeProperty(PropertiesStore.DbProperties.LOCALE,
                    locales.toString());

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (DEBUG) {
            Log.i(TAG, "Locale change completed in " + (SystemClock.elapsedRealtime() - start) + "ms");
        }
    }

    /**
     * This will grab all the songs from the medistore and add the localized data to the db
     * @param selection if we only want to do this for some songs, this selection will filter it out
     */
    private void updateLocalizedStore(final SQLiteDatabase db, final String selection) {
        db.beginTransaction();
        try {
            Cursor cursor = null;

            try {
                final String combinedSelection = MusicUtils.MUSIC_ONLY_SELECTION +
                        (TextUtils.isEmpty(selection) ? "" : " AND " + selection);

                // order by artist/album/id to minimize artist/album re-inserts
                final String orderBy = AudioColumns.ARTIST_ID + "," + AudioColumns.ALBUM + ","
                        + AudioColumns._ID;

                if (DEBUG) {
                    Log.d(TAG, "Running selection query: " + combinedSelection);
                }

                cursor = mContext.getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{
                                // 0
                                AudioColumns._ID,
                                // 1
                                AudioColumns.TITLE,
                                // 2
                                AudioColumns.ARTIST_ID,
                                // 3
                                AudioColumns.ARTIST,
                                // 4
                                AudioColumns.ALBUM_ID,
                                // 5
                                AudioColumns.ALBUM,
                        }, combinedSelection, null, orderBy);

                long previousArtistId = -1;
                long previousAlbumId = -1;
                long artistId;
                long albumId;

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        albumId = cursor.getLong(4);
                        artistId = cursor.getLong(2);

                        if (artistId != previousArtistId) {
                            previousArtistId = artistId;
                            updateArtistData(db, artistId, cursor.getString(3));
                        }

                        if (albumId != previousAlbumId) {
                            previousAlbumId = albumId;

                            updateAlbumData(db, albumId, cursor.getString(5), artistId);
                        }

                        updateSongData(db, cursor.getLong(0), cursor.getString(1), artistId, albumId);
                    } while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateArtistData(SQLiteDatabase db, long id, String name) {
        mContentValues.clear();
        name = MusicUtils.getTrimmedName(name);

        final LocaleUtils localeUtils = LocaleUtils.getInstance();
        final int bucketIndex = localeUtils.getBucketIndex(name);

        mContentValues.put(ArtistSortColumns.ID, id);
        mContentValues.put(ArtistSortColumns.NAME, name);
        mContentValues.put(ArtistSortColumns.NAME_BUCKET, bucketIndex);
        mContentValues.put(ArtistSortColumns.NAME_LABEL,
                localeUtils.getBucketLabel(bucketIndex));

        db.insertWithOnConflict(ArtistSortColumns.TABLE_NAME, null, mContentValues,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void updateAlbumData(SQLiteDatabase db, long id, String name, long artistId) {
        mContentValues.clear();
        name = MusicUtils.getTrimmedName(name);

        final LocaleUtils localeUtils = LocaleUtils.getInstance();
        final int bucketIndex = localeUtils.getBucketIndex(name);

        mContentValues.put(AlbumSortColumns.ID, id);
        mContentValues.put(AlbumSortColumns.NAME, name);
        mContentValues.put(AlbumSortColumns.NAME_BUCKET, bucketIndex);
        mContentValues.put(AlbumSortColumns.NAME_LABEL,
                localeUtils.getBucketLabel(bucketIndex));
        mContentValues.put(AlbumSortColumns.ARTIST_ID, artistId);

        db.insertWithOnConflict(AlbumSortColumns.TABLE_NAME, null, mContentValues,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void updateSongData(SQLiteDatabase db, long id, String name, long artistId,
                                long albumId) {
        mContentValues.clear();
        name = MusicUtils.getTrimmedName(name);

        final LocaleUtils localeUtils = LocaleUtils.getInstance();
        final int bucketIndex = localeUtils.getBucketIndex(name);

        mContentValues.put(SongSortColumns.ID, id);
        mContentValues.put(SongSortColumns.NAME, name);
        mContentValues.put(SongSortColumns.NAME_BUCKET, bucketIndex);
        mContentValues.put(SongSortColumns.NAME_LABEL,
                localeUtils.getBucketLabel(bucketIndex));
        mContentValues.put(SongSortColumns.ARTIST_ID, artistId);
        mContentValues.put(SongSortColumns.ALBUM_ID, albumId);

        db.insertWithOnConflict(SongSortColumns.TABLE_NAME, null, mContentValues,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * Gets the list of saved ids and labels for the itemType in localized sorted order
     * @param itemType the type of item we're querying for (artists, albums, songs)
     * @param sortType the type we want to sort by (eg songs sorted by artists,
     *                 albums sorted by artists).  Note some combinations don't make sense and
     *                 will fallback to the basic sort, for example Artists sorted by songs
     *                 doesn't make sense
     * @param descending Whether we want to sort ascending or descending.  This will only apply to
     *                  the basic searches (ie when sortType == itemType),
     *                  otherwise ascending is always assumed
     * @return sorted list of ids and bucket labels for the itemType
     */
    public SortData getSortOrder(SortParameter itemType, SortParameter sortType,
                                boolean descending) {
        SortData sortData = new SortData();
        String tableName = "";
        String joinClause = "";
        String selectParams = "";
        String postfixOrder = "";
        String prefixOrder = "";

        switch (itemType) {
            case Song:
                selectParams = SongSortColumns.CONCRETE_ID + ",";
                postfixOrder = SongSortColumns.getOrderBy(descending);
                tableName = SongSortColumns.TABLE_NAME;

                if (sortType == SortParameter.Artist) {
                    selectParams += ArtistSortColumns.NAME_LABEL;
                    prefixOrder = ArtistSortColumns.getOrderBy(false) + ",";
                    joinClause = createJoin(ArtistSortColumns.TABLE_NAME,
                            SongSortColumns.ARTIST_ID, ArtistSortColumns.CONCRETE_ID);
                } else if (sortType == SortParameter.Album) {
                    selectParams += AlbumSortColumns.NAME_LABEL;
                    prefixOrder = AlbumSortColumns.getOrderBy(false) + ",";
                    joinClause = createJoin(AlbumSortColumns.TABLE_NAME,
                            SongSortColumns.ALBUM_ID, AlbumSortColumns.CONCRETE_ID);
                } else {
                    selectParams += SongSortColumns.NAME_LABEL;
                }
                break;
            case Artist:
                selectParams = ArtistSortColumns.CONCRETE_ID + "," + ArtistSortColumns.NAME_LABEL;
                postfixOrder = ArtistSortColumns.getOrderBy(descending);
                tableName = ArtistSortColumns.TABLE_NAME;
                break;
            case Album:
                selectParams = AlbumSortColumns.CONCRETE_ID + ",";
                postfixOrder = AlbumSortColumns.getOrderBy(descending);
                tableName = AlbumSortColumns.TABLE_NAME;
                if (sortType == SortParameter.Artist) {
                    selectParams += AlbumSortColumns.NAME_LABEL;
                    prefixOrder = ArtistSortColumns.getOrderBy(false) + ",";
                    joinClause = createJoin(ArtistSortColumns.TABLE_NAME,
                            AlbumSortColumns.ARTIST_ID, ArtistSortColumns.CONCRETE_ID);
                } else {
                    selectParams += AlbumSortColumns.NAME_LABEL;
                }
                break;
        }

        final String selection = "SELECT " + selectParams
                + " FROM " + tableName
                + joinClause
                + " ORDER BY " + prefixOrder + postfixOrder;

        if (DEBUG) {
            Log.d(TAG, "Running selection: " + selection);
        }

        Cursor c = null;
        try {
            c = mMusicDatabase.getReadableDatabase().rawQuery(selection, null);

            if (c != null && c.moveToFirst()) {
                sortData.ids = new long[c.getCount()];
                sortData.bucketLabels = new ArrayList<String>(c.getCount());
                do {
                    sortData.ids[c.getPosition()] = c.getLong(0);
                    sortData.bucketLabels.add(c.getString(1));
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return sortData;
    }

    /**
     * Wraps the cursor with a sorted cursor that sorts it in the proper localized order
     * @param cursor underlying cursor to sort
     * @param columnName the column name of the id
     * @param idType the type of item that the cursor contains
     * @param sortType the type to sort by (for example can be song sorted by albums)
     * @param descending descending?
     * @param update do we want to update any discrepencies we find - only should be true if the
     *               cursor contains all songs/artists/albums and not a subset
     * @return the sorted cursor
     */
    public Cursor getLocalizedSort(Cursor cursor, String columnName, SortParameter idType,
                                   SortParameter sortType, boolean descending, boolean update) {
        if (cursor != null) {
            SortedCursor sortedCursor = null;

            // iterate up to twice if there are discrepancies found
            for (int i = 0; i < 2; i++) {
                // get the sort order for the sort parameter
                SortData sortData = getSortOrder(idType, sortType, descending);

                // get the sorted cursor based on the sort
                sortedCursor = new SortedCursor(cursor, sortData.ids, columnName,
                        sortData.bucketLabels);

                if (!update || !updateDiscrepancies(sortedCursor, idType)) {
                    break;
                }
            }

            return sortedCursor;
        }

        return cursor;
    }

    /**
     * Updates the localized store based on the cursor
     * @param sortedCursor the current sorting cursor based on the LocalizedStore sort
     * @param type the item type in the cursor
     * @return true if there are new ids in the cursor that aren't tracked in the store
     */
    private boolean updateDiscrepancies(SortedCursor sortedCursor, SortParameter type) {
        boolean hasNewIds = false;

        final ArrayList<Long> missingIds = sortedCursor.getMissingIds();
        if (missingIds.size() > 0) {
            removeIds(missingIds, type);
        }

        final Collection<Long> extraIds = sortedCursor.getExtraIds();
        if (extraIds != null && extraIds.size() > 0) {
            addIds(extraIds, type);
            hasNewIds = true;
        }

        return hasNewIds;
    }

    private void removeIds(ArrayList<Long> ids, SortParameter idType) {
        if (ids == null || ids.size() == 0) {
            return;
        }

        final String inParams = "(" + MusicUtils.buildCollectionAsString(ids) + ")";

        if (DEBUG) {
            Log.d(TAG, "Deleting from " + idType + " where id is in " + inParams);
        }

        switch (idType) {
            case Song:
                mMusicDatabase.getWritableDatabase().delete(SongSortColumns.TABLE_NAME,
                        SongSortColumns.ID + " IN " + inParams, null);
                break;
            case Album:
                mMusicDatabase.getWritableDatabase().delete(AlbumSortColumns.TABLE_NAME,
                        AlbumSortColumns.ID + " IN " + inParams, null);
                break;
            case Artist:
                mMusicDatabase.getWritableDatabase().delete(ArtistSortColumns.TABLE_NAME,
                        ArtistSortColumns.ID + " IN " + inParams, null);
                break;
        }
    }

    private void addIds(Collection<Long> ids, SortParameter idType) {
        StringBuilder builder = new StringBuilder();
        switch (idType) {
            case Song:
                builder.append(AudioColumns._ID);
                break;
            case Album:
                builder.append(AudioColumns.ALBUM_ID);
                break;
            case Artist:
                builder.append(AudioColumns.ARTIST_ID);
                break;
        }

        builder.append(" IN (");
        builder.append(MusicUtils.buildCollectionAsString(ids));
        builder.append(")");

        updateLocalizedStore(mMusicDatabase.getWritableDatabase(), builder.toString());
    }

    private static String createJoin(String tableName, String firstParam, String secondParam) {
        return " JOIN " + tableName + " ON (" + firstParam + "=" + secondParam + ")";
    }

    private static String createOrderBy(String first, String second, boolean descending) {
        String desc = descending ? " DESC" : "";
        return first + desc + "," + second + desc;
    }

    private static final class SongSortColumns {
        /* Table name */
        public static final String TABLE_NAME = "song_sort";

        /* Song IDs column */
        public static final String ID = "id";

        /* Artist IDs column */
        public static final String ARTIST_ID = "artist_id";

        /* Album IDs column */
        public static final String ALBUM_ID = "album_id";

        /* The Song name */
        public static final String NAME = "song_name";

        /* The label assigned (categorization buckets - typically the first letter) */
        public static final String NAME_LABEL = "song_name_label";

        /* The numerical index of the bucket */
        public static final String NAME_BUCKET = "song_name_bucket";

        /* Used for joins */
        public static final String CONCRETE_ID = TABLE_NAME + "." + ID;

        public static String getOrderBy(boolean descending) {
            return createOrderBy(NAME_BUCKET, NAME, descending);
        }
    }

    private static final class AlbumSortColumns {

        /* Table name */
        public static final String TABLE_NAME = "album_sort";

        /* Album IDs column */
        public static final String ID = "id";

        /* Artist IDs column */
        public static final String ARTIST_ID = "artist_id";

        /* The Album name */
        public static final String NAME = "album_name";

        /* The label assigned (categorization buckets - typically the first letter) */
        public static final String NAME_LABEL = "album_name_label";

        /* The numerical index of the bucket */
        public static final String NAME_BUCKET = "album_name_bucket";

        /* Used for joins */
        public static final String CONCRETE_ID = TABLE_NAME + "." + ID;

        public static String getOrderBy(boolean descending) {
            return createOrderBy(NAME_BUCKET, NAME, descending);
        }
    }


    private static final class ArtistSortColumns {

        /* Table name */
        public static final String TABLE_NAME = "artist_sort";

        /* Artist IDs column */
        public static final String ID = "id";

        /* The Artist name */
        public static final String NAME = "artist_name";

        /* The label assigned (categorization buckets - typically the first letter) */
        public static final String NAME_LABEL = "artist_name_label";

        /* The numerical index of the bucket */
        public static final String NAME_BUCKET = "artist_name_bucket";

        /* Used for joins */
        public static final String CONCRETE_ID = TABLE_NAME + "." + ID;

        public static String getOrderBy(boolean descending) {
            return createOrderBy(NAME_BUCKET, NAME, descending);
        }
    }

}
