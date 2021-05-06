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

import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.car.assist.CarVoiceInteractionSession;
import com.android.car.assist.payloadhandlers.NotificationPayloadHandler;
import com.android.car.voicecontrol.actuators.SkillRouter;

import java.util.List;

/**
 * Launcher activity for this sample voice interaction service. This allows voice interaction to be
 * launched from the app launcher.
 */
public class VoicePlateActivity extends Activity
        implements SpeechToText.Listener, TextToSpeech.Listener {
    public static final String EXTRA_ACTION = "com.android.car.voicecontrol.ACTION";
    public static final String EXTRA_ARGS = "com.android.car.voicecontrol.ARGS";
    private static final String TAG = "Mica.MainActivity";
    private SpeechToText mSTT;
    private TextToSpeech mTTS;
    private VoicePlateController mPresenter;
    private SkillRouter mActuator;
    private Handler mHandler = new Handler();
    private NotificationPayloadHandler mNotifHandler;
    private InteractionServiceClient mInteractionService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = new VoicePlateController(this, getLayoutInflater());
        setContentView(mPresenter.getContentView());
        mSTT = new SpeechToTextImpl(this);
        mTTS = new TextToSpeechImpl(this, this);
        mActuator = new SkillRouter(this);
        mNotifHandler = new NotificationPayloadHandler(this);
        mInteractionService = new InteractionServiceClient(this) {
            @Override
            void onConnected() {
                mTTS.setSelectedVoice(mInteractionService.getVoice());
            }
        };
        mInteractionService.connect();
    }

    @Override
    protected void onDestroy() {
        mInteractionService.disconnect();
        mSTT.destroy();
        mTTS.destroy();
        mActuator.destroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mPresenter.updateRecognizedText("");
        mPresenter.updateState(VoicePlateController.State.LISTENING);

        if (!InteractionService.hasAllPermissions(this)) {
            mTTS.speak(R.string.speech_reply_missing_permissions);
            mPresenter.updateState(VoicePlateController.State.DELIVERING);
            mInteractionService.notifySetupChanged();
            return;
        }

        Intent intent = getIntent();
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (CarVoiceInteractionSession.VOICE_ACTION_READ_NOTIFICATION.equals(action)) {
            onReadNotification(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_REPLY_NOTIFICATION.equals(action)) {
            onReplyNotification(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_HANDLE_EXCEPTION.equals(action)) {
            onReadNotificationException(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        mSTT.startListening(this);
    }

    private void onReadNotification(Bundle args) {
        Log.d(TAG, "Reading notification");
        mPresenter.updateRecognizedText("Reading a notification");
        mPresenter.updateState(VoicePlateController.State.DELIVERING);
        List<NotificationCompat.MessagingStyle.Message> msgs = mNotifHandler.getMessages(args);
        if (msgs == null || msgs.isEmpty()) {
            Log.d(TAG, "Reading notification: no messages");
            mTTS.speak(R.string.speech_reply_no_messages_in_notif);
            return;
        }

        // Composing read-out
        StringBuilder sb = new StringBuilder();
        if (msgs.size() == 1) {
            NotificationCompat.MessagingStyle.Message msg = msgs.get(0);
            Log.d(TAG, "Reading notification: " + msg.getText());
            sb.append(getString(R.string.speech_reply_reading_single_notif,
                    msg.getPerson().getName(), msg.getText()));
        } else {
            Log.d(TAG, "Reading notification: # msgs: " + msgs.size());
            sb.append(R.string.speech_reply_reading_multiple_notif_header);
            for (NotificationCompat.MessagingStyle.Message msg : msgs) {
                sb.append(getString(R.string.speech_reply_reading_multiple_notif_item,
                        msg.getPerson().getName(), msg.getText()));
            }
            sb.append(R.string.speech_reply_reading_multiple_notif_footer);
        }

        // Asking for reply, if available
        if (mNotifHandler.getAction(args, Notification.Action.SEMANTIC_ACTION_REPLY) != null) {
            sb.append(getString(R.string.speech_reply_reply_question));
            mTTS.ask(mTTS.createBooleanQuestionCallback(affirmative -> {
                if (affirmative) {
                    onReplyNotification(args);
                } else {
                    mPresenter.updateState(VoicePlateController.State.DELIVERING);
                    mTTS.speak(R.string.speech_reply_cancelled_order);
                }
            }, getResources().getInteger(R.integer.boolean_question_max_retries), false),
                    sb.toString());
        } else {
            mTTS.speak(sb.toString());
        }
    }

    private void onReplyNotification(Bundle args) {
        Log.d(TAG, "Reply notification");
        Notification.Action action = mNotifHandler.getAction(args,
                Notification.Action.SEMANTIC_ACTION_REPLY);
        if (action == null) {
            mTTS.speak(R.string.speech_reply_cannot_be_replied);
            return;
        }
        mPresenter.updateRecognizedText("Replying a notification");
        mPresenter.updateState(VoicePlateController.State.DELIVERING);
        mTTS.ask(strings -> {
            mPresenter.updateState(VoicePlateController.State.DELIVERING);
            if (strings.isEmpty()) {
                mTTS.speak(R.string.speech_reply_not_recognized);
                return;
            }
            List<NotificationCompat.MessagingStyle.Message> msgs = mNotifHandler.getMessages(args);
            String sender  = msgs.size() == 1
                    ? msgs.get(0).getPerson().getName().toString()
                    : getString(R.string.speech_reply_group_reply_receiver);
            mTTS.speak(R.string.speech_reply_sending_reply, sender);
            Intent additionalData = mNotifHandler.writeReply(action, strings.get(0));
            mNotifHandler.fireAction(action, additionalData);
        }, R.string.speech_reply_message_question);
    }

    private void onReadNotificationException(Bundle args) {
        if (CarVoiceInteractionSession.EXCEPTION_NOTIFICATION_LISTENER_PERMISSIONS_MISSING
                .equals(args.getString(CarVoiceInteractionSession.KEY_EXCEPTION))) {
            mTTS.speak(R.string.speech_reply_missing_notif_access);
            mInteractionService.notifySetupChanged();
        } else {
            mTTS.speak(R.string.speech_reply_error_reading_notif);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPresenter.updateRecognizedText("");
        mPresenter.updateState(VoicePlateController.State.IDLE);
    }

    @Override
    public void onRecognitionStarted() {
        mPresenter.updateState(VoicePlateController.State.LISTENING_ACTIVE);
    }

    @Override
    public void onPartialRecognition(List<String> strings) {
        mPresenter.updateState(VoicePlateController.State.LISTENING_STREAMING);
        mPresenter.updateRecognizedText(String.join(", ", strings));
    }

    @Override
    public void onRecognitionFinished(List<String> strings) {
        Log.d(TAG, "onRecognitionFinished (results: " + strings + ")");
        // If we were waiting for an answer, provide it.
        if (mTTS.isWaitingForAnswer()) {
            mTTS.provideAnswer(strings);
            return;
        }
        // Otherwise, execute given command
        if (!strings.isEmpty()) {
            mPresenter.updateRecognizedText(String.join(", ", strings));
            mPresenter.updateState(VoicePlateController.State.PROCESSING);
            mHandler.postDelayed(() -> {
                mPresenter.updateState(VoicePlateController.State.DELIVERING);
                mActuator.process(strings, mTTS);
            }, 500);
        } else {
            mPresenter.updateState(VoicePlateController.State.DELIVERING);
            mTTS.speak(R.string.speech_reply_not_recognized);
        }
    }

    @Override
    public void onUtteranceDone(boolean successful) {
        finish();
    }

    @Override
    public void onWaitingForAnswer() {
        mPresenter.updateRecognizedText("");
        mPresenter.updateState(VoicePlateController.State.LISTENING);
        mSTT.startListening(this);
        // The answer will be provided on onRecognitionFinished
    }
}
