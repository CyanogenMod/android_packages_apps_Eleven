/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.cache;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.cyngn.eleven.cache.ImageWorker.ImageType;
import com.cyngn.eleven.loaders.PlaylistSongLoader;
import com.cyngn.eleven.loaders.SortedCursor;
import com.cyngn.eleven.provider.PlaylistArtworkStore;
import com.cyngn.eleven.provider.SongPlayCount;

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

        // if we found a bitmap and we don't need an update, return it
        if (bitmap != null && !needsUpdate) {
            return createImageTransitionDrawable(bitmap);
        }

        // otherwise re-run the logic to get the bitmap
        Cursor sortedCursor = null;

        try {
            // get the top songs for our playlist
            sortedCursor = getTopSongsForPlaylist();

            if (sortedCursor == null || sortedCursor.getCount() == 0 || isCancelled()) {
                return null;
            }

            // run the appropriate logic
            if (mWorkerType == PlaylistWorkerType.Artist) {
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
                    MediaStore.Audio.Playlists.Members.AUDIO_ID);

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

        sortedCursor.close();

        if (bitmap != null) {
            // add the image to the cache
            mImageCache.addBitmapToCache(mKey, bitmap, true);

            // store this artist name into the db
            mPlaylistStore.updateArtistArt(mPlaylistId);
        }

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

        sortedCursor.close();

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

                combinedCanvas = null;
                bitmap = combinedBitmap;
            }

            if (bitmap != null) {
                // add the image to the cache
                mImageCache.addBitmapToCache(mKey, bitmap, true);

                // store this artist name into the db
                mPlaylistStore.updateCoverArt(mPlaylistId);
            }

            return bitmap;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPostExecute(TransitionDrawable transitionDrawable) {
        final ImageView imageView = getAttachedImageView();
        if (transitionDrawable != null && imageView != null) {
            imageView.setImageDrawable(transitionDrawable);
        }
    }
}
