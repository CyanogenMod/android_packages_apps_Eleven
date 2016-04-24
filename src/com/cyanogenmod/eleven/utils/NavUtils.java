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
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.activities.HomeActivity;
import com.cyanogenmod.eleven.ui.activities.SearchActivity;
import com.cyanogenmod.eleven.ui.activities.SettingsActivity;

import java.util.List;

/**
 * Various navigation helpers.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class NavUtils {

    /**
     * Opens the profile of an artist.
     *
     * @param context The {@link Activity} to use.
     * @param artistName The name of the artist
     */
    public static void openArtistProfile(final Activity context, final String artistName) {
        // Create a new bundle to transfer the artist info
        final Bundle bundle = new Bundle();
        bundle.putLong(Config.ID, MusicUtils.getIdForArtist(context, artistName));
        bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Artists.CONTENT_TYPE);
        bundle.putString(Config.ARTIST_NAME, artistName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(context, HomeActivity.class);
        intent.setAction(HomeActivity.ACTION_VIEW_ARTIST_DETAILS);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    /**
     * Opens the profile of an album.
     *
     * @param context The {@link Activity} to use.
     * @param albumName The name of the album
     * @param artistName The name of the album artist
     * @param albumId The id of the album
     */
    public static void openAlbumProfile(final Activity context,
            final String albumName, final String artistName, final long albumId) {

        // Create a new bundle to transfer the album info
        final Bundle bundle = new Bundle();
        bundle.putString(Config.ALBUM_YEAR, MusicUtils.getReleaseDateForAlbum(context, albumId));
        bundle.putInt(Config.SONG_COUNT, MusicUtils.getSongCountForAlbumInt(context, albumId));
        bundle.putString(Config.ARTIST_NAME, artistName);
        bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Albums.CONTENT_TYPE);
        bundle.putLong(Config.ID, albumId);
        bundle.putString(Config.NAME, albumName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(context, HomeActivity.class);
        intent.setAction(HomeActivity.ACTION_VIEW_ALBUM_DETAILS);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    public static void openSmartPlaylist(final Activity context, final Config.SmartPlaylistType type) {
        // Create the intent to launch the profile activity
        final Intent intent = new Intent(context, HomeActivity.class);
        intent.setAction(HomeActivity.ACTION_VIEW_SMART_PLAYLIST);
        intent.putExtra(Config.SMART_PLAYLIST_TYPE, type.mId);
        context.startActivity(intent);
    }

    /**
     * Opens the playlist view
     *
     * @param context The {@link Activity} to use.
     * @param playlistId the id of the playlist
     * @param playlistName the playlist name
     */
    public static void openPlaylist(final Activity context, final long playlistId,
                                    final String playlistName) {
        final Bundle bundle = new Bundle();
        bundle.putLong(Config.ID, playlistId);
        bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Playlists.CONTENT_TYPE);
        bundle.putString(Config.NAME, playlistName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(context, HomeActivity.class);
        intent.setAction(HomeActivity.ACTION_VIEW_PLAYLIST_DETAILS);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    /**
     * @return the intent to launch the effects panel/dsp manager
     */
    private static Intent createEffectsIntent() {
        final Intent effects = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        effects.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, MusicUtils.getAudioSessionId());
        return effects;
    }

    /**
     * Opens the sound effects panel or DSP manager in CM
     *
     * @param context The {@link Activity} to use.
     * @param requestCode The request code passed into startActivityForResult
     */
    public static void openEffectsPanel(final Activity context, final int requestCode) {
        try {
            // The google MusicFX apps need to be started using startActivityForResult
            context.startActivityForResult(createEffectsIntent(), requestCode);
        } catch (final ActivityNotFoundException notFound) {
            Toast.makeText(context, context.getString(R.string.no_effects_for_you),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @return true if there is an effects panel/DSK Manager
     */
    public static boolean hasEffectsPanel(final Activity activity) {
        final PackageManager packageManager = activity.getPackageManager();
        return packageManager.resolveActivity(createEffectsIntent(),
                PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    /**
     * Opens to {@link SettingsActivity}.
     *
     * @param activity The {@link Activity} to use.
     */
    public static void openSettings(final Activity activity) {
        final Intent intent = new Intent(activity, SettingsActivity.class);
        activity.startActivity(intent);
    }

    /**
     * Opens to {@link com.cyanogenmod.eleven.ui.activities.SearchActivity}.
     *
     * @param activity The {@link Activity} to use.
     * @param query The search query.
     */
    public static void openSearch(final Activity activity, final String query) {
        final Bundle bundle = new Bundle();
        final Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra(SearchManager.QUERY, query);
        intent.putExtras(bundle);
        activity.startActivity(intent);
    }

    /**
     * Opens to {@link com.cyanogenmod.eleven.ui.activities.HomeActivity}.
     *
     * @param activity The {@link Activity} to use.
     */
    public static void goHome(final Activity activity, final int browseIndex) {
        final Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(HomeActivity.EXTRA_BROWSE_PAGE_IDX, browseIndex);
        activity.startActivity(intent);
    }
}
