/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.cyanogenmod.eleven.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.eleven.R;

public class ArtworkProvider {
    private static Type sProviderType;

    private static enum Type {
        None,
        LastFm,
    }

    private static Type getArtworkProviderType(Context context) {
        if (sProviderType == null) {
            String providerType = context.getString(R.string.artwork_provider);
            if (TextUtils.isEmpty(providerType)) {
                sProviderType = Type.None;
            } else if (providerType.equals(context.getString(R.string.artwork_provider_lastfm))) {
                sProviderType = Type.LastFm;
            } else {
                Log.e(ArtworkProvider.class.getSimpleName(), "Unexpected Artwork Provider Type: " +
                        providerType + " - defaulting to None");
                sProviderType = Type.None;
            }
        }

        return sProviderType;
    }

    public static boolean hasProvider(Context context) {
        return getArtworkProviderType(context) != Type.None;
    }

    public static boolean usingLastFm(Context context) {
        return getArtworkProviderType(context) == Type.LastFm;
    }
}
