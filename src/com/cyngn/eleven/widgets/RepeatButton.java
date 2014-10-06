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

package com.cyngn.eleven.widgets;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;

import com.cyngn.eleven.MusicPlaybackService;
import com.cyngn.eleven.R;
import com.cyngn.eleven.utils.ApolloUtils;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.widgets.theme.HoloSelector;

/**
 * A custom {@link ImageButton} that represents the "repeat" button.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RepeatButton extends ImageButton implements OnClickListener, OnLongClickListener {
    public static float ACTIVE_ALPHA = 1.0f;
    public static float INACTIVE_ALPHA = 0.4f;

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    @SuppressWarnings("deprecation")
    public RepeatButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Set the selector
        setBackgroundDrawable(new HoloSelector(context));
        // Control playback (cycle repeat modes)
        setOnClickListener(this);
        // Show the cheat sheet
        setOnLongClickListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(final View v) {
        MusicUtils.cycleRepeat();
        updateRepeatState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onLongClick(final View view) {
        if (TextUtils.isEmpty(view.getContentDescription())) {
            return false;
        } else {
            ApolloUtils.showCheatSheet(view);
            return true;
        }
    }

    /**
     * Sets the correct drawable for the repeat state.
     */
    public void updateRepeatState() {
        switch (MusicUtils.getRepeatMode()) {
            case MusicPlaybackService.REPEAT_ALL:
                setContentDescription(getResources().getString(R.string.accessibility_repeat_all));
                setImageDrawable(getResources().getDrawable(R.drawable.btn_playback_repeat_all));
                setAlpha(ACTIVE_ALPHA);
                break;
            case MusicPlaybackService.REPEAT_CURRENT:
                setContentDescription(getResources().getString(R.string.accessibility_repeat_one));
                setImageDrawable(getResources().getDrawable(R.drawable.btn_playback_repeat_one));
                setAlpha(ACTIVE_ALPHA);
                break;
            case MusicPlaybackService.REPEAT_NONE:
                setContentDescription(getResources().getString(R.string.accessibility_repeat));
                setImageDrawable(getResources().getDrawable(R.drawable.btn_playback_repeat_all));
                setAlpha(INACTIVE_ALPHA);
                break;
            default:
                break;
        }
    }
}
