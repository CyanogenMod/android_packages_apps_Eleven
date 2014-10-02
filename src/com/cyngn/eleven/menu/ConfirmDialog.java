/* Copyright (C) 2014 Cyngen, Inc. */

package com.cyngn.eleven.menu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.cyngn.eleven.R;

/** Dialog to confirm a non-reversible action */
public class ConfirmDialog extends DialogFragment {
    private static final String TITLE_ID = "titleId";
    private static final String OK_ID = "okId";

    public interface ConfirmCallback {
        public void confirmOk(int requestCode);
    }

    public ConfirmDialog() {}

    /** @param title describes action user is confirming
     *  @param okId text for Ok button */
    public static void show(Fragment target, int requestCode, int titleId, int okId) {
        final ConfirmDialog frag = new ConfirmDialog();
        final Bundle args = new Bundle();
        args.putInt(TITLE_ID, titleId);
        args.putInt(OK_ID, okId);
        frag.setArguments(args);
        frag.setTargetFragment(target, requestCode);
        frag.show(target.getFragmentManager(), "ConfirmDialog");
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        Bundle args = getArguments();
        return new AlertDialog.Builder(getActivity())
            .setTitle(args.getInt(TITLE_ID))
            .setMessage(R.string.cannot_be_undone)
            .setPositiveButton(args.getInt(OK_ID), new OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    Fragment target = getTargetFragment();
                    if (target instanceof ConfirmCallback) {
                        ((ConfirmCallback)target).confirmOk(getTargetRequestCode());
                    }
                    dialog.dismiss();
                }
            }).setNegativeButton(R.string.cancel, new OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    dialog.dismiss();
                }
            }).create();
    }
}