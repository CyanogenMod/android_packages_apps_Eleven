/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;

import com.cyanogenmod.eleven.cache.ImageCache;
import com.cyanogenmod.eleven.cache.ImageFetcher;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Mostly general and UI helpers.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class ApolloUtils {

    /**
     * The threshold used calculate if a color is light or dark
     */
    private static final int BRIGHTNESS_THRESHOLD = 130;

    /**
     * Because cancelled tasks are not automatically removed from the queue, we can easily
     * run over the queue limit - so here we will have a purge policy to purge those tasks
     */
    public static class PurgePolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // try purging all cancelled work items and re-executing
            if (!e.isShutdown()) {
                Log.d(PurgePolicy.class.getSimpleName(), "Before Purge: " + e.getQueue().size());
                e.purge();
                Log.d(PurgePolicy.class.getSimpleName(), "After Purge: " + e.getQueue().size());
                e.execute(r);
            }
        }
    };

    static {
        ((ThreadPoolExecutor)AsyncTask.THREAD_POOL_EXECUTOR).setRejectedExecutionHandler(
                new PurgePolicy()
        );
    }

    /* This class is never initiated */
    public ApolloUtils() {
    }

    /**
     * Used to determine if the device is a tablet or not
     *
     * @param context The {@link Context} to use.
     * @return True if the device is a tablet, false otherwise.
     */
    public static final boolean isTablet(final Context context) {
        final int layout = context.getResources().getConfiguration().screenLayout;
        return (layout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    /**
     * Used to determine if the device is currently in landscape mode
     *
     * @param context The {@link Context} to use.
     * @return True if the device is in landscape mode, false otherwise.
     */
    public static final boolean isLandscape(final Context context) {
        final int orientation = context.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Execute an {@link AsyncTask} on a thread pool
     *
     * @param forceSerial True to force the task to run in serial order
     * @param task Task to execute
     * @param args Optional arguments to pass to
     *            {@link AsyncTask#execute(Object[])}
     * @param <T> Task argument type
     */
    @SuppressLint("NewApi")
    public static <T> void execute(final boolean forceSerial, final AsyncTask<T, ?, ?> task,
            final T... args) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.DONUT) {
            throw new UnsupportedOperationException(
                    "This class can only be used on API 4 and newer.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB || forceSerial) {
            task.execute(args);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
        }
    }

    /**
     * Used to determine if there is an active data connection and what type of
     * connection it is if there is one
     *
     * @param context The {@link Context} to use
     * @return True if there is an active data connection, false otherwise.
     *         Also, if the user has checked to only download via Wi-Fi in the
     *         settings, the mobile data and other network connections aren't
     *         returned at all
     */
    public static final boolean isOnline(final Context context) {
        /*
         * This sort of handles a sudden configuration change, but I think it
         * should be dealt with in a more professional way.
         */
        if (context == null) {
            return false;
        }

        boolean state = false;
        final boolean onlyOnWifi = PreferenceUtils.getInstance(context).onlyOnWifi();

        /* Monitor network connections */
        final ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        /* Wi-Fi connection */
        final NetworkInfo wifiNetwork = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetwork != null) {
            state = wifiNetwork.isConnectedOrConnecting();
        }

        /* Mobile data connection */
        final NetworkInfo mbobileNetwork = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mbobileNetwork != null) {
            if (!onlyOnWifi) {
                state = mbobileNetwork.isConnectedOrConnecting();
            }
        }

        /* Other networks */
        final NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null) {
            if (!onlyOnWifi) {
                state = activeNetwork.isConnectedOrConnecting();
            }
        }

        return state;
    }

    /**
     * Display a {@link Toast} letting the user know what an item does when long
     * pressed.
     *
     * @param view The {@link View} to copy the content description from.
     */
    public static void showCheatSheet(final View view) {

        final int[] screenPos = new int[2]; // origin is device display
        final Rect displayFrame = new Rect(); // includes decorations (e.g.
                                              // status bar)
        view.getLocationOnScreen(screenPos);
        view.getWindowVisibleDisplayFrame(displayFrame);

        final Context context = view.getContext();
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        final int viewCenterX = screenPos[0] + viewWidth / 2;
        final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        final int estimatedToastHeight = (int)(48 * context.getResources().getDisplayMetrics().density);

        final Toast cheatSheet = Toast.makeText(context, view.getContentDescription(),
                Toast.LENGTH_SHORT);
        final boolean showBelow = screenPos[1] < estimatedToastHeight;
        if (showBelow) {
            // Show below
            // Offsets are after decorations (e.g. status bar) are factored in
            cheatSheet.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, viewCenterX
                    - screenWidth / 2, screenPos[1] - displayFrame.top + viewHeight);
        } else {
            // Show above
            // Offsets are after decorations (e.g. status bar) are factored in
            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, viewCenterX
                    - screenWidth / 2, displayFrame.bottom - screenPos[1]);
        }
        cheatSheet.show();
    }

    /**
     * Calculate whether a color is light or dark, based on a commonly known
     * brightness formula.
     *
     * @see {@literal http://en.wikipedia.org/wiki/HSV_color_space%23Lightness}
     */
    public static final boolean isColorDark(final int color) {
        return (30 * Color.red(color) + 59 * Color.green(color) + 11 * Color.blue(color)) / 100 <= BRIGHTNESS_THRESHOLD;
    }

    /**
     * Runs a piece of code after the next layout run
     *
     * @param view The {@link View} used.
     * @param runnable The {@link Runnable} used after the next layout run
     */
    @SuppressLint("NewApi")
    public static void doAfterLayout(final View view, final Runnable runnable) {
        final OnGlobalLayoutListener listener = new OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                /* Layout pass done, unregister for further events */
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                runnable.run();
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /**
     * Creates a new instance of the {@link ImageCache} and {@link ImageFetcher}
     *
     * @param activity The {@link Activity} to use.
     * @return A new {@link ImageFetcher} used to fetch images asynchronously.
     */
    public static final ImageFetcher getImageFetcher(final Activity activity) {
        final ImageFetcher imageFetcher = ImageFetcher.getInstance(activity);
        imageFetcher.setImageCache(ImageCache.findOrCreateCache(activity));
        return imageFetcher;
    }

    /**
     * Method that removes the support for HardwareAcceleration from a {@link View}.<br/>
     * <br/>
     * Check AOSP notice:<br/>
     * <pre>
     * 'ComposeShader can only contain shaders of different types (a BitmapShader and a
     * LinearGradient for instance, but not two instances of BitmapShader)'. But, 'If your
     * application is affected by any of these missing features or limitations, you can turn
     * off hardware acceleration for just the affected portion of your application by calling
     * setLayerType(View.LAYER_TYPE_SOFTWARE, null).'</pre>
     *
     * @param v The view
     */
    public static void removeHardwareAccelerationSupport(View v) {
        if (v.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
            v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
   }

    /**
     * Gets the action bar height in pixels
     * @param context
     * @return action bar height in pixels
     */
    public static int getActionBarHeight(Context context) {
        TypedValue tv = new TypedValue();
        View view = new View(context);
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))  {
            return TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        }

        return 0;
    }

    /**
     * Returns a fancy search query cursor
     * @param context
     * @param query query string
     * @return cursor of the results
     */
    public static Cursor createSearchQueryCursor(final Context context, final String query) {
        final Uri uri = Uri.parse("content://media/external/audio/search/fancy/"
                + Uri.encode(query));
        final String[] projection = new String[] {
                BaseColumns._ID, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Media.TITLE, "data1", "data2"
        };

        // no selection/selection/sort args - they are ignored by fancy search anyways
        return context.getContentResolver().query(uri, projection, null, null, null);
    }

    /** make a useful message from an exception without the stack track */
    public static String formatException(String message, Exception e) {
        StringBuilder builder = new StringBuilder();
        if(message != null) {
            builder.append(message);
            if(e != null) { builder.append(" - "); }
        }

        if(e != null) {
            builder.append(e.getClass().getSimpleName());

            String exceptionMessage = e.getMessage();
            if(exceptionMessage != null) {
                builder.append(": ");
                builder.append(exceptionMessage);
            }

            for(Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                builder.append(" (cause ");
                builder.append(cause.getClass().getSimpleName());
                String causeMessage = e.getMessage();
                if(causeMessage != null) {
                    builder.append(": ");
                    builder.append(exceptionMessage);
                }
                builder.append(")");
            }
        }

        return builder.toString();
    }
}
