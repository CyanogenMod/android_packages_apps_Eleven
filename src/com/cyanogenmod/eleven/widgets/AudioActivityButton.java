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
package com.cyanogenmod.eleven.widgets;
import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;

import com.cyanogenmod.eleven.ui.activities.SlidingPanelActivity;

public abstract class AudioActivityButton extends AudioButton {
    protected SlidingPanelActivity mActivity;

    public AudioActivityButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setActivity(FragmentActivity activity) {
        mActivity = (SlidingPanelActivity)activity;
    }
}