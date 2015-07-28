/*
* Copyright (c) 2013, The Linux Foundation. All rights reserved.
* Copyright (C) 2015 The CyanogenMod Project
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
package com.cyanogenmod.eleven.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.utils.MusicUtils;

/**
 * This class handles the playpause button as well as the circular progress bar
 * it self-updates the progress bar but the containing activity/fragment
 * needs to add code to pause/resume this button to prevent unnecessary
 * updates while the activity/fragment is not visible
 */
public class PlayPauseProgressButton extends FrameLayout {
    private static String TAG = PlayPauseProgressButton.class.getSimpleName();
    private static boolean DEBUG = false;
    private static final int REVOLUTION_IN_DEGREES = 360;
    private static final int HALF_REVOLUTION_IN_DEGREES = REVOLUTION_IN_DEGREES / 2;

    private ProgressBar mProgressBar;
    private PlayPauseButton mPlayPauseButton;
    private Runnable mUpdateProgress;
    private boolean mPaused;

    private final int mSmallDistance;
    private float mDragPercentage = 0.0f;
    private boolean mDragEnabled = false;
    private boolean mDragging = false;
    private float mDownAngle;
    private float mDragAngle;
    private float mDownX;
    private float mDownY;
    private int mWidth;
    private long mCurrentSongDuration;
    private long mCurrentSongProgress;

    public PlayPauseProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        // set enabled to false as default so that calling enableAndShow will execute
        setEnabled(false);

        // set paused to false since we shouldn't be typically created while not visible
        mPaused = false;

        mSmallDistance = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPlayPauseButton = (PlayPauseButton)findViewById(R.id.action_button_play);
        mProgressBar = (ProgressBar)findViewById(R.id.circularProgressBar);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Make the play pause button size dependent on the container size
        int horizontalPadding = getMeasuredWidth() / 4;
        int verticalPadding = getMeasuredHeight() / 4;
        mPlayPauseButton.setPadding(
                horizontalPadding, horizontalPadding,
                verticalPadding, verticalPadding);

        // rotate the progress bar 90 degrees counter clockwise so that the
        // starting position is at the top
        mProgressBar.setPivotX(mProgressBar.getMeasuredWidth() / 2);
        mProgressBar.setPivotY(mProgressBar.getMeasuredHeight() / 2);
        mProgressBar.setRotation(-90);
    }

    /**
     * Enable and shows the container
     */
    public void enableAndShow() {
        // enable
        setEnabled(true);

        // make our view visible
        setVisibility(VISIBLE);
    }

    /**
     * Disables and sets the visibility to gone for the container
     */
    public void disableAndHide() {
        // disable
        setEnabled(false);

        // hide our view
        setVisibility(GONE);
    }

    /**
     * Sets whether the user can drag the progress in a circular motion to seek the track
     */
    public void setDragEnabled(boolean enabled) {
        mDragEnabled = enabled;
    }

    /**
     * @return true if the user is actively dragging to seek
     */
    public boolean isDragging() {
        return mDragEnabled && mDragging;
    }

    /**
     * @return how far the user has dragged in the track in ms
     */
    public long getDragProgressInMs() {
        return (long)(mDragPercentage * mCurrentSongDuration);
    }

    @Override
    public void setEnabled(boolean enabled) {
        // if the enabled state isn't changed, quit
        if (enabled == isEnabled()) return;

        super.setEnabled(enabled);

        // signal our state has changed
        onStateChanged();
    }

    /**
     * Pauses the progress bar periodic update logic
     */
    public void pause() {
        if (!mPaused) {
            mPaused = true;

            // signal our state has changed
            onStateChanged();
        }
    }

    /**
     * Resumes the progress bar periodic update logic
     */
    public void resume() {
        if (mPaused) {
            mPaused = false;

            // signal our state has changed
            onStateChanged();
        }
    }

    /**
     * @return play pause button
     */
    public PlayPauseButton getPlayPauseButton() {
        return mPlayPauseButton;
    }

    /**
     * Signaled if the state has changed (either the enabled or paused flag)
     * When the state changes, we either kick off the updates or remove them based on those flags
     */
    private void onStateChanged() {
        // if we are enabled and not paused
        if (isEnabled() && !mPaused) {
            // update the state of the progress bar and play/pause button
            updateState();

            // kick off update states
            postUpdate();
        } else {
            // otherwise remove our update
            removeUpdate();
        }
    }

    /**
     * Updates the state of the progress bar and the play pause button
     */
    private void updateState() {
        mCurrentSongDuration = MusicUtils.duration();
        mCurrentSongProgress = MusicUtils.position();

        int progress = 0;
        if (isDragging()) {
            progress = (int) (mDragPercentage * mProgressBar.getMax());
        } else if (mCurrentSongDuration > 0) {
            progress = (int) (mProgressBar.getMax() * mCurrentSongProgress / mCurrentSongDuration);
        }

        mProgressBar.setProgress(progress);
        mPlayPauseButton.updateState();
    }

    /**
     * Creates and posts the update runnable to the handler
     */
    private void postUpdate() {
        if (mUpdateProgress == null) {
            mUpdateProgress = new Runnable() {
                @Override
                public void run() {
                    updateState();
                    postDelayed(mUpdateProgress, isDragging() ? MusicUtils.UPDATE_FREQUENCY_FAST_MS
                            : MusicUtils.UPDATE_FREQUENCY_MS);
                }
            };
        }

        // remove any existing callbacks
        removeCallbacks(mUpdateProgress);

        // post ourselves as a delayed
        post(mUpdateProgress);
    }

    /**
     * Removes the runnable from the handler
     */
    private void removeUpdate() {
        if (mUpdateProgress != null) {
            removeCallbacks(mUpdateProgress);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        mWidth = Math.min(w, h);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mDragEnabled) {
            return false;
        }

        return onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        if (!mDragEnabled || mCurrentSongDuration <= 0) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mDownAngle = angle(mDownX, mDownY);
                mDragAngle = REVOLUTION_IN_DEGREES
                        * (mCurrentSongProgress / (float) mCurrentSongDuration);
                mDragPercentage = mDragAngle / REVOLUTION_IN_DEGREES;
                mDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // if the user has moved a certain distance
                if (Math.sqrt(Math.pow(event.getX() - mDownX, 2)
                        + Math.pow(event.getY() - mDownY, 2)) < mSmallDistance) {
                    return false;
                }

                // if we weren't previously dragging, immediately kick off an update to reflect
                // the change faster
                if (!mDragging) {
                    postUpdate();
                }

                mDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);

                // calculate the amount of angle we've moved
                final float deltaAngle = getDelta(x, y);
                mDragAngle = cropAngle(mDragAngle + deltaAngle);
                mDragPercentage = mDragAngle / REVOLUTION_IN_DEGREES;

                if (DEBUG) {
                    Log.d(TAG, "Delta Angle: " + deltaAngle + ", Target Angle: " + mDownAngle);
                }

                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // if we were dragging, seek to where we dragged to
                if (mDragging) {
                    MusicUtils.seek((long)(mDragPercentage * mCurrentSongDuration));
                }
                mDragging = false;
            default:
                break;
        }
        return mDragging;
    }

    /**
     * Crops the angle between 0 and 360 - if the angle is < 0, it will return 0, if it is more than
     * 360 it will return 360
     */
    private static float cropAngle(float angle) {
        return Math.min(REVOLUTION_IN_DEGREES, Math.max(0.0f, angle));
    }

    /**
     * Wraps the angle between -180 and 180. This assumes that the passed in
     * angle is >= -360 and <= 360
     */
    private static float wrapHalfRevolution(float angle) {
        if (angle < -HALF_REVOLUTION_IN_DEGREES) {
            return angle + REVOLUTION_IN_DEGREES;
        } else if (angle > HALF_REVOLUTION_IN_DEGREES) {
            return angle - REVOLUTION_IN_DEGREES;
        }

        return angle;
    }

    /**
     * Gets the change in angle from the down angle and updates the down angle to the current angle
     */
    private float getDelta(float x, float y) {
        float angle = angle(x, y);
        float deltaAngle = wrapHalfRevolution(angle - mDownAngle);
        mDownAngle = angle;
        return deltaAngle;
    }

    /**
     * Calculates the angle at the point passed in based on the center of the button
     */
    private float angle(float x, float y) {
        float center = mWidth / 2.0f;
        x -= center;
        y -= center;

        if (x == 0.0f) {
            if (y > 0.0f) {
                return 180.0f;
            } else {
                return 0.0f;
            }
        }

        float angle = (float) (Math.atan(y / x) / Math.PI * 180.0);
        if (x > 0.0f) {
            angle += 90;
        } else {
            angle += 270;
        }
        return angle;
    }
}
