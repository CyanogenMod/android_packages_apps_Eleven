/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cyanogenmod.eleven.ui.activities;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.ui.fragments.AlbumDetailFragment;
import com.cyanogenmod.eleven.ui.fragments.ArtistDetailFragment;
import com.cyanogenmod.eleven.ui.fragments.AudioPlayerFragment;
import com.cyanogenmod.eleven.ui.fragments.IChildFragment;
import com.cyanogenmod.eleven.ui.fragments.ISetupActionBar;
import com.cyanogenmod.eleven.ui.fragments.PlaylistDetailFragment;
import com.cyanogenmod.eleven.ui.fragments.RecentFragment;
import com.cyanogenmod.eleven.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.cyanogenmod.eleven.ui.fragments.profile.LastAddedFragment;
import com.cyanogenmod.eleven.ui.fragments.profile.TopTracksFragment;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.BitmapWithColors;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.NavUtils;

public class HomeActivity extends SlidingPanelActivity implements
        FragmentManager.OnBackStackChangedListener {
    private static final String TAG = "HomeActivity";
    private static final String ACTION_PREFIX = HomeActivity.class.getName();
    public static final String ACTION_VIEW_ARTIST_DETAILS = ACTION_PREFIX + ".view.ArtistDetails";
    public static final String ACTION_VIEW_ALBUM_DETAILS = ACTION_PREFIX + ".view.AlbumDetails";
    public static final String ACTION_VIEW_PLAYLIST_DETAILS = ACTION_PREFIX + ".view.PlaylistDetails";
    public static final String ACTION_VIEW_SMART_PLAYLIST = ACTION_PREFIX + ".view.SmartPlaylist";
    public static final String EXTRA_BROWSE_PAGE_IDX = "BrowsePageIndex";

    private static final String STATE_KEY_BASE_FRAGMENT = "BaseFragment";

    private static final int NEW_PHOTO = 1;
    public static final int EQUALIZER = 2;

    private String mKey;
    private boolean mLoadedBaseFragment = false;
    private boolean mHasPendingPlaybackRequest = false;
    private Handler mHandler = new Handler();
    private boolean mBrowsePanelActive = true;

    /**
     * Used by the up action to determine how to handle this
     */
    protected boolean mTopLevelActivity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // if we've been launched by an intent, parse it
        Intent launchIntent = getIntent();
        boolean intentHandled = false;
        if (launchIntent != null) {
            intentHandled = parseIntentForFragment(launchIntent);
        }

        // if the intent didn't cause us to load a fragment, load the music browse one
        if (savedInstanceState == null && !mLoadedBaseFragment) {
            final MusicBrowserPhoneFragment fragment = new MusicBrowserPhoneFragment();
            if (launchIntent != null) {
                fragment.setDefaultPageIdx(launchIntent.getIntExtra(EXTRA_BROWSE_PAGE_IDX,
                        MusicBrowserPhoneFragment.INVALID_PAGE_INDEX));
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.activity_base_content, fragment)
                    .commit();

            mLoadedBaseFragment = true;
            mTopLevelActivity = true;
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // if we are resuming from a saved instance state
        if (savedInstanceState != null) {
            // track which fragments are loaded and if this is the top level activity
            mTopLevelActivity = savedInstanceState.getBoolean(STATE_KEY_BASE_FRAGMENT);
            mLoadedBaseFragment = mTopLevelActivity;

            // update the action bar based on the top most fragment
            onBackStackChanged();

            // figure which panel we are on and update the status bar
            mBrowsePanelActive = (getCurrentPanel() == Panel.Browse);
            updateStatusBarColor();
        }

        // if intent wasn't UI related, process it as a audio playback request
        if (!intentHandled) {
            handlePlaybackIntent(launchIntent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_KEY_BASE_FRAGMENT, mTopLevelActivity);
    }

    public Fragment getTopFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.activity_base_content);
    }

    public void postRemoveFragment(final Fragment frag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // removing the fragment doesn't cause the backstack event to be triggered even if
                // it is the top fragment, so if it is the top fragment, we will just manually
                // call pop back stack
                if (frag == getTopFragment()) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    getSupportFragmentManager().beginTransaction().remove(frag).commit();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // parse intent to ascertain whether the intent is inter UI communication
        boolean intentHandled = parseIntentForFragment(intent);
        // since this activity is marked 'singleTop' (launch mode), an existing activity instance
        // could be sent media play requests
        if ( !intentHandled) {
            handlePlaybackIntent(intent);
        }
    }

    @Override
    public void onMetaChanged() {
        super.onMetaChanged();
        updateStatusBarColor();
    }

    @Override
    protected void onSlide(float slideOffset) {
        boolean isInBrowser = getCurrentPanel() == Panel.Browse && slideOffset < 0.7f;
        if (isInBrowser != mBrowsePanelActive) {
            mBrowsePanelActive = isInBrowser;
            updateStatusBarColor();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        getAudioPlayerFragment().setVisualizerVisible(hasFocus
                && getCurrentPanel() == Panel.MusicPlayer);
    }

    private void updateStatusBarColor() {
        if (mBrowsePanelActive || MusicUtils.getCurrentAlbumId() < 0) {
            updateStatusBarColor(Color.TRANSPARENT);
        } else {
            new AsyncTask<Void, Void, BitmapWithColors>() {
                @Override
                protected BitmapWithColors doInBackground(Void... params) {
                    ImageFetcher imageFetcher = ImageFetcher.getInstance(HomeActivity.this);
                    return imageFetcher.getArtwork(
                            MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                            MusicUtils.getArtistName(), true);
                }
                @Override
                protected void onPostExecute(BitmapWithColors bmc) {
                    updateVisualizerColor(bmc != null
                            ? bmc.getContrastingColor() : Color.TRANSPARENT);
                    updateStatusBarColor(bmc != null
                            ? bmc.getVibrantDarkColor() : Color.TRANSPARENT);
                }
            }.execute();
        }
    }

    private void updateVisualizerColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = getResources().getColor(R.color.visualizer_fill_color);
        }

        // check for null since updatestatusBarColor is a async task
        AudioPlayerFragment fragment = getAudioPlayerFragment();
        if (fragment != null) {
            fragment.setVisualizerColor(color);
        }
    }

    private void updateStatusBarColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = getResources().getColor(R.color.primary_dark);
        }
        final Window window = getWindow();
        ObjectAnimator animator = ObjectAnimator.ofInt(window,
                "statusBarColor", window.getStatusBarColor(), color);
        animator.setEvaluator(new ArgbEvaluator());
        animator.setDuration(300);
        animator.start();
    }

    private boolean parseIntentForFragment(Intent intent) {
        boolean handled = false;
        if (intent.getAction() != null) {
            final String action = intent.getAction();
            Fragment targetFragment = null;
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            if (action.equals(ACTION_VIEW_SMART_PLAYLIST)) {
                long playlistId = intent.getExtras().getLong(Config.SMART_PLAYLIST_TYPE);
                switch (Config.SmartPlaylistType.getTypeById(playlistId)) {
                    case LastAdded:
                        targetFragment = new LastAddedFragment();
                        break;
                    case RecentlyPlayed:
                        targetFragment = new RecentFragment();
                        break;
                    case TopTracks:
                        targetFragment = new TopTracksFragment();
                        break;
                }
            } else if (action.equals(ACTION_VIEW_PLAYLIST_DETAILS)) {
                targetFragment = new PlaylistDetailFragment();
            } else if (action.equals(ACTION_VIEW_ALBUM_DETAILS)) {
                targetFragment = new AlbumDetailFragment();
            } else if (action.equals(ACTION_VIEW_ARTIST_DETAILS)) {
                targetFragment = new ArtistDetailFragment();
            }

            if (targetFragment != null) {
                targetFragment.setArguments(intent.getExtras());
                transaction.setCustomAnimations(0, 0, 0, R.anim.fade_out);
                // If we ever come back to this because of memory concerns because
                // none of the fragments are being removed from memory, we can fix this
                // by using "replace" instead of "add".  The caveat is that the performance of
                // returning to previous fragments is a bit more sluggish because the fragment
                // view needs to be recreated. If we do remove that, we can remove the back stack
                // change listener code above
                transaction.add(R.id.activity_base_content, targetFragment);
                if (mLoadedBaseFragment) {
                    transaction.addToBackStack(null);
                    showPanel(Panel.Browse);
                } else {
                    // else mark the fragment as loaded so we don't load the music browse fragment.
                    // this happens when they launch search which is its own activity and then
                    // browse through that back to home activity
                    mLoadedBaseFragment = true;
                    getActionBar().setDisplayHomeAsUpEnabled(true);
                }
                // the current top fragment is about to be hidden by what we are replacing
                // it with -- so tell that fragment not to make its action bar menu items visible
                Fragment oldTop = getTopFragment();
                if (oldTop != null) {
                    oldTop.setMenuVisibility(false);
                }

                transaction.commit();
                handled = true;
            }
        }
        return handled;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_PHOTO && !TextUtils.isEmpty(mKey)) {
            if (resultCode == RESULT_OK) {
                MusicUtils.removeFromCache(this, mKey);
                final Uri selectedImage = data.getData();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = ImageFetcher.decodeSampledBitmapFromUri(getContentResolver(),
                                selectedImage);

                        ImageFetcher imageFetcher = ApolloUtils.getImageFetcher(HomeActivity.this);
                        imageFetcher.addBitmapToCache(mKey, bitmap);

                        MusicUtils.refresh();
                    }
                }).start();
            }
        }
    }

    /**
     * Starts an activity for result that returns an image from the Gallery.
     */
    public void selectNewPhoto(String key) {
        mKey = key;
        // Now open the gallery
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        startActivityForResult(intent, NEW_PHOTO);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateToTop();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Navigates to the top Activity and places the view to the correct page
     */
    protected void navigateToTop() {
        final Fragment topFragment = getTopFragment();
        int targetFragmentIndex = MusicBrowserPhoneFragment.INVALID_PAGE_INDEX;
        if (topFragment instanceof IChildFragment) {
            targetFragmentIndex = ((IChildFragment)topFragment).getMusicFragmentParent().ordinal();
        }

        // If we are the top activity in the stack (as determined by the activity that has loaded
        // the MusicBrowserPhoneFragment) then clear the back stack and move the browse fragment
        // to the appropriate page as per Android up standards
        if (mTopLevelActivity) {
            clearBackStack();
            MusicBrowserPhoneFragment musicFragment = (MusicBrowserPhoneFragment) getTopFragment();
            musicFragment.setDefaultPageIdx(targetFragmentIndex);
            showPanel(Panel.Browse);
        } else {
            // I've tried all other combinations with parent activities, support.NavUtils and
            // there is no easy way to achieve what we want that I'm aware of, so clear everything
            // and jump to the right page
            NavUtils.goHome(this, targetFragmentIndex);
        }
    }

    /**
     * Immediately clears the backstack
     */
    protected void clearBackStack() {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            final int id = fragmentManager.getBackStackEntryAt(0).getId();
            fragmentManager.popBackStackImmediate(id, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    @Override
    public void handlePendingPlaybackRequests() {
        if (mHasPendingPlaybackRequest) {
            Intent unhandledIntent = getIntent();
            handlePlaybackIntent(unhandledIntent);
        }
    }

    /**
     * Checks whether the passed intent contains a playback request,
     * and starts playback if that's the case
     * @return true if the intent was consumed
     */
    private boolean handlePlaybackIntent(Intent intent) {

        if (intent == null) {
            return false;
        } else if ( !MusicUtils.isPlaybackServiceConnected() ) {
            mHasPendingPlaybackRequest = true;
            return false;
        }

        String mimeType = intent.getType();
        boolean handled = false;

        if (MediaStore.Audio.Playlists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "playlistId", "playlist", -1);
            if (id >= 0) {
                MusicUtils.playPlaylist(this, id, false);
                handled = true;
            }
        } else if (MediaStore.Audio.Albums.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "albumId", "album", -1);
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playAlbum(this, id, position, false);
                handled = true;
            }
        } else if (MediaStore.Audio.Artists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "artistId", "artist", -1);
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playArtist(this, id, position, false);
                handled = true;
            }
        }

        // reset intent as it was handled as a playback request
        if (handled) {
            setIntent(new Intent());
        }

        return handled;

    }

    private long parseIdFromIntent(Intent intent, String longKey,
                                   String stringKey, long defaultId) {
        long id = intent.getLongExtra(longKey, -1);
        if (id < 0) {
            String idString = intent.getStringExtra(stringKey);
            if (idString != null) {
                try {
                    id = Long.parseLong(idString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return id;
    }

    @Override
    public void onBackStackChanged() {
        Fragment topFragment = getTopFragment();
        if (topFragment != null) {
            // the fragment that has come back to the top should now have its menu items
            // added to the action bar -- so tell it to make it menu items visible
            topFragment.setMenuVisibility(true);
            ISetupActionBar setupActionBar = (ISetupActionBar) topFragment;
            setupActionBar.setupActionBar();

            getActionBar().setDisplayHomeAsUpEnabled(
                    !(topFragment instanceof MusicBrowserPhoneFragment));
        }
    }
}
