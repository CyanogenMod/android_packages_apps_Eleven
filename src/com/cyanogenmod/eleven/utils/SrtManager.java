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
package com.cyanogenmod.eleven.utils;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

/**
 * Class that helps signal when srt text comes and goes
 */
public abstract class SrtManager implements Handler.Callback {
    private static final String TAG = SrtManager.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int POST_TEXT_MSG = 0;

    private ArrayList<SrtParser.SrtEntry> mEntries;
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private Runnable mLoader;

    private MediaPlayer mMediaPlayer;
    private int mNextIndex;

    public SrtManager() {
        mHandlerThread = new HandlerThread("SrtManager",
                android.os.Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    public synchronized void reset() {
        mHandler.removeMessages(POST_TEXT_MSG);
        mHandler.removeCallbacks(mLoader);
        mEntries = null;
        mLoader = null;
        mMediaPlayer = null;
        mNextIndex = -1;

        // post a null timed text to clear
        onTimedText(null);
    }

    public synchronized void release() {
        if (mHandlerThread != null) {
            mHandler.removeMessages(POST_TEXT_MSG);
            mHandler.removeCallbacks(mLoader);
            mHandler = null;
            mHandlerThread.quit();
            mHandlerThread = null;
        }
    }

    public synchronized void initialize(final MediaPlayer player, final File f) {
        if (player == null || f == null) {
            throw new IllegalArgumentException("Must have a valid player and file");
        }

        reset();

        if (!f.exists()) {
            return;
        }

        mMediaPlayer = player;

        mLoader = new Runnable() {
            @Override
            public void run() {
                onLoaded(this, SrtParser.getSrtEntries(f));
            }
        };

        mHandler.post(mLoader);
    }

    public synchronized void seekTo(long timeMs) {
        mHandler.removeMessages(POST_TEXT_MSG);

        mNextIndex = 0;

        if (mEntries != null) {
            if (DEBUG) {
                Log.d(TAG, "Seeking to: " + timeMs);
            }

            // find the first entry after the current time and set mNextIndex to the one before that
            for (int i = 0; i < mEntries.size(); i++) {
                mNextIndex = i;
                if (i + 1 < mEntries.size() && mEntries.get(i + 1).mStartTimeMs > timeMs) {
                    break;
                }
            }

            postNextTimedText();
        }
    }

    public synchronized void pause() {
        mHandler.removeMessages(POST_TEXT_MSG);
    }

    public synchronized void play() {
        postNextTimedText();
    }

    private synchronized void onLoaded(Runnable r, ArrayList<SrtParser.SrtEntry> entries) {
        // if this is the same loader
        if (r == mLoader) {
            mEntries = entries;
            if (mEntries != null) {
                if (DEBUG) {
                    Log.d(TAG, "Loaded: " + entries.size() + " number of entries");
                }

                try {
                    seekTo(mMediaPlayer.getCurrentPosition());
                } catch(IllegalStateException e) {
                    Log.d(TAG, "illegal state but failing silently");
                    reset();
                }
            }
        }
    }

    private synchronized void postNextTimedText() {
        if (mEntries != null) {
            long timeMs = 0;
            try {
                timeMs = mMediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.d(TAG, "illegal state - probably because media player has been " +
                        "stopped/released. failing silently");
                return;
            }

            String currentMessage = null;
            long targetTime = -1;

            // shift mNextIndex until it hits the next item we want
            while (mNextIndex < mEntries.size() && mEntries.get(mNextIndex).mStartTimeMs < timeMs) {
                mNextIndex++;
            }

            // if the previous entry is valid, set the message and target time
            if (mNextIndex > 0 && entrySurroundsTime(mEntries.get(mNextIndex - 1), timeMs)) {
                currentMessage = mEntries.get(mNextIndex - 1).mLine;
                targetTime = mEntries.get(mNextIndex - 1).mEndTimeMs;
            }

            onTimedText(currentMessage);

            // if our next index is valid, and we don't have a target time, set it
            if (mNextIndex < mEntries.size() && targetTime == -1) {
                targetTime = mEntries.get(mNextIndex).mStartTimeMs;
            }

            // if we have a targeted time entry and we are playing, then queue up a delayed message
            if (targetTime >= 0 && mMediaPlayer.isPlaying()) {
                mHandler.removeMessages(POST_TEXT_MSG);

                long delay = targetTime - timeMs;
                mHandler.sendEmptyMessageDelayed(POST_TEXT_MSG, delay);

                if (DEBUG && mNextIndex < mEntries.size()) {
                    Log.d(TAG, "Preparing next message: " + delay + "ms from now with msg: " +
                            mEntries.get(mNextIndex).mLine);
                }
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case POST_TEXT_MSG:
                postNextTimedText();
                return true;
        }

        return false;
    }

    private static boolean entrySurroundsTime(SrtParser.SrtEntry entry, long time) {
        return entry.mStartTimeMs <= time && entry.mEndTimeMs >= time;
    }

    public abstract void onTimedText(String txt);
}
