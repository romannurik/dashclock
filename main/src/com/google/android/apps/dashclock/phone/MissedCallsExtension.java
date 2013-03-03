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

package com.google.android.apps.dashclock.phone;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import net.nurik.roman.dashclock.R;

import android.content.Intent;
import android.database.Cursor;
import android.provider.CallLog;
import android.text.TextUtils;

import java.util.SortedSet;
import java.util.TreeSet;

import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * Number of missed calls extension.
 */
public class MissedCallsExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(MissedCallsExtension.class);

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    CallLog.Calls.CONTENT_URI.toString()
            });
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        Cursor cursor = tryOpenMissedCallsCursor();
        if (cursor == null) {
            LOGE(TAG, "Null missed calls cursor, short-circuiting.");
            return;
        }

        int missedCalls = 0;
        SortedSet<String> names = new TreeSet<String>();
        while (cursor.moveToNext()) {
            ++missedCalls;
            String name = cursor.getString(MissedCallsQuery.CACHED_NAME);
            if (TextUtils.isEmpty(name)) {
                name = cursor.getString(MissedCallsQuery.NUMBER);
                long parsedNumber = 0;
                try {
                    parsedNumber = Long.parseLong(name);
                } catch (Exception ignored) {
                }
                if (parsedNumber < 0) {
                    // Unknown or private number
                    name = getString(R.string.missed_calls_unknown);
                }
            }
            names.add(name);
        }
        cursor.close();

        publishUpdate(new ExtensionData()
                .visible(missedCalls > 0)
                .icon(R.drawable.ic_extension_missed_calls)
                .status(Integer.toString(missedCalls))
                .expandedTitle(
                        getResources().getQuantityString(
                                R.plurals.missed_calls_title_template, missedCalls, missedCalls))
                .expandedBody(getString(R.string.missed_calls_body_template,
                        TextUtils.join(", ", names)))
                .clickIntent(new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)));
    }

    private Cursor tryOpenMissedCallsCursor() {
        try {
            return getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    MissedCallsQuery.PROJECTION,
                    CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE + " AND "
                            + CallLog.Calls.NEW + "!=0",
                    null,
                    null);

        } catch (Exception e) {
            LOGE(TAG, "Error opening missed calls cursor", e);
            return null;
        }
    }

    private interface MissedCallsQuery {
        String[] PROJECTION = {
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
        };

        int ID = 0;
        int CACHED_NAME = 1;
        int NUMBER = 2;
    }
}
