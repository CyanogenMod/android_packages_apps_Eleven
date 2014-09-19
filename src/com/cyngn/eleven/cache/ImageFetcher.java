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
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.cache.PlaylistWorkerTask.PlaylistWorkerType;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.BlurScrimImage;

/**
 * A subclass of {@link ImageWorker} that fetches images from a URL.
 */
public class ImageFetcher extends ImageWorker {

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
        loadImage(generateAlbumCacheKey(MusicUtils.getAlbumName(), MusicUtils.getArtistName()),
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                imageView, ImageType.ALBUM);
    }

    /**
     * Used to fetch the current artwork blurred.
     */
    public void loadCurrentBlurredArtwork(final BlurScrimImage image) {
        loadBlurImage(generateAlbumCacheKey(MusicUtils.getAlbumName(), MusicUtils.getArtistName()),
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                image, ImageType.ALBUM);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadArtistImage(final String key, final ImageView imageView) {
        loadImage(key, key, null, -1, imageView, ImageType.ARTIST);
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
     * @param key The key used to find the image to return
     */
    public Bitmap getCachedBitmap(final String key) {
        if (mImageCache != null) {
            return mImageCache.getCachedBitmap(key);
        }
        return getDefaultArtwork();
    }

    /**
     * @param keyAlbum  The key (album name) used to find the album art to return
     * @param keyArtist The key (artist name) used to find the album art to return
     */
    public Bitmap getCachedArtwork(final String keyAlbum, final String keyArtist) {
        return getCachedArtwork(keyAlbum, keyArtist,
                MusicUtils.getIdForAlbum(mContext, keyAlbum, keyArtist));
    }

    /**
     * @param keyAlbum  The key (album name) used to find the album art to return
     * @param keyArtist The key (artist name) used to find the album art to return
     * @param keyId     The key (album id) used to find the album art to return
     */
    public Bitmap getCachedArtwork(final String keyAlbum, final String keyArtist,
                                   final long keyId) {
        if (mImageCache != null) {
            return mImageCache.getCachedArtwork(mContext,
                    generateAlbumCacheKey(keyAlbum, keyArtist),
                    keyId);
        }
        return getDefaultArtwork();
    }

    /**
     * Finds cached or downloads album art. Used in {@link MusicPlaybackService}
     * to set the current album art in the notification and lock screen
     *
     * @param albumName  The name of the current album
     * @param albumId    The ID of the current album
     * @param artistName The album artist in case we should have to download
     *                   missing artwork
     * @return The album art as an {@link Bitmap}
     */
    public Bitmap getArtwork(final String albumName, final long albumId, final String artistName) {
        // Check the disk cache
        Bitmap artwork = null;

        if (artwork == null && albumName != null && mImageCache != null) {
            artwork = mImageCache.getBitmapFromDiskCache(
                    generateAlbumCacheKey(albumName, artistName));
        }
        if (artwork == null && albumId >= 0 && mImageCache != null) {
            // Check for local artwork
            artwork = mImageCache.getArtworkFromFile(mContext, albumId);
        }
        if (artwork != null) {
            return artwork;
        }
        return getDefaultArtwork();
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
        return new StringBuilder(albumName)
                .append("_")
                .append(artistName)
                .append("_")
                .append(Config.ALBUM_ART_SUFFIX)
                .toString();
    }
}
