/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.configuration;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import com.google.android.apps.dashclock.render.DashClockRenderer;

/**
 * Helper class for working with DashClock appearance settings.
 */
public class AppearanceConfig {
    static final String COMPONENT_TIME = "time";
    static final String COMPONENT_DATE = "date";

    static final String PREF_STYLE_TIME = "pref_style_time";
    static final String PREF_STYLE_DATE = "pref_style_date";

    static final String PREF_HIDE_SETTINGS = "pref_hide_settings"; // deprecated
    static final String PREF_SETTINGS_BUTTON = "pref_settings_button";
    static final String PREF_AGGRESSIVE_CENTERING = "pref_aggressive_centering";

    static final String PREF_SETTINGS_BUTTON_HIDDEN = "hidden";
    static final String PREF_SETTINGS_BUTTON_IN_WIDGET = "inwidget";
    static final String PREF_SETTINGS_BUTTON_IN_LAUNCHER = "inlauncher";

    static final String PREF_HOMESCREEN_FOREGROUND_COLOR = "pref_homescreen_foreground_color";
    static final String PREF_HOMESCREEN_BACKGROUND_OPACITY = "pref_homescreen_background_opacity";
    static final String PREF_HOMESCREEN_HIDE_CLOCK = "pref_homescreen_hide_clock";

    static final String PREF_LOCKSCREEN_FOREGROUND_COLOR = "pref_lockscreen_foreground_color";
    static final String PREF_LOCKSCREEN_BACKGROUND_OPACITY = "pref_lockscreen_background_opacity";
    static final String PREF_LOCKSCREEN_HIDE_CLOCK = "pref_lockscreen_hide_clock";

    public static final int DEFAULT_WIDGET_FOREGROUND_COLOR = Color.WHITE;

    static String[] TIME_STYLE_NAMES = new String[]{
            "default",
            "light",
            "alpha",
            "stock",
            "condensed",
            "big_small",
            "analog1",
            "analog2",
    };

    static String[] DATE_STYLE_NAMES = new String[]{
            "default",
            "simple",
            "condensed_bold",
    };

    public static int getCurrentTimeLayout(Context context, int foregroundColor) {
        String currentTimeStyleName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_STYLE_TIME, TIME_STYLE_NAMES[0]);
        if (currentTimeStyleName.contains("analog")) {
            if (foregroundColor == Color.BLACK) {
                currentTimeStyleName += "_black";
            } else {
                currentTimeStyleName += "_white";
            }
        }
        return getLayoutByStyleName(context, COMPONENT_TIME, currentTimeStyleName);
    }

    public static int getCurrentDateLayout(Context context) {
        String currentDateStyleName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_STYLE_DATE, DATE_STYLE_NAMES[0]);
        return getLayoutByStyleName(context, COMPONENT_DATE, currentDateStyleName);
    }

    public static int getLayoutByStyleName(Context context, String component, String name) {
        return context.getResources().getIdentifier(
                "widget_include_" + component + "_style_" + name,
                "layout", context.getPackageName());
    }

    public static boolean isSettingsButtonHidden(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String pref = sp.getString(PREF_SETTINGS_BUTTON, null);
        if (pref == null) {
            // Check older preference
            return sp.getBoolean(PREF_HIDE_SETTINGS, false);
        }

        return !PREF_SETTINGS_BUTTON_IN_WIDGET.equals(pref);
    }

    public static boolean shouldLauncherSettingsBeShown(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String pref = sp.getString(PREF_SETTINGS_BUTTON, null);
        return PREF_SETTINGS_BUTTON_IN_LAUNCHER.equals(pref);
    }

    public static boolean isClockHiddenOnHomeScreen(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_HOMESCREEN_HIDE_CLOCK, false);
    }

    public static boolean isAggressiveCenteringEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_AGGRESSIVE_CENTERING, true);
    }

    public static boolean isClockHiddenOnLockScreen(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_LOCKSCREEN_HIDE_CLOCK, false);
    }

    public static int getForegroundColor(Context context, int target) {
        if (target == DashClockRenderer.Options.TARGET_HOME_SCREEN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PREF_HOMESCREEN_FOREGROUND_COLOR, Color.WHITE);
        } else if (target == DashClockRenderer.Options.TARGET_LOCK_SCREEN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PREF_LOCKSCREEN_FOREGROUND_COLOR, Color.WHITE);
        }
        return DEFAULT_WIDGET_FOREGROUND_COLOR;
    }

    public static int getBackgroundColor(Context context, int target) {
        int foregroundColor = getForegroundColor(context, target);
        int opacity = 0;
        try {
            if (target == DashClockRenderer.Options.TARGET_HOME_SCREEN) {
                opacity = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_HOMESCREEN_BACKGROUND_OPACITY, "50"));
            } else if (target == DashClockRenderer.Options.TARGET_LOCK_SCREEN) {
                opacity = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_LOCKSCREEN_BACKGROUND_OPACITY, "0"));
            }
        } catch (NumberFormatException ignored) {
        }

        int backgroundColor = (foregroundColor == Color.WHITE) ? Color.BLACK : Color.WHITE;
        return (backgroundColor & 0xffffff) | ((opacity * 255 / 100) << 24);
    }
}
