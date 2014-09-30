package com.cyngn.eleven.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.ui.activities.HomeActivity;

public abstract class BaseFragment extends Fragment implements MusicStateListener,
    ISetupActionBar {

    protected ViewGroup mRootView;

    protected abstract String getTitle();
    protected abstract int getLayoutToInflate();

    @Override
    public void setupActionBar() {
        getContainingActivity().setupActionBar(getTitle());
        getContainingActivity().setActionBarAlpha(255);
        getContainingActivity().setFragmentPadding(true);
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