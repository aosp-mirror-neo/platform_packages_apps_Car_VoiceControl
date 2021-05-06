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

import android.annotation.NonNull;

import java.util.List;

/**
 * Minimal speech-to-text module interface.
 */
public interface SpeechToText {
    /**
     * Callbacks
     */
    interface Listener {
        /**
         * Called when voice recognition is started
         */
        void onRecognitionStarted();

        /**
         * Called when a partial result is detected.
         */
        void onPartialRecognition(@NonNull List<String> results);

        /**
         * Called when recognition is finished.
         * @param strings potential detected texts, or empty list if recognition was interrupted or
         *                unsuccessful.
         */
        void onRecognitionFinished(@NonNull List<String> results);
    }

    /**
     * Starts recognition
     */
    void startListening(Listener listener);

    /**
     * Cancels recognition
     */
    void stopListening();

    /**
     * Releases internal resources
     */
    void destroy();
}
