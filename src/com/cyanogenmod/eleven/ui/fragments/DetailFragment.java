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
package com.cyanogenmod.eleven.ui.fragments;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.utils.PopupMenuHelper;

public abstract class DetailFragment extends BaseFragment {
    protected PopupMenuHelper mActionMenuHelper;

    /** create the popup menu helper used by the type of item
     *  for which this is a detail screen */
    protected abstract PopupMenuHelper createActionMenuHelper();
    /** menu title for the shuffle option for this screen */
    protected abstract int getShuffleTitleId();
    /** action to take if the shuffle menu is selected */
    protected abstract void playShuffled();

    @Override
    protected void onViewCreated() {
        super.onViewCreated();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.shuffle_item, menu);
        menu.findItem(R.id.menu_shuffle_item).setTitle(getShuffleTitleId());

        // use the same popup menu to provide actions for the item
        // represented by this detail screen as would be used elsewhere
        mActionMenuHelper = createActionMenuHelper();
        mActionMenuHelper.onPreparePopupMenu(0);
        mActionMenuHelper.createPopupMenu(menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if(item.getItemId() == R.id.menu_shuffle_item) {
            playShuffled();
            return true;
        }

        // delegate to the popup menu that represents the item
        // for which this is a detail screen
        if(mActionMenuHelper.onMenuItemClick(item)) { return true; }

        return super.onOptionsItemSelected(item);
    }
}