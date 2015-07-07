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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.cyanogenmod.eleven.R;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

public class EqualizerView extends VisualizerView {
    private boolean mLinked = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mEnabled = false;
    private int mColor;

    private TileBarGraphRenderer mBarRenderer;
    private ObjectAnimator mVisualizerColorAnimator;

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (!mLinked) {
                link(0);
                animate().alpha(1f).setDuration(300);
                mLinked = true;
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mLinked) {
                animate().alpha(0f).setDuration(300);
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
        mColor = res.getColor(R.color.equalizer_fill_color);
        Paint paint = new Paint();
        paint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.eqalizer_path_stroke_width));
        paint.setAntiAlias(true);
        paint.setColor(mColor);
        paint.setPathEffect(new DashPathEffect(new float[]{
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_1),
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_2)
        }, 0));

        int bars = res.getInteger(R.integer.equalizer_divisions);
        mBarRenderer = new TileBarGraphRenderer(bars, paint,
                res.getInteger(R.integer.equalizer_db_fuzz),
                res.getInteger(R.integer.equalizer_db_fuzz_factor));
        addRenderer(mBarRenderer);
    }

    public void setVisible(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            checkStateChanged();
        }
    }

    public void setColor(int color) {
        if (mColor != color) {
            mColor = color;
            if (mLinked) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }
                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mBarRenderer.mPaint, "color",
                        mBarRenderer.mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(900);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mBarRenderer.mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (mVisible && mPlaying && mEnabled) {
            mLinkVisualizer.run();
        } else {
            mUnlinkVisualizer.run();
        }
    }
}
