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
import android.widget.TextView;

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
    private ImageView mCustomButton;
    private ImageView mBackButton;
    private TextView mTitleText;

    // this tracks the views that want to add to the context menu
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

        mCustomButton = (ImageView)findViewById(R.id.header_bar_custom_button);
        mCustomButton.setVisibility(GONE);
        mCustomButton.setBackground(new HoloSelector(getContext()));

        mBackButton = (ImageView)findViewById(R.id.header_bar_up);
        mBackButton.setBackground(new HoloSelector(getContext()));

        mTitleText = (TextView)findViewById(R.id.header_bar_title);

        mPopupMenu = new PopupMenu(getContext(), mMenuButton);
        mPopupMenu.setOnMenuItemClickListener(this);
    }

    /**
     * Clears all the pop up menu listeners
     */
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

    /**
     * Adds a pop up menu creator to the list
     * @param popupMenuCreator the menu creator to add
     */
    public void add(PopupMenuCreator popupMenuCreator) {
        // add it to the list
        mListPopupMenuCreator.add(new WeakReference<PopupMenuCreator>(popupMenuCreator));

        // let the popup menu creator know about this header bar
        popupMenuCreator.addHeaderBar(new WeakReference<HeaderBar>(this));

        // update the visibility of the menu button
        updateMenuButtonVisibility();
    }

    /**
     * Hide/shows the menu button based on the # of pop up menu creators attached
     */
    private void updateMenuButtonVisibility() {
        // if there are no items, hide the button
        if (mListPopupMenuCreator.size() == 0) {
            mMenuButton.setVisibility(GONE);
        } else {
            mMenuButton.setVisibility(VISIBLE);
        }
    }

    /**
     * The menu item has been clicked, create the menu based on the lsit of pop up menu creators
     * and show it
     * @param v The view that was clicked.
     */
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

    /**
     * Dismiss the pop up menu
     */
    public void dismissPopupMenu() {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
    }

    /**
     * When the menu item is clicked, propogate the click to each of the underlying menu creators
     * @param item the item that is clicked
     * @return true if handled
     */
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

    /**
     * @param resId set the title text
     */
    public void setTitleText(int resId) {
        mTitleText.setText(resId);
    }

    /**
     * @param text set the title text
     */
    public void setTitleText(String text) {
        mTitleText.setText(text);
    }

    /**
     * This sets the image resource/listener and makes the button visible
     * @param resId the image resource to set the button to
     * @param listener the click listener
     */
    public void setupCustomButton(final int resId, final OnClickListener listener) {
        mCustomButton.setImageResource(resId);
        mCustomButton.setVisibility(VISIBLE);
        mCustomButton.setOnClickListener(listener);
    }

    /**
     * Sets the back button listener
     * @param listener listener
     */
    public void setBackListener(final OnClickListener listener) {
        mBackButton.setOnClickListener(listener);
        setOnClickListener(listener);
    }

    /**
     * Sets the header bar listener
     * @param listener listener
     */
    public void setHeaderClickListener(final OnClickListener listener) {
        setOnClickListener(listener);
    }
}
