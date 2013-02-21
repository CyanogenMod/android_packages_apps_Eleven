package com.andrew.apollo.tasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.ImageUtils;

import java.io.File;
import java.lang.ref.WeakReference;

public abstract class GetBitmapTask extends AsyncTask<String, Integer, Bitmap> {

    private static final String TAG = "GetBitmapTask";

    private static final String EXTENSION_JPG = ".jpg";
    private static final String EXTENSION_PNG = ".png";
    private static final String EXTENSION_GIF = ".gif";

    private static final String[] IMAGE_EXTENSIONS = new String[]{EXTENSION_JPG, EXTENSION_PNG, EXTENSION_GIF};

    private WeakReference<OnBitmapReadyListener> mListenerReference;

    private WeakReference<Context> mContextReference;

    private String mTag;

    public GetBitmapTask(OnBitmapReadyListener listener, String tag, Context context) {
        mListenerReference = new WeakReference<OnBitmapReadyListener>(listener);
        mContextReference = new WeakReference<Context>(context);
        mTag = tag;
    }

    @Override
    protected Bitmap doInBackground(String... ignored) {
        Context context = mContextReference.get();
        if (context == null) {
            return null;
        }

        if (ImageUtils.DEBUG) Log.v(TAG, "Get image for: " + mTag);

        File file = findCachedFile(context);

        if (file == null && !isCancelled()) {
            file = downloadImage(context);
        }

        if (file == null || isCancelled()) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (bitmap == null) {
            if (ImageUtils.DEBUG) Log.w(TAG, "Error decoding bitmap: " + file.getAbsolutePath());
            return null;
        }

        if (ImageUtils.DEBUG) Log.d(TAG, "Bitmap decoded: " + mTag + " size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
        return bitmap;
    }

    protected abstract String getImageUrl();

    protected abstract File getFile(Context context, String extension);

    private File findCachedFile(Context context) {
        for (String extension : IMAGE_EXTENSIONS) {
            File file = getFile(context, extension);
            if (file == null) {
                return null;
            }
            if (file.exists()) {
                if (ImageUtils.DEBUG) Log.d(TAG, "Cached file found: " + file.getAbsolutePath());
                return file;
            }
        }
        return null;
    }

    private File downloadImage(Context context) {
        String url = getImageUrl();
        if (url == null || url.isEmpty()) {
            if (ImageUtils.DEBUG) Log.w(TAG, "No URL received for: " + mTag);
            return null;
        }
        File file = getFile(context, getExtension(url));
        if (ImageUtils.DEBUG) Log.v(TAG, "Downloading " + url + " to " + file.getAbsolutePath());
        ApolloUtils.downloadFile(url, file);
        if (file.exists()) {
            if (ImageUtils.DEBUG) Log.v(TAG, "Image downloaded: " + mTag);
            return file;
        }
        if (ImageUtils.DEBUG) Log.w(TAG, "Error downloading a " + url + " to " + file.getAbsolutePath());
        return null;
    }

    protected String getExtension(String url) {
        for (String extension : IMAGE_EXTENSIONS) {
            if (url.endsWith(extension))
                return extension;
        }
        return EXTENSION_JPG;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        if (bitmap != null && !isCancelled()) {
            OnBitmapReadyListener listener = mListenerReference.get();
            if (listener != null) {
                listener.bitmapReady(bitmap, mTag);
            }
        }
    }

    public static interface OnBitmapReadyListener {
        public void bitmapReady(Bitmap bitmap, String tag);
    }
}
