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
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Audio.Media;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
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
 * @see {@link OnClickListener}
 * @see {@link OnAudioFocusChangeListener}
 * @see {@link OnSeekBarChangeListener}
 */
public class AudioPreviewActivity extends Activity implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, OnClickListener,
        OnAudioFocusChangeListener, OnSeekBarChangeListener, OnTouchListener {

    // Constants
    private static final String TAG = AudioPreviewActivity.class.getSimpleName();
    private static final int PROGRESS_DELAY_INTERVAL = 250;
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

    // Seeking flag
    private boolean mIsSeeking = false;
    private boolean mWasPlaying = false;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (mPreviewPlayer != null && mIsSeeking) {
            mPreviewPlayer.seekTo(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mIsSeeking = true;
        if (mCurrentState == State.PLAYING) {
            mWasPlaying = true;
            pausePlayback(false);
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mWasPlaying) {
            startPlayback();
        }
        mWasPlaying = false;
        mIsSeeking = false;
    }

    private enum State {
        INIT,
        PREPARED,
        PLAYING,
        PAUSED
    }

    /**
     * <pre>
     *     Handle some ui events
     * </pre>
     *
     * @see {@link Handler}
     */
    private class UiHandler extends Handler {

        public static final int MSG_UPDATE_PROGRESS = 1000;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PROGRESS:
                    updateProgressForPlayer();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    }

    // Members
    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // [NOTE][MSB]: Handle any audio output changes
            if (intent != null) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    pausePlayback();
                }
            }
        }
    };
    private UiHandler mHandler = new UiHandler();
    private static AsyncQueryHandler sAsyncQueryHandler;
    private AudioManager mAudioManager;
    private PreviewPlayer mPreviewPlayer;
    private PreviewSong mPreviewSong = new PreviewSong();
    private int mDuration = 0;
    private int mLastOrientationWhileBuffering;

    // Views
    private TextView mTitleTextView;
    private TextView mArtistTextView;
    private SeekBar mSeekBar;
    private ProgressBar mProgressBar;
    private ImageButton mPlayPauseBtn;
    private View mContainerView;

    // Flags
    private boolean mIsReceiverRegistered = false;
    private State mCurrentState = State.INIT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        mLastOrientationWhileBuffering = getRequestedOrientation();
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
        Logger.logd(TAG, "URI: " + uri);
        mPreviewSong.URI = uri;
        PreviewPlayer localPlayer = (PreviewPlayer) getLastNonConfigurationInstance();
        if (localPlayer == null) {
            mPreviewPlayer = new PreviewPlayer();
            mPreviewPlayer.setCallbackActivity(this);
            try {
                mPreviewPlayer.setDataSourceAndPrepare(mPreviewSong.URI);
            } catch (IOException e) {
                Logger.loge(TAG, e.getMessage());
                onError(mPreviewPlayer, MediaPlayer.MEDIA_ERROR_IO, 0);
                return;
            }
        } else {
            mPreviewPlayer = localPlayer;
            mPreviewPlayer.setCallbackActivity(this);
        }
        mAudioManager = ((AudioManager) getSystemService(Context.AUDIO_SERVICE));
        sAsyncQueryHandler = new AsyncQueryHandler(getContentResolver()) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                AudioPreviewActivity.this.onQueryComplete(token, cookie, cursor);
            }
        };
        initializeInterface();
        registerNoisyAudioReceiver();
        if (savedInstanceState == null) {
            processUri();
        } else {
            mPreviewSong.TITLE = savedInstanceState.getString(Media.TITLE);
            mPreviewSong.ARTIST = savedInstanceState.getString(Media.ARTIST);
            setNames();
        }
        if (localPlayer != null) {
            sendStateChange(State.PREPARED);
            if (localPlayer.isPlaying()) {
                startProgressUpdates();
                sendStateChange(State.PLAYING);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mIsReceiverRegistered) {
            unregisterReceiver(mAudioNoisyReceiver);
            mIsReceiverRegistered = false;
        }
        outState.putString(Media.TITLE, mPreviewSong.TITLE);
        outState.putString(Media.ARTIST, mPreviewSong.ARTIST);
        super.onSaveInstanceState(outState);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mPreviewPlayer.clearCallbackActivity();
        PreviewPlayer localPlayer = mPreviewPlayer;
        mPreviewPlayer = null;
        return localPlayer;
    }

    @Override
    public void onPause() {
        overridePendingTransition(0, 0);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mIsReceiverRegistered) {
            unregisterReceiver(mAudioNoisyReceiver);
            mIsReceiverRegistered = false;
        }
        stopPlaybackAndTeardown();
        super.onDestroy();
    }

    private void sendStateChange(State newState) {
        mCurrentState = newState;
        handleStateChangeForUi();
    }

    private void handleStateChangeForUi() {
        switch (mCurrentState) {
            case INIT:
                Logger.logd(TAG, "INIT");
                break;
            case PREPARED:
                Logger.logd(TAG, "PREPARED");
                if (mPreviewPlayer != null) {
                    mDuration = mPreviewPlayer.getDuration();
                }
                if (mDuration > 0 && mSeekBar != null) {
                    mSeekBar.setMax(mDuration);
                    mSeekBar.setEnabled(true);
                    mSeekBar.setVisibility(View.VISIBLE);
                }
                if (mProgressBar != null) {
                    mProgressBar.setVisibility(View.INVISIBLE);
                    setRequestedOrientation(mLastOrientationWhileBuffering);
                }
                if (mPlayPauseBtn != null) {
                    mPlayPauseBtn.setImageResource(R.drawable.btn_playback_play);
                    mPlayPauseBtn.setEnabled(true);
                    mPlayPauseBtn.setOnClickListener(this);
                }
                break;
            case PLAYING:
                Logger.logd(TAG, "PLAYING");
                if (mPlayPauseBtn != null) {
                    mPlayPauseBtn.setImageResource(R.drawable.btn_playback_pause);
                    mPlayPauseBtn.setEnabled(true);
                }
                break;
            case PAUSED:
                Logger.logd(TAG, "PAUSED");
                if (mPlayPauseBtn != null) {
                    mPlayPauseBtn.setImageResource(R.drawable.btn_playback_play);
                    mPlayPauseBtn.setEnabled(true);
                }
                break;
        }
        setNames();
    }

    private void onQueryComplete(int token, Object cookie, Cursor cursor) {
        String title = null;
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
        int index = -1;
        switch (token) {
            case CONTENT_QUERY_TOKEN:
                index = cursor.getColumnIndex(Media.TITLE);
                if (index > -1) {
                    title = cursor.getString(index);
                }
                index = cursor.getColumnIndex(Media.ARTIST);
                if (index > -1) {
                    artist = cursor.getString(index);
                }
                break;
            case CONTENT_BAD_QUERY_TOKEN:
                index = cursor.getColumnIndex(Media.DISPLAY_NAME);
                if (index > -1) {
                    title = cursor.getString(index);
                }
                break;
            default:
                title = null;
                break;
        }
        cursor.close();

        // Well if we didn't get the name lets fallback to something else
        if (TextUtils.isEmpty(title)) {
            title = getNameFromPath();
        }

        mPreviewSong.TITLE = title;
        mPreviewSong.ARTIST = artist;

        setNames();
    }

    private String getNameFromPath() {
        String path = "Unknown"; // [TODO][MSB]: Localize
        if (mPreviewSong != null) {
            if (mPreviewSong.URI != null) {
                path = mPreviewSong.URI.getLastPathSegment();
            }
        }
        return path;
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
        mContainerView = findViewById(R.id.grp_container_view);
        // Make it so if the user touches the background overlay we exit
        View v = findViewById(R.id.grp_transparent_wrapper);
        v.setOnTouchListener(this);
        mTitleTextView = (TextView) findViewById(R.id.tv_title);
        mArtistTextView = (TextView) findViewById(R.id.tv_artist);
        mSeekBar = (SeekBar) findViewById(R.id.sb_progress);
        mSeekBar.setOnSeekBarChangeListener(this);
        mProgressBar = (ProgressBar) findViewById(R.id.pb_loader);
        mPlayPauseBtn = (ImageButton) findViewById(R.id.ib_playpause);
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

    private void startProgressUpdates() {
        if (mHandler != null) {
            mHandler.removeMessages(UiHandler.MSG_UPDATE_PROGRESS);
            Message msg = mHandler.obtainMessage(UiHandler.MSG_UPDATE_PROGRESS);
            mHandler.sendMessage(msg);
        }
    }

    private void updateProgressForPlayer() {
        if (mSeekBar != null && mPreviewPlayer != null) {
            if (mPreviewPlayer.isPrepared()) {
                mSeekBar.setProgress(mPreviewPlayer.getCurrentPosition());
            }
        }
        if (mHandler != null) {
            mHandler.removeMessages(UiHandler.MSG_UPDATE_PROGRESS);
            Message msg = mHandler.obtainMessage(UiHandler.MSG_UPDATE_PROGRESS);
            mHandler.sendMessageDelayed(msg, PROGRESS_DELAY_INTERVAL);
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
    }

    private void handleFileScheme() {
        String path = mPreviewSong.URI.getPath();
        sAsyncQueryHandler.startQuery(CONTENT_QUERY_TOKEN, null, Media.EXTERNAL_CONTENT_URI,
                MEDIA_PROJECTION, "_data=?", new String[] { path }, null);
    }

    private void handleHttpScheme() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            mLastOrientationWhileBuffering = getRequestedOrientation();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
        mPreviewSong.TITLE = getNameFromPath();
        setNames();
    }

    private void registerNoisyAudioReceiver() {
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(this.mAudioNoisyReceiver, localIntentFilter);
        mIsReceiverRegistered = true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mHandler.removeMessages(UiHandler.MSG_UPDATE_PROGRESS);
        if (mSeekBar != null && mPreviewPlayer != null) {
            mSeekBar.setProgress(mPreviewPlayer.getDuration());
        }
        sendStateChange(State.PREPARED);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Toast.makeText(this, "Server has died!", Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_IO:
                Toast.makeText(this, "I/O error!", Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                Toast.makeText(this, "Malformed media!", Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Toast.makeText(this, "Not valid for progressive playback!", Toast.LENGTH_SHORT)
                        .show();
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                Toast.makeText(this, "Media server timed out!", Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                Toast.makeText(this, "Media is unsupported!", Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
            default:
                Toast.makeText(this, "An unkown error has occurred: " + what, Toast.LENGTH_LONG)
                        .show();
                break;
        }
        stopPlaybackAndTeardown();
        finish();
        return true; // false causes flow to not call onCompletion
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        sendStateChange(State.PREPARED);
        startPlayback();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        int containerX1 = (int) mContainerView.getX();
        int containerY1 = (int) mContainerView.getY();
        int containerX2 = (int) (mContainerView.getX() + mContainerView.getWidth());
        int containerY2 = (int) (mContainerView.getY() + mContainerView.getHeight());

        Rect r = new Rect();
        r.set(containerX1, containerY1, containerX2, containerY2);
        if (!r.contains(x, y)) {
            stopPlaybackAndTeardown();
            finish();
        }

        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_playpause:
                if (mCurrentState == State.PREPARED || mCurrentState == State.PAUSED) {
                    startPlayback();
                } else {
                    pausePlayback();
                }
                break;
            case R.id.grp_transparent_wrapper:
                stopPlaybackAndTeardown();
                finish();
                break;
            default:
                break;
        }
    }

    private boolean gainAudioFocus() {
        if (mAudioManager == null) {
            return false;
        }
        int r = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(this);
        }
    }

    private void startPlayback() {
        if (mPreviewPlayer != null && !mPreviewPlayer.isPlaying()) {
            if (mPreviewPlayer.isPrepared()) {
                if (gainAudioFocus()) {
                    mPreviewPlayer.start();
                    sendStateChange(State.PLAYING);
                    startProgressUpdates();
                } else {
                    Logger.loge(TAG, "Failed to gain audio focus!");
                    onError(mPreviewPlayer, MediaPlayer.MEDIA_ERROR_TIMED_OUT, 0);
                }
            } else {
                Logger.loge(TAG, "Not prepared!");
            }
        } else {
            Logger.logd(TAG, "No player or is not playing!");
        }
    }

    private void stopPlaybackAndTeardown() {
        if (mPreviewPlayer != null) {
            if (mPreviewPlayer.isPlaying()) {
                mPreviewPlayer.stop();
            }
            mPreviewPlayer.release();
            mPreviewPlayer = null;
        }
        abandonAudioFocus();
    }

    private void pausePlayback() {
        pausePlayback(true);
    }

    private void pausePlayback(boolean updateUi) {
        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
            mPreviewPlayer.pause();
            if (updateUi) {
                sendStateChange(State.PAUSED);
            }
            mHandler.removeMessages(UiHandler.MSG_UPDATE_PROGRESS);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (mPreviewPlayer == null) {
            if (mAudioManager != null) {
                mAudioManager.abandonAudioFocus(this);
            }
        }
        Logger.logd(TAG, "Focus change: " + focusChange);
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stopPlaybackAndTeardown();
                finish();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mPreviewPlayer.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                mPreviewPlayer.setVolume(1.0f, 1.0f);
                startPlayback();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                break;
        }
    }

    @Override
    public void onUserLeaveHint() {
        stopPlaybackAndTeardown();
        finish();
        super.onUserLeaveHint();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        boolean result = true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
                pausePlayback();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return result;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                startPlayback();
                return result;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pausePlayback();
                return result;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                result = super.onKeyDown(keyCode, keyEvent);
                return result;
            default:
                result = super.onKeyDown(keyCode, keyEvent);
                break;
        }
        stopPlaybackAndTeardown();
        finish();
        return result;
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

        /* package */ PreviewPlayer() {
            setOnPreparedListener(this);
        }

        /* package */ void clearCallbackActivity() {
            mActivityReference.clear();
            mActivityReference = null;
            setOnErrorListener(null);
            setOnCompletionListener(null);
        }

        /* package */ void setCallbackActivity(AudioPreviewActivity activity)
                throws IllegalArgumentException{
            if (activity == null) {
                throw new IllegalArgumentException("'activity' cannot be null!");
            }
            mActivityReference = new WeakReference<AudioPreviewActivity>(activity);
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
            if (mActivityReference != null) {
                AudioPreviewActivity activity = mActivityReference.get();
                if (activity != null && !activity.isFinishing()) {
                    activity.onPrepared(mp);
                }
            }
        }

    }
}
