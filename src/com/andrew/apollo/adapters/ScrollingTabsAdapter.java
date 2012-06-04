
package com.andrew.apollo.adapters;

import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.andrew.apollo.R;
import com.andrew.apollo.utils.ThemeUtils;

public class ScrollingTabsAdapter implements TabAdapter {

    private final FragmentActivity activity;

    public ScrollingTabsAdapter(FragmentActivity act) {
        activity = act;
    }

    @Override
    public View getView(int position) {
        LayoutInflater inflater = activity.getLayoutInflater();
        final Button tab = (Button)inflater.inflate(R.layout.tabs, null);
        final String[] mTitles = activity.getResources().getStringArray(R.array.tab_titles);

        if (position < mTitles.length)
            tab.setText(mTitles[position]);

        // Theme chooser
        ThemeUtils.setTextColor(activity, tab, "tab_text_color");
        return tab;
    }
}
