/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.ui.fragments.phone;

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
