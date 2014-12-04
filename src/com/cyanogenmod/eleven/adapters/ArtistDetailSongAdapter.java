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
package com.cyanogenmod.eleven.adapters;

import android.app.Activity;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.loaders.SongLoader;
import com.cyanogenmod.eleven.model.Song;

import java.util.List;

public abstract class ArtistDetailSongAdapter extends DetailSongAdapter {
    public ArtistDetailSongAdapter(Activity activity) {
        super(activity);
    }

    protected int rowLayoutId() { return R.layout.artist_detail_song; }

    protected Config.IdType getSourceType() {
        return Config.IdType.Artist;
    }

    @Override // LoaderCallbacks
    public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
        onLoading();
        setSourceId(args.getLong(Config.ID));
        final String selection = MediaStore.Audio.AudioColumns.ARTIST_ID + "=" + getSourceId();
        return new SongLoader(mActivity, selection);
    }

    protected Holder newHolder(View root, ImageFetcher fetcher) {
        return new ArtistHolder(root, fetcher);
    }

    private static class ArtistHolder extends Holder {
        ImageView art;
        TextView album;

        protected ArtistHolder(View root, ImageFetcher fetcher) {
            super(root, fetcher);
            art = (ImageView)root.findViewById(R.id.album_art);
            album = (TextView)root.findViewById(R.id.album);
        }

        protected void update(Song song) {
            title.setText(song.mSongName);
            album.setText(song.mAlbumName);

            if (song.mAlbumId >= 0) {
                fetcher.loadAlbumImage(song.mArtistName, song.mAlbumName, song.mAlbumId, art);
            }
        }
    }
}