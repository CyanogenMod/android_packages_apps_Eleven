package com.cyanogenmod.eleven.utils;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.cyanogenmod.eleven.R;

/**
 * Ancillary utilities class to customize the appearance of Toast messages
 */
public class CustomToast {

    public static final int LENGTH_LONG = Toast.LENGTH_LONG;
    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;

    private Toast mToast;
    private TextView mTextView;

    public CustomToast(Activity activity, String message) {
        mToast = new Toast( activity.getApplicationContext() );
        LayoutInflater layoutInflater = activity.getLayoutInflater();
        View toastView = layoutInflater.inflate(R.layout.custom_toast, null);
        mToast.setView(toastView);

        mTextView = (TextView) toastView.findViewById(R.id.toast_text_view);
        if (message != null) {
            mTextView.setText(message);
        }

        // set toast location
        // centered with an offset in y expressed as % of display height
        int displayHeight = activity.getWindow().getDecorView().getHeight();
        int heightOffset = (int)(0.30 * displayHeight);
        mToast.setGravity(Gravity.CENTER_HORIZONTAL, 0, heightOffset);

    }

    public static CustomToast makeText(Activity context, String text, int duration) {
        CustomToast customToast = new CustomToast(context, text);
        if (duration == CustomToast.LENGTH_LONG)
            customToast.setDuration(duration);
        else
            customToast.setDuration(CustomToast.LENGTH_SHORT);

        return customToast;
    }

    public void setDuration(int duration) {
        mToast.setDuration(duration);
    }

    public void setMessage(String message) {
        mTextView.setText(message);
    }

    public void show() {
        mToast.show();
    }
}
