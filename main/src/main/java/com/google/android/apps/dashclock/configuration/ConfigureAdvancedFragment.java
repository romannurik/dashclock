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

import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.dashclock.render.DashClockRenderer;

import net.nurik.roman.dashclock.R;

/**
 * Fragment for allowing the user to configure advanced widget settings, shown within a {@link
 * com.google.android.apps.dashclock.configuration.ConfigurationActivity}.
 */
public class ConfigureAdvancedFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public ConfigureAdvancedFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fast-forward new values based on deprecated preferences.
        SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        if (!sp.contains(AppearanceConfig.PREF_SETTINGS_BUTTON)
                && sp.getBoolean(AppearanceConfig.PREF_HIDE_SETTINGS, false)) {
            sp.edit().putString(AppearanceConfig.PREF_SETTINGS_BUTTON,
                    AppearanceConfig.PREF_SETTINGS_BUTTON_HIDDEN).apply();
        }

        // Add 'advanced' preferences.
        addPreferencesFromResource(R.xml.pref_advanced);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        BaseSettingsActivity.bindPreferenceSummaryToValue(
                findPreference(AppearanceConfig.PREF_SETTINGS_BUTTON));
        BaseSettingsActivity.bindPreferenceSummaryToValue(
                findPreference(DashClockRenderer.PREF_CLOCK_SHORTCUT));
        BaseSettingsActivity.bindPreferenceSummaryToValue(
                findPreference(AppearanceConfig.PREF_HOMESCREEN_BACKGROUND_OPACITY));
        BaseSettingsActivity.bindPreferenceSummaryToValue(
                findPreference(AppearanceConfig.PREF_LOCKSCREEN_BACKGROUND_OPACITY));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_simple_prefs, container, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        new BackupManager(getActivity()).dataChanged();

        // Potentially enable/disable the launcher activity if the settings button
        // preference has changed.
        if (isAdded() && AppearanceConfig.PREF_SETTINGS_BUTTON.equals(key)) {
            boolean enableLauncher = AppearanceConfig.PREF_SETTINGS_BUTTON_IN_LAUNCHER.equals(
                    sp.getString(AppearanceConfig.PREF_SETTINGS_BUTTON, null));
            getActivity().getPackageManager().setComponentEnabledSetting(
                    new ComponentName(
                            getActivity().getPackageName(),
                            ConfigurationActivity.LAUNCHER_ACTIVITY_NAME),
                    enableLauncher
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
