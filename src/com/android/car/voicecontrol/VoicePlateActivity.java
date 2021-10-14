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

import static com.android.car.voicecontrol.DirectSendUtils.AMBIGUOUS_RESULT;

import android.annotation.StringRes;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.car.TelecomUtils;
import com.android.car.assist.CarVoiceInteractionSession;
import com.android.car.assist.payloadhandlers.ConversationPayloadHandler;
import com.android.car.assist.payloadhandlers.NotificationPayloadHandler;
import com.android.car.messenger.common.Conversation;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.voicecontrol.actuators.SkillRouter;

import java.util.ArrayList;
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
    private TelecomUtils mTelecomUtils;

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

        mTelecomUtils = new TelecomUtils(getApplicationContext());
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
            Notification notification =
                    mNotifHandler.getNotification(intent.getBundleExtra(EXTRA_ARGS));
            onReadNotification(notification);
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_REPLY_NOTIFICATION.equals(action)) {
            Notification notification =
                    mNotifHandler.getNotification(intent.getBundleExtra(EXTRA_ARGS));
            onReplyNotification(notification);
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_HANDLE_EXCEPTION.equals(action)) {
            onReadNotificationException(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_READ_CONVERSATION.equals(action)) {
            onReadConversation(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_REPLY_CONVERSATION.equals(action)) {
            onReplyConversation(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        if (CarVoiceInteractionSession.VOICE_ACTION_SEND_SMS.equals(action)) {
            onDirectSendSMS(intent.getBundleExtra(EXTRA_ARGS));
            return;
        }
        mSTT.startListening(this);
    }

    private void ask(TextToSpeech.QuestionCallback callback, @StringRes int resId, Object... args) {
        ask(callback, getString(resId), args);
    }

    private void ask(TextToSpeech.QuestionCallback callback, String str, Object... args) {
        if (PreferencesController.getInstance(this).isDirectSendTextInput()) {
            final EditText input = new EditText(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            input.setLayoutParams(lp);

            String confirm = getString(R.string.debug_dialog_confirm);
            String cancel = getString(R.string.debug_dialog_cancel);

            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setMessage(str)
                    .setPositiveButton(confirm, (dialog, which) -> {
                        List<String> result = new ArrayList<>();
                        result.add(input.getText().toString());
                        callback.onResult(result);
                    })
                    .setNegativeButton(cancel, (dialog, which) -> {
                        List<String> result = new ArrayList<>();
                        callback.onResult(result);
                    })
                    .setView(input)
                    .create();

            alertDialog.show();
        } else {
            mTTS.ask(callback, str, args);
        }
    }

    private void askToSendSMS(PendingIntent pendingIntent,
            String deviceAddress, String number, String name) {
        mPresenter.updateRecognizedText(getString(R.string.speech_reply_request_message));
        ask(strings -> {
            if (strings.isEmpty()) {
                mTTS.speak(R.string.speech_reply_not_recognized);
                mPresenter.updateRecognizedText(getString(R.string.speech_reply_not_recognized));
                return;
            }

            String message = strings.get(0);
            String speech = getString(R.string.speech_reply_okay_sending_sms, name);
            mTTS.speak(speech);
            mPresenter.updateRecognizedText(speech);

            DirectSendUtils.sendSMS(getApplicationContext(),
                    pendingIntent, deviceAddress, number, message);
        }, R.string.speech_reply_request_message);
    }

    private void askToDisambiguatePhoneNumber(DirectSendUtils.ResultsCallback callback,
            String deviceAddress) {
        mPresenter.updateRecognizedText(getString(R.string.speech_reply_request_contact));
        ask(strings -> {
            if (strings.isEmpty()) {
                callback.onFailure(getString(R.string.speech_reply_unrecognized_contact));
                return;
            }
            Contact contact = mInteractionService.getContact(strings.get(0), deviceAddress);
            if (contact == null) {
                callback.onFailure(getString(R.string.speech_reply_unrecognized_contact));
                return;
            }

            if (contact.getNumbers().size() == 0) {
                callback.onFailure(getString(R.string.speech_reply_unrecognized_contact));
            } else if (contact.getNumbers().size() == 1) {
                callback.onSuccess(contact.getNumbers().get(0).getNumber(),
                        contact.getDisplayName());
            } else if (contact.getNumbers().size() > 1) {
                String numberAskString = DirectSendUtils.formatNumberAskString(
                        getApplicationContext(), contact.getNumbers());
                mPresenter.updateRecognizedText(numberAskString);
                ask(strings2 -> {
                    if (strings2.isEmpty()) {
                        callback.onFailure(getString(R.string.speech_reply_unrecognized_number));
                        return;
                    }

                    String input = strings2.get(0).toLowerCase();
                    String[] ordinals = getResources().getStringArray(
                            R.array.speech_reply_ordinals);
                    PhoneNumber phoneNumber;
                    try {
                        if (ordinals[0].equals(input)) {
                            phoneNumber = contact.getNumbers().get(0);
                        } else if (ordinals[1].equals(input)) {
                            phoneNumber = contact.getNumbers().get(1);
                        } else if (ordinals[2].equals(input)) {
                            phoneNumber = contact.getNumbers().get(2);
                        } else {
                            callback.onFailure(
                                    getString(R.string.speech_reply_unrecognized_number));
                            return;
                        }
                        callback.onSuccess(phoneNumber.getNumber(), contact.getDisplayName());
                    } catch (IndexOutOfBoundsException ex) {
                        Log.w(TAG, "Specified phone number index out of list bounds. " + ex);
                        callback.onFailure(getString(R.string.speech_reply_unrecognized_number));
                    }
                }, numberAskString);
            }
        }, R.string.speech_reply_request_contact);
    }

    private void askToDisambiguateDeviceAddress(DirectSendUtils.ResultsCallback callback) {
        List<BluetoothDevice> devices = mTelecomUtils.getHfpDeviceList();
        String deviceAskString = DirectSendUtils.formatDeviceAskString(getApplicationContext(),
                devices);
        mPresenter.updateRecognizedText(deviceAskString);

        ask(strings -> {
            String disambiguatedAddress = null;

            if (strings.isEmpty()) {
                callback.onFailure(getString(R.string.speech_reply_unrecognized_device));
                return;
            }

            try {
                String input = strings.get(0).toLowerCase();
                String[] ordinals = getResources().getStringArray(R.array.speech_reply_ordinals);

                if (ordinals[0].equals(input)) {
                    disambiguatedAddress = devices.get(0).getAddress();
                } else if (ordinals[1].equals(input)) {
                    disambiguatedAddress = devices.get(1).getAddress();
                } else if (ordinals[2].equals(input)) {
                    disambiguatedAddress = devices.get(2).getAddress();
                } else {
                    callback.onFailure(getString(R.string.speech_reply_unrecognized_device));
                    return;
                }

                callback.onSuccess(disambiguatedAddress);
            } catch (NullPointerException ex) {
                callback.onFailure(getString(R.string.speech_reply_unrecognized_device));
                Log.e(TAG, "Device not found" + ex);
            }
        }, deviceAskString);
    }

    private String getDeviceAddress(Bundle args) {
        String address = args.getString(CarVoiceInteractionSession.KEY_DEVICE_ADDRESS);
        if (address != null) {
            return address;
        }

        List<BluetoothDevice> devices = mTelecomUtils.getHfpDeviceList();
        if (devices.size() == 0) {
            mPresenter.updateRecognizedText(getString(R.string.speech_reply_unrecognized_device));
            mTTS.speak(R.string.speech_reply_unrecognized_device);
            return null;
        } else if (devices.size() == 1) {
            return devices.get(0).getAddress();
        } else {
            return AMBIGUOUS_RESULT;
        }
    }

    // Follows a chain of voice interactions to disambiguate phone number and device address.
    private void onDirectSendSMS(Bundle args) {
        Log.d(TAG, "Sending sms");
        mPresenter.updateRecognizedText("Sending SMS");
        mPresenter.updateState(VoicePlateController.State.DELIVERING);

        // Pending Intent is required to send SMS.
        PendingIntent pendingIntent = args.getParcelable(
                CarVoiceInteractionSession.KEY_SEND_PENDING_INTENT);
        if (pendingIntent == null) {
            Log.d(TAG, "Pending Intent is null");
            return;
        }

        // Address may or may not be provided.
        String address = getDeviceAddress(args);
        if (AMBIGUOUS_RESULT.equals(address)) {
            // If address is not provided, then contact must be looked up.
            askToDisambiguateDeviceAddress(new DirectSendUtils.ResultsCallback() {
                @Override
                public void onSuccess(String... strings) {
                    String deviceAddress = strings[0];
                    askToDisambiguatePhoneNumber(new DirectSendUtils.ResultsCallback() {
                        @Override
                        public void onSuccess(String... strings) {
                            String phoneNumber = strings[0];
                            String name = strings[1];
                            askToSendSMS(pendingIntent, deviceAddress, phoneNumber, name);
                        }
                        @Override
                        public void onFailure(String error) {
                            mPresenter.updateRecognizedText(error);
                            mTTS.speak(error);
                        }
                    }, deviceAddress);
                }
                @Override
                public void onFailure(String error) {
                    mPresenter.updateRecognizedText(error);
                    mTTS.speak(error);
                }
            });
            return;
        }

        // Address is provided, number may or may not be provided.
        String number = args.getString(CarVoiceInteractionSession.KEY_PHONE_NUMBER);
        if (number == null) {
            askToDisambiguatePhoneNumber(new DirectSendUtils.ResultsCallback() {
                @Override
                public void onSuccess(String... strings) {
                    String phoneNumber = strings[0];
                    String name = strings[1];
                    askToSendSMS(pendingIntent, address, phoneNumber, name);
                }
                @Override
                public void onFailure(String error) {
                    mPresenter.updateRecognizedText(error);
                    mTTS.speak(error);
                }
            }, address);
            return;
        }

        String name = args.getString(CarVoiceInteractionSession.KEY_RECIPIENT_NAME);
        if (name == null) {
            name = "";
        }
        askToSendSMS(pendingIntent, address, number, name);
    }

    private void onReplyConversation(Bundle args) {
        Conversation conversation = Conversation.fromBundle(
                args.getBundle(CarVoiceInteractionSession.KEY_CONVERSATION));
        Notification notification = ConversationPayloadHandler.createNotificationFromConversation(
                this, "channel_id", conversation, getIconRes(this), null);
        onReplyNotification(notification);
    }

    private void onReadConversation(Bundle args) {
        Conversation conversation = Conversation.fromBundle(
                args.getBundle(CarVoiceInteractionSession.KEY_CONVERSATION));
        Notification notification = ConversationPayloadHandler.createNotificationFromConversation(
                this, "channel_id", conversation, getIconRes(this), null);
        onReadNotification(notification);
    }

    private int getIconRes(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(
                            context.getPackageName(), PackageManager.GET_META_DATA);
            return applicationInfo.icon;
        } catch (PackageManager.NameNotFoundException e) {
            //logger.atWarning().log("Package name not found in system");
            return 0;
        }
    }

    private void onReadNotification(Notification notif) {
        Log.d(TAG, "Reading notification");
        mPresenter.updateRecognizedText("Reading a notification");
        mPresenter.updateState(VoicePlateController.State.DELIVERING);
        List<NotificationCompat.MessagingStyle.Message> msgs = mNotifHandler.getMessages(notif);
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
        if (mNotifHandler.getAction(notif, Notification.Action.SEMANTIC_ACTION_REPLY) != null) {
            sb.append(getString(R.string.speech_reply_reply_question));
            mTTS.ask(mTTS.createBooleanQuestionCallback(affirmative -> {
                if (affirmative) {
                    onReplyNotification(notif);
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

    private void onReplyNotification(Notification notif) {
        Log.d(TAG, "Reply notification");
        Notification.Action action = mNotifHandler.getAction(notif,
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
            mTTS.speak(R.string.speech_reply_sending_reply);
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
