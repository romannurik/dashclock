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

package com.google.android.apps.dashclock.weather;

import com.google.android.apps.dashclock.DashClockService;
import com.google.android.apps.dashclock.api.DashClockExtension;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver that's a target of the retry-update alarm from {@link WeatherExtension}.
 */
public class WeatherRetryReceiver extends BroadcastReceiver {
    static PendingIntent getPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, WeatherRetryReceiver.class),
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Update weather
        // TODO: there should be a way for an extension to request an update through the API
        // See issue 292.
        context.startService(new Intent(context, DashClockService.class)
                .setAction(DashClockService.ACTION_UPDATE_EXTENSIONS)
                .putExtra(DashClockService.EXTRA_UPDATE_REASON,
                        DashClockExtension.UPDATE_REASON_UNKNOWN) // TODO: UPDATE_REASON_RETRY
                .putExtra(DashClockService.EXTRA_COMPONENT_NAME,
                        new ComponentName(context, WeatherExtension.class)));

    }
}
