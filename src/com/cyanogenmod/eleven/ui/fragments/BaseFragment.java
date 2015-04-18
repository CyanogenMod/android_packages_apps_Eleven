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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.cyanogenmod.eleven.MusicStateListener;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.activities.HomeActivity;

public abstract class BaseFragment extends Fragment implements MusicStateListener,
    ISetupActionBar {

    protected ViewGroup mRootView;

    protected abstract String getTitle();
    protected abstract int getLayoutToInflate();

    protected boolean needsElevatedActionBar() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void setupActionBar() {
        getContainingActivity().setupActionBar(getTitle());
        getContainingActivity().setActionBarAlpha(255);
        getContainingActivity().setFragmentPadding(true);
        getContainingActivity().setActionBarElevation(needsElevatedActionBar());
    }

    protected HomeActivity getContainingActivity() {
        return (HomeActivity) getActivity();
    }

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup) inflater.inflate(getLayoutToInflate(), null);
        // set the background color
        mRootView.setBackgroundColor(getResources().getColor(R.color.background_color));
        // eat any touches that fall through to the root so they aren't
        // passed on to fragments "behind" the current one.
        mRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent me) { return true; }
        });

        setupActionBar();
        onViewCreated();

        return mRootView;
    }

    protected void onViewCreated() {
        getContainingActivity().setMusicStateListenerListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        getContainingActivity().removeMusicStateListenerListener(this);
    }

    @Override
    public void onMetaChanged() {

    }

    @Override
    public void onPlaylistChanged() {

    }
}
