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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.cyanogenmod.eleven.R;

public class PopupMenuButton extends ImageView implements IPopupMenuCallback,
        View.OnClickListener {
    protected int mPosition = -1;
    protected IListener mClickListener = null;

    public PopupMenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        setScaleType(ScaleType.CENTER_INSIDE);
        setBackground(getResources().getDrawable(R.drawable.selectable_background_light));
        setOnClickListener(this);
    }

    public void setPosition(final int position) {
        mPosition = position;
    }

    @Override
    public void setPopupMenuClickedListener(final IListener listener) {
        mClickListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mClickListener != null) {
            mClickListener.onPopupMenuClicked(v, mPosition);
        }
    }
}
