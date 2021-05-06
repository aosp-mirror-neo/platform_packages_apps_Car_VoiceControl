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
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.UiThread;

/**
 * Controller of voice plate UI
 */
public class VoicePlateController {
    private static final String TAG = "Mica.VoicePlatePresenter";
    private final Context mContext;
    private View mVoicePlate;
    private View mContainer;
    private TextView mRecognizedText;
    private TextView mState;
    private Handler mHandler = new Handler();

    public enum State {
        IDLE,
        LISTENING,
        LISTENING_ACTIVE,
        LISTENING_STREAMING,
        PROCESSING,
        DELIVERING,
    }

    public VoicePlateController(Context context,
                                LayoutInflater inflater) {
        mContext = context;
        mVoicePlate = inflater.inflate(R.layout.voice_plate, null);
        mState = mVoicePlate.findViewById(R.id.state);
        mContainer = mVoicePlate.findViewById(R.id.container);
        mRecognizedText = mVoicePlate.findViewById(R.id.recognized_text);
    }

    /**
     * @return root view of voice plate
     */
    public View getContentView() {
        return mVoicePlate;
    }

    /**
     * Updates voice place state
     */
    public void updateState(State state) {
        mHandler.post(() -> doUpdateState(state));
    }

    @UiThread
    private void doUpdateState(State state) {
        Log.d(TAG, "UpdateState: " + state);
        mState.setText(state.toString());
        switch (state) {
            case IDLE:
                mVoicePlate.setVisibility(View.GONE);
                break;
            case LISTENING:
                mVoicePlate.setVisibility(View.VISIBLE);
                mContainer.setBackgroundColor(mContext
                        .getColor(R.color.voice_plate_listening));
                break;
            case LISTENING_ACTIVE:
                mVoicePlate.setVisibility(View.VISIBLE);
                mContainer.setBackgroundColor(mContext
                        .getColor(R.color.voice_plate_listening_active));
                break;
            case LISTENING_STREAMING:
                mVoicePlate.setVisibility(View.VISIBLE);
                mContainer.setBackgroundColor(mContext
                        .getColor(R.color.voice_plate_partial_recognition));
                break;
            case PROCESSING:
                mVoicePlate.setVisibility(View.VISIBLE);
                mContainer.setBackgroundColor(mContext
                        .getColor(R.color.voice_plate_processing));
                break;
            case DELIVERING:
                mVoicePlate.setVisibility(View.VISIBLE);
                mContainer.setBackgroundColor(mContext
                        .getColor(R.color.voice_plate_delivering));
                break;
        }
    }

    /**
     * Updates recognized text view
     */
    public void updateRecognizedText(String text) {
        Log.d(TAG, "UpdateRecognizedText: " + text);
        mHandler.post(() -> doUpdateRecognizedText(text));
    }

    @UiThread
    private void doUpdateRecognizedText(String text) {
        mRecognizedText.setText(text);
    }
}
