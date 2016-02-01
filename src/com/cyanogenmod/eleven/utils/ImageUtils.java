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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.cyanogenmod.eleven.cache.ImageCache;
import com.cyanogenmod.eleven.cache.ImageWorker;
import com.cyanogenmod.eleven.lastfm.ImageSize;
import com.cyanogenmod.eleven.lastfm.MusicEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageUtils {
    private static final String DEFAULT_HTTP_CACHE_DIR = "http"; //$NON-NLS-1$

    public static final int IO_BUFFER_SIZE_BYTES = 1024;

    private static final int DEFAULT_MAX_IMAGE_HEIGHT = 1024;

    private static final int DEFAULT_MAX_IMAGE_WIDTH = 1024;

    private static AtomicInteger sInteger = new AtomicInteger(0);

    /**
     * Gets the image url based on the imageType
     * @param artistName The artist name param used in the Last.fm API.
     * @param albumName The album name param used in the Last.fm API.
     * @param imageType The type of image URL to fetch for.
     * @return The image URL for an artist image or album image.
     */
    public static String processImageUrl(final Context context, final String artistName,
                                         final String albumName, final ImageWorker.ImageType imageType) {
        switch (imageType) {
            case ARTIST:
                // Disable last.fm calls - TODO: Find an alternative artwork provider that has
                // the proper license rights for artwork
                /*if (!TextUtils.isEmpty(artistName)) {
                    if (PreferenceUtils.getInstance(context).downloadMissingArtistImages()) {
                        final Artist artist = Artist.getInfo(context, artistName);
                        if (artist != null) {
                            return getBestImage(artist);
                        }
                    }
                }*/
                break;
            case ALBUM:
                // Disable last.fm calls - TODO: Find an alternative artwork provider that has
                // the proper license rights for artwork
                /*if (!TextUtils.isEmpty(artistName) && !TextUtils.isEmpty(albumName)) {
                    if (PreferenceUtils.getInstance(context).downloadMissingArtwork()) {
                        final Artist correction = Artist.getCorrection(context, artistName);
                        if (correction != null) {
                            final Album album = Album.getInfo(context, correction.getName(),
                                    albumName);
                            if (album != null) {
                                return getBestImage(album);
                            }
                        }
                    }
                }*/
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * Downloads the bitmap from the url and returns it after some processing
     *
     * @param key The key to identify which image to process, as provided by
     *            {@link ImageWorker#loadImage(mKey, android.widget.ImageView)}
     * @return The processed {@link Bitmap}.
     */
    public static Bitmap processBitmap(final Context context, final String url) {
        if (url == null) {
            return null;
        }
        final File file = downloadBitmapToFile(context, url, DEFAULT_HTTP_CACHE_DIR);
        if (file != null) {
            // Return a sampled down version
            final Bitmap bitmap = decodeSampledBitmapFromFile(file.toString());
            file.delete();
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    /**
     * Decode and sample down a {@link Bitmap} from a file to the requested
     * width and height.
     *
     * @param filename The full path of the file to decode
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return A {@link Bitmap} sampled down from the original with the same
     *         aspect ratio and dimensions that are equal to or greater than the
     *         requested width and height
     */
    public static Bitmap decodeSampledBitmapFromFile(final String filename) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, DEFAULT_MAX_IMAGE_WIDTH,
                DEFAULT_MAX_IMAGE_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filename, options);
    }

    /**
     * Calculate an inSampleSize for use in a
     * {@link android.graphics.BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This
     * implementation calculates the closest inSampleSize that will result in
     * the final decoded bitmap having a width and height equal to or larger
     * than the requested width and height. This implementation does not ensure
     * a power of 2 is returned for inSampleSize which can be faster when
     * decoding but results in a larger bitmap which isn't as useful for caching
     * purposes.
     *
     * @param options An options object with out* params already populated (run
     *            through a decode* method with inJustDecodeBounds==true
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    public static final int calculateInSampleSize(final BitmapFactory.Options options,
                                                  final int reqWidth, final int reqHeight) {
        /* Raw height and width of image */
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger
            // inSampleSize).

            final float totalPixels = width * height;

            /* More than 2x the requested pixels we'll sample down further */
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    private static String getBestImage(MusicEntry e) {
        final ImageSize[] QUALITY = {ImageSize.EXTRALARGE, ImageSize.LARGE, ImageSize.MEDIUM,
                ImageSize.SMALL, ImageSize.UNKNOWN};
        for(ImageSize q : QUALITY) {
            String url = e.getImageURL(q);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Download a {@link Bitmap} from a URL, write it to a disk and return the
     * File pointer. This implementation uses a simple disk cache.
     *
     * @param context The context to use
     * @param urlString The URL to fetch
     * @return A {@link File} pointing to the fetched bitmap
     */
    public static final File downloadBitmapToFile(final Context context, final String urlString,
                                                  final String uniqueName) {
        final File cacheDir = ImageCache.getDiskCacheDir(context, uniqueName);

        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }

        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;

        try {
            // increment the number to not collisions on the temp file name.  A collision can
            // potentially cause up to 50s on the first creation of the temp file but not on
            // subsequent ones for some reason.
            int number = sInteger.getAndIncrement() % 10;
            final File tempFile = File.createTempFile("bitmap" + number, null, cacheDir); //$NON-NLS-1$

            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection)url.openConnection();
            if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            int contentLength = urlConnection.getContentLength();
            final InputStream in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE_BYTES);
            out = new BufferedOutputStream(new FileOutputStream(tempFile), IO_BUFFER_SIZE_BYTES);

            final byte[] buffer = new byte[IO_BUFFER_SIZE_BYTES];
            int numBytes;
            while ((numBytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, numBytes);
                contentLength -= numBytes;
            }

            // valid values for contentLength are either -ve (meaning it wasn't set) or 0
            // if it is  > 0 that means we got a value but didn't fully download the content
            if (contentLength > 0) {
                return null;
            }

            return tempFile;
        } catch (final IOException ignored) {
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (final IOException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Scale the bitmap to an image view. The bitmap will fill the image view bounds. The bitmap will be scaled
     * while maintaining the aspect ratio and cropped if it exceeds the image-view bounds.
     */
    public static Bitmap scaleBitmapForImageView(Bitmap src, ImageView imageView) {
        if (src == null || imageView == null) {
            return src;
        }
        // get bitmap properties
        int srcHeight = src.getHeight();
        int srcWidth = src.getWidth();

        // get image view bounds
        int viewHeight = imageView.getHeight();
        int viewWidth = imageView.getWidth();

        int deltaWidth = viewWidth - srcWidth;
        int deltaHeight = viewHeight - srcHeight;

        if (deltaWidth <= 0 && deltaWidth <= 0)     // nothing to do if src bitmap is bigger than image-view
            return src;

        // scale bitmap along the dimension that is lacking the greatest
        float scale = Math.max( ((float)viewWidth) / srcWidth, ((float)viewHeight) / srcHeight);

        // calculate the new bitmap dimensions
        int dstHeight = (int) Math.ceil(srcHeight * scale);
        int dstWidth = (int) Math.ceil(srcWidth * scale);
        Bitmap scaledBitmap =  Bitmap.createScaledBitmap(src, dstWidth, dstHeight, false);

        return Bitmap.createBitmap(scaledBitmap, 0, 0, viewWidth, viewHeight);

    }
}
