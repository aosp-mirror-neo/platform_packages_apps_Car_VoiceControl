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

import android.annotation.UiThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.telephony.common.Contact;

/**
 * A client that is able to talk to {@link InteractionService} over IPC (as InteractionService
 * might be running on a different process than the UI).
 */
public class InteractionServiceClient {
    private static final String TAG = "Mica.InteractionServiceClient";

    private final Context mContext;
    private ILocalService mInteractionService;
    private Handler mHandler = new Handler();
    private ILocalServiceListener mListener = new ILocalServiceListener.Stub() {
        @Override
        public void setupChanged() {
            mHandler.post(() -> onSetupChanged());
        }
    };
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mInteractionService = ILocalService.Stub.asInterface(service);
            try {
                mInteractionService.registerListener(mListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register listener", e);
            }
            mHandler.post(() -> onConnected());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mHandler.post(() -> onDisconnected());
            try {
                mInteractionService.unregisterListener(mListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to unregister listener", e);
            }
            mInteractionService = null;
        }
    };

    public InteractionServiceClient(Context context) {
        mContext = context;
    }

    /**
     * Connects this client to its service
     */
    public void connect() {
        Intent intent = new Intent(mContext, InteractionService.class);
        intent.setAction(InteractionService.LOCAL_SERVICE_ACTION);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Disconnects from the service
     */
    public void disconnect() {
        mContext.unbindService(mConnection);
        mInteractionService = null;
    }

    boolean isConnected() {
        return mInteractionService != null;
    }

    @UiThread
    void onConnected() {}

    @UiThread
    void onDisconnected() {}

    @UiThread
    void onSetupChanged() {}

    boolean isSetupComplete() {
        return getRemote(() -> mInteractionService.isSetupComplete());
    }

    String getUsername() {
        return getRemote(() -> mInteractionService.getUsername());
    }

    boolean hasUsername() {
        return getUsername() != null;
    }

    void setUsername(String username) {
        getRemote(() -> {
            mInteractionService.setUsername(username);
            return null;
        });
    }

    boolean hasAllPermissions() {
        return getRemote(() -> mInteractionService.hasAllPermissions());
    }

    boolean isNotificationListener() {
        return getRemote(() -> mInteractionService.isNotificationListener());
    }

    /**
     * @return the currently selected TTS voice
     */
    public String getVoice() {
        return getRemote(() -> mInteractionService.getVoice());
    }

    /**
     * Sets the TTS voice to be used
     */
    public void setVoice(String name) {
        getRemote(() -> {
            mInteractionService.setVoice(name);
            return null;
        });
    }

    /**
     * @return a contact matching the provided query. See {@link ContactsProvider} for more details.
     */
    public Contact getContact(String query, @Nullable String deviceAddress) {
        return getRemote(() -> mInteractionService.getContact(query, deviceAddress));
    }

    private interface RemoteSupplier<T> {
        T get() throws RemoteException;
    }

    private <T> T getRemote(RemoteSupplier<T> supplier) {
        try {
            if (mInteractionService == null) {
                throw new IllegalStateException("Service is not connected");
            }
            return supplier.get();
        } catch (RemoteException e) {
            throw new IllegalStateException("Unable to connect to service", e);
        }
    }

    final void notifySetupChanged() {
        Intent intent = new Intent(mContext, InteractionService.class);
        intent.setAction(InteractionService.LOCAL_SERVICE_CMD_SETUP_CHANGED);
        mContext.startService(intent);
    }
}
