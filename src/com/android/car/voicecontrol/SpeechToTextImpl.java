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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sample implementation of voice recognition module. This implementation uses Google Assistant's
 * voice recognizer.
 *
 * TODO: Replace this with pre-recorded messages as we can't depend on Google Assistant in AOSP.
 */
public class SpeechToTextImpl implements SpeechToText {
    private static final String TAG = "Mica.SpeechToTextImpl";

    private Listener mListener;
    private final SpeechRecognizer mRecognizer;
    private final Intent mRecognizerIntent;
    private final RecognitionListener mRecognizerListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Speech recognition ready");
            if (mListener != null) {
                mListener.onRecognitionStarted();
            }
        }

        @Override
        public void onBeginningOfSpeech() {
            if (mListener != null) {
                mListener.onPartialRecognition(new ArrayList<>());
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Ignored
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Ignored
        }

        @Override
        public void onEndOfSpeech() {
            // Ignored
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "Speech recognition finished with error: " + getErrorMsg(error));
            if (mListener != null) {
                mListener.onRecognitionFinished(new ArrayList<>());
                stopListening();
            }
        }

        private String getErrorMsg(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    return "ERROR_NETWORK_TIMEOUT";
                case SpeechRecognizer.ERROR_NETWORK:
                    return "ERROR_NETWORK";
                case SpeechRecognizer.ERROR_AUDIO:
                    return "ERROR_AUDIO";
                case SpeechRecognizer.ERROR_SERVER:
                    return "ERROR_SERVER";
                case SpeechRecognizer.ERROR_CLIENT:
                    return "ERROR_CLIENT";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    return "ERROR_SPEECH_TIMEOUT";
                case SpeechRecognizer.ERROR_NO_MATCH:
                    return "ERROR_NO_MATCH";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    return "ERROR_RECOGNIZER_BUSY";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    return "ERROR_INSUFFICIENT_PERMISSIONS";
            }
            return "ERROR_UNKNOWN";
        }

        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "Speech recognition finished with results: " + results.toString());
            if (mListener != null) {
                mListener.onRecognitionFinished(getResultsInConfidenceOrder(results));
                stopListening();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (mListener != null) {
                mListener.onPartialRecognition(getResultsInConfidenceOrder(partialResults));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "Speech recognition event: " + eventType + ", params: " + params);
            // Ignored
        }

        private List<String> getResultsInConfidenceOrder(Bundle partialResults) {
            List<String> values =
                    partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] scores =
                    partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (scores == null || values == null || scores.length != values.size()) {
                return values != null ? values : new ArrayList<>();
            }
            List<Pair<String, Float>> resultsWithConfidence = IntStream.range(0, values.size())
                    .mapToObj(i -> Pair.create(values.get(i), scores[i]))
                    .collect(Collectors.toList());
            Log.d(TAG, "Results confidences: " + resultsWithConfidence.stream()
                    .map(p -> String.format(Locale.US, "%s [%01.2f]", p.first, p.second))
                    .collect(Collectors.joining(", ")));
            return resultsWithConfidence.stream()
                    .sorted((o1, o2) -> -o1.second.compareTo(o2.second))
                    .map(p -> p.first)
                    .collect(Collectors.toList());
        }
    };

    public SpeechToTextImpl(Context context) {
        // Use system default recognition service
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        mRecognizer.setRecognitionListener(mRecognizerListener);
        mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mRecognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mRecognizerIntent.putExtra(
                RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    }

    @Override
    public void startListening(Listener listener) {
        if (mListener != null) {
            stopListening();
        }
        mListener = listener;
        mRecognizer.startListening(mRecognizerIntent);
    }

    @Override
    public void stopListening() {
        mListener = null;
        mRecognizer.cancel();
    }

    @Override
    public void destroy() {
        mListener = null;
        mRecognizer.destroy();
    }
}
