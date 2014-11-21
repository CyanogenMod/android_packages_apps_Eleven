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

package com.cyanogenmod.eleven.menu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.cyanogenmod.eleven.Config;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.activities.HomeActivity;
import com.cyanogenmod.eleven.utils.ApolloUtils;
import com.cyanogenmod.eleven.utils.Lists;
import com.cyanogenmod.eleven.utils.MusicUtils;

import java.util.ArrayList;

/**
 * Used when the user requests to modify Album art or Artist image
 * It provides an easy interface for them to choose a new image, use the old
 * image, or search Google for one.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PhotoSelectionDialog extends DialogFragment {

    private static final int NEW_PHOTO = 0;

    private static final int OLD_PHOTO = 1;

    private final ArrayList<String> mChoices = Lists.newArrayList();

    private static ProfileType mProfileType;

    private String mKey;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PhotoSelectionDialog() {
    }

    /**
     * @param title The dialog title.
     * @param type Either Artist or Album
     * @param key key to query ImageFetcher
     * @return A new instance of the dialog.
     */
    public static PhotoSelectionDialog newInstance(final String title, final ProfileType type,
                                                   String key) {
        final PhotoSelectionDialog frag = new PhotoSelectionDialog();
        final Bundle args = new Bundle();
        args.putString(Config.NAME, title);
        frag.setArguments(args);
        mProfileType = type;
        frag.mKey = key;
        return frag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final String title = getArguments().getString(Config.NAME);
        switch (mProfileType) {
            case ARTIST:
                setArtistChoices();
                break;
            case ALBUM:
                setAlbumChoices();
                break;
            case OTHER:
                setOtherChoices();
                break;
            default:
                break;
        }
        // Dialog item Adapter
        final HomeActivity activity = (HomeActivity) getActivity();
        final ListAdapter adapter = new ArrayAdapter<String>(activity,
                android.R.layout.select_dialog_item, mChoices);
        return new AlertDialog.Builder(activity).setTitle(title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        switch (which) {
                            case NEW_PHOTO:
                                activity.selectNewPhoto(mKey);
                                break;
                            case OLD_PHOTO:
                                MusicUtils.selectOldPhoto(activity, mKey);
                                break;
                            default:
                                break;
                        }
                    }
                }).create();
    }

    /**
     * Adds the choices for the artist profile image.
     */
    private void setArtistChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        /* Disable fetching image until we find a last.fm replacement
        if (ApolloUtils.isOnline(getActivity())) {
            // Option to fetch the old artist image
            mChoices.add(OLD_PHOTO, getString(R.string.context_menu_fetch_artist_image));
        }*/
    }

    /**
     * Adds the choices for the album profile image.
     */
    private void setAlbumChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        /* Disable fetching image until we find a last.fm replacement
        // Option to fetch the old album image
        if (ApolloUtils.isOnline(getActivity())) {
            // Option to fetch the old artist image
            mChoices.add(OLD_PHOTO, getString(R.string.context_menu_fetch_album_art));
        }*/
    }

    /**
     * Adds the choices for the genre and playlist images.
     */
    private void setOtherChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        // Disable fetching image until we find a last.fm replacement
        // Option to use the default image
        // mChoices.add(OLD_PHOTO, getString(R.string.use_default));
    }

    /**
     * Easily detect the MIME type
     */
    public enum ProfileType {
        ARTIST, ALBUM, ProfileType, OTHER
    }
}
