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
import android.util.LruCache;

public class BitmapWithColors {
    private static final class BitmapColors {
        public int mVibrantColor;
        public int mVibrantDarkColor;

        public BitmapColors(int vibrantColor, int vibrantDarkColor) {
            mVibrantColor = vibrantColor;
            mVibrantDarkColor = vibrantDarkColor;
        }
    }

    private static final int CACHE_SIZE_MAX = 20;
    private static final LruCache<Integer, BitmapColors> sCachedColors =
            new LruCache<Integer, BitmapColors>(CACHE_SIZE_MAX);

    private Bitmap mBitmap;
    private int mBitmapKey;
    private BitmapColors mColors;

    public BitmapWithColors(Bitmap bitmap, int bitmapKey) {
        mBitmap = bitmap;
        mBitmapKey = bitmapKey;
    }

    public BitmapWithColors(Bitmap bitmap, int bitmapKey, int vibrantColor, int vibrantDarkColor) {
        mBitmap = bitmap;
        mBitmapKey = bitmapKey;
        mColors = new BitmapColors(vibrantColor, vibrantDarkColor);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getVibrantColor() {
        loadColorsIfNeeded();
        if (mColors.mVibrantColor == Color.TRANSPARENT) {
            return mColors.mVibrantDarkColor;
        }
        return mColors.mVibrantColor;
    }

    public int getVibrantDarkColor() {
        loadColorsIfNeeded();
        if (mColors.mVibrantDarkColor == Color.TRANSPARENT) {
            return mColors.mVibrantColor;
        }
        return mColors.mVibrantDarkColor;
    }

    private synchronized void loadColorsIfNeeded() {
        if (mColors != null) {
            return;
        }

        synchronized (sCachedColors) {
            mColors = sCachedColors.get(mBitmapKey);
        }
        if (mColors != null) {
            return;
        }

        final Palette p = Palette.generate(mBitmap);
        if (p == null) {
            return;
        }

        int vibrantColor = Color.TRANSPARENT;
        int vibrantDarkColor = Color.TRANSPARENT;

        Palette.Swatch swatch = p.getDarkVibrantSwatch();
        if (swatch != null) {
            vibrantDarkColor = swatch.getRgb();
        }
        swatch = p.getVibrantSwatch();
        if (swatch != null) {
            vibrantColor = swatch.getRgb();
        }

        mColors = new BitmapColors(vibrantColor, vibrantDarkColor);
        synchronized (sCachedColors) {
            sCachedColors.put(mBitmapKey, mColors);
        }
    }
}
