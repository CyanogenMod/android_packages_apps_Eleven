/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.cache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.v8.renderscript.RenderScript;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.provider.PlaylistArtworkStore;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.ImageUtils;
import com.cyanogenmod.eleven.widgets.BlurScrimImage;
import com.cyanogenmod.eleven.cache.PlaylistWorkerTask.PlaylistWorkerType;
import com.cyanogenmod.eleven.widgets.LetterTileDrawable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    public static RenderScript sRenderScript = null;

    /**
     * Tracks which images we've tried to download and prevents it from trying again
     * In the future we might want to throw this into a db
     */
    public static Set<String> sKeys = Collections.synchronizedSet(new HashSet<String>());

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
     * @return A new drawable of the default artwork
     */
    public Drawable getNewDrawable(ImageType imageType, String name,
                                                String identifier) {
        LetterTileDrawable letterTileDrawable = new LetterTileDrawable(mContext);
        letterTileDrawable.setTileDetails(name, identifier, imageType);
        letterTileDrawable.setIsCircular(false);
        return letterTileDrawable;
    }

    public static Bitmap getBitmapInBackground(final Context context, final ImageCache imageCache,
                                   final String key, final String albumName, final String artistName,
                                   final long albumId, final ImageType imageType) {
        // The result
        Bitmap bitmap = null;

        // First, check the disk cache for the image
        if (key != null && imageCache != null) {
            bitmap = imageCache.getCachedBitmap(key);
        }

        // Second, if we're fetching artwork, check the device for the image
        if (bitmap == null && imageType.equals(ImageType.ALBUM) && albumId >= 0
                && key != null && imageCache != null) {
            bitmap = imageCache.getCachedArtwork(context, key, albumId);
        }

        // Third, by now we need to download the image
        if (bitmap == null && ApolloUtils.isOnline(context) && !sKeys.contains(key)) {
            // Now define what the artist name, album name, and url are.
            String url = ImageUtils.processImageUrl(context, artistName, albumName, imageType);
            if (url != null) {
                bitmap = ImageUtils.processBitmap(context, url);
            }
        }

        // Fourth, add the new image to the cache
        if (bitmap != null && key != null && imageCache != null) {
            imageCache.addBitmapToCache(key, bitmap);
        }

        sKeys.add(key);

        return bitmap;
    }

    /**
     * Parses the drawable for instances of TransitionDrawable and breaks them open until it finds
     * a drawable that isn't a transition drawable
     * @param drawable to parse
     * @return the target drawable that isn't a TransitionDrawable
     */
    public static Drawable getTopDrawable(final Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        Drawable retDrawable = drawable;
        while (retDrawable instanceof TransitionDrawable) {
            TransitionDrawable transition = (TransitionDrawable) retDrawable;
            retDrawable = transition.getDrawable(transition.getNumberOfLayers() - 1);
        }

        return retDrawable;
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
            arrayDrawable[0] = getTopDrawable(fromDrawable);

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
        arrayDrawable[0] = getTopDrawable(scrimImage.getBackground());

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
     * Cancels and clears out any pending bitmap worker tasks on this image view
     * @param image ImageView/BlurScrimImage to check
     */
    public static final void cancelWork(final View image) {
        Object tag = image.getTag();
        if (tag != null && tag instanceof AsyncTaskContainer) {
            AsyncTaskContainer asyncTaskContainer = (AsyncTaskContainer)tag;
            BitmapWorkerTask bitmapWorkerTask = asyncTaskContainer.getBitmapWorkerTask();
            if (bitmapWorkerTask != null) {
                bitmapWorkerTask.cancel(false);
            }

            // clear out the tag
            image.setTag(null);
        }
    }

    /**
     * Returns false if the existing async task is loading the same key value
     * Returns true otherwise and also cancels the async task if one exists
     */
    public static final boolean executePotentialWork(final String key, final View view) {
        final AsyncTaskContainer asyncTaskContainer = getAsyncTaskContainer(view);
        if (asyncTaskContainer != null) {
            // we are trying to reload the same image, return false to indicate no work is needed
            if (asyncTaskContainer.getKey().equals(key)) {
                return false;
            }

            // since we don't match, cancel the work and switch to the new worker task
            cancelWork(view);
        }

        return true;
    }

    /**
     * Used to determine if the current image drawable has an instance of
     * {@link AsyncTaskContainer}
     *
     * @param view Any {@link View} that either is or contains an ImageView.
     * @return Retrieve the AsyncTaskContainer assigned to the {@link View}. null if there is no
     * such task.
     */
    public static final AsyncTaskContainer getAsyncTaskContainer(final View view) {
        if (view != null) {
            if (view.getTag() instanceof AsyncTaskContainer) {
                return (AsyncTaskContainer) view.getTag();
            }
        }

        return null;
    }

    /**
     * Used to determine if the current image drawable has an instance of
     * {@link BitmapWorkerTask}.  A {@link BitmapWorkerTask} may not exist even if the {@link
     * AsyncTaskContainer} does as it may have finished its work
     *
     * @param view Any {@link View} that either is or contains an ImageView.
     * @return Retrieve the currently active work task (if any) associated with
     *         this {@link View}. null if there is no such task.
     */
    public static final BitmapWorkerTask getBitmapWorkerTask(final View view) {
        AsyncTaskContainer asyncTask = getAsyncTaskContainer(view);
        if (asyncTask != null) {
            return asyncTask.getBitmapWorkerTask();
        }

        return null;
    }

    /**
     * A custom {@link BitmapDrawable} that will be attached to the
     * {@link View} which either is or contains an {@link ImageView} while the work is in progress.
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can
     * bind its result, independently of the finish order.
     */
    public static final class AsyncTaskContainer {

        private final WeakReference<BitmapWorkerTask> mBitmapWorkerTaskReference;
        // keep a copy of the key in case the worker task mBitmapWorkerTaskReference is released
        // after completion
        private String mKey;

        /**
         * Constructor of <code>AsyncDrawable</code>
         */
        public AsyncTaskContainer(final BitmapWorkerTask bitmapWorkerTask) {
            mBitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
            mKey = bitmapWorkerTask.mKey;
        }

        /**
         * @return The {@link BitmapWorkerTask} associated with this drawable
         */
        public BitmapWorkerTask getBitmapWorkerTask() {
            return mBitmapWorkerTaskReference.get();
        }

        public String getKey() {
            return mKey;
        }
    }

    /**
     * Loads the default image into the image view given the image type
     * @param imageView The {@link ImageView}
     * @param imageType The type of image
     */
    public void loadDefaultImage(final ImageView imageView, final ImageType imageType,
                                    final String name, final String identifier) {
        if (imageView != null) {
            // if an existing letter drawable exists, re-use it
            Drawable existingDrawable = imageView.getDrawable();
            if (existingDrawable != null && existingDrawable instanceof LetterTileDrawable) {
                ((LetterTileDrawable)existingDrawable).setTileDetails(name, identifier, imageType);
            } else {
                imageView.setImageDrawable(getNewDrawable(imageType, name,
                        identifier));
            }
        }
    }

    /**
     * Called to fetch the artist or album art.
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

        loadImage(key, artistName, albumName, albumId, imageView, imageType, false);
    }

    /**
     * Called to fetch the artist or album art.
     *
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     * @param scaleImgToView config option to scale the image to the image view's dimensions
     */
    protected void loadImage(final String key, final String artistName, final String albumName,
                             final long albumId, final ImageView imageView,
                             final ImageType imageType, final boolean scaleImgToView) {

        if (key == null || mImageCache == null || imageView == null) {
            return;
        }

        // First, check the memory for the image
        final Bitmap lruBitmap = mImageCache.getBitmapFromMemCache(key);
        if (lruBitmap != null) {   // Bitmap found in memory cache
            // scale image if necessary
            if (scaleImgToView) {
                imageView.setImageBitmap(ImageUtils.scaleBitmapForImageView(lruBitmap, imageView));
            } else {
                imageView.setImageBitmap(lruBitmap);
            }
        } else {
            // load the default image
            if (imageType == ImageType.ARTIST) {
                loadDefaultImage(imageView, imageType, artistName, key);
            } else if (imageType == ImageType.ALBUM) {
                // don't show letters for albums so pass in null as the display string
                // because an album could have multiple artists, use the album id as the key here
                loadDefaultImage(imageView, imageType, null, String.valueOf(albumId));
            } else {
                // don't show letters for playlists so pass in null as the display string
                loadDefaultImage(imageView, imageType, null, key);
            }

            if (executePotentialWork(key, imageView)
                    && imageView != null && !mImageCache.isDiskCachePaused()) {
                Drawable fromDrawable = imageView.getDrawable();
                if (fromDrawable == null) {
                    fromDrawable = mTransparentDrawable;
                }

                // Otherwise run the worker task
                final SimpleBitmapWorkerTask bitmapWorkerTask = new SimpleBitmapWorkerTask(key,
                            imageView, imageType, fromDrawable, mContext, scaleImgToView);

                final AsyncTaskContainer asyncTaskContainer = new AsyncTaskContainer(bitmapWorkerTask);
                imageView.setTag(asyncTaskContainer);
                try {
                    ApolloUtils.execute(false, bitmapWorkerTask,
                            artistName, albumName, String.valueOf(albumId));
                } catch (RejectedExecutionException e) {
                    // Executor has exhausted queue space
                }
            }
        }
    }


    /**
     * Called to fetch a playlist's top artist or cover art
     * @param playlistId playlist identifier
     * @param type of work to get (Artist or CoverArt)
     * @param imageView to set the image to
     */
    public void loadPlaylistImage(final long playlistId, final PlaylistWorkerType type,
                                  final ImageView imageView) {
        if (mImageCache == null || imageView == null) {
            return;
        }

        String key = null;
        switch (type) {
            case Artist:
                key = PlaylistArtworkStore.getArtistCacheKey(playlistId);
                break;
            case CoverArt:
                key = PlaylistArtworkStore.getCoverCacheKey(playlistId);
                break;
        }

        // First, check the memory for the image
        final Bitmap lruBitmap = mImageCache.getBitmapFromMemCache(key);
        if (lruBitmap != null) {
            // Bitmap found in memory cache
            imageView.setImageBitmap(lruBitmap);
        } else {
            // load the default image
            loadDefaultImage(imageView, ImageType.PLAYLIST, null, String.valueOf(playlistId));
        }

        // even though we may have found the image in the cache, we want to check if the playlist
        // has been updated, or it's been too long since the last update and change the image
        // accordingly
        if (executePotentialWork(key, imageView) && !mImageCache.isDiskCachePaused()) {
            // since a playlist's image can change based on changes to the playlist
            // set the from drawable to be the existing image (if it exists) instead of transparent
            // and fade from there
            Drawable fromDrawable = imageView.getDrawable();
            if (fromDrawable == null) {
                fromDrawable = mTransparentDrawable;
            }

            // Otherwise run the worker task
            final PlaylistWorkerTask bitmapWorkerTask = new PlaylistWorkerTask(key, playlistId, type,
                    lruBitmap != null, imageView, fromDrawable, mContext);
            final AsyncTaskContainer asyncTaskContainer = new AsyncTaskContainer(bitmapWorkerTask);
            imageView.setTag(asyncTaskContainer);
            try {
                ApolloUtils.execute(false, bitmapWorkerTask);
            } catch (RejectedExecutionException e) {
                // Executor has exhausted queue space
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

        if (executePotentialWork(key, blurScrimImage) && !mImageCache.isDiskCachePaused()) {
            // Otherwise run the worker task
            final BlurBitmapWorkerTask blurWorkerTask = new BlurBitmapWorkerTask(key, blurScrimImage,
                    imageType, mTransparentDrawable, mContext, sRenderScript);
            final AsyncTaskContainer asyncTaskContainer = new AsyncTaskContainer(blurWorkerTask);
            blurScrimImage.setTag(asyncTaskContainer);

            try {
                ApolloUtils.execute(false, blurWorkerTask, artistName, albumName, String.valueOf(albumId));
            } catch (RejectedExecutionException e) {
                // Executor has exhausted queue space, show default artwork
                blurScrimImage.transitionToDefaultState();
            }
        }
    }

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM, PLAYLIST;
    }
}
