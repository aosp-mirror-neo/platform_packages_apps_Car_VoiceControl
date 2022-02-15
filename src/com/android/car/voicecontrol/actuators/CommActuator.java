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

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.voicecontrol.InteractionServiceClient;
import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber;

import java.util.Arrays;
import java.util.List;

/**
 * Actuator that interacts with the telephony stack
 */
public class CommActuator implements SkillRouter.Actuator {
    private static final String TAG = "Mica.CommActuator";
    public static final String SMS_RESULT_ACTION = "com.android.car.voicecontrol.sms";

    private TelecomManager mTelecomManager;
    private SmsManager mSmsManager;
    private PhoneNumberUtil mPhoneNumberUtil = PhoneNumberUtil.getInstance();
    private InteractionServiceClient mInteractionService;
    private Context mContext;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "SMS send result: " + getResultCode());
        }
    };
    private int mMaxQuestionRetries;

    @Override
    public List<Skill> onCreate(Context context) {
        mContext = context;
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        mSmsManager = SmsManager.getDefault();
        mInteractionService = new InteractionServiceClient(context);
        mInteractionService.connect();
        registerSMSResultReceiver();
        mMaxQuestionRetries = context.getResources()
                .getInteger(R.integer.boolean_question_max_retries);

        return Arrays.asList(
                new Skill("call (to )?(.*)$", (args, tts) -> call(args[2], tts)),
                new Skill("send (a )?message (to )?(.*)$", (args, tts) ->
                        sendSMS(args[3], tts))
        );
    }

    private void registerSMSResultReceiver() {
        IntentFilter intentFilter = new IntentFilter(SMS_RESULT_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
    }

    @Override
    public void onDestroy() {
        mInteractionService.disconnect();
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    private boolean call(String query, TextToSpeech tts) {
        Log.d(TAG, "callContact: " + query);

        if (mTelecomManager.getPhoneAccountsSupportingScheme("tel").isEmpty()) {
            tts.speak(R.string.speech_reply_phone_not_available);
            return true;
        }

        Pair<String, String> numberAndName = getPhoneNumberAndName(query);
        if (numberAndName == null) {
            Log.d(TAG, "Phone not found for query: " + query);
            return false;
        }

        tts.ask(tts.createBooleanQuestionCallback(affirmative -> {
            if (!affirmative) {
                tts.speak(R.string.speech_reply_unrecognized_contact);
                return;
            }
            doCall(numberAndName.first, numberAndName.second, tts);
        }, mMaxQuestionRetries, false), R.string.speech_reply_confirm_calling_contact,
                numberAndName.second, numberAndName.first);
        return true;
    }

    private Pair<String, String> getPhoneNumberAndName(String query) {
        if (isPhoneNumber(query)) {
            return Pair.create(query, query);
        }
        Contact contact = mInteractionService.getContact(query, null);
        String number = getPhoneNumberFromContact(contact);
        if (number == null) {
            Log.d(TAG, "Contact doesn't have a phone: "
                    + (contact != null ? contact.getDisplayName() : null));
            return null;
        }
        return Pair.create(number, contact.getDisplayName());
    }

    private boolean isPhoneNumber(String value) {
        try {
            Phonenumber.PhoneNumber number = mPhoneNumberUtil.parse(value, "US");
            return true;
        } catch (NumberParseException ex) {
            return false;
        }
    }

    @Nullable
    private String getPhoneNumberFromContact(@Nullable Contact contact) {
        if (contact == null) {
            return null;
        }
        if (contact.getPrimaryPhoneNumber() != null
                && !TextUtils.isEmpty(contact.getPrimaryPhoneNumber().getRawNumber())) {
            return contact.getPrimaryPhoneNumber().getRawNumber();
        }
        for (PhoneNumber number : contact.getNumbers()) {
            String value = number.getRawNumber();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void doCall(String number, String name, TextToSpeech tts) {
        try {
            Uri uri = Uri.fromParts("tel", number, null);
            Bundle extras = new Bundle();
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
            mTelecomManager.placeCall(uri, extras);
            tts.speak(R.string.speech_reply_okay_calling, name);
        } catch (Exception ex) {
            tts.speak(R.string.speech_reply_error_calling, name);
        }
    }

    private boolean sendSMS(String query, TextToSpeech tts) {
        Log.d(TAG, "sendSMSContact: " + query);

        Pair<String, String> numberAndName = getPhoneNumberAndName(query);
        if (numberAndName == null) {
            Log.d(TAG, "Phone not found for query: " + query);
            return false;
        }

        tts.ask(tts.createBooleanQuestionCallback(affirmative -> {
            if (!affirmative) {
                tts.speak(R.string.speech_reply_unrecognized_contact);
                return;
            }
            doSendSMS(numberAndName.first, numberAndName.second, tts);
        }, mMaxQuestionRetries, false),
                R.string.speech_reply_confirm_send_sms_contact, numberAndName.second,
                numberAndName.first);
        return true;
    }

    private void doSendSMS(String number, String name, TextToSpeech tts) {
        tts.ask(results -> {
            if (results.isEmpty()) {
                tts.speak(R.string.speech_reply_not_recognized);
                return;
            }
            try {
                Intent intent = new Intent(SMS_RESULT_ACTION);
                PendingIntent sentIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE);
                mSmsManager.sendTextMessage(number, null, results.get(0), sentIntent, null);
                tts.speak(R.string.speech_reply_okay_sending_sms, name);
            } catch (Exception ex) {
                tts.speak(R.string.speech_reply_error_sending_sms, name);
            }
        }, R.string.speech_reply_request_message);
    }
}
