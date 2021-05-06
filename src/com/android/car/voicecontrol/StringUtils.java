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

import android.util.Log;

/**
 * Utility string comparison methods
 */
public final class StringUtils {
    private static final String TAG = "Mica.StringUtils";

    // This is a utility class
    private StringUtils() {}

    /**
     * Returns the minimum edit distance between one source string and a set of possible
     * target strings
     */
    public static int getMinDistance(String source, String... targets) {
        int d = Integer.MAX_VALUE;
        for (String t : targets) {
            int newD = getDistance(source, t);
            d = Math.min(d, newD);
            Log.d(TAG, "Distance between: '" + source + "' and '" + t + "': " + newD);
        }
        return d;
    }

    //
    // Copied from https://commons.apache.org/proper/commons-lang/javadocs/api-2.5/src-html/org/apache/commons/lang/StringUtils.html
    //
    private static int getDistance(String s, String t) {
        int n = s.length();
        int m = t.length();

        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }

        if (n > m) {
            String tmp = s;
            s = t;
            t = tmp;
            n = m;
            m = t.length();
        }

        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;
        int i, j, cost;
        char t_j;

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }

            _d = p;
            p = d;
            d = _d;
        }

        return p[n];
    }
    //
    // end of https://commons.apache.org/proper/commons-lang/javadocs/api-2.5/src-html/org/apache/commons/lang/StringUtils.html
    //

    //
    // The section below has been copied from
    // http://commons.apache.org/proper/commons-codec/apidocs/src-html/org/apache/commons/codec/language/Soundex.html
    //
    private static final char SILENT_MARKER = '-';
    private static final char[] US_ENGLISH_MAPPING_STRING = "01230120022455012623010202"
            .toCharArray();

    private static char soundexMap(final char ch) {
        final int index = ch - 'A';
        if (index < 0 || index >= US_ENGLISH_MAPPING_STRING.length) {
            return SILENT_MARKER;
        }
        return US_ENGLISH_MAPPING_STRING[index];
    }

    private static String soundexClean(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        int len = str.length();
        char[] chars = new char[len];
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (Character.isLetter(str.charAt(i))) {
                chars[count++] = str.charAt(i);
            }
        }
        return new String(chars, 0, count).toUpperCase(java.util.Locale.ENGLISH);
    }

    /**
     * Encodes a string into a Soundex value. Soundex is an encoding used to relate similar names,
     * but can also be used as a general purpose scheme to find word with similar phonemes.
     */
    public static String soundex(String str) {
        if (str == null) {
            return null;
        }
        str = soundexClean(str);
        if (str.length() == 0) {
            return str;
        }
        final char[] out = {'0', '0', '0', '0'};
        int count = 0;
        final char first = str.charAt(0);
        out[count++] = first;
        char lastDigit = soundexMap(first);
        for (int i = 1; i < str.length() && count < out.length; i++) {
            final char ch = str.charAt(i);
            if (ch == 'H' || ch == 'W') {
                continue;
            }
            final char digit = soundexMap(ch);
            if (digit == SILENT_MARKER) {
                continue;
            }
            if (digit != '0' && digit != lastDigit) {
                out[count++] = digit;
            }
            lastDigit = digit;
        }
        return new String(out);
    }
    //
    // End of
    // http://commons.apache.org/proper/commons-codec/apidocs/src-html/org/apache/commons/codec/language/Soundex.html
    //
}
