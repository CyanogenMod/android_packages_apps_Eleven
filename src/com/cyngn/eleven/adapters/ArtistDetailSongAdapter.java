package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.ArtistSongLoader;
import com.cyngn.eleven.model.Song;

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
        return new ArtistSongLoader(mActivity, getSourceId());
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