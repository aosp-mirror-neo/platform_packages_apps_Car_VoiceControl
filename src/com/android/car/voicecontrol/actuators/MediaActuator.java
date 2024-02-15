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

import android.annotation.MainThread;
import android.annotation.StringRes;
import android.car.Car;
import android.car.media.CarMediaManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.media.MediaBrowserService;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.source.MediaBrowserConnector;
import com.android.car.media.common.source.MediaSource;
import com.android.car.voicecontrol.LogUtils;
import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actuator that interacts with media sources
 */
public class MediaActuator implements SkillRouter.Actuator, MediaBrowserConnector.Callback {
    private static final String TAG = "Mica.MediaActuator";

    private Context mContext;
    private Car mCar;
    private CarMediaManager mCarMediaManager;
    private CarMediaManager.MediaSourceChangedListener mSourceChangedListener =
            this::onMediaSourceChanged;
    private MediaBrowserConnector mBrowserConnector;
    private PackageManager mPackageManager;
    private AppInstallUninstallReceiver mInstallUninstallReceiver;
    private MediaSource mCurrentSource;
    private MediaBrowserCompat mCurrentBrowser;
    private MediaControllerCompat mMediaController;
    // Media sources by display name (the name users would use to refer to these sources)
    private Map<String, MediaSource> mAvailableMediaSources;

    @Override
    public List<Skill> onCreate(Context context) {
        mContext = context;
        mCar = Car.createCar(context);
        mBrowserConnector = new MediaBrowserConnector(context, this);
        mCarMediaManager = (CarMediaManager) mCar.getCarManager(Car.CAR_MEDIA_SERVICE);
        mCarMediaManager.addMediaSourceListener(mSourceChangedListener,
                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK);
        mPackageManager = context.getPackageManager();
        onMediaSourceChanged(mCarMediaManager.getMediaSource(
                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK));
        mAvailableMediaSources = getAvailableMediaSources();
        mInstallUninstallReceiver = createAppInstallUninstallReceiver(context);

        return Arrays.asList(
                new Skill("play (.*) on (.*)", (args, tts) -> playSongOnSource(args[1],
                        args[2], tts)),
                new Skill("play (.*)", (args, tts) -> playSongOnDefault(args[1], tts)),
                new Skill("play", (args, tts) -> playCurrentSource(tts)),
                new Skill("pause", (args, tts) -> pauseCurrentSource(tts)),
                new Skill("stop", (args, tts) -> stopCurrentSource(tts)),
                new Skill("what(.*) playing?", (args, tts) -> informCurrentSong(tts))
        );
    }

    private class AppInstallUninstallReceiver extends BroadcastReceiver {
        @MainThread
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (TextUtils.isEmpty(packageName)) {
                // Ignoring empty announcements
                return;
            }
            mAvailableMediaSources = getAvailableMediaSources();
        }
    }

    /** Media states that can be "stopped" */
    static final Set<Integer> ACTIVE_STATES = new HashSet<>(Arrays.asList(
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.STATE_FAST_FORWARDING,
            PlaybackStateCompat.STATE_REWINDING,
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_CONNECTING,
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM
    ));

    private Map<String, MediaSource> getAvailableMediaSources() {
        List<String> customMediaServices = Arrays.asList(mContext.getResources()
                .getStringArray(R.array.custom_media_packages));
        List<ResolveInfo> mediaServices = mPackageManager.queryIntentServices(
                new Intent(MediaBrowserService.SERVICE_INTERFACE),
                PackageManager.GET_SERVICES);
        Map<String, MediaSource> result = new HashMap<>();
        for (ResolveInfo info : mediaServices) {
            String packageName = info.serviceInfo.packageName;
            if (customMediaServices.contains(packageName)) {
                // Custom media sources should be ignored, as they might have a specialized
                // handling (e.g.: radio).
                continue;
            }
            String className = info.serviceInfo.name;
            ComponentName componentName = new ComponentName(packageName, className);
            MediaSource source = MediaSource.create(mContext, componentName);
            if (source != null) {
                result.put(source.getDisplayName(mContext).toString().toLowerCase(), source);
            }
        }
        return result;
    }

    private AppInstallUninstallReceiver createAppInstallUninstallReceiver(Context context) {
        AppInstallUninstallReceiver installUninstallReceiver = new AppInstallUninstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        context.registerReceiver(installUninstallReceiver, filter);
        return installUninstallReceiver;
    }

    @Override
    public void onDestroy() {
        if (mCurrentBrowser != null) {
            mCurrentBrowser.disconnect();
            mCurrentBrowser = null;
        }
        mContext.unregisterReceiver(mInstallUninstallReceiver);
        mCarMediaManager.removeMediaSourceListener(mSourceChangedListener,
                CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK);
        mCar.disconnect();
    }

    @Nullable
    private MediaControllerCompat.TransportControls getTransportControls(
            MediaControllerCompat controller) {
        if (controller == null) {
            Log.d(TAG, "No connected controller");
            return null;
        }
        return controller.getTransportControls();
    }

    private boolean playSongOnSource(String song, String source, TextToSpeech tts) {
        Log.i(TAG, "playSongOnSource(song: " + song + ", source: " + source + ")");
        MediaSource mediaSource = mAvailableMediaSources.get(source.toLowerCase());
        if (mediaSource == null) {
            tts.speak(R.string.speech_reply_application_not_found, source);
            return true;
        }
        MediaBrowserConnector mediaBrowserConnector = new MediaBrowserConnector(mContext,
                browsingState -> {
                    Log.d(TAG, "playSongOnSource(): " + browsingState.mMediaSource
                            + " connection: " + browsingState.mConnectionStatus);

                    switch (browsingState.mConnectionStatus) {
                        case CONNECTED:
                            MediaBrowserCompat browser = browsingState.mBrowser;
                            MediaControllerCompat controller =
                                    new MediaControllerCompat(mContext, browser.getSessionToken());
                            if (!executePlaybackAction(controller, mediaSource,
                                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH, song,
                                    R.string.speech_reply_not_ready_to_play,
                                    R.string.speech_reply_okay_to_play_source, tts)) {
                                tts.speak(R.string.speech_reply_source_error,
                                        mediaSource.getDisplayName(mContext));
                            }
                            browser.disconnect();
                            break;
                        case REJECTED:
                            tts.speak(R.string.speech_reply_unable_to_connect, source);
                            break;
                        case SUSPENDED:
                            tts.speak(R.string.speech_reply_crashed, source);
                            break;
                        case CONNECTING:
                        case DISCONNECTING:
                            // Do nothing (these are normal transitory states)
                            break;
                    }
                });
        Log.d(TAG, "playSongOnSource(): connecting to: " + mediaSource);
        mediaBrowserConnector.connectTo(mediaSource);
        return true;
    }

    private boolean playSongOnDefault(String song, TextToSpeech tts) {
        Log.i(TAG, "playSongOnDefault(song: " + song + ")");
        return executePlaybackAction(mMediaController, mCurrentSource,
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH,
                song, R.string.speech_reply_not_ready_to_play,
                R.string.speech_reply_okay_to_play_source, tts);
    }

    private boolean informCurrentSong(TextToSpeech tts) {
        Log.i(TAG, "informCurrentSong()");
        if (mMediaController == null) {
            Log.d(TAG, "No connected controller");
            return false;
        }
        CharSequence displayName = mCurrentSource.getDisplayName(mContext);
        PlaybackStateCompat playbackState = mMediaController.getPlaybackState();
        if (!ACTIVE_STATES.contains(playbackState.getState())) {
            tts.speak(R.string.speech_reply_nothing_to_play, displayName);
            return false;
        }
        MediaMetadataCompat metadata = mMediaController.getMetadata();
        if (metadata == null || metadata.getDescription() == null
                || metadata.getDescription().getTitle() == null) {
            tts.speak(R.string.speech_reply_playing_unknown, displayName);
            return true;
        }
        String song = metadata.toString();
        tts.speak(R.string.speech_reply_playing_song, song, displayName);
        return true;
    }

    private boolean stopCurrentSource(TextToSpeech tts) {
        Log.i(TAG, "stopCurrentSource()");
        return executePlaybackAction(mMediaController, mCurrentSource,
                PlaybackStateCompat.ACTION_STOP, null,
                R.string.speech_reply_source_is_not_playing,
                R.string.speech_reply_okay_stopping, tts);
    }

    private boolean pauseCurrentSource(TextToSpeech tts) {
        Log.i(TAG, "stopCurrentSource()");
        return executePlaybackAction(mMediaController, mCurrentSource,
                PlaybackStateCompat.ACTION_PAUSE, null,
                R.string.speech_reply_cannot_pause,
                R.string.speech_reply_okay_pausing, tts);
    }

    private boolean playCurrentSource(TextToSpeech tts) {
        Log.i(TAG, "playCurrentSource()");
        return executePlaybackAction(mMediaController, mCurrentSource,
                PlaybackStateCompat.ACTION_PLAY, null,
                R.string.speech_reply_cannot_resume,
                R.string.speech_reply_okay_resuming, tts);
    }

    private boolean executePlaybackAction(MediaControllerCompat controller, MediaSource source,
            long action, String query, @StringRes int unavailableSpeech, @StringRes int okaySpeech,
            TextToSpeech tts) {
        MediaControllerCompat.TransportControls controls = getTransportControls(controller);
        if (controls == null) {
            Log.d(TAG, "executePlaybackAction(): no transport controls");
            return false;
        }
        PlaybackStateCompat state = controller.getPlaybackState();
        if (state == null || ((state.getActions() & action) == 0)) {
            Log.d(TAG, String.format("executePlaybackAction(): action %s not available"
                            + " (available: %s)", LogUtils.playbackActionToString(action),
                    LogUtils.playbackActionToString(state != null ? state.getActions() : 0)));
            tts.speak(unavailableSpeech, source.getDisplayName(mContext));
            return true;
        }
        if (action == PlaybackStateCompat.ACTION_PLAY) {
            controls.play();
        } else if (action == PlaybackStateCompat.ACTION_PAUSE) {
            controls.pause();
        } else if (action == PlaybackStateCompat.ACTION_STOP) {
            controls.stop();
        } else if (action == PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) {
            controls.playFromSearch(query, null);
        } else {
            tts.speak(R.string.speech_reply_unrecognized_cmd);
            return false;
        }
        tts.speak(okaySpeech, source.getDisplayName(mContext));
        return true;
    }

    private void onMediaSourceChanged(@Nullable ComponentName newComponent) {
        Log.d(TAG, "onMediaSourceChanged: " + newComponent);
        mCurrentSource = newComponent != null ? MediaSource.create(mContext, newComponent) : null;
        // Let's not connect to the radio browser from here
        if (newComponent == null
                || newComponent.equals(RadioActuator.getRadioComponentName(mPackageManager))) {
            mBrowserConnector.connectTo(null);
        } else {
            mBrowserConnector.connectTo(mCurrentSource);
        }
    }

    @Override
    public void onBrowserConnectionChanged(@NonNull MediaBrowserConnector.BrowsingState state) {
        Log.d(TAG, "onBrowserConnectionChanged: " + state.mMediaSource
                + " connection: " + state.mConnectionStatus);
        MediaBrowserCompat browser = state.mBrowser;
        if (mCurrentBrowser != null && mCurrentBrowser != browser) {
            mCurrentBrowser.disconnect();
            mCurrentBrowser = null;
        }

        if (browser.isConnected()) {
            mCurrentBrowser = browser;
            mMediaController = new MediaControllerCompat(mContext, browser.getSessionToken());
        } else {
            mMediaController = null;
        }
    }
}
