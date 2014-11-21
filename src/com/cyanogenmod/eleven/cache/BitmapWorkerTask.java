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
package com.cyanogenmod.eleven.cache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.cyanogenmod.eleven.cache.ImageWorker.ImageType;

import java.lang.ref.WeakReference;

/**
 * The actual {@link android.os.AsyncTask} that will process the image.
 */
public abstract class BitmapWorkerTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    /**
     * The {@link android.widget.ImageView} used to set the result
     */
    protected final WeakReference<ImageView> mImageReference;

    /**
     * Type of URL to download
     */
    protected final ImageWorker.ImageType mImageType;

    /**
     * Layer drawable used to cross fade the result from the worker
     */
    protected Drawable mFromDrawable;

    protected final Context mContext;

    protected final ImageCache mImageCache;

    protected final Resources mResources;

    protected boolean mScaleImgToView;

    /**
     * The key used to store cached entries
     */
    public String mKey;

    /**
     * Constructor of <code>BitmapWorkerTask</code>
     * @param key used for caching the image
     * @param imageView The {@link ImageView} to use.
     * @param imageType The type of image URL to fetch for.
     * @param fromDrawable what drawable to transition from
     */
    public BitmapWorkerTask(final String key, final ImageView imageView, final ImageType imageType,
                            final Drawable fromDrawable, final Context context) {
        this(key, imageView, imageType, fromDrawable, context, false);
    }

    /**
     * Constructor of <code>BitmapWorkerTask</code>
     * @param key used for caching the image
     * @param imageView The {@link ImageView} to use.
     * @param imageType The type of image URL to fetch for.
     * @param fromDrawable what drawable to transition from
     * @param scaleImgToView flag to scale the bitmap to the image view bounds
     */
    public BitmapWorkerTask(final String key, final ImageView imageView, final ImageType imageType,
                            final Drawable fromDrawable, final Context context, final boolean scaleImgToView) {
        mKey = key;

        mContext = context;
        mImageCache = ImageCache.getInstance(mContext);
        mResources = mContext.getResources();

        mImageReference = new WeakReference<ImageView>(imageView);
        mImageType = imageType;

        // A transparent image (layer 0) and the new result (layer 1)
        mFromDrawable = fromDrawable;

        mScaleImgToView = scaleImgToView;
    }

    /**
     * @return The {@link ImageView} associated with this task as long as
     * the ImageView's task still points to this task as well.
     * Returns null otherwise.
     */
    protected ImageView getAttachedImageView() {
        final ImageView imageView = mImageReference.get();
        if (imageView != null) {
            final BitmapWorkerTask bitmapWorkerTask = ImageWorker.getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask) {
                return imageView;
            }
        }

        return null;
    }

    /**
     * Gets the bitmap given the input params
     * @param params artistName, albumName, albumId
     * @return Bitmap
     */
    protected Bitmap getBitmapInBackground(final String... params) {
        return ImageWorker.getBitmapInBackground(mContext, mImageCache, mKey,
                params[1], params[0], Long.valueOf(params[2]), mImageType);
    }

    /**
     * Creates a transition drawable with default parameters
     * @param bitmap the bitmap to transition to
     * @return the transition drawable
     */
    protected TransitionDrawable createImageTransitionDrawable(final Bitmap bitmap) {
        return createImageTransitionDrawable(bitmap, ImageWorker.FADE_IN_TIME, false, false);
    }

    /**
     * Creates a transition drawable
     * @param bitmap to transition to
     * @param fadeTime the time to fade in ms
     * @param dither setting
     * @param force force create a transition even if bitmap == null (fade to transparent)
     * @return the transition drawable
     */
    protected TransitionDrawable createImageTransitionDrawable(final Bitmap bitmap,
                                                               final int fadeTime, final boolean dither, final boolean force) {
        return ImageWorker.createImageTransitionDrawable(mResources, mFromDrawable, bitmap,
                fadeTime, dither, force);
    }
}
