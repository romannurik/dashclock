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

import com.google.android.apps.dashclock.api.DashClockExtension;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * The DashClock {@link AppWidgetProvider}, which just forwards commands to {@link
 * DashClockService}.
 */
public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = LogUtils.makeLogTag(WidgetProvider.class);

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        // Update extensions and ensure the periodic refresh is set up.
        PeriodicExtensionRefreshReceiver.updateExtensionsAndEnsurePeriodicRefresh(context);

        // Update widgets
        for (final int appWidgetId : appWidgetIds) {
            Intent widgetUpdateIntent = new Intent(context, DashClockService.class);
            widgetUpdateIntent.setAction(DashClockService.ACTION_UPDATE_WIDGETS);
            widgetUpdateIntent.putExtra(DashClockService.EXTRA_APPWIDGET_ID, appWidgetId);
            context.startService(widgetUpdateIntent);
        }

        StringBuilder sb = new StringBuilder();
        for (int appWidgetId : appWidgetIds) {
            sb.append(appWidgetId).append(" ");
        }
        LOGD(TAG, "onUpdate for appWidgetId(s): " + sb);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);

        LOGD(TAG, "onAppWidgetOptionsChanged for appWidgetId: " + appWidgetId);

        Intent widgetUpdateIntent = new Intent(context, DashClockService.class);
        widgetUpdateIntent.setAction(DashClockService.ACTION_UPDATE_WIDGETS);
        widgetUpdateIntent.putExtra(DashClockService.EXTRA_APPWIDGET_ID, appWidgetId);
        context.startService(widgetUpdateIntent);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        LOGD(TAG, "onDisabled; stopping DashClockService.");
        context.stopService(new Intent(context, DashClockService.class));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        int[] remainingIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, WidgetProvider.class));
        if (remainingIds == null || remainingIds.length == 0) {
            LOGD(TAG, "Widget deleted, none remaining; stopping DashClockService.");
            context.stopService(new Intent(context, DashClockService.class));
        } else {
            LOGD(TAG, "Widget deleted, " + remainingIds.length + " remaining.");
        }
    }
}
