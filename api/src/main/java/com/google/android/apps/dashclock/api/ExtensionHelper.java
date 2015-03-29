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

package com.google.android.apps.dashclock.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public final class ExtensionHelper {

    /**
     * Returns all the {@link com.google.android.apps.dashclock.api.internal.IExtension}
     * available in the device.
     */
    public static List<ExtensionListing> getAllAvailableExtensions(Context context, int protocolVersion) {
        List<ExtensionListing> availableExtensions = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentServices(
                new Intent(DashClockExtension.ACTION_EXTENSION), PackageManager.GET_META_DATA);
        for (ResolveInfo ri : ris) {
            ExtensionListing extension = new ExtensionListing();
            extension.componentName(new ComponentName(ri.serviceInfo.packageName,
                    ri.serviceInfo.name));
            extension.title(ri.loadLabel(pm).toString());
            extension.icon(ri.icon);
            Bundle metaData = ri.serviceInfo.metaData;
            if (metaData != null) {
                int extProtocolVersion = metaData.getInt("protocolVersion");
                extension.protocolVersion(extProtocolVersion);
                extension.compatible(extProtocolVersion != 0 && extProtocolVersion <= protocolVersion);
                extension.worldReadable(metaData.getBoolean("worldReadable", false));
                extension.description(metaData.getString("description"));
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    extension.settingsActivity(ComponentName.unflattenFromString(
                            ri.serviceInfo.packageName + "/" + settingsActivity));
                }
            }
            availableExtensions.add(extension);
        }
        return availableExtensions;
    }
}
