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

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;

import com.cyanogenmod.eleven.MusicPlaybackService;
import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.utils.MusicUtils;

public class SleepModeDialog extends DialogFragment {

    private static AlarmManager alarmManager;
    private static Context context;
    private static PendingIntent pendingIntent;

    private static String TAG = "SleepTimerDialog";

    private static final long mMill = 60 * 1000;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        context = getActivity().getBaseContext();
        Intent action = new Intent(MusicPlaybackService.SLEEP_MODE_STOP_ACTION);
        ComponentName serviceName = new ComponentName(context, MusicPlaybackService.class);
        action.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context, 4, action, 0);
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(
                context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.sleep_mode_time_selector, null);
        int minutes = Integer.valueOf(getString(R.string.default_interval));
        final TextView tvPopUpTime = (TextView) view.findViewById(R.id.pop_up_time);
        tvPopUpTime.setText(String.valueOf(minutes));
        final SeekBar sBar = (SeekBar) view.findViewById(R.id.seekbar);
        sBar.setProgress(minutes - 1);
        sBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                tvPopUpTime.setText(String.valueOf(arg1 + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {

            }
        });
        builder.setTitle(R.string.select_quit_time);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                long timeLeft = (sBar.getProgress() + 1) * mMill;
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeLeft + System.currentTimeMillis(),
                        pendingIntent);
                MusicUtils.setSleepMode(true);
                Toast.makeText(context,
                        String.format(getString(R.string.quit_warining), sBar.getProgress() + 1),
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });
        builder.setView(view);
        return builder.create();
    }

    public static void show(FragmentManager parent) {
        final SleepModeDialog dialog = new SleepModeDialog();
        if (MusicUtils.getSleepMode()) {
            alarmManager.cancel(pendingIntent);
            Toast.makeText(context, R.string.cancel_sleep_mode, Toast.LENGTH_SHORT).show();
            MusicUtils.setSleepMode(false);
        } else {
            dialog.show(parent, TAG);
        }
    }
}
