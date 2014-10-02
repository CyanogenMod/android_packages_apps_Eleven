/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyngn.eleven.ui.fragments;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.cyngn.eleven.Config.SmartPlaylistType;
import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.PagerAdapter;
import com.cyngn.eleven.adapters.PlaylistAdapter;
import com.cyngn.eleven.loaders.PlaylistLoader;
import com.cyngn.eleven.model.Playlist;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.ui.activities.BaseActivity;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserFragment;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.LoadingEmptyContainer;

import java.util.List;

/**
 * This class is used to display all of the playlists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistFragment extends MusicBrowserFragment implements
        LoaderCallbacks<List<Playlist>>,
        OnItemClickListener, MusicStateListener {

    /**
     * The adapter for the list
     */
    private PlaylistAdapter mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * This holds the loading progress bar as well as the no results message
     */
    private LoadingEmptyContainer mLoadingEmptyContainer;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PlaylistFragment() {
    }

    @Override
    public int getLoaderId() {
        return PagerAdapter.MusicFragments.PLAYLIST.ordinal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPopupMenuHelper = new PopupMenuHelper(getActivity(), getFragmentManager()) {
            /**
             * Represents a playlist
             */
            private Playlist mPlaylist;

            @Override
            protected PopupMenuType onPreparePopupMenu(int position) {
                // Create a new playlist
                mPlaylist = mAdapter.getItem(position);

                return mPlaylist.isSmartPlaylist() ?
                        PopupMenuType.SmartPlaylist : PopupMenuType.Playlist;
            }

            @Override
            protected long[] getIdList() {
                if (mPlaylist.isSmartPlaylist()) {
                    return MusicUtils.getSongListForSmartPlaylist(getActivity(),
                            SmartPlaylistType.getTypeById(mPlaylist.mPlaylistId));
                } else {
                    return MusicUtils.getSongListForPlaylist(getActivity(),
                            mPlaylist.mPlaylistId);
                }
            }

            @Override
            protected void onDeleteClicked() {
                buildDeleteDialog(getId(), mPlaylist.mPlaylistName).show();
            }

            @Override
            protected long getId() {
                return mPlaylist.mPlaylistId;
            }
        };

        // Create the adpater
        mAdapter = new PlaylistAdapter(getActivity());
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        // Initialize the list
        mListView = (ListView)rootView.findViewById(R.id.list_base);
        // Set the data behind the grid
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Play the selected song
        mListView.setOnItemClickListener(this);
        // Setup the loading and empty state
        mLoadingEmptyContainer =
                (LoadingEmptyContainer)rootView.findViewById(R.id.loading_empty_container);
        mListView.setEmptyView(mLoadingEmptyContainer);

        // Register the music status listener
        ((BaseActivity)getActivity()).setMusicStateListenerListener(this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ((BaseActivity)getActivity()).removeMusicStateListenerListener(this);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        initLoader(null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        Playlist playlist = mAdapter.getItem(position);

        SmartPlaylistType playlistType = SmartPlaylistType.getTypeById(playlist.mPlaylistId);
        if (playlistType != null) {
            NavUtils.openSmartPlaylist(getActivity(), playlistType);
        } else {
            NavUtils.openPlaylist(getActivity(), playlist.mPlaylistId, playlist.mPlaylistName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Playlist>> onCreateLoader(final int id, final Bundle args) {
        // show the loading progress bar
        mLoadingEmptyContainer.showLoading();
        return new PlaylistLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Playlist>> loader, final List<Playlist> data) {
        // Check for any errors
        if (data.isEmpty()) {
            mLoadingEmptyContainer.showNoResults();
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Add the data to the adpater
        for (final Playlist playlist : data) {
            mAdapter.add(playlist);
        }
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Playlist>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        restartLoader(null, this);
    }

    @Override
    public void onPlaylistChanged() {
        restartLoader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // Nothing to do
    }

    /**
     * Create a new {@link AlertDialog} for easy playlist deletion
     * 
     * @param playlistName The title of the playlist being deleted
     * @param playlistId The ID of the playlist being deleted
     * @return A new {@link AlertDialog} used to delete playlists
     */
    private final AlertDialog buildDeleteDialog(final long playlistId, final String playlistName) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.delete_dialog_title, playlistName))
                .setPositiveButton(R.string.context_menu_delete, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Uri mUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                playlistId);
                        getActivity().getContentResolver().delete(mUri, null, null);
                        MusicUtils.refresh();
                    }
                }).setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).setMessage(R.string.cannot_be_undone).create();
    }

}
