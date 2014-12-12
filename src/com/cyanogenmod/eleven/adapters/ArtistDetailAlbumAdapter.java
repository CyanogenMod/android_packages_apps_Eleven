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
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.loaders.AlbumLoader;
import com.cyanogenmod.eleven.model.Album;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.NavUtils;
import com.cyanogenmod.eleven.widgets.IPopupMenuCallback;
import com.cyanogenmod.eleven.widgets.PopupMenuButton;

import java.util.Collections;
import java.util.List;

public class ArtistDetailAlbumAdapter
extends RecyclerView.Adapter<ArtistDetailAlbumAdapter.ViewHolder>
implements LoaderCallbacks<List<Album>>, IPopupMenuCallback {
    private static final int TYPE_FIRST = 1;
    private static final int TYPE_MIDDLE = 2;
    private static final int TYPE_LAST = 3;

    private final Activity mActivity;
    private final ImageFetcher mImageFetcher;
    private final LayoutInflater mInflater;
    private List<Album> mAlbums = Collections.emptyList();
    private IListener mListener;
    private int mListMargin;

    public ArtistDetailAlbumAdapter(final Activity activity) {
        mActivity = activity;
        mImageFetcher = ApolloUtils.getImageFetcher(activity);
        mInflater = LayoutInflater.from(activity);
        mListMargin = activity.getResources().
            getDimensionPixelSize(R.dimen.list_item_general_margin);
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
        if     (viewType == TYPE_FIRST) { params.leftMargin = mListMargin; }
        else if(viewType == TYPE_LAST)  { params.rightMargin = mListMargin; }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Album a = mAlbums.get(position);
        holder.title.setText(a.mAlbumName);
        holder.year.setText(a.mYear);
        mImageFetcher.loadAlbumImage(
            a.mArtistName, a.mAlbumName, a.mAlbumId, holder.art);
        holder.popupbutton.setPopupMenuClickedListener(mListener);
        holder.popupbutton.setPosition(position);
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

    public Album getItem(int position) {
        return mAlbums.get(position);
    }

    @Override
    public void setPopupMenuClickedListener(IListener listener) {
        mListener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView art;
        public TextView title;
        public TextView year;
        public PopupMenuButton popupbutton;
        public ViewHolder(View root) {
            super(root);
            art = (ImageView)root.findViewById(R.id.album_art);
            title = (TextView)root.findViewById(R.id.title);
            year = (TextView)root.findViewById(R.id.year);
            popupbutton = (PopupMenuButton)root.findViewById(R.id.overflow);
        }
    }

    @Override // LoaderCallbacks
    public Loader<List<Album>> onCreateLoader(int id, Bundle args) {
        return new AlbumLoader(mActivity, args.getLong(Config.ID));
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