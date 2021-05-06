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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Sample {@link android.speech.RecognitionService}. A real implementation would obtain access to
 * the microphone and stream audio to either a backend service or an offline on-device voice
 * recognition model. This sample code just pretends to have detected a set of pre-recorded phrases.
 */
public class RecognitionService extends android.speech.RecognitionService {
    private static final String TAG = "Mica.RecognitionService";
    private Callback mCurrentListener;
    private Handler mHandler = new Handler();
    private Runnable mExecutionTask = this::onExecutionComplete;
    private String[] mPrerecordedPhrases;
    private int mPrerecordedPhraseIndex;

    @Override
    public void onCreate() {
        super.onCreate();
        mPrerecordedPhrases = getResources().getStringArray(R.array.recognizer_phrases);
        mPrerecordedPhraseIndex = 0;
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        Log.d(TAG, "onStartListening(recognizerIntent: " + recognizerIntent
                        + ", listener: " + listener);
        if (mCurrentListener != null) {
            mHandler.removeCallbacks(mExecutionTask);
            sendEndOfSpeech();
            sendResults(null);
        }
        mCurrentListener = listener;
        sendBeginningOfSpeech();
        mHandler.postDelayed(mExecutionTask, 1000);
    }

    @Override
    protected void onCancel(Callback listener) {
        Log.d(TAG, "onCancel(listener: " + listener);
        if (mCurrentListener == null || listener != mCurrentListener) {
            return;
        }
        mHandler.removeCallbacks(mExecutionTask);
        mCurrentListener = null;
    }

    @Override
    protected void onStopListening(Callback listener) {
        Log.d(TAG, "onStopListening(listener: " + listener);
        if (mCurrentListener == null || listener != mCurrentListener) {
            return;
        }
        mHandler.removeCallbacks(mExecutionTask);
        sendEndOfSpeech();
        sendResults(null);
        mCurrentListener = null;
    }

    private void onExecutionComplete() {
        if (mCurrentListener != null) {
            sendEndOfSpeech();
            sendResults(getNextResult());
        }
    }

    private void sendBeginningOfSpeech() {
        try {
            mCurrentListener.beginningOfSpeech();
            Log.d(TAG, "Sent beginningOfSpeech");
        } catch (RemoteException e) {
            Log.e(TAG, "Error initiating speech", e.getCause());
        }
    }

    private void sendEndOfSpeech() {
        try {
            mCurrentListener.endOfSpeech();
            Log.d(TAG, "Sent endOfSpeech");
        } catch (RemoteException e) {
            Log.e(TAG, "Error cancelling speech", e.getCause());
        }
    }

    private String getNextResult() {
        String currentString = mPrerecordedPhrases[mPrerecordedPhraseIndex++];
        mPrerecordedPhraseIndex %= mPrerecordedPhrases.length;
        return currentString;
    }

    private void sendResults(@Nullable String result) {
        try {
            mCurrentListener.endOfSpeech();
            Bundle results = new Bundle();
            results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, result != null
                    ? new ArrayList<>(Collections.singletonList(result))
                    : new ArrayList<>());
            results.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, result != null
                    ? new float[]{1.0f}
                    : new float[0]);
            mCurrentListener.results(results);
            Log.d(TAG, "Sent results: " + results);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending results", e.getCause());
        }
    }
}
