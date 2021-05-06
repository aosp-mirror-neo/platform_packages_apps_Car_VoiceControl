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
import android.annotation.Nullable;
import android.annotation.StringRes;

import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal text-to-speech module interface.
 */
public interface TextToSpeech {
    /**
     * Listener used to receive a notification once utterance is finished.
     */
    interface Listener {
        /**
         * Called when TTS is ready.
         */
        default void onReady(TextToSpeech tts) {};

        /**
         * Called when the last utterance requested with {@link TextToSpeech#speak(int, Object...)}
         * has finished.
         * @param successful whether utterance was successful.
         */
        default void onUtteranceDone(boolean successful) {};

        /**
         * Called when the last utterance requires the user to answer a question. Implementors must
         * call {@link TextToSpeech#provideAnswer(List)} once that answer has been
         * captured.
         */
        default void onWaitingForAnswer() {}
    }

    /**
     * A callback to be provided when asking a question to the user
     */
    interface QuestionCallback {
        /**
         * Method invoked when the answer to a question is received
         *
         * @param results Recognized answers in confidence order, or empty list if no answer was
         *               recognized.
         */
        void onResult(@NonNull List<String> results);
    }

    /**
     * Known answer types
     */
    enum AnswerType {
        AFFIRMATIVE,
        NEGATIVE,
    }

    /**
     * Convenient factory method that creates a 'yes/no' question callback.
     *
     * @param consumer A lambda that will receive the yes or no answer.
     * @param retries Number of times we will try to get a yes/no answer from the user. After this
     *                many tries, the application will use the default answer.
     */
    default QuestionCallback createBooleanQuestionCallback(Consumer<Boolean> consumer,
            int retries, boolean defaultAnswer) {
        return new QuestionCallback() {
            private int mRetries = retries;

            @Override
            public void onResult(List<String> results) {
                AnswerType type = getAnswerType(results);
                if (type != AnswerType.AFFIRMATIVE && type != AnswerType.NEGATIVE) {
                    if (mRetries > 0) {
                        mRetries--;
                        ask(this, R.string.speech_reply_yes_no_question_not_understood);
                    } else {
                        // If no answer is understood after a few retries, let's assume a
                        // negative answer
                        consumer.accept(defaultAnswer);
                    }
                } else {
                    consumer.accept(type == AnswerType.AFFIRMATIVE);
                }
            }
        };
    }

    /**
     * Releases internal resources
     */
    void destroy();

    /**
     * Requests the given text to be added to the queue of pending utterances to read out.
     * If this method is called before {@link Listener#onReady(TextToSpeech)}, the speech will be
     * saved and its utterance will be delayed until TTS is ready.
     */
    void speak(String fmt, Object... args);

    /**
     * Similar to {@link #speak(String, Object[])} but it takes a string resource rather than an
     * actual string.
     */
    void speak(@StringRes int stringId, Object... args);

    /**
     * Requests the given text to be added to the queue of pending utterances to read out (similar
     * semantics as {@link #speak(int, Object...)}.
     * Once this is done, instead of calling {@link Listener#onUtteranceDone(boolean)}, it will
     * call {@link Listener#onWaitingForAnswer()}, to wait for the user to provide an answer. Once
     * the answer is collected (using {@link #provideAnswer(List)}), the answer will be
     * given through the {@link QuestionCallback}.
     */
    void ask(QuestionCallback callback, String fmt, Object... args);

    /**
     * Similar to {@link #ask(QuestionCallback, String, Object...)} but it takes a string
     * resource rather than an actual string.
     */
    void ask(QuestionCallback callback, @StringRes int resId, Object... args);

    /**
     * @return true if the last utterance was a question that is waiting to be answered
     */
    boolean isWaitingForAnswer();

    /**
     * Provides an answer to last uttered questions. Calling this method could cause the
     * {@link QuestionCallback#onResult(List)} method of the last
     * {@link #ask(QuestionCallback, int, Object...)} invocation to be called.
     */
    void provideAnswer(@NonNull List<String> results);

    /**
     * @return the type of answer received, or null if the answer is not recognized
     */
    @Nullable AnswerType getAnswerType(List<String> strings);

    /**
     * @return the list of voices supported by this TTS. Can only be called after
     * {@link Listener#onReady(TextToSpeech)}
     */
    List<String> getVoices();

    /**
     * Sets the voice that should be used. The value provided should be one of items returned by
     * {@link #getVoices()}
     */
    void setSelectedVoice(String name);
}
