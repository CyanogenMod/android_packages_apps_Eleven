
package com.andrew.apollo.adapters;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import com.andrew.apollo.grid.fragments.QuickQueueFragment;
import com.andrew.apollo.utils.ImageUtils;
import com.andrew.apollo.views.ViewHolderQueue;
import com.androidquery.AQuery;

import java.lang.ref.WeakReference;

/**
 * @author Andrew Neal
 */
public class QuickQueueAdapter extends SimpleCursorAdapter {

    private WeakReference<ViewHolderQueue> holderReference;

    public QuickQueueAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
            int flags) {
        super(context, layout, c, from, to, flags);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        // ViewHolderQueue
        final ViewHolderQueue viewholder;

        if (view != null) {

            viewholder = new ViewHolderQueue(view);
            holderReference = new WeakReference<ViewHolderQueue>(viewholder);
            view.setTag(holderReference.get());

        } else {
            viewholder = (ViewHolderQueue)convertView.getTag();
        }

        // AQuery
        final AQuery aq = new AQuery(view);

        // Artist Name
        String artistName = mCursor.getString(QuickQueueFragment.mArtistIndex);

        // Album name
        String albumName = mCursor.getString(QuickQueueFragment.mAlbumIndex);

        // Track name
        String trackName = mCursor.getString(QuickQueueFragment.mTitleIndex);
        holderReference.get().mTrackName.setText(trackName);

        ImageUtils.setArtistImage(viewholder.mArtistImage, artistName);
        ImageUtils.setAlbumImage(viewholder.mAlbumArt, artistName, albumName);

        return view;
    }
}
