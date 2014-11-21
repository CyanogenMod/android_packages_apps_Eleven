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
package com.cyanogenmod.eleven.ui.fragments.phone;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;

/**
 * This class is used for fragments under the {@link MusicBrowserFragment}
 * Even though the containing view pager creates all the fragments, the loader
 * does not load complete until the user navigates to that page.  To get around this
 * we will use the containing fragment's loader manager
 */
public abstract class MusicBrowserFragment extends Fragment {
    public abstract int getLoaderId();

    public LoaderManager getContainingLoaderManager() {
        return getParentFragment().getLoaderManager();
    }

    protected void initLoader(Bundle args, LoaderCallbacks<? extends Object> callback) {
        getContainingLoaderManager().initLoader(getLoaderId(), args, callback);
    }

    protected void restartLoader(Bundle args, LoaderCallbacks<? extends Object> callback) {
        getContainingLoaderManager().restartLoader(getLoaderId(), args, callback);
    }
}
