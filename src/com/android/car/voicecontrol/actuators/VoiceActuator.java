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

import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * An actuator that can execute command just using voice utterances.
 */
public class VoiceActuator implements SkillRouter.Actuator {
    @Override
    public List<Skill> onCreate(Context context) {
        return Arrays.asList(
                new Skill("what.* time", (args, tts) -> giveTheTime(tts))
        );
    }

    @Override
    public void onDestroy() {
        // Nothing to do
    }

    private boolean giveTheTime(TextToSpeech tts) {
        tts.speak(R.string.speech_reply_time_okay,
                new SimpleDateFormat("h:m a", Locale.US).format(new Date()));
        return true;
    }
}
