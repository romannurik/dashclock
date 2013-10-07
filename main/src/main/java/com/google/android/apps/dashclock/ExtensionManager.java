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

import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.gmail.GmailExtension;
import com.google.android.apps.dashclock.nextalarm.NextAlarmExtension;
import com.google.android.apps.dashclock.weather.WeatherExtension;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * A singleton class in charge of extension registration, activation (change in user-specified
 * 'active' extensions), and data caching.
 */
public class ExtensionManager {
    private static final String TAG = LogUtils.makeLogTag(ExtensionManager.class);

    private static final String PREF_ACTIVE_EXTENSIONS = "active_extensions";

    private static final Class[] DEFAULT_EXTENSIONS = {
            WeatherExtension.class,
            GmailExtension.class,
            NextAlarmExtension.class,
    };

    private final Context mApplicationContext;

    private final List<ExtensionWithData> mActiveExtensions = new ArrayList<ExtensionWithData>();
    private Map<ComponentName, ExtensionWithData> mExtensionInfoMap
            = new HashMap<ComponentName, ExtensionWithData>();
    private List<OnChangeListener> mOnChangeListeners = new ArrayList<OnChangeListener>();

    private SharedPreferences mDefaultPreferences;
    private SharedPreferences mValuesPreferences;
    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private static ExtensionManager sInstance;

    public static ExtensionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExtensionManager(context);
        }

        return sInstance;
    }

    private ExtensionManager(Context context) {
        mApplicationContext = context.getApplicationContext();
        mDefaultPreferences = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
        mValuesPreferences = mApplicationContext.getSharedPreferences("extension_data", 0);
        loadActiveExtensionList();
    }

    /**
     * De-activates active extensions that are unsupported or are no longer installed.
     */
    public boolean cleanupExtensions() {
        Set<ComponentName> availableExtensions = new HashSet<ComponentName>();
        for (ExtensionListing listing : getAvailableExtensions()) {
            // Ensure the extension protocol version is supported. If it isn't, don't allow its use.
            if (!ExtensionHost.supportsProtocolVersion(listing.protocolVersion)) {
                LOGW(TAG, "Extension '" + listing.title + "' using unsupported protocol version "
                        + listing.protocolVersion + ".");
                continue;
            }
            availableExtensions.add(listing.componentName);
        }

        boolean cleanupRequired = false;
        ArrayList<ComponentName> newActiveExtensions = new ArrayList<ComponentName>();

        synchronized (mActiveExtensions) {
            for (ExtensionWithData ewd : mActiveExtensions) {
                if (availableExtensions.contains(ewd.listing.componentName)) {
                    newActiveExtensions.add(ewd.listing.componentName);
                } else {
                    cleanupRequired = true;
                }
            }
        }

        if (cleanupRequired) {
            setActiveExtensions(newActiveExtensions);
            return true;
        }

        return false;
    }

    private void loadActiveExtensionList() {
        List<ComponentName> activeExtensions = new ArrayList<ComponentName>();
        String extensions;
        if (mDefaultPreferences.contains(PREF_ACTIVE_EXTENSIONS)) {
            extensions = mDefaultPreferences.getString(PREF_ACTIVE_EXTENSIONS, "");
        } else {
            extensions = createDefaultExtensionList();
        }
        String[] componentNameStrings = extensions.split(",");
        for (String componentNameString : componentNameStrings) {
            if (TextUtils.isEmpty(componentNameString)) {
                continue;
            }
            activeExtensions.add(ComponentName.unflattenFromString(componentNameString));
        }
        setActiveExtensions(activeExtensions, false);
    }

    private String createDefaultExtensionList() {
        StringBuilder sb = new StringBuilder();

        for (Class cls : DEFAULT_EXTENSIONS) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(new ComponentName(mApplicationContext, cls).flattenToString());
        }

        return sb.toString();
    }

    private void saveActiveExtensionList() {
        StringBuilder sb = new StringBuilder();

        synchronized (mActiveExtensions) {
            for (ExtensionWithData ci : mActiveExtensions) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(ci.listing.componentName.flattenToString());
            }
        }

        mDefaultPreferences.edit()
                .putString(PREF_ACTIVE_EXTENSIONS, sb.toString())
                .commit();
        new BackupManager(mApplicationContext).dataChanged();
    }

    /**
     * Replaces the set of active extensions with the given list.
     */
    public void setActiveExtensions(List<ComponentName> extensions) {
        setActiveExtensions(extensions, true);
    }

    private void setActiveExtensions(List<ComponentName> extensionNames, boolean saveAndNotify) {
        Map<ComponentName, ExtensionListing> listings
                = new HashMap<ComponentName, ExtensionListing>();
        for (ExtensionListing listing : getAvailableExtensions()) {
            listings.put(listing.componentName, listing);
        }

        List<ComponentName> activeExtensionNames = getActiveExtensionNames();
        if (activeExtensionNames.equals(extensionNames)) {
            LOGD(TAG, "No change to list of active extensions.");
            return;
        }

        // Clear cached data for any no-longer-active extensions.
        for (ComponentName cn : activeExtensionNames) {
            if (!extensionNames.contains(cn)) {
                destroyExtensionData(cn);
            }
        }

        // Set the new list of active extensions, loading cached data if necessary.
        List<ExtensionWithData> newActiveExtensions = new ArrayList<ExtensionWithData>();

        for (ComponentName cn : extensionNames) {
            if (mExtensionInfoMap.containsKey(cn)) {
                newActiveExtensions.add(mExtensionInfoMap.get(cn));
            } else {
                ExtensionWithData ewd = new ExtensionWithData();
                ewd.listing = listings.get(cn);
                if (ewd.listing == null) {
                    ewd.listing = new ExtensionListing();
                    ewd.listing.componentName = cn;
                }
                ewd.latestData = deserializeExtensionData(ewd.listing.componentName);
                newActiveExtensions.add(ewd);
            }
        }

        mExtensionInfoMap.clear();
        for (ExtensionWithData ewd : newActiveExtensions) {
            mExtensionInfoMap.put(ewd.listing.componentName, ewd);
        }

        synchronized (mActiveExtensions) {
            mActiveExtensions.clear();
            mActiveExtensions.addAll(newActiveExtensions);
        }

        if (saveAndNotify) {
            LOGD(TAG, "List of active extensions has changed.");
            saveActiveExtensionList();
            notifyOnChangeListeners(null);
        }
    }

    /**
     * Updates and caches the user-visible data for a given extension.
     */
    public boolean updateExtensionData(ComponentName cn, ExtensionData data) {
        data.clean();

        ExtensionWithData ewd = mExtensionInfoMap.get(cn);
        if (ewd != null && !ExtensionData.equals(ewd.latestData, data)) {
            ewd.latestData = data;
            serializeExtensionData(ewd.listing.componentName, data);
            notifyOnChangeListeners(ewd.listing.componentName);
            return true;
        }
        return false;
    }

    private ExtensionData deserializeExtensionData(ComponentName componentName) {
        ExtensionData extensionData = new ExtensionData();
        String val = mValuesPreferences.getString(componentName.flattenToString(), "");
        if (!TextUtils.isEmpty(val)) {
            try {
                extensionData.deserialize((JSONObject) new JSONTokener(val).nextValue());
            } catch (JSONException e) {
                LOGE(TAG, "Error loading extension data cache for " + componentName + ".",
                        e);
            }
        }
        return extensionData;
    }

    private void serializeExtensionData(ComponentName componentName, ExtensionData extensionData) {
        try {
            mValuesPreferences.edit()
                    .putString(componentName.flattenToString(),
                            extensionData.serialize().toString())
                    .commit();
        } catch (JSONException e) {
            LOGE(TAG, "Error storing extension data cache for " + componentName + ".", e);
        }
    }

    private void destroyExtensionData(ComponentName componentName) {
        mValuesPreferences.edit()
                .remove(componentName.flattenToString())
                .commit();
    }

    public List<ExtensionWithData> getActiveExtensionsWithData() {
        ArrayList<ExtensionWithData> activeExtensions;
        synchronized (mActiveExtensions) {
            activeExtensions = new ArrayList<ExtensionWithData>(mActiveExtensions);
        }
        return activeExtensions;
    }

    public List<ExtensionWithData> getVisibleExtensionsWithData() {
        ArrayList<ExtensionWithData> visibleExtensions = new ArrayList<ExtensionWithData>();
        synchronized (mActiveExtensions) {
            for (ExtensionManager.ExtensionWithData ewd : mActiveExtensions) {
                if (ewd.latestData.visible()) {
                    visibleExtensions.add(ewd);
                }
            }
        }
        return visibleExtensions;
    }

    public List<ComponentName> getActiveExtensionNames() {
        List<ComponentName> list = new ArrayList<ComponentName>();
        for (ExtensionWithData ci : mActiveExtensions) {
            list.add(ci.listing.componentName);
        }
        return list;
    }

    /**
     * Returns a listing of all available (installed) extensions.
     */
    public List<ExtensionListing> getAvailableExtensions() {
        List<ExtensionListing> availableExtensions = new ArrayList<ExtensionListing>();
        PackageManager pm = mApplicationContext.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(DashClockExtension.ACTION_EXTENSION), PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing listing = new ExtensionListing();
            listing.componentName = new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name);
            listing.title = resolveInfo.loadLabel(pm).toString();
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                listing.protocolVersion = metaData.getInt("protocolVersion");
                listing.worldReadable = metaData.getBoolean("worldReadable", false);
                listing.description = metaData.getString("description");
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    listing.settingsActivity = ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity);
                }
            }

            listing.icon = resolveInfo.loadIcon(pm);
            availableExtensions.add(listing);
        }

        return availableExtensions;
    }

    /**
     * Registers a listener to be triggered when either the list of active extensions changes or an
     * extension's data changes.
     */
    public void addOnChangeListener(OnChangeListener onChangeListener) {
        mOnChangeListeners.add(onChangeListener);
    }

    /**
     * Removes a listener previously registered with {@link #addOnChangeListener}.
     */
    public void removeOnChangeListener(OnChangeListener onChangeListener) {
        mOnChangeListeners.remove(onChangeListener);
    }

    private void notifyOnChangeListeners(final ComponentName sourceExtension) {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                for (OnChangeListener listener : mOnChangeListeners) {
                    listener.onExtensionsChanged(sourceExtension);
                }
            }
        });
    }

    public static interface OnChangeListener {
        /**
         * @param sourceExtension null if not related to any specific extension (e.g. list of
         *                        extensions has changed).
         */
        void onExtensionsChanged(ComponentName sourceExtension);
    }

    public static class ExtensionWithData {
        public ExtensionListing listing;
        public ExtensionData latestData;
    }

    public static class ExtensionListing {
        public ComponentName componentName;
        public int protocolVersion;
        public boolean worldReadable;
        public String title;
        public String description;
        public Drawable icon;
        public ComponentName settingsActivity;
    }
}
