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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.util.List;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * Broadcast receiver used to watch for changes to installed packages on the device. This triggers
 * a cleanup of extensions (in case one was uninstalled), or a data update request to an extension
 * if it was updated (its package was replaced).
 */
public class ExtensionPackageChangeReceiver extends BroadcastReceiver {
    private static final String TAG = LogUtils.makeLogTag(ExtensionPackageChangeReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        ExtensionManager extensionManager = ExtensionManager.getInstance(context);
        if (extensionManager.cleanupExtensions()) {
            LOGD(TAG, "Extension cleanup performed and action taken.");

            Intent widgetUpdateIntent = new Intent(context, DashClockService.class);
            widgetUpdateIntent.setAction(DashClockService.ACTION_UPDATE_WIDGETS);
            context.startService(widgetUpdateIntent);
        }

        // If this is a replacement or change in the package, update all active extensions from
        // this package.
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (TextUtils.isEmpty(packageName)) {
                return;
            }

            List<ComponentName> activeExtensions = extensionManager.getActiveExtensionNames();
            for (ComponentName cn : activeExtensions) {
                if (packageName.equals(cn.getPackageName())) {
                    Intent extensionUpdateIntent = new Intent(context, DashClockService.class);
                    extensionUpdateIntent.setAction(DashClockService.ACTION_UPDATE_EXTENSIONS);
                    // TODO: UPDATE_REASON_PACKAGE_CHANGED
                    extensionUpdateIntent.putExtra(DashClockService.EXTRA_COMPONENT_NAME,
                            cn.flattenToShortString());
                    context.startService(extensionUpdateIntent);
                }
            }
        }
    }
}
