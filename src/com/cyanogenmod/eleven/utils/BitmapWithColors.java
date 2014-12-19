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
    private boolean mLoadedVibrantColor = false;
    private Bitmap mBitmap;
    private int mVibrantColor;

    public BitmapWithColors(Bitmap bitmap) {
        mBitmap = bitmap;
        mVibrantColor = Color.TRANSPARENT;
        mLoadedVibrantColor = false;
    }

    public BitmapWithColors(Bitmap bitmap, int vibrantColor) {
        mBitmap = bitmap;
        mVibrantColor = vibrantColor;
        mLoadedVibrantColor = true;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getVibrantColor() {
        if (!mLoadedVibrantColor) {
            mLoadedVibrantColor = true;
            mVibrantColor = Color.TRANSPARENT;

            final Palette p = Palette.generate(mBitmap);
            if (p != null) {
                // Check for dark vibrant colors, then vibrant
                Palette.Swatch swatch = p.getDarkVibrantSwatch();

                if (swatch == null) {
                    swatch = p.getVibrantSwatch();
                }

                if (swatch != null) {
                    mVibrantColor = swatch.getRgb();
                }
            }
        }

        return mVibrantColor;
    }
}
