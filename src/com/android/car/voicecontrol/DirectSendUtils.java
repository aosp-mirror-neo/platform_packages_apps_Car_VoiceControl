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

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.car.assist.CarVoiceInteractionSession;
import com.android.car.telephony.common.PhoneNumber;

import java.util.List;

/**
 * Utility class for Direct Send SMS functionality.
 */
public final class DirectSendUtils {
    private static final String TAG = "Mica.DirectSendUtils";

    public static final String AMBIGUOUS_RESULT = "AMBIGUOUS_RESULT";

    /**
     * Callback interface used for chaining direct send user flow.
     * See {@link VoicePlateActivity#askToDisambiguateDeviceAddress} and
     * {@link VoicePlateActivity#askToDisambiguatePhoneNumber}.
     */
    public interface ResultsCallback {
        /**
         * Callback used when user prompt is successful.
         * @param results The resulting Strings from user disambiguation.
         */
        void onSuccess(String... results);

        /**
         * Callback used when user prompt is unsuccessful.
         * @param error The error string to be presented.
         */
        void onFailure(String error);
    }

    /**
     * Sends attached Pending Intent for SMS direct send.
     */
    public static void sendSMS(
            Context context,
            PendingIntent pendingIntent,
            String address,
            String number,
            String message) {
        Intent intent = new Intent();
        intent.putExtra(CarVoiceInteractionSession.KEY_PHONE_NUMBER, number);
        intent.putExtra(CarVoiceInteractionSession.KEY_DEVICE_ADDRESS, address);
        intent.putExtra(Intent.EXTRA_TEXT, message);

        try {
            Log.d(TAG, "Sending SMS Pending Intent");
            pendingIntent.send(context, 0, intent);
        } catch (PendingIntent.CanceledException ex) {
            Log.e(TAG, "Pending Intent canceled: " + ex);
        }
    }

    /**
     * Formats the user prompt string for number disambiguation.
     */
    public static String formatNumberAskString(Context context, List<PhoneNumber> numbers) {
        String str = context.getString(R.string.speech_reply_request_number);
        String[] key = context.getResources().getStringArray(R.array.speech_reply_ordinals);
        int i = 0;
        for (PhoneNumber number : numbers) {
            str += String.format(" Say %s for %s.", key[i], number.getNumber());
            i++;
            if (i > 2) {
                break;
            }
        }

        return str;
    }

    /**
     * Formats the user prompt string for device disambiguation.
     */
    public static String formatDeviceAskString(Context context, List<BluetoothDevice> devices) {
        String str = context.getString(R.string.speech_reply_request_device);
        String[] key = context.getResources().getStringArray(R.array.speech_reply_ordinals);
        int i = 0;
        for (BluetoothDevice device : devices) {
            str += String.format(" Say %s for %s.", key[i], device.getName());
            i++;
            if (i > 2) {
                break;
            }
        }

        return str;
    }
}
