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

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ICacheListener;
import com.cyanogenmod.eleven.cache.ImageCache;
import com.cyanogenmod.eleven.model.AlbumArtistDetails;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.widgets.SquareImageView;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * A {@link android.support.v4.app.FragmentStatePagerAdapter} class for swiping between album art
 */
public class AlbumArtPagerAdapter extends FragmentStatePagerAdapter {
    private static boolean DEBUG = false;
    private static final String TAG = AlbumArtPagerAdapter.class.getSimpleName();

    public static final long NO_TRACK_ID = -1;
    private static final int MAX_ALBUM_ARTIST_SIZE = 10;

    // This helps with flickering and jumping and reloading the same tracks
    private final static LinkedList<AlbumArtistDetails> sCacheAlbumArtistDetails = new LinkedList<AlbumArtistDetails>();

    /**
     * Adds the album artist details to the cache
     * @param details the AlbumArtistDetails to add
     */
    public static void addAlbumArtistDetails(AlbumArtistDetails details) {
        if (getAlbumArtistDetails(details.mAudioId) == null) {
            sCacheAlbumArtistDetails.add(details);
            if (sCacheAlbumArtistDetails.size() > MAX_ALBUM_ARTIST_SIZE) {
                sCacheAlbumArtistDetails.remove();
            }
        }
    }

    /**
     * Gets the album artist details for the audio track.  If it exists, it re-inserts the item
     * to the end of the queue so it is considered the 'freshest' and stays longer
     * @param audioId the audio track to look for
     * @return the details of the album artist
     */
    public static AlbumArtistDetails getAlbumArtistDetails(long audioId) {
        for (Iterator<AlbumArtistDetails> i = sCacheAlbumArtistDetails.descendingIterator(); i.hasNext();) {
            final AlbumArtistDetails entry = i.next();
            if (entry.mAudioId == audioId) {
                // remove it from the stack to re-add to the top
                i.remove();
                sCacheAlbumArtistDetails.add(entry);
                return entry;
            }
        }

        return null;
    }

    // the length of the playlist
    private int mPlaylistLen = 0;

    public AlbumArtPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(final int position) {
        long trackID = getTrackId(position);
        return AlbumArtFragment.newInstance(trackID);
    }

    @Override
    public int getCount() {
        return mPlaylistLen;
    }

    public void setPlaylistLength(final int len) {
        mPlaylistLen = len;
        notifyDataSetChanged();
    }

    /**
     * Gets the track id for the item at position
     * @param position position of the item of the queue
     * @return track id of the item at position or NO_TRACK_ID if unknown
     */
    private long getTrackId(int position) {
        if (MusicUtils.getRepeatMode() == MusicPlaybackService.REPEAT_CURRENT) {
            // if we are only playing one song, return the current audio id
            return MusicUtils.getCurrentAudioId();
        } else if (MusicUtils.getShuffleMode() == MusicPlaybackService.SHUFFLE_NONE) {
            // if we aren't shuffling, just return based on the queue position
            // add a check for empty queue
            return MusicUtils.getQueueItemAtPosition(position);
        } else {
            // if we are shuffling, there is no 'queue' going forward per say
            // because it is dynamically generated.  In that case we can only look
            // at the history and up to the very next track.  When we come back to this
            // after the demo, we should redo that queue logic to be able to give us
            // tracks going forward

            // how far into the history we are
            int positionOffset = MusicUtils.getQueueHistorySize();

            if (position - positionOffset == 0) { // current track
                return MusicUtils.getCurrentAudioId();
            } else if (position - positionOffset == 1) { // next track
                return MusicUtils.getNextAudioId();
            } else if (position < positionOffset) {
                int queuePosition = MusicUtils.getQueueHistoryPosition(position);
                if (position >= 0) {
                    return MusicUtils.getQueueItemAtPosition(queuePosition);
                }
            }
        }

        // fallback case
        return NO_TRACK_ID;
    }

    /**
     * The fragments to be displayed inside this adapter.  This wraps the album art
     * and handles loading the album art for a given audio id
     */
    public static class AlbumArtFragment extends Fragment implements ICacheListener {
        private static final String ID = "com.cyanogenmod.eleven.adapters.AlbumArtPagerAdapter.AlbumArtFragment.ID";

        private View mRootView;
        private AlbumArtistLoader mTask;
        private SquareImageView mImageView;
        private long mAudioId = NO_TRACK_ID;

        public static AlbumArtFragment newInstance(final long trackId) {
            AlbumArtFragment frag = new AlbumArtFragment();
            final Bundle args = new Bundle();
            args.putLong(ID, trackId);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mAudioId = getArguments().getLong(ID, NO_TRACK_ID);
            ImageCache.getInstance(getActivity()).addCacheListener(this);
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.album_art_fragment, null);
            return mRootView;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            ImageCache.getInstance(getActivity()).removeCacheListener(this);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();

            // if we are destroying our view, cancel our task and null it
            if (mTask != null) {
                mTask.cancel(true);
                mTask = null;
            }
        }

        @Override
        public void onActivityCreated(final Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            mImageView = (SquareImageView)mRootView.findViewById(R.id.audio_player_album_art);
            loadImageAsync();
        }

        /**
         * Loads the image asynchronously
         */
        private void loadImageAsync() {
            // if we have no track id, quit
            if (mAudioId == NO_TRACK_ID) {
                return;
            }

            // try loading from the cache
            AlbumArtistDetails details = getAlbumArtistDetails(mAudioId);
            if (details != null) {
                loadImageAsync(details);
            } else {
                // Cancel any previous tasks
                if (mTask != null) {
                    mTask.cancel(true);
                    mTask = null;
                }

                mTask = new AlbumArtistLoader(this, getActivity());
                ApolloUtils.execute(false, mTask, mAudioId);
            }

        }

        /**
         * Loads the image asynchronously
         * @param details details of the image to load
         */
        private void loadImageAsync(AlbumArtistDetails details) {
            // load the actual image
            ApolloUtils.getImageFetcher(getActivity()).loadAlbumImage(
                    details.mArtistName,
                    details.mAlbumName,
                    details.mAlbumId,
                    mImageView
            );
        }

        @Override
        public void onCacheUnpaused() {
            loadImageAsync();
        }
    }

    /**
     * This looks up the album and artist details for a track
     */
    private static class AlbumArtistLoader extends AsyncTask<Long, Void, AlbumArtistDetails> {
        private Context mContext;
        private AlbumArtFragment mFragment;

        public AlbumArtistLoader(final AlbumArtFragment albumArtFragment, final Context context) {
            mContext = context;
            mFragment = albumArtFragment;
        }

        @Override
        protected AlbumArtistDetails doInBackground(final Long... params) {
            long id = params[0];
            return MusicUtils.getAlbumArtDetails(mContext, id);
        }

        @Override
        protected void onPostExecute(final AlbumArtistDetails result) {
            if (result != null) {
                if (DEBUG) {
                    Log.d(TAG, "[" + mFragment.mAudioId + "] Loading image: "
                            + result.mAlbumId + ","
                            + result.mAlbumName + ","
                            + result.mArtistName);
                }

                AlbumArtPagerAdapter.addAlbumArtistDetails(result);
                mFragment.loadImageAsync(result);
            } else if (DEBUG) {
                Log.d(TAG, "No Image found for audioId: " + mFragment.mAudioId);
            }
        }
    }
}
