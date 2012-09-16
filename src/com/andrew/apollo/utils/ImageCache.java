package com.andrew.apollo.utils;

import android.graphics.Bitmap;
import android.util.LruCache;

public class ImageCache extends LruCache<String, Bitmap> {

    public final static int DEFAULT_SIZE = 1024 * 1024 * 16;

    public ImageCache(int maxSize) {
        super(maxSize);
    }

    /**
     * Measure item size in bytes rather than units which is more practical for a bitmap
     * cache
     */
    @Override
    protected int sizeOf(String key, Bitmap bitmap) {
        return bitmap.getByteCount();
    }
}
