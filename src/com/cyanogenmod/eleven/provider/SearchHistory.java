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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

public class SearchHistory {
    /* Maximum # of items in the db */
    private static final int MAX_ITEMS_IN_DB = 25;

    private static SearchHistory sInstance = null;

    private MusicDB mMusicDatabase = null;

    public SearchHistory(final Context context) {
        mMusicDatabase = MusicDB.getInstance(context);
    }

    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + SearchHistoryColumns.NAME + " ("
                + SearchHistoryColumns.SEARCHSTRING + " STRING NOT NULL,"
                + SearchHistoryColumns.TIMESEARCHED + " LONG NOT NULL);");
    }

    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // No upgrade path needed yet
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // If we ever have downgrade, drop the table to be safe
        db.execSQL("DROP TABLE IF EXISTS " + SearchHistoryColumns.NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link android.content.Context} to use
     * @return A new instance of this class.
     */
    public static final synchronized SearchHistory getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new SearchHistory(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to save a search request into the db.  This function will remove any old
     * searches for that string before inserting it into the db
     * @param searchString The string that has been searched
     */
    public void addSearchString(final String searchString) {
        if (searchString == null) {
            return;
        }

        String trimmedString = searchString.trim();

        if (trimmedString.isEmpty()) {
            return;
        }

        final SQLiteDatabase database = mMusicDatabase.getWritableDatabase();
        database.beginTransaction();

        try {
            // delete existing searches with the same search string
            database.delete(SearchHistoryColumns.NAME,
                    SearchHistoryColumns.SEARCHSTRING + " = ? COLLATE NOCASE",
                    new String[] { trimmedString });

            // add the entry
            final ContentValues values = new ContentValues(2);
            values.put(SearchHistoryColumns.SEARCHSTRING, trimmedString);
            values.put(SearchHistoryColumns.TIMESEARCHED, System.currentTimeMillis());
            database.insert(SearchHistoryColumns.NAME, null, values);

            // if our db is too large, delete the extra items
            Cursor oldest = null;
            try {
                database.query(SearchHistoryColumns.NAME,
                        new String[]{SearchHistoryColumns.TIMESEARCHED}, null, null, null, null,
                        SearchHistoryColumns.TIMESEARCHED + " ASC");

                if (oldest != null && oldest.getCount() > MAX_ITEMS_IN_DB) {
                    oldest.moveToPosition(oldest.getCount() - MAX_ITEMS_IN_DB);
                    long timeOfRecordToKeep = oldest.getLong(0);

                    database.delete(SearchHistoryColumns.NAME,
                            SearchHistoryColumns.TIMESEARCHED + " < ?",
                            new String[] { String.valueOf(timeOfRecordToKeep) });

                }
            } finally {
                if (oldest != null) {
                    oldest.close();
                    oldest = null;
                }
            }
        } finally {
            database.setTransactionSuccessful();
            database.endTransaction();
        }
    }

    /**
     * Gets a cursor to the list of recently searched strings
     * @param limit number of items to limit to
     * @return cursor
     */
    public Cursor queryRecentSearches(final String limit) {
        final SQLiteDatabase database = mMusicDatabase.getReadableDatabase();
        return database.query(SearchHistoryColumns.NAME,
                new String[]{SearchHistoryColumns.SEARCHSTRING}, null, null, null, null,
                SearchHistoryColumns.TIMESEARCHED + " DESC", limit);
    }

    /**
     * Gets the recently searched strings
     * @return list of esarched strings
     */
    public ArrayList<String> getRecentSearches() {
        Cursor searches = queryRecentSearches(String.valueOf(MAX_ITEMS_IN_DB));

        ArrayList<String> results = new ArrayList<String>(MAX_ITEMS_IN_DB);

        try {
            if (searches != null && searches.moveToFirst()) {
                int colIdx = searches.getColumnIndex(SearchHistoryColumns.SEARCHSTRING);

                do {
                    results.add(searches.getString(colIdx));
                } while (searches.moveToNext());
            }
        } finally {
            if (searches != null) {
                searches.close();
                searches = null;
            }
        }

        return results;
    }

    public interface SearchHistoryColumns {
        /* Table name */
        public static final String NAME = "searchhistory";

        /* What was searched */
        public static final String SEARCHSTRING = "searchstring";

        /* Time of search */
        public static final String TIMESEARCHED = "timesearched";
    }
}
