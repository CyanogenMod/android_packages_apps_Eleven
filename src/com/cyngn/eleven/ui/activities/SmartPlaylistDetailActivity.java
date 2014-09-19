/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */

package com.cyngn.eleven.ui.activities;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.MenuItem;

import com.cyngn.eleven.Config;
import com.cyngn.eleven.R;
import com.cyngn.eleven.ui.fragments.RecentFragment;
import com.cyngn.eleven.ui.fragments.profile.LastAddedFragment;
import com.cyngn.eleven.ui.fragments.profile.TopTracksFragment;

import java.util.Locale;

public class SmartPlaylistDetailActivity extends SlidingPanelActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            int playlistType = getIntent().getExtras().getInt(Config.SMART_PLAYLIST_TYPE);
            switch (Config.SmartPlaylistType.values()[playlistType]) {
                case LastAdded:
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.activity_base_content, new LastAddedFragment()).commit();

                    setupActionBar(R.string.playlist_last_added);
                    break;
                case RecentlyPlayed:
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.activity_base_content, new RecentFragment()).commit();

                    setupActionBar(R.string.playlist_recently_played);
                    break;
                case TopTracks:
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.activity_base_content, new TopTracksFragment()).commit();

                    setupActionBar(R.string.playlist_top_tracks);
                    break;
            }
        }
    }

    private void setupActionBar(int resId) {
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(getString(resId).toUpperCase(Locale.getDefault()));
        actionBar.setIcon(R.drawable.ic_action_back);
        actionBar.setHomeButtonEnabled(true);
    }

    /** cause action bar icon tap to act like back -- boo-urns! */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}