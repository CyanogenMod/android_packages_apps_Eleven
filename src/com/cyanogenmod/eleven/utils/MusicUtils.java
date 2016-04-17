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

package com.cyanogenmod.eleven.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.ArtistColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Audio.PlaylistsColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.cyanogenmod.eleven.Config.IdType;
import com.cyanogenmod.eleven.Config.SmartPlaylistType;
import com.cyanogenmod.eleven.IElevenService;
import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.loaders.LastAddedLoader;
import com.cyanogenmod.eleven.loaders.PlaylistLoader;
import com.cyanogenmod.eleven.loaders.PlaylistSongLoader;
import com.cyanogenmod.eleven.loaders.SongLoader;
import com.cyanogenmod.eleven.loaders.TopTracksLoader;
import com.cyanogenmod.eleven.locale.LocaleUtils;
import com.cyanogenmod.eleven.menu.FragmentMenuItems;
import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.model.AlbumArtistDetails;
import com.cyanogenmod.eleven.model.Artist;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.provider.RecentStore;
import com.cyanogenmod.eleven.provider.SongPlayCount;
import com.cyanogenmod.eleven.service.MusicPlaybackTrack;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.WeakHashMap;

/**
 * A collection of helpers directly related to music or Apollo's service.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class MusicUtils {

    public static IElevenService mService = null;

    private static final WeakHashMap<Context, ServiceBinder> mConnectionMap;

    private static final long[] sEmptyList;

    private static ContentValues[] mContentValuesCache = null;

    private static final int MIN_VALID_YEAR = 1900; // used to remove invalid years from metadata

    public static final String MUSIC_ONLY_SELECTION = MediaStore.Audio.AudioColumns.IS_MUSIC + "=1"
                    + " AND " + MediaStore.Audio.AudioColumns.TITLE + " != ''"; //$NON-NLS-2$

    public static final long UPDATE_FREQUENCY_MS = 500;
    public static final long UPDATE_FREQUENCY_FAST_MS = 30;

    static {
        mConnectionMap = new WeakHashMap<Context, ServiceBinder>();
        sEmptyList = new long[0];
    }

    /* This class is never initiated */
    public MusicUtils() {
    }

    /**
     * @param context The {@link Context} to use
     * @param callback The {@link ServiceConnection} to use
     * @return The new instance of {@link ServiceToken}
     */
    public static final ServiceToken bindToService(final Context context,
            final ServiceConnection callback) {
        Activity realActivity = ((Activity)context).getParent();
        if (realActivity == null) {
            realActivity = (Activity)context;
        }
        final ContextWrapper contextWrapper = new ContextWrapper(realActivity);
        contextWrapper.startService(new Intent(contextWrapper, MusicPlaybackService.class));
        final ServiceBinder binder = new ServiceBinder(callback,
                contextWrapper.getApplicationContext());
        if (contextWrapper.bindService(
                new Intent().setClass(contextWrapper, MusicPlaybackService.class), binder, 0)) {
            mConnectionMap.put(contextWrapper, binder);
            return new ServiceToken(contextWrapper);
        }
        return null;
    }

    /**
     * @param token The {@link ServiceToken} to unbind from
     */
    public static void unbindFromService(final ServiceToken token) {
        if (token == null) {
            return;
        }
        final ContextWrapper mContextWrapper = token.mWrappedContext;
        final ServiceBinder mBinder = mConnectionMap.remove(mContextWrapper);
        if (mBinder == null) {
            return;
        }
        mContextWrapper.unbindService(mBinder);
        if (mConnectionMap.isEmpty()) {
            mService = null;
        }
    }

    public static final class ServiceBinder implements ServiceConnection {
        private final ServiceConnection mCallback;
        private final Context mContext;

        /**
         * Constructor of <code>ServiceBinder</code>
         *
         * @param context The {@link ServiceConnection} to use
         */
        public ServiceBinder(final ServiceConnection callback, final Context context) {
            mCallback = callback;
            mContext = context;
        }

        @Override
        public void onServiceConnected(final ComponentName className, final IBinder service) {
            mService = IElevenService.Stub.asInterface(service);
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
            MusicUtils.initPlaybackServiceWithSettings(mContext);
        }

        @Override
        public void onServiceDisconnected(final ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            mService = null;
        }
    }

    public static final class ServiceToken {
        public ContextWrapper mWrappedContext;

        /**
         * Constructor of <code>ServiceToken</code>
         *
         * @param context The {@link ContextWrapper} to use
         */
        public ServiceToken(final ContextWrapper context) {
            mWrappedContext = context;
        }
    }

    public static final boolean isPlaybackServiceConnected() {
        return mService != null;
    }

    /**
     * Used to make number of labels for the number of artists, albums, songs,
     * genres, and playlists.
     *
     * @param context The {@link Context} to use.
     * @param pluralInt The ID of the plural string to use.
     * @param number The number of artists, albums, songs, genres, or playlists.
     * @return A {@link String} used as a label for the number of artists,
     *         albums, songs, genres, and playlists.
     */
    public static final String makeLabel(final Context context, final int pluralInt,
            final int number) {
        return context.getResources().getQuantityString(pluralInt, number, number);
    }

    /**
     * * Used to create a formatted time string for the duration of tracks.
     *
     * @param context The {@link Context} to use.
     * @param secs The track in seconds.
     * @return Duration of a track that's properly formatted.
     */
    public static final String makeShortTimeString(final Context context, long secs) {
        long hours, mins;

        hours = secs / 3600;
        secs %= 3600;
        mins = secs / 60;
        secs %= 60;

        final String durationFormat = context.getResources().getString(
                hours == 0 ? R.string.durationformatshort : R.string.durationformatlong);
        return String.format(durationFormat, hours, mins, secs);
    }

    /**
     * Used to create a formatted time string in the format of #h #m or #m if there is only minutes
     *
     * @param context The {@link Context} to use.
     * @param secs The duration seconds.
     * @return Duration properly formatted in #h #m format
     */
    public static final String makeLongTimeString(final Context context, long secs) {
        long hours, mins;

        hours = secs / 3600;
        secs %= 3600;
        mins = secs / 60;

        String hoursString = MusicUtils.makeLabel(context, R.plurals.Nhours, (int)hours);
        String minutesString = MusicUtils.makeLabel(context, R.plurals.Nminutes, (int)mins);

        if (hours == 0) {
            return minutesString;
        } else if (mins == 0) {
            return hoursString;
        }

        final String durationFormat = context.getResources().getString(R.string.duration_format);
        return String.format(durationFormat, hoursString, minutesString);
    }

    /**
     * Used to combine two strings with some kind of separator in between
     *
     * @param context The {@link Context} to use.
     * @param first string to combine
     * @param second string to combine
     * @return the combined string
     */
    public static final String makeCombinedString(final Context context, final String first,
                                                  final String second) {
        final String formatter = context.getResources().getString(R.string.combine_two_strings);
        return String.format(formatter, first, second);
    }

    /**
     * Changes to the next track
     */
    public static void next() {
        try {
            if (mService != null) {
                mService.next();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Initialize playback service with values from Settings
     */
    public static void initPlaybackServiceWithSettings(final Context context) {
        MusicUtils.setShakeToPlayEnabled(
                PreferenceUtils.getInstance(context).getShakeToPlay());
        MusicUtils.setShowAlbumArtOnLockscreen(
                PreferenceUtils.getInstance(context).getShowAlbumArtOnLockscreen());
    }

    /**
     * Set shake to play status
     */
    public static void setShakeToPlayEnabled(final boolean enabled) {
        try {
            if (mService != null) {
                mService.setShakeToPlayEnabled(enabled);
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Set show album art on lockscreen
     */
    public static void setShowAlbumArtOnLockscreen(final boolean enabled) {
        try {
            if (mService != null) {
                mService.setLockscreenAlbumArt(enabled);
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Changes to the next track asynchronously
     */
    public static void asyncNext(final Context context) {
        final Intent previous = new Intent(context, MusicPlaybackService.class);
        previous.setAction(MusicPlaybackService.NEXT_ACTION);
        context.startService(previous);
    }

    /**
     * Changes to the previous track.
     *
     * @NOTE The AIDL isn't used here in order to properly use the previous
     *       action. When the user is shuffling, because {@link
     *       MusicPlaybackService.#openCurrentAndNext()} is used, the user won't
     *       be able to travel to the previously skipped track. To remedy this,
     *       {@link MusicPlaybackService.#openCurrent()} is called in {@link
     *       MusicPlaybackService.#prev()}. {@code #startService(Intent intent)}
     *       is called here to specifically invoke the onStartCommand used by
     *       {@link MusicPlaybackService}, which states if the current position
     *       less than 2000 ms, start the track over, otherwise move to the
     *       previously listened track.
     */
    public static void previous(final Context context, final boolean force) {
        final Intent previous = new Intent(context, MusicPlaybackService.class);
        if (force) {
            previous.setAction(MusicPlaybackService.PREVIOUS_FORCE_ACTION);
        } else {
            previous.setAction(MusicPlaybackService.PREVIOUS_ACTION);
        }
        context.startService(previous);
    }

    /**
     * Plays or pauses the music.
     */
    public static void playOrPause() {
        try {
            if (mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Cycles through the repeat options.
     */
    public static void cycleRepeat() {
        try {
            if (mService != null) {
                switch (mService.getRepeatMode()) {
                    case MusicPlaybackService.REPEAT_NONE:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        break;
                    case MusicPlaybackService.REPEAT_ALL:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_CURRENT);
                        if (mService.getShuffleMode() != MusicPlaybackService.SHUFFLE_NONE) {
                            mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        }
                        break;
                    default:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
                        break;
                }
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Cycles through the shuffle options.
     */
    public static void cycleShuffle() {
        try {
            if (mService != null) {
                switch (mService.getShuffleMode()) {
                    case MusicPlaybackService.SHUFFLE_NONE:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
                        if (mService.getRepeatMode() == MusicPlaybackService.REPEAT_CURRENT) {
                            mService.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        }
                        break;
                    case MusicPlaybackService.SHUFFLE_NORMAL:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        break;
                    case MusicPlaybackService.SHUFFLE_AUTO:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        break;
                    default:
                        break;
                }
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @return True if we're playing music, false otherwise.
     */
    public static final boolean isPlaying() {
        if (mService != null) {
            try {
                return mService.isPlaying();
            } catch (final RemoteException ignored) {
            }
        }
        return false;
    }

    /**
     * @return The current shuffle mode.
     */
    public static final int getShuffleMode() {
        if (mService != null) {
            try {
                return mService.getShuffleMode();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current repeat mode.
     */
    public static final int getRepeatMode() {
        if (mService != null) {
            try {
                return mService.getRepeatMode();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current track name.
     */
    public static final String getTrackName() {
        if (mService != null) {
            try {
                return mService.getTrackName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current artist name.
     */
    public static final String getArtistName() {
        if (mService != null) {
            try {
                return mService.getArtistName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album name.
     */
    public static final String getAlbumName() {
        if (mService != null) {
            try {
                return mService.getAlbumName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album Id.
     */
    public static final long getCurrentAlbumId() {
        if (mService != null) {
            try {
                return mService.getAlbumId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id.
     */
    public static final long getCurrentAudioId() {
        if (mService != null) {
            try {
                return mService.getAudioId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current Music Playback Track
     */
    public static final MusicPlaybackTrack getCurrentTrack() {
        if (mService != null) {
            try {
                return mService.getCurrentTrack();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The Music Playback Track at the specified index
     */
    public static final MusicPlaybackTrack getTrack(int index) {
        if (mService != null) {
            try {
                return mService.getTrack(index);
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The next song Id.
     */
    public static final long getNextAudioId() {
        if (mService != null) {
            try {
                return mService.getNextAudioId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The previous song Id.
     */
    public static final long getPreviousAudioId() {
        if (mService != null) {
            try {
                return mService.getPreviousAudioId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current artist Id.
     */
    public static final long getCurrentArtistId() {
        if (mService != null) {
            try {
                return mService.getArtistId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The audio session Id.
     */
    public static final int getAudioSessionId() {
        if (mService != null) {
            try {
                return mService.getAudioSessionId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The queue.
     */
    public static final long[] getQueue() {
        try {
            if (mService != null) {
                return mService.getQueue();
            } else {
            }
        } catch (final RemoteException ignored) {
        }
        return sEmptyList;
    }

    /**
     * @param position
     * @return the id of the track in the queue at the given position
     */
    public static final long getQueueItemAtPosition(int position) {
        try {
            if (mService != null) {
                return mService.getQueueItemAtPosition(position);
            } else {
            }
        } catch (final RemoteException ignored) {
        }
        return -1;
    }

    /**
     * @return the current queue size
     */
    public static final int getQueueSize() {
        try {
            if (mService != null) {
                return mService.getQueueSize();
            } else {
            }
        } catch (final RemoteException ignored) {
        }
        return 0;
    }

    /**
     * @return The position of the current track in the queue.
     */
    public static final int getQueuePosition() {
        try {
            if (mService != null) {
                return mService.getQueuePosition();
            }
        } catch (final RemoteException ignored) {
        }
        return 0;
    }

    /**
     * @return The queue history size
     */
    public static final int getQueueHistorySize() {
        if (mService != null) {
            try {
                return mService.getQueueHistorySize();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The queue history position at the position
     */
    public static final int getQueueHistoryPosition(int position) {
        if (mService != null) {
            try {
                return mService.getQueueHistoryPosition(position);
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The queue history
     */
    public static final int[] getQueueHistoryList() {
        if (mService != null) {
            try {
                return mService.getQueueHistoryList();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @param id The ID of the track to remove.
     * @return removes track from a playlist or the queue.
     */
    public static final int removeTrack(final long id) {
        try {
            if (mService != null) {
                return mService.removeTrack(id);
            }
        } catch (final RemoteException ingored) {
        }
        return 0;
    }

    /**
     * Remove song at a specified position in the list
     *
     * @param id The ID of the track to remove
     * @param position The position of the song
     *
     * @return true if successful, false otherwise
     */
    public static final boolean removeTrackAtPosition(final long id, final int position) {
        try {
            if (mService != null) {
                return mService.removeTrackAtPosition(id, position);
            }
        } catch (final RemoteException ingored) {
        }
        return false;
    }

    /**
     * @param cursor The {@link Cursor} used to perform our query.
     * @return The song list for a MIME type.
     */
    public static final long[] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        final int len = cursor.getCount();
        final long[] list = new long[len];
        cursor.moveToFirst();
        int columnIndex = -1;
        try {
            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (final IllegalArgumentException notaplaylist) {
            columnIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(columnIndex);
            cursor.moveToNext();
        }
        cursor.close();
        cursor = null;
        return list;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the artist.
     * @return The song list for an artist.
     */
    public static final long[] getSongListForArtist(final Context context, final long id) {
        final String[] projection = new String[] {
            BaseColumns._ID
        };
        final String selection = AudioColumns.ARTIST_ID + "=" + id + " AND "
                + AudioColumns.IS_MUSIC + "=1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                AudioColumns.ALBUM_KEY + "," + AudioColumns.TRACK);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return mList;
        }
        return sEmptyList;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the album.
     * @return The song list for an album.
     */
    public static final long[] getSongListForAlbum(final Context context, final long id) {
        final String[] projection = new String[] {
            BaseColumns._ID
        };
        final String selection = AudioColumns.ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC
                + "=1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                AudioColumns.TRACK + ", " + MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return mList;
        }
        return sEmptyList;
    }

    /**
     * Plays songs by an artist.
     *
     * @param context The {@link Context} to use.
     * @param artistId The artist Id.
     * @param position Specify where to start.
     */
    public static void playArtist(final Context context, final long artistId, int position, boolean shuffle) {
        final long[] artistList = getSongListForArtist(context, artistId);
        if (artistList != null) {
            playAll(context, artistList, position, artistId, IdType.Artist, shuffle);
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the genre.
     * @return The song list for an genre.
     */
    public static final long[] getSongListForGenre(final Context context, final long id) {
        final String[] projection = new String[] {
            BaseColumns._ID
        };
        String selection = (AudioColumns.IS_MUSIC + "=1") +
                " AND " + MediaColumns.TITLE + "!=''";
        final Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", Long.valueOf(id));
        Cursor cursor = context.getContentResolver().query(uri, projection, selection,
                null, null);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return mList;
        }
        return sEmptyList;
    }

    /**
     * @param context The {@link Context} to use
     * @param uri The source of the file
     */
    public static void playFile(final Context context, final Uri uri) {
        if (uri == null || mService == null) {
            return;
        }

        // If this is a file:// URI, just use the path directly instead
        // of going through the open-from-filedescriptor codepath.
        String filename;
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            filename = uri.getPath();
        } else {
            filename = uri.toString();
        }

        try {
            mService.stop();
            mService.openFile(filename);
            mService.play();
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param list The list of songs to play.
     * @param position Specify where to start.
     * @param forceShuffle True to force a shuffle, false otherwise.
     */
    public static void playAll(final Context context, final long[] list, int position,
                               final long sourceId, final IdType sourceType,
                               final boolean forceShuffle) {
        if (list == null || list.length == 0 || mService == null) {
            return;
        }
        try {
            if (forceShuffle) {
                mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
            }
            if (position < 0) {
                position = 0;
            }
            mService.open(list, forceShuffle ? -1 : position, sourceId, sourceType.mId);
            mService.play();
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param list The list to enqueue.
     */
    public static void playNext(final long[] list, final long sourceId, final IdType sourceType) {
        if (mService == null) {
            return;
        }
        try {
            mService.enqueue(list, MusicPlaybackService.NEXT, sourceId, sourceType.mId);
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     */
    public static void shuffleAll(final Context context) {
        Cursor cursor = SongLoader.makeSongCursor(context, null);
        final long[] mTrackList = getSongListForCursor(cursor);
        if (mTrackList.length == 0 || mService == null) {
            return;
        }
        try {
            mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
            final long mCurrentId = mService.getAudioId();
            final int mCurrentQueuePosition = getQueuePosition();
            if (mCurrentQueuePosition == 0
                    && mCurrentId == mTrackList[0]) {
                final long[] mPlaylist = getQueue();
                if (Arrays.equals(mTrackList, mPlaylist)) {
                    mService.play();
                    return;
                }
            }
            mService.open(mTrackList, -1, -1, IdType.NA.mId);
            mService.play();
            cursor.close();
            cursor = null;
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Returns The ID for a playlist.
     *
     * @param context The {@link Context} to use.
     * @param name The name of the playlist.
     * @return The ID for a playlist.
     */
    public static final long getIdForPlaylist(final Context context, final String name) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[]{
                        BaseColumns._ID
                }, PlaylistsColumns.NAME + "=?", new String[]{
                        name
                }, PlaylistsColumns.NAME);
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
            cursor = null;
        }
        return id;
    }

    /** @param context The {@link Context} to use.
     *  @param id The id of the playlist.
     *  @return The name for a playlist. */
    public static final String getNameForPlaylist(final Context context, final long id) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { PlaylistsColumns.NAME },
                BaseColumns._ID + "=?",
                new String[] { Long.toString(id) },
                null);
        if (cursor != null) {
            try {
                if(cursor.moveToFirst()) { return cursor.getString(0); }
            } finally { cursor.close(); }
        }
        // nothing found
        return null;
    }

    /**
     * Returns the Id for an artist.
     *
     * @param context The {@link Context} to use.
     * @param name The name of the artist.
     * @return The ID for an artist.
     */
    public static final long getIdForArtist(final Context context, final String name) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, new String[]{
                        BaseColumns._ID
                }, ArtistColumns.ARTIST + "=?", new String[]{
                        name
                }, ArtistColumns.ARTIST);
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
            cursor = null;
        }
        return id;
    }

    /**
     * Returns the ID for an album.
     *
     * @param context The {@link Context} to use.
     * @param albumName The name of the album.
     * @param artistName The name of the artist
     * @return The ID for an album.
     */
    public static final long getIdForAlbum(final Context context, final String albumName,
            final String artistName) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[] {
                    BaseColumns._ID
                }, AlbumColumns.ALBUM + "=? AND " + AlbumColumns.ARTIST + "=?", new String[] {
                    albumName, artistName
                }, AlbumColumns.ALBUM);
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
            cursor = null;
        }
        return id;
    }

    /**
     * Plays songs from an album.
     *
     * @param context The {@link Context} to use.
     * @param albumId The album Id.
     * @param position Specify where to start.
     */
    public static void playAlbum(final Context context, final long albumId, int position, boolean shuffle) {
        final long[] albumList = getSongListForAlbum(context, albumId);
        if (albumList != null) {
            playAll(context, albumList, position, albumId, IdType.Album, shuffle);
        }
    }

    /*  */
    public static void makeInsertItems(final long[] ids, final int offset, int len, final int base) {
        if (offset + len > ids.length) {
            len = ids.length - offset;
        }

        if (mContentValuesCache == null || mContentValuesCache.length != len) {
            mContentValuesCache = new ContentValues[len];
        }
        for (int i = 0; i < len; i++) {
            if (mContentValuesCache[i] == null) {
                mContentValuesCache[i] = new ContentValues();
            }
            mContentValuesCache[i].put(Playlists.Members.PLAY_ORDER, base + offset + i);
            mContentValuesCache[i].put(Playlists.Members.AUDIO_ID, ids[offset + i]);
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param name The name of the new playlist.
     * @return A new playlist ID.
     */
    public static final long createPlaylist(final Context context, final String name) {
        if (name != null && name.length() > 0) {
            final ContentResolver resolver = context.getContentResolver();
            final String[] projection = new String[] {
                PlaylistsColumns.NAME
            };
            final String selection = PlaylistsColumns.NAME + " = '" + name + "'";
            Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    projection, selection, null, null);
            if (cursor.getCount() <= 0) {
                final ContentValues values = new ContentValues(1);
                values.put(PlaylistsColumns.NAME, name);
                final Uri uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values);
                return Long.parseLong(uri.getLastPathSegment());
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            return -1;
        }
        return -1;
    }

    /**
     * @param context The {@link Context} to use.
     * @param playlistId The playlist ID.
     */
    public static void clearPlaylist(final Context context, final int playlistId) {
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        context.getContentResolver().delete(uri, null, null);
        return;
    }

    /** remove all backing data for top tracks playlist */
    public static void clearTopTracks(Context context) {
        SongPlayCount.getInstance(context).deleteAll();
    }

    /** remove all backing data for top tracks playlist */
    public static void clearRecent(Context context) {
        RecentStore.getInstance(context).deleteAll();
    }

    /** move up cutoff for last added songs so playlist will be cleared */
    public static void clearLastAdded(Context context) {
        PreferenceUtils.getInstance(context)
            .setLastAddedCutoff(System.currentTimeMillis());
    }

    /**
     * @param context The {@link Context} to use.
     * @param ids The id of the song(s) to add.
     * @param playlistid The id of the playlist being added to.
     */
    public static void addToPlaylist(final Context context, final long[] ids, final long playlistid) {
        final int size = ids.length;
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[] {
            "max(" + Playlists.Members.PLAY_ORDER + ")",
        };
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid);
        Cursor cursor = null;
        int base = 0;

        try {
            cursor = resolver.query(uri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                base = cursor.getInt(0) + 1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        int numinserted = 0;
        for (int offSet = 0; offSet < size; offSet += 1000) {
            makeInsertItems(ids, offSet, 1000, base);
            numinserted += resolver.bulkInsert(uri, mContentValuesCache);
        }
        final String message = context.getResources().getQuantityString(
                R.plurals.NNNtrackstoplaylist, numinserted, numinserted);
        Toast.makeText((Activity)context, message, Toast.LENGTH_SHORT).show();
        playlistChanged();
    }

    /**
     * Removes a single track from a given playlist
     * @param context The {@link Context} to use.
     * @param id The id of the song to remove.
     * @param playlistId The id of the playlist being removed from.
     */
    public static void removeFromPlaylist(final Context context, final long id,
            final long playlistId) {
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        final ContentResolver resolver = context.getContentResolver();
        resolver.delete(uri, Playlists.Members.AUDIO_ID + " = ? ", new String[] {
            Long.toString(id)
        });
        final String message = context.getResources().getQuantityString(
                R.plurals.NNNtracksfromplaylist, 1, 1);
        Toast.makeText((Activity)context, message, Toast.LENGTH_SHORT).show();
        playlistChanged();
    }

    /**
     * @param context The {@link Context} to use.
     * @param list The list to enqueue.
     */
    public static void addToQueue(final Context context, final long[] list, long sourceId,
                                  IdType sourceType) {
        if (mService == null) {
            return;
        }
        try {
            mService.enqueue(list, MusicPlaybackService.LAST, sourceId, sourceType.mId);
            final String message = makeLabel(context, R.plurals.NNNtrackstoqueue, list.length);
            Toast.makeText((Activity) context, message, Toast.LENGTH_SHORT).show();
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use
     * @param id The song ID.
     */
    public static void setRingtone(final Context context, final long id) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            final ContentValues values = new ContentValues(2);
            values.put(AudioColumns.IS_RINGTONE, "1");
            values.put(AudioColumns.IS_ALARM, "1");
            resolver.update(uri, values, null, null);
        } catch (final UnsupportedOperationException ingored) {
            return;
        }

        final String[] projection = new String[] {
                BaseColumns._ID, MediaColumns.DATA, MediaColumns.TITLE
        };

        final String selection = BaseColumns._ID + "=" + id;
        Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                selection, null, null);
        try {
            if (cursor != null && cursor.getCount() == 1) {
                cursor.moveToFirst();
                Settings.System.putString(resolver, Settings.System.RINGTONE, uri.toString());
                final String message = context.getString(R.string.set_as_ringtone,
                        cursor.getString(2));
                Toast.makeText((Activity)context, message, Toast.LENGTH_SHORT).show();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The id of the album.
     * @return The song count for an album.
     */
    public static final int getSongCountForAlbumInt(final Context context, final long id) {
        int songCount = 0;
        if (id == -1) { return songCount; }

        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
        Cursor cursor = context.getContentResolver().query(uri,
                new String[] { AlbumColumns.NUMBER_OF_SONGS }, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                if(!cursor.isNull(0)) {
                    songCount = cursor.getInt(0);
                }
            }
            cursor.close();
            cursor = null;
        }

        return songCount;
    }

    /**
     * Gets the number of songs for a playlist
     * @param context The {@link Context} to use.
     * @param playlistId the id of the playlist
     * @return the # of songs in the playlist
     */
    public static final int getSongCountForPlaylist(final Context context, final long playlistId) {
        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                new String[]{BaseColumns._ID}, MusicUtils.MUSIC_ONLY_SELECTION, null, null);

        if (c != null) {
            int count = 0;
            if (c.moveToFirst()) {
                count = c.getCount();
            }
            c.close();
            c = null;
            return count;
        }

        return 0;
    }

    public static final AlbumArtistDetails getAlbumArtDetails(final Context context, final long trackId) {
        String selection = (AudioColumns.IS_MUSIC + "=1") +
                " AND " + BaseColumns._ID + " = '" + trackId + "'";

        Cursor cursor = context.getContentResolver().query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            new String[] {
                    /* 0 */
                MediaStore.Audio.AudioColumns.ALBUM_ID,
                    /* 1 */
                MediaStore.Audio.AudioColumns.ALBUM,
                    /* 2 */
                MediaStore.Audio.AlbumColumns.ARTIST,
            }, selection, null, null
        );

        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        AlbumArtistDetails result = new AlbumArtistDetails();
        result.mAudioId = trackId;
        result.mAlbumId = cursor.getLong(0);
        result.mAlbumName = cursor.getString(1);
        result.mArtistName = cursor.getString(2);
        cursor.close();

        return result;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The id of the album.
     * @return The release date for an album.
     */
    public static final String getReleaseDateForAlbum(final Context context, final long id) {
        if (id == -1) {
            return null;
        }
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
        Cursor cursor = context.getContentResolver().query(uri, new String[] {
                    AlbumColumns.FIRST_YEAR
                }, null, null, null);
        String releaseDate = null;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                releaseDate = cursor.getString(0);
            }
            cursor.close();
            cursor = null;
        }
        return releaseDate;
    }

    /**
     * @return The path to the currently playing file as {@link String}
     */
    public static final String getFilePath() {
        try {
            if (mService != null) {
                return mService.getPath();
            }
        } catch (final RemoteException ignored) {
        }
        return null;
    }

    /**
     * @param from The index the item is currently at.
     * @param to The index the item is moving to.
     */
    public static void moveQueueItem(final int from, final int to) {
        try {
            if (mService != null) {
                mService.moveQueueItem(from, to);
            } else {
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to sue
     * @param playlistId The playlist Id
     * @return The track list for a playlist
     */
    public static final long[] getSongListForPlaylist(final Context context, final long playlistId) {
        Cursor cursor = PlaylistSongLoader.makePlaylistSongCursor(context, playlistId);

        if (cursor != null) {
            final long[] list = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return list;
        }
        return sEmptyList;
    }

    /**
     * Plays a user created playlist.
     *
     * @param context The {@link Context} to use.
     * @param playlistId The playlist Id.
     */
    public static void playPlaylist(final Context context, final long playlistId, boolean shuffle) {
        final long[] playlistList = getSongListForPlaylist(context, playlistId);
        if (playlistList != null) {
            playAll(context, playlistList, -1, playlistId, IdType.Playlist, shuffle);
        }
    }

    /**
     * @param context The {@link Context} to use
     * @param type The Smart Playlist Type
     * @return The song list for the last added playlist
     */
    public static final long[] getSongListForSmartPlaylist(final Context context,
                                                           final SmartPlaylistType type) {
        Cursor cursor = null;
        try {
            switch (type) {
                case LastAdded:
                    cursor = LastAddedLoader.makeLastAddedCursor(context);
                    break;
                case RecentlyPlayed:
                    cursor = TopTracksLoader.makeRecentTracksCursor(context);
                    break;
                case TopTracks:
                    cursor = TopTracksLoader.makeTopTracksCursor(context);
                    break;
            }
            return MusicUtils.getSongListForCursor(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
    }

    /**
     * Plays the smart playlist
     * @param context The {@link Context} to use
     * @param position the position to start playing from
     * @param type The Smart Playlist Type
     */
    public static void playSmartPlaylist(final Context context, final int position,
                                         final SmartPlaylistType type, final boolean shuffle) {
        final long[] list = getSongListForSmartPlaylist(context, type);
        MusicUtils.playAll(context, list, position, type.mId, IdType.Playlist, shuffle);
    }

    /**
     * Creates a sub menu used to add items to a new playlist or an existsing
     * one.
     *
     * @param context The {@link Context} to use.
     * @param groupId The group Id of the menu.
     * @param menu The {@link Menu} to add to.
     */
    public static void makePlaylistMenu(final Context context, final int groupId,
            final Menu menu) {
        menu.clear();
        menu.add(groupId, FragmentMenuItems.NEW_PLAYLIST, Menu.NONE, R.string.new_playlist);
        Cursor cursor = PlaylistLoader.makePlaylistCursor(context);
        if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                final Intent intent = new Intent();
                String name = cursor.getString(1);
                if (name != null) {
                    intent.putExtra("playlist", getIdForPlaylist(context, name));
                    menu.add(groupId, FragmentMenuItems.PLAYLIST_SELECTED, Menu.NONE,
                            name).setIntent(intent);
                }
                cursor.moveToNext();
            }
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }

    /**
     * Called when one of the lists should refresh or requery.
     */
    public static void refresh() {
        try {
            if (mService != null) {
                mService.refresh();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Called when one of playlists have changed
     */
    public static void playlistChanged() {
        try {
            if (mService != null) {
                mService.playlistChanged();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Seeks the current track to a desired position
     *
     * @param position The position to seek to
     */
    public static void seek(final long position) {
        if (mService != null) {
            try {
                mService.seek(position);
            } catch (final RemoteException ignored) {
            }
        }
    }

    /**
     * Seeks the current track to a desired relative position.  This can be used
     * to simulate fastforward and rewind
     *
     * @param deltaInMs The delta in ms to seek from the current position
     */
    public static void seekRelative(final long deltaInMs) {
        if (mService != null) {
            try {
                mService.seekRelative(deltaInMs);
            } catch (final RemoteException ignored) {
            } catch (final IllegalStateException ignored) {
                // Illegal State Exception message is empty so logging will actually throw an
                // exception.  We should come back and figure out why we get an exception in the
                // first place and make sure we understand it completely.  I will use
                // https://cyanogen.atlassian.net/browse/MUSIC-125 to track investigating this more
            }
        }
    }

    /**
     * @return The current position time of the track
     */
    public static final long position() {
        if (mService != null) {
            try {
                return mService.position();
            } catch (final RemoteException ignored) {
            } catch (final IllegalStateException ex) {
                // Illegal State Exception message is empty so logging will actually throw an
                // exception.  We should come back and figure out why we get an exception in the
                // first place and make sure we understand it completely.  I will use
                // https://cyanogen.atlassian.net/browse/MUSIC-125 to track investigating this more
            }
        }
        return 0;
    }

    /**
     * @return The total length of the current track
     */
    public static final long duration() {
        if (mService != null) {
            try {
                return mService.duration();
            } catch (final RemoteException ignored) {
            } catch (final IllegalStateException ignored) {
                // Illegal State Exception message is empty so logging will actually throw an
                // exception.  We should come back and figure out why we get an exception in the
                // first place and make sure we understand it completely.  I will use
                // https://cyanogen.atlassian.net/browse/MUSIC-125 to track investigating this more
            }
        }
        return 0;
    }

    /**
     * @param position The position to move the queue to
     */
    public static void setQueuePosition(final int position) {
        if (mService != null) {
            try {
                mService.setQueuePosition(position);
            } catch (final RemoteException ignored) {
            }
        }
    }

    /**
     * Clears the qeueue
     */
    public static void clearQueue() {
        try {
            mService.removeTracks(0, Integer.MAX_VALUE);
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Perminately deletes item(s) from the user's device
     *
     * @param context The {@link Context} to use.
     * @param list The item(s) to delete.
     */
    public static void deleteTracks(final Context context, final long[] list) {
        final String[] projection = new String[] {
                BaseColumns._ID, MediaColumns.DATA, AudioColumns.ALBUM_ID
        };
        final StringBuilder selection = new StringBuilder();
        selection.append(BaseColumns._ID + " IN (");
        for (int i = 0; i < list.length; i++) {
            selection.append(list[i]);
            if (i < list.length - 1) {
                selection.append(",");
            }
        }
        selection.append(")");
        final Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection.toString(),
                null, null);
        if (c != null) {
            // Step 1: Remove selected tracks from the current playlist, as well
            // as from the album art cache
            c.moveToFirst();
            while (!c.isAfterLast()) {
                // Remove from current playlist
                final long id = c.getLong(0);
                removeTrack(id);
                // Remove the track from the play count
                SongPlayCount.getInstance(context).removeItem(id);
                // Remove any items in the recents database
                RecentStore.getInstance(context).removeItem(id);
                c.moveToNext();
            }

            // Step 2: Remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    selection.toString(), null);

            // Step 3: Remove files from card
            c.moveToFirst();
            while (!c.isAfterLast()) {
                final String name = c.getString(1);
                final File f = new File(name);
                try { // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e("MusicUtils", "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (final SecurityException ex) {
                    c.moveToNext();
                }
            }
            c.close();
        }

        final String message = makeLabel(context, R.plurals.NNNtracksdeleted, list.length);

        Toast.makeText((Activity)context, message, Toast.LENGTH_SHORT).show();
        // We deleted a number of tracks, which could affect any number of
        // things
        // in the media content domain, so update everything.
        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
        // Notify the lists to update
        refresh();
    }

    /**
     * Simple function used to determine if the song/album year is invalid
     * @param year value to test
     * @return true if the app considers it valid
     */
    public static boolean isInvalidYear(int year) {
        return year < MIN_VALID_YEAR;
    }

    /**
     * A snippet is taken from MediaStore.Audio.keyFor method
     * This will take a name, removes things like "the", "an", etc
     * as well as special characters and return it
     * @param name the string to trim
     * @return the trimmed name
     */
    public static String getTrimmedName(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }

        name = name.trim().toLowerCase();
        if (name.startsWith("the ")) {
            name = name.substring(4);
        }
        if (name.startsWith("an ")) {
            name = name.substring(3);
        }
        if (name.startsWith("a ")) {
            name = name.substring(2);
        }
        if (name.endsWith(", the") || name.endsWith(",the") ||
                name.endsWith(", an") || name.endsWith(",an") ||
                name.endsWith(", a") || name.endsWith(",a")) {
            name = name.substring(0, name.lastIndexOf(','));
        }
        name = name.replaceAll("[\\[\\]\\(\\)\"'.,?!]", "").trim();

        return name;
    }

    /**
     * A snippet is taken from MediaStore.Audio.keyFor method
     * This will take a name, removes things like "the", "an", etc
     * as well as special characters, then find the localized label
     * @param name Name to get the label of
     * @return the localized label of the bucket that the name falls into
     */
    public static String getLocalizedBucketLetter(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }

        name = getTrimmedName(name);

        if (name.length() > 0) {
            return LocaleUtils.getInstance().getLabel(name);
        }

        return null;
    }

    /** @return true if a string is null, empty, or contains only whitespace */
    public static boolean isBlank(String s) {
        if(s == null) { return true; }
        if(s.isEmpty()) { return true; }
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if(!Character.isWhitespace(c)) { return false; }
        }
        return true;
    }

    /**
     * Removes the header image from the cache.
     */
    public static void removeFromCache(Activity activity, String key) {
        ImageFetcher imageFetcher = ApolloUtils.getImageFetcher(activity);
        imageFetcher.removeFromCache(key);
        // Give the disk cache a little time before requesting a new image.
        SystemClock.sleep(80);
    }

    /**
     * Removes image from cache so that the stock image is retrieved on reload
     */
    public static void selectOldPhoto(Activity activity, String key) {
        // First remove the old image
        removeFromCache(activity, key);
        MusicUtils.refresh();
    }

    /**
     *
     * @param sortOrder values are mostly derived from SortOrder.class or could also be any sql
     *                  order clause
     * @return
     */
    public static boolean isSortOrderDesending(String sortOrder) {
        return sortOrder.endsWith(" DESC");
    }

    /**
     * Takes a collection of items and builds a comma-separated list of them
     * @param items collection of items
     * @return comma-separted list of items
     */
    public static final <E> String buildCollectionAsString(Collection<E> items) {
        Iterator<E> iterator = items.iterator();
        StringBuilder str = new StringBuilder();
        if (iterator.hasNext()) {
            str.append(iterator.next());
            while (iterator.hasNext()) {
                str.append(",");
                str.append(iterator.next());
            }
        }

        return str.toString();
    }
}
