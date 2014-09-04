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
package com.cyngn.eleven.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import com.cyngn.eleven.R;
import com.cyngn.eleven.ui.fragments.AudioPlayerFragment;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserPhoneFragment;

public class HomeActivity extends SlidingPanelActivity {

    public static final String ACTION_VIEW_BROWSE = "com.cyngn.eleven.ui.activities.HomeActivity.view.Browse";
    public static final String ACTION_VIEW_MUSIC_PLAYER = "com.cyngn.eleven.ui.activities.HomeActivity.view.MusicPlayer";
    public static final String ACTION_VIEW_QUEUE = "com.cyngn.eleven.ui.activities.HomeActivity.view.Queue";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.activity_base_content, new MusicBrowserPhoneFragment()).commit();
        }

        // if we've been launched by an intent, parse it
        Intent launchIntent = getIntent();
        if (launchIntent != null) {
            parseIntent(launchIntent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        parseIntent(intent);
    }

    private void parseIntent(Intent intent) {
        Panel targetPanel = null;

        if (intent.getAction() != null) {

        } else {
            AudioPlayerFragment player = getAudioPlayerFragment();
            if (player != null && player.startPlayback()) {
                targetPanel = Panel.MusicPlayer;
            }
        }

        if (targetPanel != null) {
            showPanel(targetPanel);
        }
    }


}
