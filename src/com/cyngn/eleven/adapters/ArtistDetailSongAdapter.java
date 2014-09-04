package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.ArtistSongLoader;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;

import java.util.Collections;
import java.util.List;

public class ArtistDetailSongAdapter extends BaseAdapter
implements LoaderCallbacks<List<Song>> {
    private final Activity mActivity;
    private final ImageFetcher mImageFetcher;
    private final LayoutInflater mInflater;
    private List<Song> mSongs = Collections.emptyList();

    public ArtistDetailSongAdapter(final Activity activity) {
        mActivity = activity;
        mImageFetcher = ApolloUtils.getImageFetcher(activity);
        mInflater = LayoutInflater.from(activity);
    }

    @Override
    public int getCount() { return mSongs.size(); }

    @Override
    public Song getItem(int pos) { return mSongs.get(pos); }

    @Override
    public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = mInflater.inflate(R.layout.artist_detail_song, parent, false);
            convertView.setTag(new Holder(convertView));
        }

        Holder h = (Holder)convertView.getTag();
        Song s = getItem(pos);
        h.title.setText(s.mSongName);
        h.album.setText(s.mAlbumName);

        if (s.mAlbumId >= 0) {
            mImageFetcher.loadAlbumImage(s.mArtistName, s.mAlbumName, s.mAlbumId, h.art);
        }

        addAction(convertView, s);

        return convertView;
    }

    private void addAction(View view, final Song song) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicUtils.playAll(mActivity, new long[] { song.mSongId }, -1, false);
            }
        });
    }

    @Override // LoaderCallbacks
    public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
        return new ArtistSongLoader(mActivity, args.getLong(Config.ID));
    }

    @Override // LoaderCallbacks
    public void onLoadFinished(Loader<List<Song>> loader, List<Song> songs) {
        if (songs.isEmpty()) { return; }
        mSongs = songs;
        notifyDataSetChanged();
    }

    @Override // LoaderCallbacks
    public void onLoaderReset(Loader<List<Song>> loader) {
        mSongs = Collections.emptyList();
        notifyDataSetChanged();
        mImageFetcher.flush();
    }

    private class Holder {
        ImageView art;
        TextView title;
        TextView album;

        Holder(View root) {
            art = (ImageView)root.findViewById(R.id.album_art);
            title = (TextView)root.findViewById(R.id.title);
            album = (TextView)root.findViewById(R.id.album);
        }
    }
}