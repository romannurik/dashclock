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

package com.google.android.apps.dashclock.render;

import net.nurik.roman.dashclock.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.google.android.apps.dashclock.WidgetProvider;
import com.google.android.apps.dashclock.configuration.AppearanceConfig;

/**
 * Class in charge of rendering DashClock to {@link android.widget.RemoteViews},
 * along with {@link WidgetRemoteViewsFactoryService}.
 */
public class WidgetRenderer extends DashClockRenderer {
    protected WidgetRenderer(Context context) {
        super(context);
    }

    @Override
    protected ViewBuilder onCreateViewBuilder() {
        return new WidgetViewBuilder(mContext);
    }

    /**
     * Renders the DashClock UI to the given app widget IDs.
     */
    public static void renderWidgets(Context context, int[] appWidgetIds) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        WidgetRenderer renderer = new WidgetRenderer(context);
        for (int appWidgetId : appWidgetIds) {
            Options options = new Options();
            options.appWidgetId = appWidgetId;
            options.target = Options.TARGET_HOME_SCREEN;
            options.minWidthDp= Integer.MAX_VALUE;
            options.minHeightDp = Integer.MAX_VALUE;
            Bundle widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
            if (widgetOptions != null) {
                options.minWidthDp = widgetOptions
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                options.minHeightDp = widgetOptions
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                options.target = (AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD ==
                        widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY))
                        ? Options.TARGET_LOCK_SCREEN : Options.TARGET_HOME_SCREEN;
            }
            options.foregroundColor = AppearanceConfig.getForegroundColor(options.target, context);

            renderer.setOptions(options);
            appWidgetManager.updateAppWidget(appWidgetId,
                    (RemoteViews) renderer.renderWidget(null));

            // During an update to an existing expanded widget, setRemoteAdapter does nothing,
            // so we need to explicitly call notifyAppWidgetViewDataChanged to update data.
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId,
                    R.id.expanded_extensions);
        }
    }

    public static void notifyDataSetChanged(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, WidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds,
                R.id.expanded_extensions);
    }

    @Override
    protected void builderSetExpandedExtensionsAdapter(ViewBuilder vb, int viewId,
            boolean mini, Intent clickTemplateIntent) {
        Intent remoteAdapterIntent = new Intent(mContext, WidgetRemoteViewsFactoryService.class);
        if (mOptions.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            remoteAdapterIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    mOptions.appWidgetId);
            remoteAdapterIntent.putExtra(WidgetRemoteViewsFactoryService.EXTRA_TARGET,
                    mOptions.target);
            remoteAdapterIntent.putExtra(WidgetRemoteViewsFactoryService.EXTRA_IS_MINI,
                    mini);
        }

        // TODO: is this setData call really necessary?
        remoteAdapterIntent.setData(Uri.parse(remoteAdapterIntent.toUri(Intent.URI_INTENT_SCHEME)));
        RemoteViews root = (RemoteViews) vb.getRoot();
        root.setRemoteAdapter(viewId, remoteAdapterIntent);
        root.setPendingIntentTemplate(viewId,
                PendingIntent.getActivity(mContext, 0,
                        clickTemplateIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
