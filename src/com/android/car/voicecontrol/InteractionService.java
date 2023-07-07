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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.car.Car;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.car.assist.CarVoiceInteractionSession;
import com.android.car.telephony.common.Contact;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Voice interaction entry point. This service is kept running for as long as this application
 * is set as the default voice interaction service.
 */
public class InteractionService extends VoiceInteractionService {
    private static final String TAG = "Mica.InteractionService";
    private static final String CHANNEL_ID = "Mica";
    private static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;
    public static final String LOCAL_SERVICE_ACTION = "com.android.car.voicecontrol.local";
    public static final String LOCAL_SERVICE_CMD_SETUP_CHANGED = "setup-changed";
    private static final List<String> SUPPORTED_VOICE_ACTIONS = Arrays.asList(
            CarVoiceInteractionSession.VOICE_ACTION_READ_NOTIFICATION,
            CarVoiceInteractionSession.VOICE_ACTION_REPLY_NOTIFICATION,
            CarVoiceInteractionSession.VOICE_ACTION_HANDLE_EXCEPTION,
            CarVoiceInteractionSession.VOICE_ACTION_SEND_SMS
    );
    public static final List<String> REQUIRED_PERMISSIONS = Arrays.asList(
            Manifest.permission.RECORD_AUDIO,
            Car.PERMISSION_SPEED,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
    );
    private ContactsProvider mContactsProvider;

    private final ILocalService.Stub mBinder = new ILocalService.Stub() {
        @Override
        public boolean isSetupComplete() {
            return InteractionService.this.isSetupComplete();
        }

        @Override
        public void registerListener(ILocalServiceListener listener) {
            mListeners.register(listener);
        }

        @Override
        public void unregisterListener(ILocalServiceListener listener) {
            mListeners.unregister(listener);
        }

        @Override
        public String getVoice() {
            return PreferencesController.getInstance(InteractionService.this).getVoice();
        }

        @Override
        public void setVoice(String name) {
            PreferencesController.getInstance(InteractionService.this).setVoice(name);
        }

        @Override
        public String getUsername() {
            String value = PreferencesController.getInstance(InteractionService.this).getUsername();
            Log.d(TAG, "getUsername(): " + value);
            return value;
        }

        @Override
        public void setUsername(String value) {
            Log.d(TAG, "setUsername: " + value);
            PreferencesController.getInstance(InteractionService.this).setUsername(value);
            onSetupChanged();
        }

        @Override
        public boolean hasAllPermissions() {
            return InteractionService.hasAllPermissions(InteractionService.this);
        }

        @Override
        public boolean isNotificationListener() {
            return InteractionService.this.isNotificationListener();
        }

        @Override
        public Contact getContact(String query, @Nullable String deviceAddress) {
            return mContactsProvider.getContact(query, deviceAddress);
        }
    };
    private final RemoteCallbackList<ILocalServiceListener> mListeners = new RemoteCallbackList<>();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent);
        if (LOCAL_SERVICE_ACTION.equals(intent.getAction())) {
            return mBinder;
        }
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case LOCAL_SERVICE_CMD_SETUP_CHANGED:
                onSetupChanged();
                return START_NOT_STICKY;
            default:
                return super.onStartCommand(intent, flags, startId);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContactsProvider = new ContactsProvider(this);
    }

    @Override
    public void onDestroy() {
        mContactsProvider.destroy();
        super.onDestroy();
    }

    @Override
    public void onReady() {
        Log.e(TAG, "onReady()");
        super.onReady();
        updateNotification();

        // Setup always-on hot-word detector here (see: VoiceInteractionService#onReady()) for
        // details.
    }

    private boolean isSetupComplete() {
        boolean setupComplete = PreferencesController.getInstance(this).isUserSignedIn()
                && hasAllPermissions(this) && isNotificationListener();
        Log.d(TAG, "isSetupComplete: " + setupComplete);
        return setupComplete;
    }

    /**
     * @return true if this application has all required dangerous permissions (permissions the
     * user is able to grant).
     */
    public static boolean hasAllPermissions(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        return true;
    }

    private boolean isNotificationListener() {
        ComponentName component = new ComponentName(this,
                VoiceNotificationListenerService.class);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        return notificationManager.isNotificationListenerAccessGranted(component);
    }

    private void onSetupChanged() {
        Log.d(TAG, "onSetupChanged");
        int items = mListeners.beginBroadcast();
        for (int i = 0; i < items; i++) {
            try {
                mListeners.getBroadcastItem(i).setupChanged();
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to notify broadcast item "
                        + mListeners.getBroadcastItem(i), e);
            }
        }
        mListeners.finishBroadcast();
        updateNotification();
    }

    private void updateNotification() {
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);
        }
        if (isSetupComplete()) {
            Log.d(TAG, "updateNotification(): is setup complete = true");
            mNotificationManager.cancel(NOTIFICATION_ID);
            return;
        }
        Log.d(TAG, "updateNotification(): is setup complete = false");
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setColor(getColor(R.color.car_ui_color_accent))
                .setContentTitle(getString(R.string.notification_setup_title))
                .setContentText(getString(R.string.notification_setup_text))
                .setContentIntent(pendingIntent)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    @NonNull
    @Override
    public Set<String> onGetSupportedVoiceActions(@NonNull Set<String> voiceActions) {
        Set<String> result = new HashSet<>(voiceActions);
        result.retainAll(SUPPORTED_VOICE_ACTIONS);
        return result;
    }

}
