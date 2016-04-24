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

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class SrtParser {
    private static final String TAG = SrtParser.class.getSimpleName();

    public static class SrtEntry {
        public long mStartTimeMs;
        public long mEndTimeMs;
        String mLine;
    }

    /**
     * The SubRip file format should contain entries that follow the following format:
     *
     * 1. A numeric counter identifying each sequential subtitle
     * 2. The time that the subtitle should appear on the screen, followed by --> and the time it
     *    should disappear
     * 3. Subtitle text itself on one or more lines
     * 4. A blank line containing no text, indicating the end of this subtitle
     *
     * The timecode format should be hours:minutes:seconds,milliseconds with time units fixed to two
     * zero-padded digits and fractions fixed to three zero-padded digits (00:00:00,000).
     */
    public static ArrayList<SrtEntry> getSrtEntries(File f) {
        ArrayList<SrtEntry> ret = null;
        FileReader reader = null;
        BufferedReader br = null;

        try {
            reader = new FileReader(f);
            br = new BufferedReader(reader);

            String header;
            // since we don't really care about the 1st line of each entry (the # val) then read
            // and discard it
            while ((header = br.readLine()) != null) {
                // discard subtitle number
                header = br.readLine();
                if (header == null) {
                    break;
                }

                SrtEntry entry = new SrtEntry();

                String[] startEnd = header.split("-->");
                entry.mStartTimeMs = parseMs(startEnd[0]);
                entry.mEndTimeMs = parseMs(startEnd[1]);

                StringBuilder subtitleBuilder = new StringBuilder("");
                String s = br.readLine();

                if (!TextUtils.isEmpty(s)) {
                    subtitleBuilder.append(s);

                    while (!((s = br.readLine()) == null || s.trim().equals(""))) {
                        subtitleBuilder.append("\n").append(s);
                    }
                }

                entry.mLine = subtitleBuilder.toString();

                if (ret == null) {
                    ret = new ArrayList<SrtEntry>();
                }

                ret.add(entry);
            }
        } catch (NumberFormatException nfe) {
            // The file isn't a valid srt format
            Log.e(TAG, nfe.getMessage(), nfe);
            ret = null;
        } catch (IOException ioe) {
            // shouldn't happen
            Log.e(TAG, ioe.getMessage(), ioe);
            ret = null;
        } catch (ArrayIndexOutOfBoundsException e) {
            // if the time is malformed
            Log.e(TAG, e.getMessage());
            ret = null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }

            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }

        return ret;
    }

    private static long parseMs(String in) {
        String[] timeArray = in.split(":");
        long hours = Long.parseLong(timeArray[0].trim());
        long minutes = Long.parseLong(timeArray[1].trim());

        String[] secondTimeArray = timeArray[2].split(",");

        long seconds = Long.parseLong(secondTimeArray[0].trim());
        long millies = Long.parseLong(secondTimeArray[1].trim());

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;
    }
}
