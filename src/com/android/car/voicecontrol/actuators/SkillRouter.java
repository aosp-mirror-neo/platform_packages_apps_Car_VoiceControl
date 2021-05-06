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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fake implementation NLP and command recognition. This is a very simplified implementation, only
 * to illustrate the execution of a limited set of commands with a fixed set of recognized phrases.
 */
public class SkillRouter {
    private static final List<Actuator> ACTUATORS = Arrays.asList(
            new NavigationActuator(),
            new VoiceActuator(),
            new MediaActuator(),
            new RadioActuator(),
            new CarActuator(),
            new CommActuator());
    private List<Skill> mSkills = new ArrayList<>();

    /**
     * An encapsulation of related skills.
     */
    public interface Actuator {
        /**
         * Initializes the actuator and returns a list of skills it is capable of performing. This
         * method will be invoked only once.
         */
        List<Skill> onCreate(Context context);

        /**
         * Called when it is time for the actuator to release any resources it might be holding.
         * This method is invoked only once. The object won't be usable after this method returns.
         */
        void onDestroy();
    }

    public SkillRouter(Context context) {
        for (Actuator actuator : ACTUATORS) {
            mSkills.addAll(actuator.onCreate(context));
        }
    }

    /**
     * Attempts to process the received text. This is a naive and inefficient NLP processing, used
     * for illustration purposes only.
     * @param recognizedTexts list of recognized text in confidence level order
     */
    public void process(List<String> recognizedTexts, TextToSpeech tts) {
        for (String recognizedText : recognizedTexts) {
            for (Skill skill : mSkills) {
                if (skill.tryExecute(recognizedText, tts)) {
                    return;
                }
            }
        }
        tts.speak(R.string.speech_reply_unrecognized_cmd);
    }

    /**
     * Called to release resources any actuator might be holding
     */
    public void destroy() {
        for (Actuator actuator : ACTUATORS) {
            actuator.onDestroy();
        }
    }
}
