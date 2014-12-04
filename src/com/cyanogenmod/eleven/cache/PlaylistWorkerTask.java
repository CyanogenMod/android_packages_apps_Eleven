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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageWorker.ImageType;
import com.cyanogenmod.eleven.loaders.PlaylistSongLoader;
import com.cyanogenmod.eleven.loaders.SortedCursor;
import com.cyanogenmod.eleven.provider.PlaylistArtworkStore;
import com.cyanogenmod.eleven.provider.SongPlayCount;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * The playlistWorkerTask will load either the top artist image or the cover art (a combination of
 * up to 4 of the top song's album images) into the designated ImageView.  If not enough time has
 * elapsed since the last update or if the # of songs in the playlist hasn't changed, no new images
 * will be loaded.
 */
public class PlaylistWorkerTask extends BitmapWorkerTask<Void, Void, TransitionDrawable> {
    // the work type
    public enum PlaylistWorkerType {
        Artist, CoverArt
    }

    // number of images to load in the cover art
    private static final int MAX_NUM_BITMAPS_TO_LOAD = 4;

    protected final long mPlaylistId;
    protected final PlaylistArtworkStore mPlaylistStore;
    protected final PlaylistWorkerType mWorkerType;

    // if we've found it in the cache, don't do any more logic unless enough time has elapsed or
    // if the playlist has changed
    protected final boolean mFoundInCache;

    // because a cached image can be loaded, we use this flag to signal to remove that default image
    protected boolean mFallbackToDefaultImage;

    /**
     * Constructor of <code>PlaylistWorkerTask</code>
     * @param key the key of the image to store to
     * @param playlistId the playlist identifier
     * @param type Artist or CoverArt?
     * @param foundInCache does this exist in the memory cache already
     * @param imageView The {@link ImageView} to use.
     * @param fromDrawable what drawable to transition from
     */
    public PlaylistWorkerTask(final String key, final long playlistId, final PlaylistWorkerType type,
                              final boolean foundInCache, final ImageView imageView,
                              final Drawable fromDrawable, final Context context) {
        super(key, imageView, ImageType.PLAYLIST, fromDrawable, context);

        mPlaylistId = playlistId;
        mWorkerType = type;
        mPlaylistStore = PlaylistArtworkStore.getInstance(mContext);
        mFoundInCache = foundInCache;
        mFallbackToDefaultImage = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransitionDrawable doInBackground(final Void... params) {
        if (isCancelled()) {
            return null;
        }

        Bitmap bitmap = null;

        // See if we need to update the image
        boolean needsUpdate = false;
        if (mWorkerType == PlaylistWorkerType.Artist
                && mPlaylistStore.needsArtistArtUpdate(mPlaylistId)) {
            needsUpdate = true;
        } else if (mWorkerType == PlaylistWorkerType.CoverArt
                && mPlaylistStore.needsCoverArtUpdate(mPlaylistId)) {
            needsUpdate = true;
        }

        // if we don't need to update and we've already found it in the cache, then return
        if (!needsUpdate && mFoundInCache) {
            return null;
        }

        // if we didn't find it in memory cache, try the disk cache
        if (!mFoundInCache) {
            bitmap = mImageCache.getCachedBitmap(mKey);
        }

        // if we don't need an update, return something
        if (!needsUpdate) {
            if (bitmap != null) {
                // if we found a bitmap, return it
                return createImageTransitionDrawable(bitmap);
            } else {
                // otherwise return null since we don't need an update
                return null;
            }
        }

        // otherwise re-run the logic to get the bitmap
        Cursor sortedCursor = null;

        try {
            // get the top songs for our playlist
            sortedCursor = getTopSongsForPlaylist();

            if (isCancelled()) {
                return null;
            }

            if (sortedCursor == null || sortedCursor.getCount() == 0) {
                // if all songs were removed from the playlist, update the last updated time
                // and reset to the default art
                if (mWorkerType == PlaylistWorkerType.Artist) {
                    // update the timestamp
                    mPlaylistStore.updateArtistArt(mPlaylistId);
                    // remove the cached image
                    mImageCache.removeFromCache(PlaylistArtworkStore.getArtistCacheKey(mPlaylistId));
                    // revert back to default image
                    mFallbackToDefaultImage = true;
                } else if (mWorkerType == PlaylistWorkerType.CoverArt) {
                    // update the timestamp
                    mPlaylistStore.updateCoverArt(mPlaylistId);
                    // remove the cached image
                    mImageCache.removeFromCache(PlaylistArtworkStore.getCoverCacheKey(mPlaylistId));
                    // revert back to default image
                    mFallbackToDefaultImage = true;
                }
            } else if (mWorkerType == PlaylistWorkerType.Artist) {
                bitmap = loadTopArtist(sortedCursor);
            } else {
                bitmap = loadTopSongs(sortedCursor);
            }
        } finally {
            if (sortedCursor != null) {
                sortedCursor.close();
            }
        }

        // if we have a bitmap create a transition drawable
        if (bitmap != null) {
            return createImageTransitionDrawable(bitmap);
        }

        return null;
    }

    /**
     * This gets the sorted cursor of the songs from a playlist based on play count
     * @return Cursor containing the sorted list
     */
    protected Cursor getTopSongsForPlaylist() {
        Cursor playlistCursor = null;
        SortedCursor sortedCursor = null;

        try {
            // gets the songs in the playlist
            playlistCursor = PlaylistSongLoader.makePlaylistSongCursor(mContext, mPlaylistId);
            if (playlistCursor == null || !playlistCursor.moveToFirst()) {
                return null;
            }

            // get all the ids in the list
            long[] songIds = new long[playlistCursor.getCount()];
            do {
                long id = playlistCursor.getLong(playlistCursor.getColumnIndex(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID));

                songIds[playlistCursor.getPosition()] = id;
            } while (playlistCursor.moveToNext());

            if (isCancelled()) {
                return null;
            }

            // find the sorted order for the playlist based on the top songs database
            long[] order = SongPlayCount.getInstance(mContext).getTopPlayedResultsForList(songIds);

            // create a new cursor that takes the playlist cursor and the sorted order
            sortedCursor = new SortedCursor(playlistCursor, order,
                    MediaStore.Audio.Playlists.Members.AUDIO_ID, null);

            // since this cursor is now wrapped by SortedTracksCursor, remove the reference here
            // so we don't accidentally close it in the finally loop
            playlistCursor = null;
        } finally {
            // if we quit early from isCancelled(), close our cursor
            if (playlistCursor != null) {
                playlistCursor.close();
                playlistCursor = null;
            }
        }

        return sortedCursor;
    }

    /**
     * Gets the most played song's artist image
     * @param sortedCursor the sorted playlist song cursor
     * @return Bitmap of the artist
     */
    protected Bitmap loadTopArtist(Cursor sortedCursor) {
        if (sortedCursor == null || !sortedCursor.moveToFirst()) {
            return null;
        }

        Bitmap bitmap = null;
        int artistIndex = sortedCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST);
        String artistName = null;

        do {
            if (isCancelled()) {
                return null;
            }

            artistName = sortedCursor.getString(artistIndex);
            // try to load the bitmap
            bitmap = ImageWorker.getBitmapInBackground(mContext, mImageCache, artistName,
                    null, artistName, -1, ImageType.ARTIST);
        } while (sortedCursor.moveToNext() && bitmap == null);

        if (bitmap == null) {
            // if we can't find any artist images, try loading the top songs image
            bitmap = mImageCache.getCachedBitmap(
                    PlaylistArtworkStore.getCoverCacheKey(mPlaylistId));
        }

        if (bitmap != null) {
            // add the image to the cache
            mImageCache.addBitmapToCache(mKey, bitmap, true);
        } else {
            mImageCache.removeFromCache(mKey);
            mFallbackToDefaultImage = true;
        }

        // store the fact that we ran this code into the db to prevent multiple re-runs
        mPlaylistStore.updateArtistArt(mPlaylistId);

        return bitmap;
    }

    /**
     * Gets the Cover Art of the playlist, which is a combination of the top song's album image
     * @param sortedCursor the sorted playlist song cursor
     * @return Bitmap of the artist
     */
    protected Bitmap loadTopSongs(Cursor sortedCursor) {
        if (sortedCursor == null || !sortedCursor.moveToFirst()) {
            return null;
        }

        ArrayList<Bitmap> loadedBitmaps = new ArrayList<Bitmap>(MAX_NUM_BITMAPS_TO_LOAD);

        final int artistIdx = sortedCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST);
        final int albumIdIdx = sortedCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID);
        final int albumIdx = sortedCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM);

        Bitmap bitmap = null;
        String artistName = null;
        String albumName = null;
        long albumId = -1;

        // create a hashset of the keys so we don't load images from the same album multiple times
        HashSet<String> keys = new HashSet<String>(sortedCursor.getCount());

        do {
            if (isCancelled()) {
                return null;
            }

            artistName = sortedCursor.getString(artistIdx);
            albumName = sortedCursor.getString(albumIdx);
            albumId = sortedCursor.getLong(albumIdIdx);

            String key = ImageFetcher.generateAlbumCacheKey(albumName, artistName);

            // if we successfully added the key (ie the key didn't previously exist)
            if (keys.add(key)) {
                // try to load the bitmap
                bitmap = ImageWorker.getBitmapInBackground(mContext, mImageCache,
                        key, albumName, artistName, albumId, ImageType.ALBUM);

                // if we got the bitmap, add it to the list
                if (bitmap != null) {
                    loadedBitmaps.add(bitmap);
                    bitmap = null;
                }
            }
        } while (sortedCursor.moveToNext() && loadedBitmaps.size() < MAX_NUM_BITMAPS_TO_LOAD);

        // if we found at least 1 bitmap
        if (loadedBitmaps.size() > 0) {
            // get the first bitmap
            bitmap = loadedBitmaps.get(0);

            // if we have many bitmaps
            if (loadedBitmaps.size() == MAX_NUM_BITMAPS_TO_LOAD) {
                // create a combined bitmap of the 4 images
                final int width = bitmap.getWidth();
                final int height = bitmap.getHeight();
                Bitmap combinedBitmap = Bitmap.createBitmap(width, height,
                        bitmap.getConfig());
                Canvas combinedCanvas = new Canvas(combinedBitmap);

                // top left
                combinedCanvas.drawBitmap(loadedBitmaps.get(0), null,
                        new Rect(0, 0, width / 2, height / 2), null);

                // top right
                combinedCanvas.drawBitmap(loadedBitmaps.get(1), null,
                        new Rect(width / 2, 0, width, height / 2), null);

                // bottom left
                combinedCanvas.drawBitmap(loadedBitmaps.get(2), null,
                        new Rect(0, height / 2, width / 2, height), null);

                // bottom right
                combinedCanvas.drawBitmap(loadedBitmaps.get(3), null,
                        new Rect(width / 2, height / 2, width, height), null);

                combinedCanvas.release();
                combinedCanvas = null;
                bitmap = combinedBitmap;
            }
        }

        // store the fact that we ran this code into the db to prevent multiple re-runs
        mPlaylistStore.updateCoverArt(mPlaylistId);

        if (bitmap != null) {
            // add the image to the cache
            mImageCache.addBitmapToCache(mKey, bitmap, true);
        } else {
            mImageCache.removeFromCache(mKey);
            mFallbackToDefaultImage = true;
        }

        return bitmap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPostExecute(TransitionDrawable transitionDrawable) {
        final ImageView imageView = getAttachedImageView();
        if (imageView != null) {
            if (transitionDrawable != null) {
                imageView.setImageDrawable(transitionDrawable);
            } else if (mFallbackToDefaultImage) {
                ImageFetcher.getInstance(mContext).loadDefaultImage(imageView,
                        ImageType.PLAYLIST, null, String.valueOf(mPlaylistId));
            }
        }
    }
}
