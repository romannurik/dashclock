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

package com.google.android.apps.dashclock.gmail;

import com.google.android.apps.dashclock.configuration.BaseSettingsActivity;

import net.nurik.roman.dashclock.R;

import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GmailSettingsActivity extends BaseSettingsActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setIcon(R.drawable.ic_extension_gmail);
    }

    @Override
    protected void setupSimplePreferencesScreen() {
        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_gmail);

        addAccountsPreference();

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference(GmailExtension.PREF_LABEL));
    }

    private void addAccountsPreference() {
        final String[] accounts = GmailExtension.getAllAccountNames(this);
        Set<String> allAccountsSet = new HashSet<String>();
        allAccountsSet.addAll(Arrays.asList(accounts));

        MultiSelectListPreference accountsPreference = new MultiSelectListPreference(this);
        accountsPreference.setKey(GmailExtension.PREF_ACCOUNTS);
        accountsPreference.setTitle(R.string.pref_gmail_accounts_title);
        accountsPreference.setEntries(accounts);
        accountsPreference.setEntryValues(accounts);
        accountsPreference.setDefaultValue(allAccountsSet);
        getPreferenceScreen().addPreference(accountsPreference);

        Preference.OnPreferenceChangeListener accountsChangeListener
                = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                int numSelected = 0;
                int numTotal = accounts.length;

                try {
                    //noinspection unchecked
                    Set<String> selectedAccounts = (Set<String>) value;
                    if (selectedAccounts != null) {
                        numSelected = selectedAccounts.size();
                    }
                } catch (ClassCastException ignored) {
                }

                preference.setSummary(getResources().getQuantityString(
                        R.plurals.pref_gmail_accounts_summary_template,
                        numTotal, numSelected, numTotal));
                return true;
            }
        };

        accountsPreference.setOnPreferenceChangeListener(accountsChangeListener);
        accountsChangeListener.onPreferenceChange(accountsPreference,
                PreferenceManager
                        .getDefaultSharedPreferences(this)
                        .getStringSet(accountsPreference.getKey(), allAccountsSet));
    }
}
