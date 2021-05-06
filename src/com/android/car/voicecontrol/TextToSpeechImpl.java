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
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sample implementation of {@link TextToSpeech} interface. This implementation uses the system
 * default {@link android.speech.tts.TextToSpeech} service.
 */
public class TextToSpeechImpl implements TextToSpeech {
    private static final String TAG = "Mica.TextToSpeechImpl";

    // Strings used when warming up the TTS pipeline.
    private static final String WARM_UP_PIPELINE_FILENAME = "warmUpPipeline";
    private static final String WARM_UP_PIPELINE_TEXT = "Hello";
    private static final String WARM_UP_PIPELINE_UTTERANCE_ID = "warm-up";

    private android.speech.tts.TextToSpeech mTTS;
    private Context mContext;
    private final Listener mListener;
    private List<String> mPendingUtterance = new ArrayList<>();
    private boolean mIsReady;
    private String mSelectedVoice;
    private Handler mHandler = new Handler();
    private final UtteranceProgressListener mTTSListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String id) {
            Log.d(TAG, "TTS start");
        }

        @Override
        public void onDone(String id) {
            Log.d(TAG, "TTS done");
            if (WARM_UP_PIPELINE_UTTERANCE_ID.equals(id)) {
                // Ignore warm up utterance.
                return;
            }
            mHandler.post(() -> {
                if (isWaitingForAnswer()) {
                    mListener.onWaitingForAnswer();
                } else {
                    mListener.onUtteranceDone(true);
                }
            });
        }

        @Override
        public void onError(String id) {
            Log.d(TAG, "TTS error");
            mHandler.post(() -> mListener.onUtteranceDone(false));
        }
    };
    private QuestionCallback mQuestionCallback = null;
    private final Map<String, AnswerType> mAnswerTypes = new HashMap<>();
    private File mWarmUpPipelineFile;

    public TextToSpeechImpl(Context context, Listener listener) {
        Log.d(TAG, "TTS create");
        mContext = context;
        mListener = listener;
        Arrays.stream(context.getResources()
                .getStringArray(R.array.speech_reply_affirmative_answers))
                .forEach(answer -> mAnswerTypes.put(answer, AnswerType.AFFIRMATIVE));
        Arrays.stream(context.getResources()
                .getStringArray(R.array.speech_reply_negative_answers))
                .forEach(answer -> mAnswerTypes.put(answer, AnswerType.NEGATIVE));
        mTTS = new android.speech.tts.TextToSpeech(context, status -> {
            Log.d(TAG, "TTS started: " + status);
            if (status == android.speech.tts.TextToSpeech.ERROR) {
                throw new IllegalStateException("Unable to setup TTS");
            }
            doSetVoice(mSelectedVoice);
            mTTS.setOnUtteranceProgressListener(mTTSListener);
            mTTS.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .build());
            mIsReady = true;
            for (String text : mPendingUtterance) {
                mTTS.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "");
            }
            mPendingUtterance.clear();
            warmUpPipeline();
            mListener.onReady(this);
        });
    }

    @Override
    public void setSelectedVoice(String name) {
        if (mIsReady) {
            doSetVoice(name);
        } else {
            mSelectedVoice = name;
        }
    }

    private void doSetVoice(String name) {
        Voice voice = getVoice(name);
        if (voice != null) {
            mTTS.setVoice(voice);
        } else {
            // Setting the language resets the voice to the default for that language
            mTTS.setLanguage(Locale.US);
        }
    }

    private void warmUpPipeline() {
        if (mWarmUpPipelineFile != null) {
            mWarmUpPipelineFile.delete();
        }
        // Start loading up the pipeline by synthesizing some throwaway text to a file. This will
        // reduce latency for the first Text to Speech request.
        mWarmUpPipelineFile = new File(mContext.getFilesDir(), WARM_UP_PIPELINE_FILENAME);
        mTTS.synthesizeToFile(WARM_UP_PIPELINE_TEXT, null, mWarmUpPipelineFile,
                WARM_UP_PIPELINE_UTTERANCE_ID);
    }

    @Override
    public void destroy() {
        if (mTTS != null) {
            mTTS.shutdown();
            mTTS = null;
        }
        if (mWarmUpPipelineFile != null) {
            mWarmUpPipelineFile.delete();
            mWarmUpPipelineFile = null;
        }
    }

    @Override
    public void speak(@StringRes int resId, Object... args) {
        doSpeak(mContext.getString(resId, args));
    }

    @Override
    public void speak(String text, Object... args) {
        doSpeak(String.format(text, args));
    }

    private void doSpeak(String text) {
        if (mIsReady) {
            mTTS.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "");
        } else {
            mPendingUtterance.add(text);
        }
    }

    @Override
    public void ask(QuestionCallback callback, String fmt, Object... args) {
        doAsk(String.format(fmt, args), callback);
    }

    @Override
    public void ask(QuestionCallback callback, @StringRes int resId, Object... args) {
        doAsk(mContext.getString(resId, args), callback);
    }

    private <T> void doAsk(String text, QuestionCallback callback) {
        mQuestionCallback = callback;
        doSpeak(text);
    }

    @Override
    public boolean isWaitingForAnswer() {
        return mQuestionCallback != null;
    }

    @Override
    public void provideAnswer(@NonNull List<String> strings) {
        QuestionCallback callback = mQuestionCallback;
        mQuestionCallback = null;
        callback.onResult(strings);
    }

    @Override
    public List<String> getVoices() {
        if (!mIsReady) {
            return new ArrayList<>();
        }
        return mTTS.getVoices()
                .stream()
                .map(Voice::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    @Nullable
    private Voice getVoice(String name) {
        if (!mIsReady || TextUtils.isEmpty(name)) {
            return null;
        }
        return mTTS.getVoices()
                .stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public AnswerType getAnswerType(List<String> strings) {
        return strings.stream()
                .map(s -> s.toLowerCase().trim())
                .map(s -> mAnswerTypes.get(s))
                .filter(type -> type != null)
                .findFirst()
                .orElse(null);
    }
}
