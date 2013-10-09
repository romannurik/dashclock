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

import net.nurik.roman.dashclock.R;

import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.dashclock.DaydreamService;

/**
 * Fragment for allowing the user to configure daydream settings, shown within a {@link
 * ConfigurationActivity}.
 */
public class ConfigureDaydreamFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_DAYDREAM_SYSTEM_SETTINGS = "pref_daydream_system_settings";

    public ConfigureDaydreamFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add 'advanced' preferences.
        addPreferencesFromResource(R.xml.pref_daydream);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        BaseSettingsActivity.bindPreferenceSummaryToValue(
                findPreference(DaydreamService.PREF_DAYDREAM_ANIMATION));

        // Hide Daydream settings if the activity doesn't exist.
        hidePreferenceIfTroublesomeIntent(PREF_DAYDREAM_SYSTEM_SETTINGS);
    }

    private void hidePreferenceIfTroublesomeIntent(String key) {
        Preference pref = findPreference(key);
        if (pref == null) {
            return;
        }

        Intent intent = pref.getIntent();
        if (intent != null
                && getActivity().getPackageManager().resolveActivity(intent, 0) == null) {
            getPreferenceScreen().removePreference(pref);
        }
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        new BackupManager(getActivity()).dataChanged();
    }
}
