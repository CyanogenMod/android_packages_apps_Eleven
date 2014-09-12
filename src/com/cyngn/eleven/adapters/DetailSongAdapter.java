package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;

import java.util.Collections;
import java.util.List;

public abstract class DetailSongAdapter extends BaseAdapter
implements LoaderCallbacks<List<Song>> {
    protected final Activity mActivity;
    private final ImageFetcher mImageFetcher;
    private final LayoutInflater mInflater;
    private List<Song> mSongs = Collections.emptyList();

    public DetailSongAdapter(final Activity activity) {
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
            convertView = mInflater.inflate(rowLayoutId(), parent, false);
            convertView.setTag(newHolder(convertView, mImageFetcher));
        }

        Holder holder = (Holder)convertView.getTag();

        Song song = getItem(pos);
        holder.update(song);
        addAction(convertView, pos);

        return convertView;
    }

    protected abstract int rowLayoutId();

    private void addAction(View view, final int position) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // play clicked song and enqueue all following songs
                long[] toPlay = new long[getCount() - position];
                for(int i = 0; i < toPlay.length; i++) {
                    toPlay[i] = getItem(position + i).mSongId;
                }
                MusicUtils.playAll(mActivity, toPlay, -1, false);
            }
        });
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

    protected abstract Holder newHolder(View root, ImageFetcher fetcher);

    protected static abstract class Holder {
        protected ImageFetcher fetcher;
        protected TextView title;

        protected Holder(View root, ImageFetcher fetcher) {
            this.fetcher = fetcher;
            title = (TextView)root.findViewById(R.id.title);
        }

        protected abstract void update(Song song);
    }
}