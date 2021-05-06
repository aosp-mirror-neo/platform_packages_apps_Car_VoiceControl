/**
 * Copyright (C) 2021 The Android Open Source Project
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
 * limitations under the License.
 */
package com.android.car.voicecontrol;

import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * Minimal implementation of a preferences controller.
 */
public class PreferencesController {
    private SharedPreferences mPreferences;
    private static PreferencesController sInstance;

    public static final String PREF_KEY_USER_NAME = "user_name";
    public static final String PREF_KEY_VOICE = "voice";

    private PreferencesController(Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * @return a singleton instance of {@link PreferencesController}
     */
    public static PreferencesController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PreferencesController(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * @return whether the user has signed or not
     */
    public boolean isUserSignedIn() {
        return getUsername() != null;
    }

    /**
     * Stores the username of the currently logged user. Set this to null if no user is currently
     * logged in.
     */
    public void setUsername(String username) {
        mPreferences.edit()
                .putString(PREF_KEY_USER_NAME, username)
                .apply();
    }

    /**
     * @return the user name of the authenticated user
     */
    public String getUsername() {
        return mPreferences.getString(PREF_KEY_USER_NAME, null);
    }

    /**
     * Sets the preferred voice to use for text-to-speech.
     *
     * @param name Should be the name of one of the voices returned by
     * {@link android.speech.tts.TextToSpeech#getVoices()}
     */
    public void setVoice(@Nullable String name) {
        mPreferences.edit()
                .putString(PREF_KEY_VOICE, name)
                .apply();
    }

    /**
     * @return preferred voice name or null if there is no selection
     */
    public String getVoice() {
        return mPreferences.getString(PREF_KEY_VOICE, null);
    }
}
