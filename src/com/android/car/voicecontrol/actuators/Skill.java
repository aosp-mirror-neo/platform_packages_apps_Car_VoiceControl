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

import android.util.Log;

import com.android.car.voicecontrol.TextToSpeech;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An actuator skill
 */
public class Skill {
    private static final String TAG = "Mica.Skill";

    private final Pattern mPattern;
    private final Action mAction;

    /**
     * Code to execute when a skill is matched to a recognized speech.
     */
    public interface Action {
        /**
         * Executes the action associated with this skill. Execution receives the arguments
         * detected during the skill pattern matching (see {@link Skill#Skill(String, Action)} and
         * a {@link TextToSpeech} that can be used to verbalize the result.
         *
         * @return true if executing this action completes the user request, or false if this was
         * not a good match and some other skill could be tried instead.
         */
        boolean execute(String[] args, TextToSpeech tts);
    }

    /**
     * Creates a voice control skill. In this naive implementation, skills are triggered by
     * a simple regular expression matching (see {@link Pattern}) on the recognized speech.
     *
     * @param regexp regular expression to match against (refer to {@link Pattern} for syntax
     *               details)
     * @param action action to execute if the regular express matches. This is a function that
     *               receives the recognized groups in the regular expression (see
     *               {@link Matcher#group(int)}), and returns true if the skill is able to
     *               execute the recognized command.
     */
    Skill(String regexp, Action action) {
        mPattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE);
        mAction = action;
    }

    /**
     * If this skill is a good match for the recognized text, the action associated with this skill
     * will be executed.
     *
     * @param text recognized text
     * @param tts {@link TextToSpeech} that will be used to utter the result of the action,
     *                                if matched.
     * @return true if this is a good match for the recognized text, or false if some other skill
     * should be attempted instead.
     */
    boolean tryExecute(String text, TextToSpeech tts) {
        Matcher matcher = mPattern.matcher(text);
        if (!matcher.matches()) {
            Log.d(TAG, "Checking '" + text + "' against '" + mPattern.toString() + "': no match");
            return false;
        }
        String[] arguments = new String[matcher.groupCount() + 1];
        for (int i = 0; i < matcher.groupCount() + 1; i++) {
            arguments[i] = matcher.group(i);
        }
        Log.d(TAG, "Checking '" + text + "' against '" + mPattern.toString()
                + "': matched! Arguments: " + Arrays.toString(arguments));
        return mAction.execute(arguments, tts);
    }
}
