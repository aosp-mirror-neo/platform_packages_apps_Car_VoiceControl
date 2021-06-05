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

import com.android.car.voicecontrol.ILocalServiceListener;
import com.android.car.telephony.common.Contact;

interface ILocalService {
    boolean isSetupComplete();
    void registerListener(ILocalServiceListener listener);
    void unregisterListener(ILocalServiceListener listener);
    String getVoice();
    void setVoice(String value);
    String getUsername();
    void setUsername(String value);
    boolean hasAllPermissions();
    boolean isNotificationListener();
    Contact getContact(String query, String deviceAddress);
}
