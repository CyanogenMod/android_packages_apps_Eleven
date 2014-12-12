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
package com.cyanogenmod.eleven.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PreferenceUtils;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

public class EqualizerView extends VisualizerView {
    private boolean mLinked = false;
    private boolean mStarted = false;
    private boolean mPanelVisible = false;

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (!mLinked) {
                animate().alpha(1).setDuration(300);
                link(0);
                mLinked = true;
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mLinked) {
                animate().alpha(0).setDuration(300);
                unlink();
                mLinked = false;
            }
        }
    };

    private static class TileBarGraphRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

        /**
         * Renders the FFT data as a series of lines, in histogram form
         *
         * @param divisions - must be a power of 2. Controls how many lines to draw
         * @param paint     - Paint to draw lines with
         * @param dbfuzz    - final dB display adjustment
         * @param dbFactor  - dbfuzz is multiplied by dbFactor.
         */
        public TileBarGraphRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
            super();
            mDivisions = divisions;
            mPaint = paint;
            mDbFuzz = dbfuzz;
            mDbFuzzFactor = dbFactor;
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect) {
            // Do nothing, we only display FFT data
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = data.bytes[mDivisions * i];
                byte ifk = data.bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * mDbFuzzFactor + mDbFuzz);
            }

            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    public EqualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EqualizerView(Context context) {
        this(context, null, 0);
    }

    public void initialize(Context context) {
        setEnabled(false);

        Resources res = mContext.getResources();
        Paint paint = new Paint();
        paint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.eqalizer_path_stroke_width));
        paint.setAntiAlias(true);
        paint.setColor(res.getColor(R.color.equalizer_fill_color));
        paint.setPathEffect(new DashPathEffect(new float[]{
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_1),
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_2)
        }, 0));

        int bars = res.getInteger(R.integer.equalizer_divisions);
        addRenderer(new TileBarGraphRenderer(bars, paint,
                res.getInteger(R.integer.equalizer_db_fuzz),
                res.getInteger(R.integer.equalizer_db_fuzz_factor)));
    }

    /**
     * Follows Fragment onStart to determine if the containing fragment/activity is started
     */
    public void onStart() {
        mStarted = true;
        checkStateChanged();
    }

    /**
     * Follows Fragment onStop to determine if the containing fragment/activity is stopped
     */
    public void onStop() {
        mStarted = false;
        checkStateChanged();
    }

    /**
     * Separate method to toggle panel visibility - currently used when the user slides to
     * improve performance of the sliding panel
     */
    public void setPanelVisible(boolean panelVisible) {
        if (mPanelVisible != panelVisible) {
            mPanelVisible = panelVisible;
            checkStateChanged();
        }
    }

    /**
     * Checks the state of the EqualizerView to determine whether we want to link up the equalizer
     */
    public void checkStateChanged() {
        if (mPanelVisible && mStarted
                && PreferenceUtils.getInstance(mContext).getShowVisualizer()
                && MusicUtils.getQueueSize() > 0) {
            mLinkVisualizer.run();
        } else {
            mUnlinkVisualizer.run();
        }
    }
}
