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

package com.google.android.apps.dashclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.apps.dashclock.api.DashClockExtension;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * A broadcast receiver in charge of scheduling DashClock extension refreshes. This was
 * originally handled by updatePeriodMillis but custom refresh behavior and a separation of
 * extension refreshes from widget refreshes was desirable so was moved here.
 */
public class PeriodicExtensionRefreshReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = LogUtils.makeLogTag(PeriodicExtensionRefreshReceiver.class);

    private static final String ACTION_PERIODIC_ALARM
            = "com.google.android.apps.dashclock.action.PERIODIC_ALARM";

    private static final long FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PERIODIC_ALARM.equals(intent.getAction())) {
            return;
        }

        // Periodic alarm has triggered. Update all extensions.
        startWakefulService(context,
                getUpdateAllExtensionsIntent(context, DashClockExtension.UPDATE_REASON_PERIODIC));
    }

    private static Intent getUpdateAllExtensionsIntent(Context context, int reason) {
        LOGD(TAG, "getUpdateAllExtensionsIntent, reason=" + reason);
        return new Intent(context, DashClockService.class)
                .setAction(DashClockService.ACTION_UPDATE_EXTENSIONS)
                .putExtra(DashClockService.EXTRA_UPDATE_REASON, reason);
    }

    /**
     * Sets the refresh schedule if one isn't set already.
     */
    public static void updateExtensionsAndEnsurePeriodicRefresh(final Context context) {
        LOGD(TAG, "updateExtensionsAndEnsurePeriodicRefresh");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Update all extensions now.
        context.startService(getUpdateAllExtensionsIntent(context,
                DashClockExtension.UPDATE_REASON_MANUAL));

        // Schedule an alarm for every 30 minutes; it will not wake up the device;
        // it will be handled only once the device is awake. The first time that this
        // alarm can go off is in 15 minutes, and the latest time it will go off is
        // 45 minutes from now.
        PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                new Intent(context, PeriodicExtensionRefreshReceiver.class)
                        .setAction(ACTION_PERIODIC_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + FIFTEEN_MINUTES_MILLIS,
                AlarmManager.INTERVAL_HALF_HOUR,
                pi);
    }
}
