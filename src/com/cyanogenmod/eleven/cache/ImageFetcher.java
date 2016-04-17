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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;
import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.cache.PlaylistWorkerTask.PlaylistWorkerType;
import com.cyanogenmod.eleven.utils.BitmapWithColors;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.BlurScrimImage;
import com.cyanogenmod.eleven.widgets.LetterTileDrawable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A subclass of {@link ImageWorker} that fetches images from a URL.
 */
public class ImageFetcher extends ImageWorker {

    private static final int DEFAULT_MAX_IMAGE_HEIGHT = 1024;

    private static final int DEFAULT_MAX_IMAGE_WIDTH = 1024;

    private static ImageFetcher sInstance = null;

    /**
     * Creates a new instance of {@link ImageFetcher}.
     *
     * @param context The {@link Context} to use.
     */
    public ImageFetcher(final Context context) {
        super(context);
    }

    /**
     * Used to create a singleton of the image fetcher
     *
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static final ImageFetcher getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new ImageFetcher(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Loads a playlist's most played song's artist image
     * @param playlistId id of the playlist
     * @param imageView imageview to load into
     */
    public void loadPlaylistArtistImage(final long playlistId, final ImageView imageView) {
        loadPlaylistImage(playlistId, PlaylistWorkerType.Artist, imageView);
    }

    /**
     * Loads a playlist's most played songs into a combined image, or show 1 if not enough images
     * @param playlistId id of the playlist
     * @param imageView imageview to load into
     */
    public void loadPlaylistCoverArtImage(final long playlistId, final ImageView imageView) {
        loadPlaylistImage(playlistId, PlaylistWorkerType.CoverArt, imageView);
    }

    /**
     * Used to fetch album images.
     */
    public void loadAlbumImage(final String artistName, final String albumName, final long albumId,
                               final ImageView imageView) {
        loadImage(generateAlbumCacheKey(albumName, artistName), artistName, albumName, albumId, imageView,
                ImageType.ALBUM);
    }

    /**
     * Used to fetch the current artwork.
     */
    public void loadCurrentArtwork(final ImageView imageView) {
        loadImage(getCurrentCacheKey(),
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                imageView, ImageType.ALBUM);
    }

    /**
     * Used to fetch the current artwork blurred.
     */
    public void loadCurrentBlurredArtwork(final BlurScrimImage image) {
        loadBlurImage(getCurrentCacheKey(),
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                image, ImageType.ALBUM);
    }

    public static String getCurrentCacheKey() {
        return generateAlbumCacheKey(MusicUtils.getAlbumName(), MusicUtils.getArtistName());
    }

    /**
     * Used to fetch artist images.
     */
    public void loadArtistImage(final String key, final ImageView imageView) {
        loadImage(key, key, null, -1, imageView, ImageType.ARTIST);
    }

    /**
     * Used to fetch artist images. It also scales the image to fit the image view, if necessary.
     */
    public void loadArtistImage(final String key, final ImageView imageView, boolean scaleImgToView) {
        loadImage(key, key, null, -1, imageView, ImageType.ARTIST, scaleImgToView);
    }

    /**
     * Used to fetch the current artist image.
     */
    public void loadCurrentArtistImage(final ImageView imageView) {
        loadImage(MusicUtils.getArtistName(), MusicUtils.getArtistName(), null, -1, imageView,
                ImageType.ARTIST);
    }

    /**
     * @param pause True to temporarily pause the disk cache, false otherwise.
     */
    public void setPauseDiskCache(final boolean pause) {
        if (mImageCache != null) {
            mImageCache.setPauseDiskCache(pause);
        }
    }

    /**
     * Clears the disk and memory caches
     */
    public void clearCaches() {
        if (mImageCache != null) {
            mImageCache.clearCaches();
        }

        // clear the keys of images we've already downloaded
        sKeys.clear();
    }

    public void addCacheListener(ICacheListener listener) {
        if (mImageCache != null) {
            mImageCache.addCacheListener(listener);
        }
    }

    public void removeCacheListener(ICacheListener listener) {
        if (mImageCache != null) {
            mImageCache.removeCacheListener(listener);
        }
    }

    /**
     * @param key The key used to find the image to remove
     */
    public void removeFromCache(final String key) {
        if (mImageCache != null) {
            mImageCache.removeFromCache(key);
        }
    }

    /**
     * Finds cached or downloads album art. Used in {@link MusicPlaybackService}
     * to set the current album art in the notification and lock screen
     *
     * @param albumName  The name of the current album
     * @param albumId    The ID of the current album
     * @param artistName The album artist in case we should have to download
     *                   missing artwork
     * @param smallArtwork Get the small version of the default artwork if no artwork exists
     * @return The album art as an {@link Bitmap}
     */
    public BitmapWithColors getArtwork(final String albumName, final long albumId,
            final String artistName, boolean smallArtwork) {
        // Check the disk cache
        Bitmap artwork = null;
        String key = String.valueOf(albumId);

        if (artwork == null && albumName != null && mImageCache != null) {
            artwork = mImageCache.getBitmapFromDiskCache(key);
        }
        if (artwork == null && albumId >= 0 && mImageCache != null) {
            // Check for local artwork
            artwork = mImageCache.getArtworkFromFile(mContext, albumId);
        }
        if (artwork != null) {
            return new BitmapWithColors(artwork, key.hashCode());
        }

        return LetterTileDrawable.createDefaultBitmap(mContext, key, ImageType.ALBUM, false,
                smallArtwork);
    }

    /**
     * Generates key used by album art cache. It needs both album name and artist name
     * to let to select correct image for the case when there are two albums with the
     * same artist.
     *
     * @param albumName  The album name the cache key needs to be generated.
     * @param artistName The artist name the cache key needs to be generated.
     * @return
     */
    public static String generateAlbumCacheKey(final String albumName, final String artistName) {
        if (albumName == null || artistName == null) {
            return null;
        }
        return albumName + "_" + artistName + "_" + Config.ALBUM_ART_SUFFIX;
    }

    /**
     * Decode and sample down a {@link Bitmap} from a Uri.
     *
     * @param selectedImage Uri of the Image to decode
     * @return A {@link Bitmap} sampled down from the original with the same
     *         aspect ratio and dimensions that are equal to or greater than the
     *         requested width and height
     */
    public static Bitmap decodeSampledBitmapFromUri(ContentResolver cr, final Uri selectedImage) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try {
            InputStream input = cr.openInputStream(selectedImage);
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            if (options.outHeight == -1 || options.outWidth == -1) {
                return null;
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, DEFAULT_MAX_IMAGE_WIDTH,
                    DEFAULT_MAX_IMAGE_HEIGHT);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            input = cr.openInputStream(selectedImage);
            return BitmapFactory.decodeStream(input, null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Calculate an inSampleSize for use in a
     * {@link android.graphics.BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This
     * implementation calculates the closest inSampleSize that will result in
     * the final decoded bitmap having a width and height equal to or larger
     * than the requested width and height. This implementation does not ensure
     * a power of 2 is returned for inSampleSize which can be faster when
     * decoding but results in a larger bitmap which isn't as useful for caching
     * purposes.
     *
     * @param options An options object with out* params already populated (run
     *            through a decode* method with inJustDecodeBounds==true
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    public static final int calculateInSampleSize(final BitmapFactory.Options options,
                                                  final int reqWidth, final int reqHeight) {
        /* Raw height and width of image */
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger
            // inSampleSize).

            final float totalPixels = width * height;

            /* More than 2x the requested pixels we'll sample down further */
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }
}
