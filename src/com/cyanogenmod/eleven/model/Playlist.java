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

package com.cyanogenmod.eleven.model;

import android.text.TextUtils;

/**
 * A class that represents a playlist.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class Playlist {

    /**
     * The unique Id of the playlist
     */
    public long mPlaylistId;

    /**
     * The playlist name
     */
    public String mPlaylistName;

    /**
     * The number of songs in this playlist
     */
    public int mSongCount;

    /**
     * Constructor of <code>Genre</code>
     *
     * @param playlistId The Id of the playlist
     * @param playlistName The playlist name
     */
    public Playlist(final long playlistId, final String playlistName, final int songCount) {
        super();
        mPlaylistId = playlistId;
        mPlaylistName = playlistName;
        mSongCount = songCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) mPlaylistId;
        result = prime * result + (mPlaylistName == null ? 0 : mPlaylistName.hashCode());
        result = prime * result + mSongCount;
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Playlist other = (Playlist)obj;
        if (mPlaylistId != other.mPlaylistId) {
            return false;
        }

        if (mSongCount != other.mSongCount) {
            return false;
        }

        return TextUtils.equals(mPlaylistName, other.mPlaylistName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return mPlaylistName;
    }

    /**
     * @return true if this is a smart playlist
     */
    public boolean isSmartPlaylist() {
        return mPlaylistId < 0;
    }
}
