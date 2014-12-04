/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2009 The Android Open Source Project
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

public class PropertiesStore {
    private final MusicDB mMusicDatabase;
    private static PropertiesStore sInstance = null;

    public static final synchronized PropertiesStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new PropertiesStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private PropertiesStore(final Context context) {
        mMusicDatabase = MusicDB.getInstance(context);
    }

    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PropertiesColumns.TABLE_NAME + "(" +
                PropertiesColumns.PROPERTY_KEY + " STRING PRIMARY KEY," +
                PropertiesColumns.PROPERTY_VALUE + " TEXT);");
    }

    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // this table was created in version 3 so call the onCreate method if we hit that scenario
        if (oldVersion < 3 && newVersion >= 3) {
            onCreate(db);
        }
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // If we ever have downgrade, drop the table to be safe
        db.execSQL("DROP TABLE IF EXISTS " + PropertiesColumns.TABLE_NAME);
        onCreate(db);
    }

    public String getProperty(String key) {
        return getProperty(key, null);
    }

    public String getProperty(String key, String defaultValue) {
        Cursor cursor = mMusicDatabase.getReadableDatabase().query(PropertiesColumns.TABLE_NAME,
                new String[] { PropertiesColumns.PROPERTY_VALUE },
                PropertiesColumns.PROPERTY_KEY + "=?",
                new String[] { key }, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        return defaultValue;
    }

    public void storeProperty(String key, String value) {
        ContentValues values = new ContentValues(2);
        values.put(PropertiesColumns.PROPERTY_KEY, key);
        values.put(PropertiesColumns.PROPERTY_VALUE, value);
        mMusicDatabase.getWritableDatabase().replace(PropertiesColumns.TABLE_NAME,
                null, values);
    }

    public interface DbProperties {
        String ICU_VERSION = "icu_version";
        String LOCALE = "locale";
    }

    private static final class PropertiesColumns {
        public static final String TABLE_NAME = "properties";
        public static final String PROPERTY_KEY = "property_key";
        public static final String PROPERTY_VALUE = "property_value";
    }
}
