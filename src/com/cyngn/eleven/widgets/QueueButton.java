package com.cyngn.eleven.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.cyngn.eleven.ui.activities.SlidingPanelActivity;

public class QueueButton extends AudioActivityButton {

    public QueueButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onClick(View view) {
        mActivity.showPanel(SlidingPanelActivity.Panel.Queue);
    }
}