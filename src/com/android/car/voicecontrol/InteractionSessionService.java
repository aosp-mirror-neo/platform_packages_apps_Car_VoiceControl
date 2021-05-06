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

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

/**
 * Voice interaction session factory. This service will be invoked every time the system needs to
 * start a new {@link VoiceInteractionSession}. Events received by this service are delegated to the
 * {@klink VoiceInteractionSession} for it to handle them.
 */
public class InteractionSessionService extends VoiceInteractionSessionService {
    private static final String TAG = "Mica.SessionService";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.i(TAG, "onNewSession(args: " + args + ")");
        return new InteractionSession(this);
    }
}
