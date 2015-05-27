/*
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

package com.cyanogenmod.eleven.ui.activities.preview;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Audio.Media;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.ui.activities.preview.util.Logger;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * AudioPreview
 * <pre>
 *     Preview plays external audio files in a dialog over the application
 * </pre>
 *
 * @see {@link Activity}
 * @see {@link android.media.MediaPlayer.OnCompletionListener}
 * @see {@link android.media.MediaPlayer.OnErrorListener}
 * @see {@link android.media.MediaPlayer.OnPreparedListener}
 */
public class AudioPreviewActivity extends Activity implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, OnClickListener,
        OnAudioFocusChangeListener {

    // Constants
    private static final String TAG = AudioPreviewActivity.class.getSimpleName();
    private static final String SCHEME_CONTENT = "content";
    private static final String SCHEME_FILE = "file";
    private static final String SCHEME_HTTP = "http";
    private static final String AUTHORITY_MEDIA = "media";
    private static final int CONTENT_QUERY_TOKEN = 1000;
    private static final int CONTENT_BAD_QUERY_TOKEN = CONTENT_QUERY_TOKEN + 1;
    private static final String[] MEDIA_PROJECTION = new String[] {
            Media.TITLE,
            Media.ARTIST
    };

    private enum State {
        INIT,
        PREPARED,
        PLAYING,
        PAUSED,
        STOPPING,
        ERROR
    }

    // Members
    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // [NOTE][MSB]: Handle any audio output changes
            if (intent != null) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    if (mPreviewPlayer.isPlaying()) {
                        mPreviewPlayer.pause();
                        sendStateChange(State.PAUSED);
                    }
                }
            }
        }
    };
    private static AsyncQueryHandler sAsyncQueryHandler;
    private AudioManager mAudioManager;
    private PreviewPlayer mPreviewPlayer;
    private PreviewSong mPreviewSong = new PreviewSong();
    private int mDuration = 0;

    // Views
    private TextView mTitleTextView;
    private TextView mArtistTextView;
    private SeekBar mSeekBar;
    private ImageButton mPlayPauseBtn;

    // Flags
    private boolean mIsReceiverRegistered = false;
    private State mCurrentState = State.INIT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.logd(TAG, "onCreate(" + savedInstanceState + ")");
        Intent intent = getIntent();
        if (intent == null) {
            Logger.loge(TAG, "No intent");
            finish();
            return;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            Logger.loge(TAG, "No uri data");
            finish();
            return;
        }
        mPreviewSong.URI = uri;
        mPreviewPlayer = new PreviewPlayer(this);
        try {
            mPreviewPlayer.setDataSourceAndPrepare(mPreviewSong.URI);
        } catch (IOException e) {
            Logger.loge(TAG, e.getMessage());
            onError(mPreviewPlayer, MediaPlayer.MEDIA_ERROR_IO, 0);
            return;
        }
        mAudioManager = ((AudioManager) getSystemService(Context.AUDIO_SERVICE));
        sAsyncQueryHandler = new AsyncQueryHandler(getContentResolver()) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                AudioPreviewActivity.this.onQueryComplete(token, cookie, cursor);
            }
        };
        initializeInterface();
        processUri();
    }

    @Override
    public void onDestroy() {
        if (mIsReceiverRegistered) {
            unregisterReceiver(mAudioNoisyReceiver);
        }
        stopPlaybackAndTeardown();
        super.onDestroy();
    }

    private void sendStateChange(State newState) {
        mCurrentState = newState;
        // [TODO][MSB]: Post to handler
        handleStateChangeForUi();
    }

    private void handleStateChangeForUi() {
        switch (mCurrentState) {
            case INIT:
                Logger.logd(TAG, "INIT");
                break;
            case PREPARED:
                Logger.logd(TAG, "PREPARED");
                mDuration = mPreviewPlayer.getDuration();
                if (mDuration > 0) {
                    mSeekBar.setMax(mDuration);
                    mSeekBar.setVisibility(View.VISIBLE);
                }
                mPlayPauseBtn.setImageResource(R.drawable.btn_playback_play);
                mPlayPauseBtn.setEnabled(true);
                mPlayPauseBtn.setOnClickListener(this);
                break;
            case PLAYING:
                Logger.logd(TAG, "PLAYING");
                mPlayPauseBtn.setImageResource(R.drawable.btn_playback_pause);
                mPlayPauseBtn.setEnabled(true);
                break;
            case PAUSED:
                Logger.logd(TAG, "PAUSED");
                mPlayPauseBtn.setImageResource(R.drawable.btn_playback_play);
                mPlayPauseBtn.setEnabled(true);
                break;
            case STOPPING:
                break;
            case ERROR:
                Logger.logd(TAG, "ERROR");
                mTitleTextView.setText("Error: some error has occurred!");
                break;
        }

    }

    private void onQueryComplete(int token, Object cookie, Cursor cursor) {
        String title;
        String artist = null;
        if (cursor == null || cursor.getCount() < 1) {
            Logger.loge(TAG, "Null or empty cursor!");
            return;
        }
        boolean moved = cursor.moveToFirst();
        if (!moved) {
            Logger.loge(TAG, "Failed to read cursor!");
            return;
        }
        switch (token) {
            case CONTENT_QUERY_TOKEN:
                title = cursor.getString(cursor.getColumnIndex(Media.TITLE));
                artist = cursor.getString(cursor.getColumnIndex(Media.ARTIST));
                break;
            case CONTENT_BAD_QUERY_TOKEN:
                title = cursor.getString(cursor.getColumnIndex(Media.DISPLAY_NAME));
                break;
            default:
                title = "Information Not Available"; // [TODO][MSB]: Add default copy to strings
                break;
        }
        cursor.close();

        mPreviewSong.TITLE = title;
        mPreviewSong.ARTIST = artist;

        setNames();
    }

    private void setNames() {
        // Set the text
        mTitleTextView.setText(mPreviewSong.TITLE);
        mArtistTextView.setText(mPreviewSong.ARTIST);
    }

    private void initializeInterface() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_audio_preview);
        mTitleTextView = (TextView) findViewById(R.id.tv_title);
        mArtistTextView = (TextView) findViewById(R.id.tv_artist);
        mSeekBar = (SeekBar) findViewById(R.id.sb_progress);
        mPlayPauseBtn = (ImageButton) findViewById(R.id.ib_playpause);
        sendStateChange(State.INIT);
    }

    private void processUri() {
        String scheme = mPreviewSong.URI.getScheme();
        Logger.logd(TAG, "Uri Scheme: " + scheme);
        if (SCHEME_CONTENT.equalsIgnoreCase(scheme)) {
            handleContentScheme();
        } else if (SCHEME_FILE.equalsIgnoreCase(scheme)) {
            handleFileScheme();
        } else if (SCHEME_HTTP.equalsIgnoreCase(scheme)) {
            handleHttpScheme();
        }
    }

    private void handleContentScheme() {
        String authority = mPreviewSong.URI.getAuthority();
        if (!AUTHORITY_MEDIA.equalsIgnoreCase(authority)) {
            Logger.logd(TAG, "Bad authority!");
            sAsyncQueryHandler
                    .startQuery(CONTENT_BAD_QUERY_TOKEN, null, mPreviewSong.URI, null, null, null,
                            null);
        } else {
            sAsyncQueryHandler
                    .startQuery(CONTENT_QUERY_TOKEN, null, mPreviewSong.URI, MEDIA_PROJECTION, null,
                            null, null);
        }
        registerNoisyAudioReceiver();
    }

    private void handleFileScheme() {
        // [TODO][MSB]: Implement
    }

    private void handleHttpScheme() {
        // [TODO][MSB]: Implement
    }

    private void registerNoisyAudioReceiver() {
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(this.mAudioNoisyReceiver, localIntentFilter);
        mIsReceiverRegistered = true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        sendStateChange(State.PREPARED);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "An error has occurred!", Toast.LENGTH_LONG).show();
        return false; // false causes flow to not call onCompletion
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        sendStateChange(State.PREPARED);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_playpause:
                if (mCurrentState == State.PREPARED || mCurrentState == State.PAUSED) {
                    startPlayback();
                } else {
                    pausePlaybackPause();
                }
                break;
            default:
                break;
        }
    }

    private boolean gainAudioFocus() {
        int r = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(this);
    }

    private void startPlayback() {
        if (mPreviewPlayer != null && !mPreviewPlayer.isPlaying()) {
            if (mPreviewPlayer.isPrepared()) {
                if (gainAudioFocus()) {
                    mPreviewPlayer.start();
                    sendStateChange(State.PLAYING);
                } else {
                    Logger.loge(TAG, "Failed to gain audio focus!");
                    sendStateChange(State.ERROR);
                }
            } else {
                Logger.loge(TAG, "Not prepared!");
            }
        } else {
            Logger.logd(TAG, "No player or is not playing!");
        }
    }

    private void stopPlaybackAndTeardown() {
        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
            mPreviewPlayer.stop();
        }
        mPreviewPlayer.release();
        mPreviewPlayer = null;
        abandonAudioFocus();
    }

    private void pausePlaybackPause() {
        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
            mPreviewPlayer.pause();
            sendStateChange(State.PAUSED);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        
    }

    @Override
    public void onUserLeaveHint() {
        stopPlaybackAndTeardown();
        finish();
        super.onUserLeaveHint();
    }

    /**
     * <pre>
     *     Media player specifically tweaked for use in this audio preview context
     * </pre>
     */
    private static class PreviewPlayer extends MediaPlayer
            implements MediaPlayer.OnPreparedListener {

        // Members
        private WeakReference<AudioPreviewActivity> mActivityReference; // weakref from static class
        private boolean mIsPrepared = false;

        /* package */ boolean isPrepared() {
            return mIsPrepared;
        }

        /* package */ PreviewPlayer(AudioPreviewActivity activity) {
            mActivityReference = new WeakReference<AudioPreviewActivity>(activity);
            setOnPreparedListener(this);
            setOnErrorListener(activity);
            setOnCompletionListener(activity);
        }

        /* package */ void setDataSourceAndPrepare(Uri uri)
                throws IllegalArgumentException, IOException {
            if (uri == null || uri.toString().length() < 1) {
                throw new IllegalArgumentException("'uri' cannot be null or empty!");
            }
            AudioPreviewActivity activity = mActivityReference.get();
            if (activity != null && !activity.isFinishing()) {
                setDataSource(activity, uri);
                prepareAsync();
            }
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            mIsPrepared = true;
            AudioPreviewActivity activity = mActivityReference.get();
            if (activity != null && !activity.isFinishing()) {
                activity.onPrepared(mp);
            }
        }

    }
}
