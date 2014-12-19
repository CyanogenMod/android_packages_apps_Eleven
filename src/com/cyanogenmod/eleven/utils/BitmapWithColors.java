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

public class BitmapWithColors {
    private Bitmap mBitmap;
    private int mVibrantColor;
    private int mVibrantDarkColor;
    private boolean mColorsLoaded = false;

    public BitmapWithColors(Bitmap bitmap) {
        mBitmap = bitmap;
        mVibrantColor = Color.TRANSPARENT;
        mVibrantDarkColor = Color.TRANSPARENT;
        mColorsLoaded = false;
    }

    public BitmapWithColors(Bitmap bitmap, int vibrantColor, int vibrantDarkColor) {
        mBitmap = bitmap;
        mVibrantColor = vibrantColor;
        mVibrantDarkColor = vibrantDarkColor;
        mColorsLoaded = true;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getVibrantColor() {
        loadColorsIfNeeded();
        return mVibrantColor;
    }

    public int getVibrantDarkColor() {
        loadColorsIfNeeded();
        return mVibrantDarkColor;
    }

    private void loadColorsIfNeeded() {
        synchronized (this) {
            if (mColorsLoaded) {
                return;
            }
        }

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

        synchronized (this) {
            mColorsLoaded = true;
            mVibrantColor = vibrantColor;
            mVibrantDarkColor = vibrantDarkColor;
        }
    }
}
