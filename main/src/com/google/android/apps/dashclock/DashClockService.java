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
import com.google.android.apps.dashclock.api.IDashClockDataProvider;
import com.google.android.apps.dashclock.api.VisibleExtension;
import com.google.android.apps.dashclock.render.WidgetRenderer;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * The primary service for DashClock. This service is in charge of updating widget UI (see {@link
 * #ACTION_UPDATE_WIDGETS}) and updating extension data via an internal instance of {@link
 * ExtensionHost} (see {@link #ACTION_UPDATE_EXTENSIONS}).
 */
public class DashClockService extends Service implements ExtensionManager.OnChangeListener {
    private static final String TAG = LogUtils.makeLogTag(DashClockService.class);

    /**
     * Intent action for updating widget views. If {@link #EXTRA_APPWIDGET_ID} is provided, updates
     * only that widget. Otherwise, updates all widgets.
     */
    public static final String ACTION_UPDATE_WIDGETS =
            "com.google.android.apps.dashclock.action.UPDATE_WIDGETS";
    public static final String EXTRA_APPWIDGET_ID =
            "com.google.android.apps.dashclock.extra.APPWIDGET_ID";

    /**
     * Intent action for telling extensions to update their data. If {@link #EXTRA_COMPONENT_NAME}
     * is provided, updates only that extension. Otherwise, updates all active extensions. Also
     * optional is {@link #EXTRA_UPDATE_REASON} (see {@link DashClockExtension} for update reasons).
     */
    public static final String ACTION_UPDATE_EXTENSIONS =
            "com.google.android.apps.dashclock.action.UPDATE_EXTENSIONS";
    public static final String EXTRA_COMPONENT_NAME =
            "com.google.android.apps.dashclock.extra.COMPONENT_NAME";
    public static final String EXTRA_UPDATE_REASON =
            "com.google.android.apps.dashclock.extra.UPDATE_REASON";

    /**
     * Broadcast intent action that's triggered when the set of visible extensions or their
     * data change.
     */
    public static final String ACTION_EXTENSIONS_CHANGED =
            "com.google.android.apps.dashclock.action.EXTENSIONS_CHANGED";

    /**
     * Private Read API
     */
    public static final String ACTION_BIND_DASHCLOCK_SERVICE
            = "com.google.android.apps.dashclock.action.BIND_SERVICE";

    private ExtensionManager mExtensionManager;
    private ExtensionHost mExtensionHost;

    private Handler mUpdateHandler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        mExtensionManager = ExtensionManager.getInstance(this);
        mExtensionManager.addOnChangeListener(this);
        mExtensionHost = new ExtensionHost(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUpdateHandler.removeCallbacksAndMessages(null);
        mExtensionManager.removeOnChangeListener(this);
        mExtensionHost.destroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_UPDATE_WIDGETS.equals(action)) {
                handleUpdateWidgets(intent);

            } else if (ACTION_UPDATE_EXTENSIONS.equals(action)) {
                handleUpdateExtensions(intent);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onExtensionsChanged() {
        mUpdateHandler.removeCallbacks(mExtensionsChangedRunnable);
        mUpdateHandler.postDelayed(mExtensionsChangedRunnable,
                ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS);
    }

    private Runnable mExtensionsChangedRunnable = new Runnable() {
        @Override
        public void run() {
            sendBroadcast(new Intent(ACTION_EXTENSIONS_CHANGED));
            handleUpdateWidgets(new Intent());
        }
    };

    /**
     * Updates a widget's UI.
     */
    private void handleUpdateWidgets(Intent intent) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        // Either update all app widgets, or only those which were requested.
        int appWidgetIds[];
        if (intent.hasExtra(EXTRA_APPWIDGET_ID)) {
            appWidgetIds = new int[]{intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)};
        } else {
            appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(
                    this, WidgetProvider.class));
        }

        StringBuilder sb = new StringBuilder();
        for (int appWidgetId : appWidgetIds) {
            sb.append(appWidgetId).append(" ");
        }
        LOGD(TAG, "Rendering widgets with appWidgetId(s): " + sb);

        WidgetRenderer.renderWidgets(this, appWidgetIds);
    }

    /**
     * Asks extensions to provide data updates.
     */
    private void handleUpdateExtensions(Intent intent) {
        int reason = intent.getIntExtra(EXTRA_UPDATE_REASON,
                DashClockExtension.UPDATE_REASON_UNKNOWN);

        // Either update all extensions, or only the requested one.
        String updateExtension = intent.getStringExtra(EXTRA_COMPONENT_NAME);
        if (!TextUtils.isEmpty(updateExtension)) {
            ComponentName cn = ComponentName.unflattenFromString(updateExtension);
            mExtensionHost.execute(cn, ExtensionHost.UPDATE_OPERATIONS.get(reason),
                    ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS, reason);
        } else {
            for (ComponentName cn : mExtensionManager.getActiveExtensionNames()) {
                mExtensionHost.execute(cn, ExtensionHost.UPDATE_OPERATIONS.get(reason),
                        ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS, reason);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION_BIND_DASHCLOCK_SERVICE.equals(intent.getAction())) {
            // Private Read API
            return new IDashClockDataProvider.Stub() {
                @Override
                public List<VisibleExtension> getVisibleExtensionData() throws RemoteException {
                    List<VisibleExtension> visibleExtensions = new ArrayList<VisibleExtension>();
                    for (ExtensionManager.ExtensionWithData extension :
                            mExtensionManager.getVisibleExtensionsWithData()) {
                        if (!extension.listing.worldReadable) {
                            // Enforce permissions. This private 'read API' only exposes
                            // data from world-readable extensions.
                            continue;
                        }
                        visibleExtensions.add(new VisibleExtension()
                                .data(extension.latestData)
                                .componentName(extension.listing.componentName));
                    }
                    return visibleExtensions;
                }

                @Override
                public void updateExtensions() {
                    handleUpdateExtensions(new Intent());
                }
            };
        }
        return null;
    }
}
