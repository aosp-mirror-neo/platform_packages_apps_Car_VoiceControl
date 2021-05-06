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

import static com.android.car.voicecontrol.actuators.MediaActuator.ACTIVE_STATES;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.car.broadcastradio.support.media.BrowseTree;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.media.common.source.MediaBrowserConnector;
import com.android.car.media.common.source.MediaSource;
import com.android.car.voicecontrol.LogUtils;
import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;

import java.util.Arrays;
import java.util.List;

/**
 * Actuator that interacts with local radio
 */
public class RadioActuator implements SkillRouter.Actuator, MediaBrowserConnector.Callback {
    private static final String TAG = "Mica.RadioActuator";
    private Context mContext;
    private ComponentName mRadioComponentName;
    private MediaBrowserConnector mBrowserConnector;
    private PackageManager mPackageManager;
    private MediaControllerCompat mMediaController;
    private MediaBrowserCompat mMediaBrowser;

    @Override
    public List<Skill> onCreate(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mBrowserConnector = new MediaBrowserConnector(context, this);
        mRadioComponentName = getRadioComponentName(mPackageManager);
        mBrowserConnector.connectTo(MediaSource.create(context, mRadioComponentName));

        return Arrays.asList(
                new Skill("tune to (.*) fm", (args, tts) ->
                        tuneRadioFrequencyAndBand(args[1], RadioManager.BAND_FM, tts)),
                new Skill("tune to (.*) am", (args, tts) ->
                        tuneRadioFrequencyAndBand(args[1], RadioManager.BAND_AM, tts)),
                new Skill("tune to (.*)", (args, tts) ->
                        tuneRadioFrequencyAndBand(args[1], RadioManager.BAND_INVALID, tts)),
                new Skill("stop", (args, tts) -> stopTuning(tts))
        );
    }

    @Override
    public void onDestroy() {
        if (mMediaBrowser != null) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }
    }

    /**
     * @return the radio browser service component name
     */
    public static ComponentName getRadioComponentName(PackageManager packageManager) {
        Intent radioIntent = new Intent(BrowseTree.ACTION_PLAY_BROADCASTRADIO);
        List<ResolveInfo> mediaServices = packageManager.queryIntentServices(
                radioIntent, PackageManager.GET_SERVICES);
        if (mediaServices.isEmpty()) {
            Log.d(TAG, "There is no radio mediabrowse installed");
            return null;
        } else if (mediaServices.size() > 1) {
            Log.w(TAG, "There is more than one potential radio mediabrowse service. It is "
                    + "recommend to leave only one. Picking the first one.");
        }
        return mediaServices.get(0).serviceInfo.getComponentName();
    }

    private boolean tuneRadioFrequencyAndBand(String frequency, @RadioManager.Band int bandId,
            TextToSpeech tts) {
        if (mMediaController == null) {
            Log.d(TAG, "No radio MediaController connected.");
            return false;
        }
        MediaControllerCompat.TransportControls controls = mMediaController.getTransportControls();
        ProgramSelector selector = parseFrequency(frequency, bandId);
        Uri uri = (selector == null) ? null : ProgramSelectorExt.toUri(selector);
        if (uri == null) {
            tts.speak(R.string.speech_reply_radio_not_found);
            return true;
        }
        controls.playFromUri(uri, null);
        tts.speak(R.string.speech_reply_okay_tuning_radio,
                ProgramSelectorExt.getDisplayName(selector, 0));
        return true;
    }

    private boolean stopTuning(TextToSpeech tts) {
        if (mMediaController == null) {
            Log.d(TAG, "No radio MediaController connected.");
            return false;
        }
        PlaybackStateCompat state = mMediaController.getPlaybackState();
        if (state == null || !ACTIVE_STATES.contains(state.getState())) {
            Log.d(TAG, "Radio is not playing anything");
            return false;
        }
        if ((state.getActions() & PlaybackStateCompat.ACTION_STOP) == 0) {
            Log.d(TAG, "Stop action not available. Available actions: "
                    + LogUtils.playbackActionToString(state.getActions()));
            tts.speak(R.string.speech_reply_radio_cannot_stop);
            return true;
        }
        mMediaController.getTransportControls().stop();
        tts.speak(R.string.speech_reply_okay_stop_radio);
        return true;
    }

    private ProgramSelector parseFrequency(String frequency, @RadioManager.Band int bandId) {
        try {
            final int frequencyKHz = (int) (Float.parseFloat(frequency) * 1000);
            return ProgramSelector.createAmFmSelector(bandId, frequencyKHz);
        } catch (NumberFormatException ex) {
            Log.d(TAG, "Unable to parse frequency: " + frequency);
            return null;
        }
    }

    @Override
    public void onBrowserConnectionChanged(@NonNull MediaBrowserConnector.BrowsingState state) {
        Log.d(TAG, "onBrowserConnectionChanged: " + state.mMediaSource
                + " connection: " + state.mConnectionStatus);

        MediaBrowserCompat browser = state.mBrowser;
        if (mMediaBrowser != null && mMediaBrowser != browser) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }

        mMediaBrowser = browser;
        if (browser.isConnected()) {
            mMediaController = new MediaControllerCompat(mContext, browser.getSessionToken());
        } else {
            mMediaController = null;
        }
    }
}
