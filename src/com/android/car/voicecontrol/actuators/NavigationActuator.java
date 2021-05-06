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
package com.android.car.voicecontrol.actuators;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;

import java.util.Arrays;
import java.util.List;

/**
 * An actuator that interacts with a navigation application.
 */
public class NavigationActuator implements SkillRouter.Actuator {
    private static final String TAG = "Mica.NavigationActuator";
    private Context mContext;

    @Override
    public List<Skill> onCreate(Context context) {
        mContext = context;
        return Arrays.asList(
                new Skill("navigate (to) (.*)", (args, tts) ->
                        startNavigation(args[2], tts)),
                new Skill("cancel navigation", (args, tts) -> cancelNavigation(tts))
        );
    }

    @Override
    public void onDestroy() {
        // Nothing to do
    }

    private boolean startNavigation(String destination, TextToSpeech tts) {
        //TODO(b/187467593): Update NavigationActuator intent to use generic eGMM
        Log.i(TAG, "startNavigation(" + destination + ")");
        tts.speak(R.string.speech_reply_nav_start_not_supported);
        // No op for now
        return true;
    }

    private boolean cancelNavigation(TextToSpeech tts) {
        tts.speak(R.string.speech_reply_nav_cancel_not_supported);
        return true;
    }
}
