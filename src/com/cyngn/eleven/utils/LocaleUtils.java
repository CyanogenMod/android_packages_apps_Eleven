/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.cyngn.eleven.utils;

import android.provider.ContactsContract.FullNameStyle;
import android.provider.ContactsContract.PhoneticNameStyle;
import android.text.TextUtils;
import android.util.Log;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import libcore.icu.AlphabeticIndex;
import libcore.icu.AlphabeticIndex.ImmutableIndex;
import libcore.icu.Transliterator;

/**
 * This utility class provides specialized handling for locale specific
 * information: labels, name lookup keys.
 *
 * This class has been modified from ContactLocaleUtils.java for now to rip out
 * Chinese/Japanese specific Alphabetic Indexers because the MediaProvider's sort
 * is using a Collator sort which can result in confusing behavior, so for now we will
 * simplify and batch up those results until we later support our own internal databases
 * An example of what This is, if we have songs "Able", "Xylophone" and "上" in
 * simplified chinese language The media provider would give it to us in that order sorted,
 * but the ICU lib would return "A", "X", "S".  Unless we write our own db or do our own sort
 * there is no good easy solution
 */
public class LocaleUtils {
    public static final String TAG = "MusicLocale";

    public static final Locale LOCALE_ARABIC = new Locale("ar");
    public static final Locale LOCALE_GREEK = new Locale("el");
    public static final Locale LOCALE_HEBREW = new Locale("he");
    // Ukrainian labels are superset of Russian
    public static final Locale LOCALE_UKRAINIAN = new Locale("uk");
    public static final Locale LOCALE_THAI = new Locale("th");

    /**
     * This class is the default implementation and should be the base class
     * for other locales.
     *
     * sortKey: same as name
     * nameLookupKeys: none
     * labels: uses ICU AlphabeticIndex for labels and extends by labeling
     *     phone numbers "#".  Eg English labels are: [A-Z], #, " "
     */
    private static class LocaleUtilsBase {
        private static final String EMPTY_STRING = "";
        private static final String NUMBER_STRING = "#";

        protected final ImmutableIndex mAlphabeticIndex;
        private final int mAlphabeticIndexBucketCount;
        private final int mNumberBucketIndex;

        public LocaleUtilsBase(Locale locale) {
            // AlphabeticIndex.getBucketLabel() uses a binary search across
            // the entire label set so care should be taken about growing this
            // set too large. The following set determines for which locales
            // we will show labels other than your primary locale. General rules
            // of thumb for adding a locale: should be a supported locale; and
            // should not be included if from a name it is not deterministic
            // which way to label it (so eg Chinese cannot be added because
            // the labeling of a Chinese character varies between Simplified,
            // Traditional, and Japanese locales). Use English only for all
            // Latin based alphabets. Ukrainian is chosen for Cyrillic because
            // its alphabet is a superset of Russian.
            mAlphabeticIndex = new AlphabeticIndex(locale)
                    .setMaxLabelCount(300)
                    .addLabels(Locale.ENGLISH)
                    .addLabels(Locale.JAPANESE)
                    .addLabels(Locale.KOREAN)
                    .addLabels(LOCALE_THAI)
                    .addLabels(LOCALE_ARABIC)
                    .addLabels(LOCALE_HEBREW)
                    .addLabels(LOCALE_GREEK)
                    .addLabels(LOCALE_UKRAINIAN)
                    .getImmutableIndex();
            mAlphabeticIndexBucketCount = mAlphabeticIndex.getBucketCount();
            mNumberBucketIndex = mAlphabeticIndexBucketCount - 1;
        }

        public String getSortKey(String name) {
            return name;
        }

        /**
         * Returns the bucket index for the specified string. AlphabeticIndex
         * sorts strings into buckets numbered in order from 0 to N, where the
         * exact value of N depends on how many representative index labels are
         * used in a particular locale. This routine adds one additional bucket
         * for phone numbers. It attempts to detect phone numbers and shifts
         * the bucket indexes returned by AlphabeticIndex in order to make room
         * for the new # bucket, so the returned range becomes 0 to N+1.
         */
        public int getBucketIndex(String name) {
            boolean prefixIsNumeric = false;
            final int length = name.length();
            int offset = 0;
            while (offset < length) {
                int codePoint = Character.codePointAt(name, offset);
                // Ignore standard phone number separators and identify any
                // string that otherwise starts with a number.
                if (Character.isDigit(codePoint)) {
                    prefixIsNumeric = true;
                    break;
                } else if (!Character.isSpaceChar(codePoint) &&
                        codePoint != '+' && codePoint != '(' &&
                        codePoint != ')' && codePoint != '.' &&
                        codePoint != '-' && codePoint != '#') {
                    break;
                }
                offset += Character.charCount(codePoint);
            }
            if (prefixIsNumeric) {
                return mNumberBucketIndex;
            }

            final int bucket = mAlphabeticIndex.getBucketIndex(name);
            if (bucket < 0) {
                return -1;
            }
            if (bucket >= mNumberBucketIndex) {
                return bucket + 1;
            }
            return bucket;
        }

        /**
         * Returns the number of buckets in use (one more than AlphabeticIndex
         * uses, because this class adds a bucket for phone numbers).
         */
        public int getBucketCount() {
            return mAlphabeticIndexBucketCount + 1;
        }

        /**
         * Returns the label for the specified bucket index if a valid index,
         * otherwise returns an empty string. '#' is returned for the phone
         * number bucket; for all others, the AlphabeticIndex label is returned.
         */
        public String getBucketLabel(int bucketIndex) {
            if (bucketIndex < 0 || bucketIndex >= getBucketCount()) {
                return EMPTY_STRING;
            } else if (bucketIndex == mNumberBucketIndex) {
                return NUMBER_STRING;
            } else if (bucketIndex > mNumberBucketIndex) {
                --bucketIndex;
            }
            return mAlphabeticIndex.getBucketLabel(bucketIndex);
        }

        @SuppressWarnings("unused")
        public Iterator<String> getNameLookupKeys(String name, int nameStyle) {
            return null;
        }

        public ArrayList<String> getLabels() {
            final int bucketCount = getBucketCount();
            final ArrayList<String> labels = new ArrayList<String>(bucketCount);
            for(int i = 0; i < bucketCount; ++i) {
                labels.add(getBucketLabel(i));
            }
            return labels;
        }
    }

    private static LocaleUtils sSingleton;

    private final Locale mLocale;
    private final LocaleUtilsBase mUtils;

    private LocaleUtils(Locale locale) {
        if (locale == null) {
            mLocale = Locale.getDefault();
        } else {
            mLocale = locale;
        }
        mUtils = new LocaleUtilsBase(mLocale);

        Log.i(TAG, "AddressBook Labels [" + mLocale.toString() + "]: "
                + getLabels().toString());
    }

    public boolean isLocale(Locale locale) {
        return mLocale.equals(locale);
    }

    public static synchronized LocaleUtils getInstance() {
        if (sSingleton == null) {
            sSingleton = new LocaleUtils(null);
        }
        return sSingleton;
    }

    public static synchronized void setLocale(Locale locale) {
        if (sSingleton == null || !sSingleton.isLocale(locale)) {
            sSingleton = new LocaleUtils(locale);
        }
    }

    public String getSortKey(String name, int nameStyle) {
        return mUtils.getSortKey(name);
    }

    public int getBucketIndex(String name) {
        return mUtils.getBucketIndex(name);
    }

    public int getBucketCount() {
        return mUtils.getBucketCount();
    }

    public String getBucketLabel(int bucketIndex) {
        return mUtils.getBucketLabel(bucketIndex);
    }

    public String getLabel(String name) {
        return getBucketLabel(getBucketIndex(name));
    }

    public ArrayList<String> getLabels() {
        return mUtils.getLabels();
    }
}
