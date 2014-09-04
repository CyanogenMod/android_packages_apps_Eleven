package com.cyngn.eleven.adapters;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.loaders.ArtistAlbumLoader;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.NavUtils;

import java.util.Collections;
import java.util.List;

public class ArtistDetailAlbumAdapter
extends RecyclerView.Adapter<ArtistDetailAlbumAdapter.ViewHolder>
implements LoaderCallbacks<List<Album>> {
    private static final int TYPE_FIRST = 1;
    private static final int TYPE_MIDDLE = 2;
    private static final int TYPE_LAST = 3;

    private final Activity mActivity;
    private final ImageFetcher mImageFetcher;
    private final LayoutInflater mInflater;
    private List<Album> mAlbums = Collections.emptyList();

    public ArtistDetailAlbumAdapter(final Activity activity) {
        mActivity = activity;
        mImageFetcher = ApolloUtils.getImageFetcher(activity);
        mInflater = LayoutInflater.from(activity);
    }

    @Override
    public int getItemViewType(int position) {
        // use view types to distinguish first and last elements
        // so they can be given special treatment for layout
        if(position == 0) { return TYPE_FIRST; }
        else if(position == getItemCount()-1) { return TYPE_LAST; }
        else return TYPE_MIDDLE;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mInflater.inflate(R.layout.artist_detail_album, parent, false);
        // add extra margin to the first and last elements
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
        if     (viewType == TYPE_FIRST) { params.leftMargin = 30; }
        else if(viewType == TYPE_LAST)  { params.rightMargin = 30; }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Album a = mAlbums.get(position);
        holder.title.setText(a.mAlbumName);
        holder.year.setText(a.mYear);
        mImageFetcher.loadAlbumImage(
            a.mArtistName, a.mAlbumName, a.mAlbumId, holder.art);
        addAction(holder.itemView, a);
    }

    private void addAction(View view, final Album album) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavUtils.openAlbumProfile(
                    mActivity, album.mAlbumName, album.mArtistName, album.mAlbumId);
            }
        });
    }

    @Override
    public int getItemCount() { return mAlbums.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView art;
        public TextView title;
        public TextView year;
        public ViewHolder(View root) {
            super(root);
            art = (ImageView)root.findViewById(R.id.album_art);
            title = (TextView)root.findViewById(R.id.title);
            year = (TextView)root.findViewById(R.id.year);
        }
    }

    @Override // LoaderCallbacks
    public Loader<List<Album>> onCreateLoader(int id, Bundle args) {
        return new ArtistAlbumLoader(mActivity, args.getLong(Config.ID));
    }

    @Override // LoaderCallbacks
    public void onLoadFinished(Loader<List<Album>> loader, List<Album> albums) {
        if (albums.isEmpty()) { return; }
        mAlbums = albums;
        notifyDataSetChanged();
    }

    @Override // LoaderCallbacks
    public void onLoaderReset(Loader<List<Album>> loader) {
        mAlbums = Collections.emptyList();
        notifyDataSetChanged();
        mImageFetcher.flush();
    }
}