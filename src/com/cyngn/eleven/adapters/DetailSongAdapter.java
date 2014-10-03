package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.model.Song;
import com.cyngn.eleven.service.MusicPlaybackTrack;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.PopupMenuButton;

import java.util.Collections;
import java.util.List;

public abstract class DetailSongAdapter extends BaseAdapter
        implements LoaderCallbacks<List<Song>>, OnItemClickListener, IPopupMenuCallback {
    protected final Activity mActivity;
    private final ImageFetcher mImageFetcher;
    private final LayoutInflater mInflater;
    private List<Song> mSongs = Collections.emptyList();
    private IListener mListener;
    private long mSourceId = -1;
    private MusicPlaybackTrack mCurrentlyPlayingTrack;

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

    protected long getSourceId() { return mSourceId; }
    protected void setSourceId(long id) { mSourceId = id; }

    public void setCurrentlyPlayingTrack(MusicPlaybackTrack currentTrack) {
        if (mCurrentlyPlayingTrack == null || !mCurrentlyPlayingTrack.equals(currentTrack)) {
            mCurrentlyPlayingTrack = currentTrack;
            notifyDataSetChanged();
        }
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = mInflater.inflate(rowLayoutId(), parent, false);
            convertView.setTag(newHolder(convertView, mImageFetcher));
        }

        Holder holder = (Holder)convertView.getTag();

        Song song = getItem(pos);
        holder.update(song);
        holder.popupMenuButton.setPopupMenuClickedListener(mListener);
        holder.popupMenuButton.setPosition(pos);

        if (mCurrentlyPlayingTrack != null
                && mCurrentlyPlayingTrack.mSourceId == getSourceId()
                && mCurrentlyPlayingTrack.mSourceType == getSourceType()
                && mCurrentlyPlayingTrack.mId == song.mSongId) {
            holder.playIcon.setVisibility(View.VISIBLE);
        } else {
            holder.playIcon.setVisibility(View.GONE);
        }

        return convertView;
    }

    protected abstract int rowLayoutId();
    protected abstract void onLoading();
    protected abstract void onNoResults();
    protected abstract Config.IdType getSourceType();

    @Override // OnItemClickListener
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        // id is in this case the index in the underlying collection,
        // which is what we are interested in here -- so use as position
        int position = (int)id;
        // play clicked song and enqueue all following songs
        long[] toPlay = new long[getCount() - position];
        for(int i = 0; i < toPlay.length; i++) {
            toPlay[i] = getItem(position + i).mSongId;
        }
        MusicUtils.playAll(mActivity, toPlay, -1, getSourceId(), getSourceType(), false);
    }

    @Override // LoaderCallbacks
    public void onLoadFinished(Loader<List<Song>> loader, List<Song> songs) {
        if (songs.isEmpty()) {
            onNoResults();
            return;
        }
        mSongs = songs;
        notifyDataSetChanged();
    }

    @Override // LoaderCallbacks
    public void onLoaderReset(Loader<List<Song>> loader) {
        mSongs = Collections.emptyList();
        notifyDataSetChanged();
        mImageFetcher.flush();
    }

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }

    protected abstract Holder newHolder(View root, ImageFetcher fetcher);

    protected static abstract class Holder {
        protected ImageFetcher fetcher;
        protected TextView title;
        protected PopupMenuButton popupMenuButton;
        protected ImageView playIcon;

        protected Holder(View root, ImageFetcher fetcher) {
            this.fetcher = fetcher;
            title = (TextView)root.findViewById(R.id.title);
            popupMenuButton = (PopupMenuButton)root.findViewById(R.id.overflow);
            playIcon = (ImageView)root.findViewById(R.id.now_playing);
        }

        protected abstract void update(Song song);
    }
}