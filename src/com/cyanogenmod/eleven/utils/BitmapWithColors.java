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
package com.cyanogenmod.eleven.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.graphics.Palette;

import java.util.ArrayList;

public class BitmapWithColors {
    private static final class BitmapColors {
        public int mHashCode;
        public int mVibrantColor;
        public int mVibrantDarkColor;

        public BitmapColors(int hashCode, int vibrantColor, int vibrantDarkColor) {
            mHashCode = hashCode;
            mVibrantColor = vibrantColor;
            mVibrantDarkColor = vibrantDarkColor;
        }
    }
    private static final int CACHE_SIZE_MAX = 10;
    private static final int CACHE_SIZE_MIN = 5;
    private static final ArrayList<BitmapColors> sCachedColors =
            new ArrayList<BitmapColors>(CACHE_SIZE_MAX);

    private Bitmap mBitmap;
    private int mBitmapKey;
    private BitmapColors mColors;
    private boolean mColorsLoaded = false;

    public BitmapWithColors(Bitmap bitmap, int bitmapKey) {
        mBitmap = bitmap;
        mBitmapKey = bitmapKey;
        mColorsLoaded = false;
    }

    public BitmapWithColors(Bitmap bitmap, int bitmapKey, int vibrantColor, int vibrantDarkColor) {
        mBitmap = bitmap;
        mBitmapKey = bitmapKey;
        mColors = new BitmapColors(bitmapKey, vibrantColor, vibrantDarkColor);
        mColorsLoaded = true;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getVibrantColor() {
        loadColorsIfNeeded();
        return mColors.mVibrantColor;
    }

    public int getVibrantDarkColor() {
        loadColorsIfNeeded();
        return mColors.mVibrantDarkColor;
    }

    private synchronized void loadColorsIfNeeded() {
        if (mColorsLoaded) {
            return;
        }

        BitmapColors colors = getColors(mBitmapKey);
        if (colors == null) {
            final Palette p = Palette.generate(mBitmap);
            int vibrantColor = Color.TRANSPARENT;
            int vibrantDarkColor = Color.TRANSPARENT;

            if (p != null) {
                Palette.Swatch swatch = p.getDarkVibrantSwatch();
                if (swatch != null) {
                    vibrantDarkColor = swatch.getRgb();
                }
                swatch = p.getVibrantSwatch();
                if (swatch != null) {
                    vibrantColor = swatch.getRgb();
                }
            }

            if (vibrantColor == Color.TRANSPARENT && vibrantDarkColor != Color.TRANSPARENT) {
                vibrantColor = vibrantDarkColor;
            }
            if (vibrantColor != Color.TRANSPARENT && vibrantDarkColor == Color.TRANSPARENT) {
                vibrantDarkColor = vibrantColor;
            }

            colors = new BitmapColors(mBitmapKey, vibrantColor, vibrantDarkColor);
            putColor(colors);
        }

        mColorsLoaded = true;
        mColors = colors;
    }


    private static BitmapColors getColors(int hashCode) {
        synchronized (sCachedColors) {
            for (BitmapColors cacheItem : sCachedColors) {
                if (cacheItem.mHashCode == hashCode) {
                    return cacheItem;
                }
            }

            return null;
        }
    }

    private static void putColor(BitmapColors item) {
        synchronized (sCachedColors) {
            for (BitmapColors cacheItem : sCachedColors) {
                if (cacheItem.mHashCode == item.mHashCode) {
                    return;
                }
            }

            sCachedColors.add(item);
            if (sCachedColors.size() >= CACHE_SIZE_MAX) {
                sCachedColors.subList(0, sCachedColors.size() - CACHE_SIZE_MIN).clear();
            }
        }
    }
}
