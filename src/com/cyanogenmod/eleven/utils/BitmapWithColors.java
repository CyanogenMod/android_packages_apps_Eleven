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
import android.os.Looper;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.Target;
import android.util.LruCache;

public class BitmapWithColors {
    private static final class BitmapColors {
        public final int mVibrantColor;
        public final int mVibrantDarkColor;
        public final int mVibrantLightColor;
        public final int mDominantColor;

        public BitmapColors(Palette palette) {
            mVibrantColor = determineColor(palette.getVibrantSwatch());
            mVibrantDarkColor = determineColor(palette.getDarkVibrantSwatch());
            mVibrantLightColor = determineColor(palette.getLightVibrantSwatch());
            mDominantColor = determineColor(getDominantSwatch(palette));
        }

        public BitmapColors(int vibrantColor, int vibrantDarkColor) {
            mVibrantColor = vibrantColor;
            mVibrantDarkColor = vibrantDarkColor;
            mVibrantLightColor = Color.TRANSPARENT;
            mDominantColor = vibrantColor;
        }

        private int determineColor(Palette.Swatch swatch) {
            return swatch != null ? swatch.getRgb() : Color.TRANSPARENT;
        }

        private static Palette.Swatch getDominantSwatch(Palette palette) {
            Palette.Swatch dominant = null;
            for (Palette.Swatch swatch : palette.getSwatches()) {
                if (dominant == null || swatch.getPopulation() > dominant.getPopulation()) {
                    dominant = swatch;
                }
            }
            return dominant;
        }

        @Override
        public String toString() {
            return "BitmapColors[vibrant=" + Integer.toHexString(mVibrantColor)
                    + ", vibrantDark=" + Integer.toHexString(mVibrantDarkColor)
                    + ", vibrantLight=" + Integer.toHexString(mVibrantLightColor)
                    + ", dominant=" + Integer.toHexString(mDominantColor) + "]";
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

        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            // we're already running in background, so do the
            // (costly) palette initialization immediately
            loadColorsIfNeeded();
        }
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

    public int getContrastingColor() {
        loadColorsIfNeeded();

        float contrastToDark = computeContrastBetweenColors(mColors.mDominantColor,
                mColors.mVibrantDarkColor);
        float contrastToLight = computeContrastBetweenColors(mColors.mDominantColor,
                mColors.mVibrantLightColor);
        float contrastToVibrant = computeContrastBetweenColors(mColors.mDominantColor,
                mColors.mVibrantColor);

        int bestColor = mColors.mDominantColor;
        float bestContrast = -1;
        if (contrastToVibrant > bestContrast) {
            bestColor = mColors.mVibrantColor;
            bestContrast = contrastToVibrant;
        }
        if (contrastToDark > bestContrast) {
            bestColor = mColors.mVibrantDarkColor;
            bestContrast = contrastToDark;
        }
        if (contrastToLight > bestContrast) {
            bestColor = mColors.mVibrantLightColor;
            bestContrast = contrastToLight;
        }

        return bestColor;
    }

    /** Calculates the constrast between two colors, using the algorithm provided by the WCAG v2. */
    private static float computeContrastBetweenColors(int bg, int fg) {
        if (bg == Color.TRANSPARENT || fg == Color.TRANSPARENT || bg == fg) {
            return -1;
        }

        float bgR = Color.red(bg) / 255f;
        float bgG = Color.green(bg) / 255f;
        float bgB = Color.blue(bg) / 255f;
        bgR = (bgR < 0.03928f) ? bgR / 12.92f : (float) Math.pow((bgR + 0.055f) / 1.055f, 2.4f);
        bgG = (bgG < 0.03928f) ? bgG / 12.92f : (float) Math.pow((bgG + 0.055f) / 1.055f, 2.4f);
        bgB = (bgB < 0.03928f) ? bgB / 12.92f : (float) Math.pow((bgB + 0.055f) / 1.055f, 2.4f);
        float bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB;

        float fgR = Color.red(fg) / 255f;
        float fgG = Color.green(fg) / 255f;
        float fgB = Color.blue(fg) / 255f;
        fgR = (fgR < 0.03928f) ? fgR / 12.92f : (float) Math.pow((fgR + 0.055f) / 1.055f, 2.4f);
        fgG = (fgG < 0.03928f) ? fgG / 12.92f : (float) Math.pow((fgG + 0.055f) / 1.055f, 2.4f);
        fgB = (fgB < 0.03928f) ? fgB / 12.92f : (float) Math.pow((fgB + 0.055f) / 1.055f, 2.4f);
        float fgL = 0.2126f * fgR + 0.7152f * fgG + 0.0722f * fgB;

        return Math.abs((fgL + 0.05f) / (bgL + 0.05f));
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

        final Palette p = Palette.from(mBitmap).generate();
        if (p == null) {
            return;
        }

        mColors = new BitmapColors(p);
        synchronized (sCachedColors) {
            sCachedColors.put(mBitmapKey, mColors);
        }
    }

    @Override
    public String toString() {
        return "BitmapWithColors[key=" + mBitmapKey + ", colors=" + mColors + "]";
    }
}
