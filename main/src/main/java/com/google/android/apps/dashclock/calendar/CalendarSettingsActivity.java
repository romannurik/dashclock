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

package com.google.android.apps.dashclock.calendar;

import com.google.android.apps.dashclock.configuration.BaseSettingsActivity;

import net.nurik.roman.dashclock.R;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarSettingsActivity extends BaseSettingsActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setIcon(R.drawable.ic_extension_calendar);
    }

    @Override
    protected void setupSimplePreferencesScreen() {
        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_calendar);
        bindSelectedCalendarsPreference();

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference(CalendarExtension.PREF_LOOK_AHEAD_HOURS));
    }

    private void bindSelectedCalendarsPreference() {
        CalendarSelectionPreference preference = (CalendarSelectionPreference) findPreference(
                CalendarExtension.PREF_SELECTED_CALENDARS);
        final List<Pair<String, Boolean>> allCalendars = CalendarExtension.getAllCalendars(this);
        Set<String> allVisibleCalendarsSet = new HashSet<String>();
        for (Pair<String, Boolean> pair : allCalendars) {
            if (pair.second) {
                allVisibleCalendarsSet.add(pair.first);
            }
        }

        Preference.OnPreferenceChangeListener calendarsChangeListener
                = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                int numSelected = 0;
                int numTotal = allCalendars.size();

                try {
                    //noinspection check,unchecked
                    Set<String> selectedCalendars = (Set<String>) value;

                    if (selectedCalendars != null) {
                        numSelected = selectedCalendars.size();
                    }
                } catch (ClassCastException ignored) {
                }

                preference.setSummary(getResources().getQuantityString(
                        R.plurals.pref_calendar_selected_summary_template,
                        numTotal, numSelected, numTotal));
                return true;
            }
        };

        preference.setOnPreferenceChangeListener(calendarsChangeListener);
        calendarsChangeListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(this)
                        .getStringSet(preference.getKey(), allVisibleCalendarsSet));
    }
}
