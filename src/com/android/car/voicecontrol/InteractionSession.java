/**
 * Copyright (C) 2020 The Android Open Source Project
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
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.android.car.assist.CarVoiceInteractionSession;

/**
 * Sample interaction session. Most of the voice interaction business logic is implemented here.
 * This sample interaction session uses the {@link SpeechRecognizer} API to implement STT. Other
 * implementations might choose to access the microphone directly and stream the audio to their
 * backend services.
 */
public class InteractionSession extends CarVoiceInteractionSession {
    private static final String TAG = "Mica.Session";

    public InteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onPrepareShow(Bundle args, int showFlags) {
        super.onPrepareShow(args, showFlags);
        setUiEnabled(false);
    }

    @Override
    protected void onShow(String action, Bundle args, int showFlags) {
        Log.i(TAG, "onShow(action: " + action + ", args: " + LogUtils.parcelableToString(args)
                + ", flags: " + LogUtils.flagsToString(showFlags) + ")");
        closeSystemDialogs();
        Intent intent = new Intent(getContext(), VoicePlateActivity.class);
        intent.putExtra(VoicePlateActivity.EXTRA_ACTION, action);
        intent.putExtra(VoicePlateActivity.EXTRA_ARGS, args);
        startAssistantActivity(intent);
    }
}
