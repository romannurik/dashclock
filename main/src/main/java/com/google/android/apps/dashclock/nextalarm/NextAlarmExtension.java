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

package com.google.android.apps.dashclock.nextalarm;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.Utils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import net.nurik.roman.dashclock.R;

import android.provider.Settings;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Next alarm extension.
 */
public class NextAlarmExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(NextAlarmExtension.class);
    private static Pattern sDigitPattern = Pattern.compile("\\s[0-9]");

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED).toString()
            });
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        String nextAlarm = Settings.System.getString(getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        if (!TextUtils.isEmpty(nextAlarm)) {
            Matcher m = sDigitPattern.matcher(nextAlarm);
            if (m.find() && m.start() > 0) {
                nextAlarm = nextAlarm.substring(0, m.start()) + "\n"
                        + nextAlarm.substring(m.start() + 1); // +1 to skip whitespace
            }
        }
        publishUpdate(new ExtensionData()
                .visible(!TextUtils.isEmpty(nextAlarm))
                .icon(R.drawable.ic_extension_next_alarm)
                .status(nextAlarm)
                .clickIntent(Utils.getDefaultAlarmsIntent(this)));
    }
}
