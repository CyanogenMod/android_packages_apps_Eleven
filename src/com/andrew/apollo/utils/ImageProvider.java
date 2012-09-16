package com.andrew.apollo.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;
import com.andrew.apollo.R;
import com.andrew.apollo.tasks.GetAlbumImageTask;
import com.andrew.apollo.tasks.GetArtistImageTask;
import com.andrew.apollo.tasks.GetBitmapTask;

import java.util.*;

public class ImageProvider implements GetBitmapTask.OnBitmapReadyListener {

    private ImageCache memCache = new ImageCache(ImageCache.DEFAULT_SIZE);

    private Map<String, Set<ImageView>> pendingImagesMap = new HashMap<String, Set<ImageView>>();

    private Set<String> unavailable = new HashSet<String>();

    public ImageProvider() {

    }

    public void setArtistImage(ImageView imageView, String artist) {
        String tag = getArtistTag(artist);
        if (!setCachedBitmap(imageView, tag)) {
            asyncLoad(tag, imageView, new GetArtistImageTask(artist, this, tag, imageView.getContext()));
        }
    }

    public void setAlbumImage(ImageView imageView, String artist, String album) {
        String tag = getAlbumTag(artist, album);
        if (!setCachedBitmap(imageView, tag)) {
            asyncLoad(tag, imageView, new GetAlbumImageTask(artist, album, this, tag, imageView.getContext()));
        }
    }

    private boolean setCachedBitmap(ImageView imageView, String tag) {
        if (unavailable.contains(tag)) {
            handleBitmapUnavailable(imageView, tag);
            return true;
        }
        Bitmap bitmap = memCache.get(tag);
        if (bitmap == null)
            return false;
        imageView.setTag(tag);
        imageView.setImageBitmap(bitmap);
        return true;
    }

    private void handleBitmapUnavailable(ImageView imageView, String tag) {
        imageView.setTag(tag);
        imageView.setImageDrawable(null);
    }

    private void setLoadedBitmap(ImageView imageView, Bitmap bitmap, String tag) {
        if (!tag.equals(imageView.getTag()))
            return;

        final TransitionDrawable transition = new TransitionDrawable(new Drawable[]{
                new ColorDrawable(android.R.color.transparent),
                new BitmapDrawable(imageView.getResources(), bitmap)
        });

        imageView.setImageDrawable(transition);
        final int duration = imageView.getResources().getInteger(R.integer.image_fade_in_duration);
        transition.startTransition(duration);
    }

    private void asyncLoad(String tag, ImageView imageView, GetBitmapTask task) {
        Set<ImageView> pendingImages = pendingImagesMap.get(tag);
        if (pendingImages == null) {
            pendingImages = Collections.newSetFromMap(new WeakHashMap<ImageView, Boolean>()); // create weak set
            pendingImagesMap.put(tag, pendingImages);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        pendingImages.add(imageView);
        imageView.setTag(tag);
        imageView.setImageDrawable(null);
    }

    private String getArtistTag(String artist) {
        return artist;
    }

    private String getAlbumTag(String artist, String album) {
        return artist + " - " + album;
    }

    @Override
    public void bitmapReady(Bitmap bitmap, String tag) {
        if (bitmap == null) {
            unavailable.add(tag);
        }
        else
        {
            memCache.put(tag, bitmap);
        }
        Set<ImageView> pendingImages = pendingImagesMap.get(tag);
        if (pendingImages != null) {
            pendingImagesMap.remove(tag);
            for (ImageView imageView : pendingImages) {
                setLoadedBitmap(imageView, bitmap, tag);
            }
        }
    }
}
