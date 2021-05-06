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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.car.ui.core.CarUi;
import com.android.car.ui.toolbar.Toolbar;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample sign-in or setup activity. This represents a UI this voice interaction service would use
 * to configure the service. If this service is not provisioned yet when it gets selected as
 * default, this UI will be shown. If the user exists the setup before it is complete, a
 * notification will be displayed, reminding the user to complete the setup.
 */
public class SignInActivity extends Activity {
    private static final String TAG = "Mica.SignInActivity";
    private static final int REQUEST_CODE = 1;

    private InteractionServiceClient mInteractionService;
    private Button mSignInButton;
    private Button mPermissionButton;
    private Button mNotifListenerButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sign_in_activity);

        ToolbarController toolbar = CarUi.requireToolbar(this);
        toolbar.setTitle(R.string.sign_in_title);
        toolbar.setState(Toolbar.State.SUBPAGE);

        mSignInButton = findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(v -> onSignInClicked());
        mPermissionButton = findViewById(R.id.permissions_button);
        mPermissionButton.setOnClickListener(v -> onRequestPermissions());
        mNotifListenerButton = findViewById(R.id.notifications_button);
        mNotifListenerButton.setOnClickListener(v -> onRequestNotificationListenerAccess());
        mInteractionService = new InteractionServiceClient(this) {
            @Override
            void onConnected() {
                updateSetupStatus();
            }

            @Override
            void onSetupChanged() {
                updateSetupStatus();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mInteractionService.connect();
    }

    @Override
    protected void onPause() {
        mInteractionService.disconnect();
        super.onPause();
    }

    private void updateSetupStatus() {
        if (!mInteractionService.isConnected()) {
            // Wait until we are connected
            return;
        }
        if (mInteractionService.isSetupComplete()) {
            finish();
        } else {
            mSignInButton.setVisibility(!mInteractionService.hasUsername()
                    ? View.VISIBLE : View.GONE);
            mPermissionButton.setVisibility(!mInteractionService.hasAllPermissions()
                    ? View.VISIBLE : View.GONE);
            mNotifListenerButton.setVisibility(!mInteractionService.isNotificationListener()
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void onSignInClicked() {
        // This is just a fake implementation of sign-in. Complete implementation of authentication
        // flow is outside the scope of this sample application.
        mInteractionService.setUsername(getString(R.string.sign_in_fake_username));
    }

    private void onRequestPermissions() {
        requestPermissions(InteractionService.REQUIRED_PERMISSIONS.toArray(new String[0]),
                REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode != REQUEST_CODE) {
            return;
        }
        List<String> missingPermissions = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permissions[i]);
            }
        }
        if (!missingPermissions.isEmpty()) {
            Toast.makeText(this, "The following permissions have not been granted: "
                    + missingPermissions, Toast.LENGTH_SHORT).show();
            return;
        }
        mInteractionService.notifySetupChanged();
    }

    private void onRequestNotificationListenerAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }
}
