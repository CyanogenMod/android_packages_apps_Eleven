/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.cyngn.eleven.R;
import com.cyngn.eleven.widgets.theme.HoloSelector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;


/**
 * Simple Header bar wrapper class that also has its own menu bar button.
 * It can collect a list of popup menu creators and create a pop up menu
 * from the list
 */
public class HeaderBar extends LinearLayout implements View.OnClickListener,
        PopupMenu.OnMenuItemClickListener {

    public static interface PopupMenuCreator {
        public void onCreatePopupMenu(final Menu menu, final MenuInflater inflater);
        public void clearHeaderBars();
        public void addHeaderBar(final WeakReference<HeaderBar> headerBar);
        public boolean onPopupMenuItemClick(final MenuItem item);
    }

    private ImageView mMenuButton;
    private ArrayList<WeakReference<PopupMenuCreator>> mListPopupMenuCreator;
    private PopupMenu mPopupMenu;

    public HeaderBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        mListPopupMenuCreator = new ArrayList<WeakReference<PopupMenuCreator>>();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mMenuButton = (ImageView)findViewById(R.id.header_bar_menu_button);
        mMenuButton.setBackground(new HoloSelector(getContext()));
        mMenuButton.setOnClickListener(this);
        updateMenuButtonVisibility();

        mPopupMenu = new PopupMenu(getContext(), mMenuButton);
        mPopupMenu.setOnMenuItemClickListener(this);
    }

    public void clear() {
        // first dismiss the popup menu
        dismissPopupMenu();

        // disconnect the popup menu creators
        for (WeakReference<PopupMenuCreator> creator : mListPopupMenuCreator) {
            PopupMenuCreator popupMenuCreator = creator.get();
            if (popupMenuCreator != null) {
                popupMenuCreator.clearHeaderBars();
            }
        }

        // clear the list
        mListPopupMenuCreator.clear();
    }

    public void add(PopupMenuCreator popupMenuCreator) {
        // add it to the list
        mListPopupMenuCreator.add(new WeakReference<PopupMenuCreator>(popupMenuCreator));

        // let the popup menu creator know about this header bar
        popupMenuCreator.addHeaderBar(new WeakReference<HeaderBar>(this));

        // update the visibility of the menu button
        updateMenuButtonVisibility();
    }

    private void updateMenuButtonVisibility() {
        // if there are no items, hide the button
        if (mListPopupMenuCreator.size() == 0) {
            mMenuButton.setVisibility(GONE);
        } else {
            mMenuButton.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        // clear any previous items
        mPopupMenu.getMenu().clear();

        // walk through each popup menu creator and inflate
        for (WeakReference<PopupMenuCreator> creator : mListPopupMenuCreator) {
            PopupMenuCreator popupMenuCreator = creator.get();
            if (popupMenuCreator != null) {
                popupMenuCreator.onCreatePopupMenu(mPopupMenu.getMenu(),
                        mPopupMenu.getMenuInflater());
            }
        }

        // show the popup
        mPopupMenu.show();
    }

    public void dismissPopupMenu() {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // walk through each popup menu creator until one of them acknowledges the click
        for (WeakReference<PopupMenuCreator> creator : mListPopupMenuCreator) {
            PopupMenuCreator popupMenuCreator = creator.get();
            if (popupMenuCreator != null) {
                if (popupMenuCreator.onPopupMenuItemClick(item)) {
                    return true;
                }
            }
        }

        return false;
    }
}
