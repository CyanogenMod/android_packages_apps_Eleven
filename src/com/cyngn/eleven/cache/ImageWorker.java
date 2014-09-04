/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyngn.eleven.cache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.widget.ImageView;

import com.cyngn.eleven.R;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.widgets.BlurScrimImage;

import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a {@link Bitmap} to an {@link ImageView}. It handles things like using a
 * memory and disk cache, running the work in a background thread and setting a
 * placeholder image.
 */
public abstract class ImageWorker {

    /**
     * Render script
     */
    private static RenderScript sRenderScript = null;

    /**
     * Default transition drawable fade time
     */
    public static final int FADE_IN_TIME = 200;

    /**
     * Default transition drawable fade time slow
     */
    public static final int FADE_IN_TIME_SLOW = 1000;

    /**
     * The resources to use
     */
    private final Resources mResources;

    /**
     * First layer of the transition drawable
     */
    private final ColorDrawable mTransparentDrawable;

    /**
     * Default album art
     */
    private final Bitmap mDefault;

    /**
     * Default album art blurred
     */
    private final Bitmap mDefaultBlur;

    /**
     * Default Artist art
     */
    private final Bitmap mDefaultArtist;

    /**
     * Default Playlist art
     */
    private final Bitmap mDefaultPlaylist;

    /**
     * The Context to use
     */
    protected Context mContext;

    /**
     * Disk and memory caches
     */
    protected ImageCache mImageCache;

    /**
     * Constructor of <code>ImageWorker</code>
     *
     * @param context The {@link Context} to use
     */
    protected ImageWorker(final Context context) {
        mContext = context.getApplicationContext();

        if (sRenderScript == null) {
            sRenderScript = RenderScript.create(mContext);
        }

        mResources = mContext.getResources();
        // Create the default artwork
        mDefault = ((BitmapDrawable) mResources.getDrawable(R.drawable.default_artwork)).getBitmap();
        // Create the default blurred artwork
        mDefaultBlur = ((BitmapDrawable) mResources.getDrawable(R.drawable.default_artwork_blur)).getBitmap();
        // Create the artist artwork
        mDefaultArtist = ((BitmapDrawable) mResources.getDrawable(R.drawable.default_artist)).getBitmap();
        // Create the playlist artwork
        mDefaultPlaylist = ((BitmapDrawable) mResources.getDrawable(R.drawable.default_playlist)).getBitmap();
        // Create the transparent layer for the transition drawable
        mTransparentDrawable = new ColorDrawable(Color.TRANSPARENT);
    }

    /**
     * Set the {@link ImageCache} object to use with this ImageWorker.
     *
     * @param cacheCallback new {@link ImageCache} object.
     */
    public void setImageCache(final ImageCache cacheCallback) {
        mImageCache = cacheCallback;
    }

    /**
     * Closes the disk cache associated with this ImageCache object. Note that
     * this includes disk access so this should not be executed on the main/UI
     * thread.
     */
    public void close() {
        if (mImageCache != null) {
            mImageCache.close();
        }
    }

    /**
     * flush() is called to synchronize up other methods that are accessing the
     * cache first
     */
    public void flush() {
        if (mImageCache != null) {
            mImageCache.flush();
        }
    }

    /**
     * Adds a new image to the memory and disk caches
     *
     * @param data The key used to store the image
     * @param bitmap The {@link Bitmap} to cache
     */
    public void addBitmapToCache(final String key, final Bitmap bitmap) {
        if (mImageCache != null) {
            mImageCache.addBitmapToCache(key, bitmap);
        }
    }

    /**
     * @return The deafult artwork
     */
    public Bitmap getDefaultArtwork() {
        return mDefault;
    }

    /**
     * @return A new bitmap drawable of the default artwork
     */
    public BitmapDrawable getNewDefaultBitmapDrawable(ImageType imageType) {
        Bitmap targetBitmap = null;

        switch (imageType) {
            case ARTIST:
                targetBitmap = mDefaultArtist;
                break;

            case ALBUM:
            default:
                targetBitmap = mDefault;
                break;
        }

        BitmapDrawable bitmapDrawable = new BitmapDrawable(mResources, targetBitmap);
        // No filter and no dither makes things much quicker
        bitmapDrawable.setFilterBitmap(false);
        bitmapDrawable.setDither(false);

        return bitmapDrawable;
    }

    /**
     * The actual {@link AsyncTask} that will process the image.
     */
    private class BitmapWorkerTask extends AsyncTask<String, Void, Object> {

        /**
         * The {@link ImageView} used to set the result
         */
        private final WeakReference<ImageView> mImageReference;

        /**
         * Type of URL to download
         */
        private final ImageType mImageType;

        /**
         * The key used to store cached entries
         */
        private String mKey;

        /**
         * Artist name param
         */
        private String mArtistName;

        /**
         * Album name parm
         */
        private String mAlbumName;

        /**
         * The album ID used to find the corresponding artwork
         */
        private long mAlbumId;

        /**
         * The URL of an image to download
         */
        private String mUrl;

        /**
         * Layer drawable used to cross fade the result from the worker
         */
        protected Drawable mFromDrawable;

        /**
         * Constructor of <code>BitmapWorkerTask</code>
         *
         * @param imageView The {@link ImageView} to use.
         * @param imageType The type of image URL to fetch for.
         */
        @SuppressWarnings("deprecation")
        public BitmapWorkerTask(final ImageView imageView, final ImageType imageType) {
            mImageReference = new WeakReference<ImageView>(imageView);
            mImageType = imageType;

            // A transparent image (layer 0) and the new result (layer 1)
            mFromDrawable = mTransparentDrawable;
        }

        protected Bitmap getBitmapInBackground(final String... params) {
            // Define the key
            mKey = params[0];

            // The result
            Bitmap bitmap = null;

            // First, check the disk cache for the image
            if (mKey != null && mImageCache != null && !isCancelled()
                    && getAttachedImageView() != null) {
                bitmap = mImageCache.getCachedBitmap(mKey);
            }

            // Define the album id now
            mAlbumId = Long.valueOf(params[3]);

            // Second, if we're fetching artwork, check the device for the image
            if (bitmap == null && mImageType.equals(ImageType.ALBUM) && mAlbumId >= 0
                    && mKey != null && !isCancelled() && getAttachedImageView() != null
                    && mImageCache != null) {
                bitmap = mImageCache.getCachedArtwork(mContext, mKey, mAlbumId);
            }

            // Third, by now we need to download the image
            if (bitmap == null && ApolloUtils.isOnline(mContext) && !isCancelled()
                    && getAttachedImageView() != null) {
                // Now define what the artist name, album name, and url are.
                mArtistName = params[1];
                mAlbumName = params[2];
                mUrl = processImageUrl(mArtistName, mAlbumName, mImageType);
                if (mUrl != null) {
                    bitmap = processBitmap(mUrl);
                }
            }

            // Fourth, add the new image to the cache
            if (bitmap != null && mKey != null && mImageCache != null) {
                addBitmapToCache(mKey, bitmap);
            }

            return bitmap;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Object doInBackground(final String... params) {
            final Bitmap bitmap = getBitmapInBackground(params);
            return createImageTransitionDrawable(mResources, mFromDrawable, bitmap,
                    FADE_IN_TIME, false, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void onPostExecute(Object result) {
            if (isCancelled()) {
                return;
            }

            TransitionDrawable transitionDrawable = (TransitionDrawable)result;

            final ImageView imageView = getAttachedImageView();
            if (transitionDrawable != null && imageView != null) {
                imageView.setImageDrawable(transitionDrawable);
            }
        }

        /**
         * @return The {@link ImageView} associated with this task as long as
         *         the ImageView's task still points to this task as well.
         *         Returns null otherwise.
         */
        protected ImageView getAttachedImageView() {
            final ImageView imageView = mImageReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask) {
                return imageView;
            }
            return null;
        }
    }

    /**
     * This will download the image (if needed) and create a blur and set the scrim as well on the
     * BlurScrimImage
     */
    private class BlurBitmapWorkerTask extends BitmapWorkerTask {
        // if the image is too small, the blur will look bad post scale up so we use the min size
        // to scale up before bluring
        private static final int MIN_BITMAP_SIZE = 500;
        // number of times to run the blur
        private static final int NUM_BLUR_RUNS = 8;
        // 25f is the max blur radius possible
        private static final float BLUR_RADIUS = 25f;

        // container for the result
        private class ResultContainer {
            public TransitionDrawable mImageViewBitmapDrawable;
            public int mPaletteColor;
        }

        /**
         * The {@link BlurScrimImage} used to set the result
         */
        private final WeakReference<BlurScrimImage> mBlurScrimImage;

        /**
         * Constructor of <code>BitmapWorkerTask</code>
         *
         * @param blurScrimImage The {@link BlurScrimImage} to use.
         * @param imageType The type of image URL to fetch for.
         */
        @SuppressWarnings("deprecation")
        public BlurBitmapWorkerTask(final BlurScrimImage blurScrimImage, final ImageType imageType) {
            super(blurScrimImage.getImageView(), imageType);
            mBlurScrimImage = new WeakReference<BlurScrimImage>(blurScrimImage);

            // use the existing image as the drawable and if it doesn't exist fallback to transparent
            mFromDrawable = blurScrimImage.getImageView().getDrawable();
            if (mFromDrawable == null) {
                mFromDrawable = mTransparentDrawable;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Object doInBackground(final String... params) {
            Bitmap bitmap = getBitmapInBackground(params);

            ResultContainer result = new ResultContainer();

            Bitmap output = null;

            if (bitmap != null) {
                // now create the blur bitmap
                Bitmap input = bitmap;

                // if the image is too small, scale it up before running through the blur
                if (input.getWidth() < MIN_BITMAP_SIZE || input.getHeight() < MIN_BITMAP_SIZE) {
                    float multiplier = Math.max(MIN_BITMAP_SIZE / (float)input.getWidth(),
                            MIN_BITMAP_SIZE / (float)input.getHeight());
                    input = input.createScaledBitmap(bitmap, (int)(input.getWidth() * multiplier),
                            (int)(input.getHeight() * multiplier), true);
                    // since we created a new bitmap, we can re-use the bitmap for our output
                    output = input;
                } else {
                    // if we aren't creating a new bitmap, create a new output bitmap
                    output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), input.getConfig());
                }

                // run the blur multiple times
                for (int i = 0; i < NUM_BLUR_RUNS; i++) {
                    final Allocation inputAlloc = Allocation.createFromBitmap(sRenderScript, input);
                    final Allocation outputAlloc = Allocation.createTyped(sRenderScript,
                            inputAlloc.getType());
                    final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(sRenderScript,
                            Element.U8_4(sRenderScript));

                    script.setRadius(BLUR_RADIUS);
                    script.setInput(inputAlloc);
                    script.forEach(outputAlloc);
                    outputAlloc.copyTo(output);

                    // if we run more than 1 blur, the new input should be the old output
                    input = output;
                }

                // calculate the palette color
                result.mPaletteColor = getPaletteColorInBackground(output);

                // create the bitmap transition drawable
                result.mImageViewBitmapDrawable = createImageTransitionDrawable(mResources, mFromDrawable,
                        output, FADE_IN_TIME_SLOW, true, true);

                return result;
            }

            return null;
        }

        /**
         * This will get the most vibrant palette color for a bitmap
         * @param input to process
         * @return the most vibrant color or transparent if none found
         */
        private int getPaletteColorInBackground(Bitmap input) {
            int color = Color.TRANSPARENT;

            if (input != null) {
                Palette palette = Palette.generate(input);
                PaletteItem paletteItem = palette.getVibrantColor();

                // keep walking through the palette items to find a color if we don't have any
                if (paletteItem == null) {
                    paletteItem = palette.getVibrantColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getLightVibrantColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getLightMutedColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getLightMutedColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getDarkVibrantColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getMutedColor();
                }

                if (paletteItem == null) {
                    paletteItem = palette.getDarkMutedColor();
                }

                if (paletteItem != null) {
                    // grab the rgb values
                    color = paletteItem.getRgb() | 0xFFFFFF;

                    // make it 20% opacity
                    color &= 0x33000000;
                }
            }

            return color;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void onPostExecute(Object result) {
            if (isCancelled()) {
                return;
            }

            BlurScrimImage blurScrimImage = mBlurScrimImage.get();
            if (blurScrimImage != null) {
                if (result == null) {
                    // if we have no image, then signal the transition to the default state
                    blurScrimImage.transitionToDefaultState();
                } else {
                    ResultContainer resultContainer = (ResultContainer)result;

                    // create the palette transition
                    TransitionDrawable paletteTransition = createPaletteTransition(blurScrimImage,
                            resultContainer.mPaletteColor);

                    // set the transition drawable
                    blurScrimImage.setTransitionDrawable(false,
                            resultContainer.mImageViewBitmapDrawable, paletteTransition);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected final ImageView getAttachedImageView() {
            final BlurScrimImage blurImage  = mBlurScrimImage.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(blurImage);
            if (this == bitmapWorkerTask) {
                return blurImage.getImageView();
            }
            return null;
        }
    }

    /**
     * Creates a transition drawable to Bitmap with params
     * @param resources Android Resources!
     * @param fromDrawable the drawable to transition from
     * @param bitmap the bitmap to transition to
     * @param fadeTime the fade time in MS to fade in
     * @param dither setting
     * @param force force create a transition even if bitmap == null (fade to transparent)
     * @return the drawable if created, null otherwise
     */
    public static TransitionDrawable createImageTransitionDrawable(final Resources resources,
               final Drawable fromDrawable, final Bitmap bitmap, final int fadeTime,
               final boolean dither, final boolean force) {
        if (bitmap != null || force) {
            final Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = fromDrawable;

            // Add the transition to drawable
            Drawable layerTwo;
            if (bitmap != null) {
                layerTwo = new BitmapDrawable(resources, bitmap);
                layerTwo.setFilterBitmap(false);
                layerTwo.setDither(dither);
            } else {
                // if no bitmap (forced) then transition to transparent
                layerTwo = new ColorDrawable(Color.TRANSPARENT);
            }

            arrayDrawable[1] = layerTwo;

            // Finally, return the image
            final TransitionDrawable result = new TransitionDrawable(arrayDrawable);
            result.setCrossFadeEnabled(true);
            result.startTransition(fadeTime);
            return result;
        }

        return null;
    }

    /**
     * This will create the palette transition from the original color to the new one
     * @param scrimImage the container to change the color for
     * @param color the color to transition to
     * @return the transition to run
     */
    public static TransitionDrawable createPaletteTransition(BlurScrimImage scrimImage, int color) {
        final Drawable[] arrayDrawable = new Drawable[2];
        arrayDrawable[0] = scrimImage.getBackground();

        if (arrayDrawable[0] == null) {
            arrayDrawable[0] = new ColorDrawable(Color.TRANSPARENT);
        }

        arrayDrawable[1] = new ColorDrawable(color);

        // create the transition
        final TransitionDrawable result = new TransitionDrawable(arrayDrawable);
        result.setCrossFadeEnabled(true);
        result.startTransition(FADE_IN_TIME_SLOW);
        return result;
    }

    /**
     * Calls {@code cancel()} in the worker task
     *
     * @param imageView the {@link ImageView} to use
     */
    public static final void cancelWork(final ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            bitmapWorkerTask.cancel(true);
        }
    }

    /**
     * Returns true if the current work has been canceled or if there was no
     * work in progress on this image view. Returns false if the work in
     * progress deals with the same data. The work is not stopped in that case.
     */
    public static final boolean executePotentialWork(final Object data, final ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        return executePotentialWork(data, bitmapWorkerTask);
    }

    /**
     * Returns true if the current work has been canceled or if there was no
     * work in progress on this image view. Returns false if the work in
     * progress deals with the same data. The work is not stopped in that case.
     */
    public static final boolean executePotentialWork(final Object data, final BlurScrimImage image) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(image);
        return executePotentialWork(data, bitmapWorkerTask);
    }

    /**
     * Returns true if the current work has been canceled or if there was no
     * work in progress on this image view. Returns false if the work in
     * progress deals with the same data. The work is not stopped in that case.
     */
    public static final boolean executePotentialWork(final Object data,
                                                     final BitmapWorkerTask bitmapWorkerTask) {
        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mKey;
            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        return true;
    }

    /**
     * Used to determine if the current image drawable has an instance of
     * {@link BitmapWorkerTask}
     *
     * @param imageView Any {@link ImageView}.
     * @return Retrieve the currently active work task (if any) associated with
     *         this {@link ImageView}. null if there is no such task.
     */
    private static final BitmapWorkerTask getBitmapWorkerTask(final ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable)drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * Used to determine if the current image drawable has an instance of
     * {@link BitmapWorkerTask}
     *
     * @param image Any {@link BlurScrimImage}.
     * @return Retrieve the currently active work task (if any) associated with
     *         this {@link BlurScrimImage}. null if there is no such task.
     */
    private static final BitmapWorkerTask getBitmapWorkerTask(final BlurScrimImage image) {
        if (image != null) {
            final AsyncDrawable asyncDrawable = (AsyncDrawable)image.getTag();
            if (asyncDrawable != null) {
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * A custom {@link BitmapDrawable} that will be attached to the
     * {@link ImageView} while the work is in progress. Contains a reference to
     * the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can
     * bind its result, independently of the finish order.
     */
    private static final class AsyncDrawable extends ColorDrawable {

        private final WeakReference<BitmapWorkerTask> mBitmapWorkerTaskReference;

        /**
         * Constructor of <code>AsyncDrawable</code>
         */
        public AsyncDrawable(final Resources res, final Bitmap bitmap,
                final BitmapWorkerTask mBitmapWorkerTask) {
            super(Color.TRANSPARENT);
            mBitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(mBitmapWorkerTask);
        }

        /**
         * @return The {@link BitmapWorkerTask} associated with this drawable
         */
        public BitmapWorkerTask getBitmapWorkerTask() {
            return mBitmapWorkerTaskReference.get();
        }
    }

    /**
     * Called to fetch the artist or ablum art.
     *
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    protected void loadImage(final String key, final String artistName, final String albumName,
            final long albumId, final ImageView imageView, final ImageType imageType) {
        if (key == null || mImageCache == null || imageView == null) {
            return;
        }

        // First, check the memory for the image
        final Bitmap lruBitmap = mImageCache.getBitmapFromMemCache(key);
        if (lruBitmap != null && imageView != null) {
            // Bitmap found in memory cache
            imageView.setImageBitmap(lruBitmap);
        } else {
            // if a background drawable hasn't been set, create one so that even if
            // the disk cache is paused we see something
            if (imageView.getBackground() == null) {
                imageView.setBackgroundDrawable(getNewDefaultBitmapDrawable(imageType));
            }

            if (executePotentialWork(key, imageView)
                    && imageView != null && !mImageCache.isDiskCachePaused()) {
                // cancel the old task if any
                final Drawable previousDrawable = imageView.getDrawable();
                if (previousDrawable != null && previousDrawable instanceof AsyncDrawable) {
                    BitmapWorkerTask workerTask = ((AsyncDrawable)previousDrawable).getBitmapWorkerTask();
                    if (workerTask != null) {
                        workerTask.cancel(false);
                    }
                }

                // Otherwise run the worker task
                final BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(imageView, imageType);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mDefault,
                        bitmapWorkerTask);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    ApolloUtils.execute(false, bitmapWorkerTask, key,
                            artistName, albumName, String.valueOf(albumId));
                } catch (RejectedExecutionException e) {
                    // Executor has exhausted queue space, show default artwork
                    imageView.setImageBitmap(getDefaultArtwork());
                }
            }
        }
    }

    /**
     * Called to fetch the blurred artist or album art.
     *
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param blurScrimImage The {@link BlurScrimImage} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    protected void loadBlurImage(final String key, final String artistName, final String albumName,
                             final long albumId, final BlurScrimImage blurScrimImage, final ImageType imageType) {
        if (key == null || mImageCache == null || blurScrimImage == null) {
            return;
        }

        // Create the blur key
        final String blurKey = key + "_blur";

        if (executePotentialWork(blurKey, blurScrimImage)
                && blurScrimImage != null && !mImageCache.isDiskCachePaused()) {
            // cancel the old task if any
            final AsyncDrawable previousDrawable = (AsyncDrawable)blurScrimImage.getTag();
            if (previousDrawable != null) {
                BitmapWorkerTask workerTask = previousDrawable.getBitmapWorkerTask();
                if (workerTask != null) {
                    workerTask.cancel(true);
                }
            }

            // Otherwise run the worker task
            final BlurBitmapWorkerTask blurWorkerTask = new BlurBitmapWorkerTask(blurScrimImage, imageType);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mDefault,
                    blurWorkerTask);
            blurScrimImage.setTag(asyncDrawable);

            try {
                ApolloUtils.execute(false, blurWorkerTask, key,
                        artistName, albumName, String.valueOf(albumId));
            } catch (RejectedExecutionException e) {
                // Executor has exhausted queue space, show default artwork
                blurScrimImage.transitionToDefaultState();
            }
        }
    }

    /**
     * Subclasses should override this to define any processing or work that
     * must happen to produce the final {@link Bitmap}. This will be executed in
     * a background thread and be long running.
     *
     * @param key The key to identify which image to process, as provided by
     *            {@link ImageWorker#loadImage(mKey, ImageView)}
     * @return The processed {@link Bitmap}.
     */
    protected abstract Bitmap processBitmap(String key);

    /**
     * Subclasses should override this to define any processing or work that
     * must happen to produce the URL needed to fetch the final {@link Bitmap}.
     *
     * @param artistName The artist name param used in the Last.fm API.
     * @param albumName The album name param used in the Last.fm API.
     * @param imageType The type of image URL to fetch for.
     * @return The image URL for an artist image or album image.
     */
    protected abstract String processImageUrl(String artistName, String albumName,
            ImageType imageType);

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM;
    }

}
