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

package com.cyanogenmod.eleven.ui;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.widgets.PlayPauseProgressButton;
import com.cyanogenmod.eleven.widgets.PopupMenuButton;

import java.lang.ref.WeakReference;

/**
 * Used to efficiently cache and recyle the {@link View}s used in the artist,
 * album, song, playlist, and genre adapters.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class MusicHolder {

    /**
     * This is the overlay ontop of the background artist, playlist, or genre
     * image
     */
    public WeakReference<RelativeLayout> mOverlay;

    /**
     * This is the artist or album image
     */
    public WeakReference<ImageView> mImage;

    /**
     * This is the first line displayed in the list or grid
     *
     * @see {@code #getView()} of a specific adapter for more detailed info
     */
    public WeakReference<TextView> mLineOne;

    /**
     * This is displayed on the right side of the first line in the list or grid
     *
     * @see {@code #getView()} of a specific adapter for more detailed info
     */
    public WeakReference<TextView> mLineOneRight;

    /**
     * This is the second line displayed in the list or grid
     *
     * @see {@code #getView()} of a specific adapter for more detailed info
     */
    public WeakReference<TextView> mLineTwo;

    /**
     * The container for the circular progress bar and play/pause button
     *
     * @see {@code #getView()} of a specific adapter for more detailed info
     */
    public WeakReference<PlayPauseProgressButton> mPlayPauseProgressButton;

    /**
     * The Padding container for the circular progress bar
     */
    public WeakReference<View> mPlayPauseProgressContainer;

    /**
     * The song indicator for the currently playing track
     */
    public WeakReference<View> mNowPlayingIndicator;

    /**
     * The divider for the list item
     */
    public WeakReference<View> mDivider;

    /**
     * The divider for the list item
     */
    public WeakReference<PopupMenuButton> mPopupMenuButton;

    /**
     * Constructor of <code>ViewHolder</code>
     *
     * @param context The {@link Context} to use.
     */
    public MusicHolder(final View view) {
        super();
        // Initialize mImage
        mImage = new WeakReference<ImageView>((ImageView)view.findViewById(R.id.image));

        // Initialize mLineOne
        mLineOne = new WeakReference<TextView>((TextView)view.findViewById(R.id.line_one));

        // Initialize mLineOneRight
        mLineOneRight = new WeakReference<TextView>(
                (TextView)view.findViewById(R.id.line_one_right));

        // Initialize mLineTwo
        mLineTwo = new WeakReference<TextView>((TextView)view.findViewById(R.id.line_two));

        // Initialize Circular progress bar container
        mPlayPauseProgressButton = new WeakReference<PlayPauseProgressButton>(
                (PlayPauseProgressButton)view.findViewById(R.id.playPauseProgressButton));

        // Get the padding container for the progress bar
        mPlayPauseProgressContainer = new WeakReference<View>(
                view.findViewById(R.id.play_pause_container));

        mNowPlayingIndicator = new WeakReference<View>(view.findViewById(R.id.now_playing));

        // Get the divider for the list item
        mDivider = new WeakReference<View>(view.findViewById(R.id.divider));

        // Get the pop up menu button
        mPopupMenuButton = new WeakReference<PopupMenuButton>(
                (PopupMenuButton)view.findViewById(R.id.popup_menu_button));
    }

    /**
     * @param view The {@link View} used to initialize content
     */
    public final static class DataHolder {

        /**
         * This is the ID of the item being loaded in the adapter
         */
        public long mItemId;

        /**
         * This is the first line displayed in the list or grid
         *
         * @see {@code #getView()} of a specific adapter for more detailed info
         */
        public String mLineOne;

        /**
         * This is displayed on the right side of the first line in the list or grid
         *
         * @see {@code #getView()} of a specific adapter for more detailed info
         */
        public String mLineOneRight;

        /**
         * This is the second line displayed in the list or grid
         *
         * @see {@code #getView()} of a specific adapter for more detailed info
         */
        public String mLineTwo;

        /**
         * Constructor of <code>DataHolder</code>
         */
        public DataHolder() {
            super();
        }

    }
}
