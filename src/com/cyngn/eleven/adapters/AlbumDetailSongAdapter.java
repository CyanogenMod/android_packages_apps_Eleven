package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.AlbumSongLoader;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.ui.fragments.AlbumDetailFragment;
import com.cyngn.eleven.utils.MusicUtils;

import java.util.List;

public class AlbumDetailSongAdapter extends DetailSongAdapter {
    private AlbumDetailFragment mFragment;

    public AlbumDetailSongAdapter(Activity activity, AlbumDetailFragment fragment) {
        super(activity);
        mFragment = fragment;
    }

    protected int rowLayoutId() { return R.layout.album_detail_song; }

    @Override // LoaderCallbacks
    public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
        return new AlbumSongLoader(mActivity, args.getLong(Config.ID));
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