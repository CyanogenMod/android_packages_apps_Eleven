/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class ElevenIntro extends AppIntro2 {

    // Please DO NOT override onCreate. Use init
    @Override
    public void init(Bundle savedInstanceState) {

        showDoneButton(true);
        // We must ask all permissions
        showSkipButton(false);
        setSeparatorColor(Color.parseColor("#fafafa"));

        //TODO: add proper strings
        private String mTitle       = getString(R.string.app_name);
        private String mDescription = getString(R.string.app_name);

        //TODO: add proper images for slides

        // Welcome
        addSlide(AppIntroFragment.newInstance(mTitle, mDescription, R.mipmap.ic_launcher_eleven, Color.parseColor("#333333")));
        addSlide(SampleSlide.newInstance(R.layout.introOne));

        // Explain what's Eleven
        addSlide(AppIntroFragment.newInstance(mTitle, mDescription, R.mipmap.ic_launcher_eleven, Color.parseColor("#303F9F")));

        // Explain permissions
        addSlide(AppIntroFragment.newInstance(mTitle, mDescription, R.mipmap.ic_launcher_eleven, Color.parseColor("#673AB7")));

        // Ask permisssions
        addSlide(AppIntroFragment.newInstance(mTitle, mDescription, R.mipmap.ic_launcher_eleven, Color.parseColor("#F44336")));

        // Start
        addSlide(AppIntroFragment.newInstance(mTitle, mDescription, R.mipmap.ic_launcher_eleven, Color.parseColor("#388E3C")));

        // Ask permissions on the 4th slide
        askForPermissions(new String[]{Manifest.permision.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO}, 4);
    }

    @Override
    public void onSkipPressed() {
    // Do something when users tap on Skip button.
    }

    @Override
    public void onDonePressed() {
    Intent mMain = new Intent(this, ElevenApplication.class);
    startActivity(mMain);
    }
}
