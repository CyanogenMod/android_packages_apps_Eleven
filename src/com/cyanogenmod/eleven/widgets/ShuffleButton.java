/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.utils.MusicUtils;

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ShuffleButton extends AudioButton {
    public ShuffleButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onClick(final View v) {
        MusicUtils.cycleShuffle();
        updateShuffleState();
    }

    /** Sets the correct drawable for the shuffle state. */
    public void updateShuffleState() {
        switch (MusicUtils.getShuffleMode()) {
            case MusicPlaybackService.SHUFFLE_NORMAL:
                setContentDescription(getResources().getString(R.string.accessibility_shuffle_all));
                setAlpha(ACTIVE_ALPHA);
                break;
            case MusicPlaybackService.SHUFFLE_AUTO:
                setContentDescription(getResources().getString(R.string.accessibility_shuffle_all));
                setAlpha(ACTIVE_ALPHA);
                break;
            case MusicPlaybackService.SHUFFLE_NONE:
                setContentDescription(getResources().getString(R.string.accessibility_shuffle));
                setAlpha(INACTIVE_ALPHA);
                break;
            default:
                break;
        }
    }
}