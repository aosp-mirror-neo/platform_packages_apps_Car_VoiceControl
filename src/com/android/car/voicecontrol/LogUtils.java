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
import android.os.Parcelable;
import android.service.voice.VoiceInteractionSession;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logging utility methods.
 */
public class LogUtils {
    private LogUtils() {}

    /**
     * Converts {@link VoiceInteractionSession#onShow(Bundle, int)} flags to human readable string.
     */
    public static String flagsToString(int showFlags) {
        List<String> parsedFlags = new ArrayList<>();
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE) != 0) {
            parsedFlags.add("SOURCE_ASSIST_GESTURE");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_APPLICATION) != 0) {
            parsedFlags.add("SOURCE_APPLICATION");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_ACTIVITY) != 0) {
            parsedFlags.add("SOURCE_ACTIVITY");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK) != 0) {
            parsedFlags.add("SOURCE_PUSH_TO_TALK");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_NOTIFICATION) != 0) {
            parsedFlags.add("SOURCE_NOTIFICATION");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_SOURCE_AUTOMOTIVE_SYSTEM_UI) != 0) {
            parsedFlags.add("SOURCE_AUTOMOTIVE_SYSTEM_UI");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_WITH_ASSIST) != 0) {
            parsedFlags.add("WITH_ASSIST");
        }
        if ((showFlags & VoiceInteractionSession.SHOW_WITH_SCREENSHOT) != 0) {
            parsedFlags.add("WITH_SCREENSHOT");
        }

        return String.join(", ", parsedFlags);
    }

    /**
     * Converts a {@link Parcelable} to human readable string.
     */
    public static String parcelableToString(Parcelable p) {
        StringBuilder sb = new StringBuilder();
        parcelableToString(p, sb);
        return sb.toString();
    }

    private static void parcelableToString(Parcelable p, StringBuilder sb) {
        sb.append("[");
        if (p instanceof Bundle) {
            Bundle b = (Bundle) p;
            for (String key : b.keySet()) {
                Object value = b.get(key);
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append(key);
                sb.append("=");
                if (value instanceof Parcelable) {
                    parcelableToString((Parcelable) value, sb);
                } else {
                    sb.append(value);
                }
            }
        } else {
            sb.append(p);
        }
        sb.append("]");
    }

    private static final Map<Long, String> PLAYBACK_STATES = Map.ofEntries(
            Map.entry(PlaybackStateCompat.ACTION_STOP, "STOP"),
            Map.entry(PlaybackStateCompat.ACTION_PAUSE, "ACTION_PAUSE"),
            Map.entry(PlaybackStateCompat.ACTION_PLAY, "ACTION_PLAY"),
            Map.entry(PlaybackStateCompat.ACTION_REWIND, "ACTION_REWIND"),
            Map.entry(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, "ACTION_SKIP_TO_PREVIOUS"),
            Map.entry(PlaybackStateCompat.ACTION_SKIP_TO_NEXT, "ACTION_SKIP_TO_NEXT"),
            Map.entry(PlaybackStateCompat.ACTION_FAST_FORWARD, "ACTION_FAST_FORWARD"),
            Map.entry(PlaybackStateCompat.ACTION_SET_RATING, "ACTION_SET_RATING"),
            Map.entry(PlaybackStateCompat.ACTION_SEEK_TO, "ACTION_SEEK_TO"),
            Map.entry(PlaybackStateCompat.ACTION_PLAY_PAUSE, "ACTION_PLAY_PAUSE"),
            Map.entry(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID, "ACTION_PLAY_FROM_MEDIA_ID"),
            Map.entry(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH, "ACTION_PLAY_FROM_SEARCH"),
            Map.entry(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM, "ACTION_SKIP_TO_QUEUE_ITEM"),
            Map.entry(PlaybackStateCompat.ACTION_PLAY_FROM_URI, "ACTION_PLAY_FROM_URI"),
            Map.entry(PlaybackStateCompat.ACTION_PREPARE, "ACTION_PREPARE"),
            Map.entry(
                    PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID,
                    "ACTION_PREPARE_FROM_MEDIA_ID"),
            Map.entry(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH, "ACTION_PREPARE_FROM_SEARCH"),
            Map.entry(PlaybackStateCompat.ACTION_PREPARE_FROM_URI, "ACTION_PREPARE_FROM_URI"),
            Map.entry(PlaybackStateCompat.ACTION_SET_REPEAT_MODE, "ACTION_SET_REPEAT_MODE"),
            Map.entry(
                    PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED,
                    "ACTION_SET_CAPTIONING_ENABLED"),
            Map.entry(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE, "ACTION_SET_SHUFFLE_MODE")
    );

    /**
     * Converts a {@link PlaybackStateCompat} action to readable string.
     */
    public static String playbackActionToString(long action) {
        List<String> actions = new ArrayList<>();
        for (Map.Entry<Long, String> e : PLAYBACK_STATES.entrySet()) {
            if ((e.getKey() & action) != 0) {
                actions.add(e.getValue());
            }
        }
        return String.join(", ", actions);
    }
}
