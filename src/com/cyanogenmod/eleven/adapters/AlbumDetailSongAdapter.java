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
import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.loaders.AlbumSongLoader;
import com.cyanogenmod.eleven.model.Song;
import com.cyanogenmod.eleven.ui.fragments.AlbumDetailFragment;
import com.cyanogenmod.eleven.utils.MusicUtils;

import java.util.List;

public abstract class AlbumDetailSongAdapter extends DetailSongAdapter {
    private AlbumDetailFragment mFragment;

    public AlbumDetailSongAdapter(Activity activity, AlbumDetailFragment fragment) {
        super(activity);
        mFragment = fragment;
    }

    protected int rowLayoutId() { return R.layout.album_detail_song; }

    protected Config.IdType getSourceType() {
        return Config.IdType.Album;
    }

    @Override // LoaderCallbacks
    public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
        onLoading();
        setSourceId(args.getLong(Config.ID));
        return new AlbumSongLoader(mActivity, getSourceId());
    }

    @Override // LoaderCallbacks
    public void onLoadFinished(Loader<List<Song>> loader, List<Song> songs) {
        super.onLoadFinished(loader, songs);
        mFragment.update(songs);
    }

    protected Holder newHolder(View root, ImageFetcher fetcher) {
        return new AlbumHolder(root, fetcher, mActivity);
    }

    private static class AlbumHolder extends Holder {
        TextView duration;
        Context context;

        protected AlbumHolder(View root, ImageFetcher fetcher, Context context) {
            super(root, fetcher);
            this.context = context;
            duration = (TextView)root.findViewById(R.id.duration);
        }

        protected void update(Song song) {
            title.setText(song.mSongName);
            duration.setText(MusicUtils.makeShortTimeString(context, song.mDuration));
        }
    }
}