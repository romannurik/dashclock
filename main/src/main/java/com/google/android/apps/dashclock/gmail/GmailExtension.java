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

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import net.nurik.roman.dashclock.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.IntentCompat;
import android.text.TextUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * Gmail unread count extension.
 */
public class GmailExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(GmailExtension.class);

    public static final String PREF_ACCOUNTS = "pref_gmail_accounts";
    public static final String PREF_LABEL = "pref_gmail_label";

    private static final String ACCOUNT_TYPE_GOOGLE = "com.google";

    private static final String SECTIONED_INBOX_CANONICAL_NAME_PREFIX = "^sq_ig_i_";
    private static final String SECTIONED_INBOX_CANONICAL_NAME_PERSONAL = "^sq_ig_i_personal";

    //private static final String[] FEATURES_MAIL = {"service_mail"};

    static String[] getAllAccountNames(Context context) {
        final Account[] accounts = AccountManager.get(context).getAccountsByType(
                GmailExtension.ACCOUNT_TYPE_GOOGLE);
        final String[] accountNames = new String[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            accountNames[i] = accounts[i].name;
        }
        return accountNames;
    }

    private Set<String> getSelectedAccounts() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final String[] accounts = GmailExtension.getAllAccountNames(this);
        Set<String> allAccountsSet = new HashSet<String>();
        allAccountsSet.addAll(Arrays.asList(accounts));
        return sp.getStringSet(PREF_ACCOUNTS, allAccountsSet);
    }

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (!isReconnect) {
            Set<String> selectedAccounts = getSelectedAccounts();
            String[] uris = new String[selectedAccounts.size()];

            int i = 0;
            for (String account : selectedAccounts) {
                uris[i++] = GmailContract.Labels.getLabelsUri(account).toString();
                // TODO: only watch the individual label's URI (GmailContract.Labels.URI)
            }

            addWatchContentUris(uris);
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String labelCanonical = sp.getString(PREF_LABEL, "i");
        Set<String> selectedAccounts = getSelectedAccounts();

        if ("i".equals(labelCanonical)) {
            labelCanonical = GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_INBOX;
        } else if ("p".equals(labelCanonical)) {
            labelCanonical = GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_PRIORITY_INBOX;
        }

        int unread = 0;
        List<Pair<String, Integer>> unreadPerAccount = new ArrayList<Pair<String, Integer>>();
        String lastUnreadLabelUri = null;

        for (String account : selectedAccounts) {
            Cursor cursor = tryOpenLabelsCursor(account);
            if (cursor == null || cursor.isAfterLast()) {
                LOGD(TAG, "No Gmail inbox information found for account.");
                if (cursor != null) {
                    cursor.close();
                }
                continue;
            }

            int accountUnread = 0;

            while (cursor.moveToNext()) {
                int thisUnread = cursor.getInt(LabelsQuery.NUM_UNREAD_CONVERSATIONS);
                String thisCanonicalName = cursor.getString(LabelsQuery.CANONICAL_NAME);
                if (labelCanonical.equals(thisCanonicalName)) {
                    accountUnread = thisUnread;
                    if (thisUnread > 0) {
                        lastUnreadLabelUri = cursor.getString(LabelsQuery.URI);
                    }
                    break;
                } else if (!TextUtils.isEmpty(thisCanonicalName)
                        && thisCanonicalName.startsWith(SECTIONED_INBOX_CANONICAL_NAME_PREFIX)) {
                    accountUnread += thisUnread;
                    if (thisUnread > 0
                            && SECTIONED_INBOX_CANONICAL_NAME_PERSONAL.equals(thisCanonicalName)) {
                        lastUnreadLabelUri = cursor.getString(LabelsQuery.URI);
                    }
                }
            }

            if (accountUnread > 0) {
                unreadPerAccount.add(new Pair<String, Integer>(account, accountUnread));
                unread += accountUnread;
            }

            cursor.close();
        }

        StringBuilder body = new StringBuilder();
        for (Pair<String, Integer> pair : unreadPerAccount) {
            if (pair.second == 0) {
                continue;
            }

            if (body.length() > 0) {
                body.append("\n");
            }
            body.append(pair.first).append(" (").append(pair.second).append(")");
        }

        Intent clickIntent = null;
        if (lastUnreadLabelUri != null) {
            try {
                clickIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(lastUnreadLabelUri));
                if (getPackageManager().resolveActivity(clickIntent, 0) == null) {
                    throw new IllegalStateException("Gmail can't open this label directly.");
                }
            } catch (Exception e) {
                LOGW(TAG, "Can't open Gmail label directly.", e);
                clickIntent = null;
            }
        }

        if (clickIntent == null) {
            clickIntent = new Intent(Intent.ACTION_MAIN)
                    .setPackage("com.google.android.gm")
                    .addCategory(Intent.CATEGORY_LAUNCHER);
        }

        publishUpdate(new ExtensionData()
                .visible(unread > 0)
                .status(Integer.toString(unread))
                .expandedTitle(getResources().getQuantityString(
                        R.plurals.gmail_title_template, unread, unread))
                .icon(R.drawable.ic_extension_gmail)
                .expandedBody(body.toString())
                .clickIntent(clickIntent));
    }

    private Cursor tryOpenLabelsCursor(String account) {
        try {
            return getContentResolver().query(
                    GmailContract.Labels.getLabelsUri(account),
                    LabelsQuery.PROJECTION,
                    null, // NOTE: the Labels API doesn't allow selections here
                    null,
                    null);

        } catch (Exception e) {
            // From developer console: "Permission Denial: opening provider com.google.android.gsf..
            // From developer console: "SQLiteException: no such table: labels"
            // From developer console: "NullPointerException"
            LOGE(TAG, "Error opening Gmail labels", e);
            return null;
        }
    }

    private interface LabelsQuery {
        String[] PROJECTION = {
                GmailContract.Labels.NUM_UNREAD_CONVERSATIONS,
                GmailContract.Labels.URI,
                GmailContract.Labels.CANONICAL_NAME,
        };

        int NUM_UNREAD_CONVERSATIONS = 0;
        int URI = 1;
        int CANONICAL_NAME = 2;
    }
}
