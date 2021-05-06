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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.car.ui.preference.PreferenceFragment;

import java.lang.ref.WeakReference;

/**
 * Sample voice interaction service settings activity
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "Mica.SettingsActivity";
    private InteractionServiceClient mInteractionService;
    private TextToSpeech mTextToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInteractionService = new InteractionServiceClient(this) {
            @Override
            void onConnected() {
                // Display the fragment as the main content.
                if (savedInstanceState == null) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, new SettingsFragment(), "settings")
                            .commitNow();
                }
            }

            @Override
            void onSetupChanged() {
                SettingsFragment fragment = (SettingsFragment) getSupportFragmentManager()
                        .findFragmentByTag("settings");
                if (fragment != null) {
                    fragment.updateSignInState();
                }
            }
        };
        mInteractionService.connect();
        mTextToSpeech = new TextToSpeechImpl(this, new TextToSpeech.Listener() {
            @Override
            public void onReady(TextToSpeech tts) {
                SettingsFragment fragment = (SettingsFragment) getSupportFragmentManager()
                        .findFragmentByTag("settings");
                if (fragment != null) {
                    fragment.updateVoices();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        mInteractionService.disconnect();
        mTextToSpeech.destroy();
        super.onDestroy();
    }

    /**
     * {@link PreferenceFragment} implementation for voice interaction service settings
     */
    public static class SettingsFragment extends PreferenceFragment {
        private WeakReference<SettingsActivity> mActivity;

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mActivity = new WeakReference<>((SettingsActivity) context);
        }

        @Override
        public void onDetach() {
            mActivity = null;
            super.onDetach();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            updateSignInState();
            updateVoices();
        }

        private void updateSignInState() {
            boolean isSignedIn = mActivity.get().mInteractionService.hasUsername();
            Log.d(TAG, "updateSignInState: signed in " + isSignedIn);
            Preference signInOutButton = getPreferenceManager().findPreference("sign_in_out");
            assert signInOutButton != null;
            signInOutButton.setTitle(getString(isSignedIn
                    ? R.string.preference_sign_out
                    : R.string.preference_sign_in));
            signInOutButton.setSummary(getString(isSignedIn
                    ? R.string.preference_sign_out
                    : R.string.preference_sign_in));
            signInOutButton.setOnPreferenceClickListener(p -> {
                if (mActivity.get().mInteractionService.hasUsername()) {
                    mActivity.get().mInteractionService.setUsername(null);
                } else {
                    Intent intent = new Intent(getContext(), SignInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return true;
            });
        }

        private void updateVoices() {
            ListPreference pref = getPreferenceManager()
                    .findPreference(PreferencesController.PREF_KEY_VOICE);
            String[] voices = mActivity.get().mTextToSpeech.getVoices().toArray(new String[0]);
            pref.setEntries(voices);
            pref.setEntryValues(voices);
            pref.setOnPreferenceChangeListener((p, value) -> {
                mActivity.get().mInteractionService.setVoice((String) value);
                return true;
            });
        }
    }
}
