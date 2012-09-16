package com.andrew.apollo.tasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.andrew.apollo.lastfm.api.Album;
import com.andrew.apollo.lastfm.api.ImageSize;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.ImageUtils;

import java.io.File;

import static com.andrew.apollo.Constants.LASTFM_API_KEY;

public class GetAlbumImageTask extends GetBitmapTask {

    private final String TAG = "GetArtistImageTask";

    private String mArtist;

    private String mAlbum;

    public GetAlbumImageTask(String artist, String album, OnBitmapReadyListener listener, String tag, Context context) {
        super(listener, tag, context);
        mArtist = artist;
        mAlbum = album;
    }

    @Override
    protected File getFile(Context context, String extension) {
        String albumPart = ApolloUtils.escapeForFileSystem(mAlbum);
        String artistPart = ApolloUtils.escapeForFileSystem(mArtist);

        if (albumPart == null || artistPart == null) {
            Log.e(TAG, "Can't create file name for: " + mAlbum + " " + mArtist);
            return null;
        }

        return new File(context.getExternalFilesDir(null), artistPart + " - " + albumPart + extension);
    }

    @Override
    protected String getImageUrl() {
        try {
            Album album = Album.getInfo(mArtist, this.mAlbum, LASTFM_API_KEY);
            if (album == null) {
                if (ImageUtils.DEBUG) Log.w(TAG, "Album not found: " + mArtist + " - " + this.mAlbum);
                return null;
            }
            return album.getImageURL(ImageSize.LARGE); //TODO: ensure that there is an image available in the specified size
        } catch (Exception e) {
            if (ImageUtils.DEBUG) Log.w(TAG, "Error when retrieving album image url", e);
            return null;
        }
    }
}
