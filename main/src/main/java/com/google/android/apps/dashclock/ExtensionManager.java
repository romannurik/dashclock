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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.host.ExtensionListing;
import com.google.android.apps.dashclock.gmail.GmailExtension;
import com.google.android.apps.dashclock.nextalarm.NextAlarmExtension;
import com.google.android.apps.dashclock.weather.WeatherExtension;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
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

    private final Context mApplicationContext;

    private final List<ComponentName> mInternalActiveExtensions = new ArrayList<>();
    private final Set<ExtensionWithData> mActiveExtensions = new HashSet<>();

    private Map<ComponentName, ExtensionWithData> mExtensionInfoMap = new HashMap<>();
    private List<OnChangeListener> mOnChangeListeners = new ArrayList<>();

    private SharedPreferences mValuesPreferences;
    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private static ExtensionManager sInstance;
    private static final String PREF_ACTIVE_EXTENSIONS = "active_extensions";

    private static final Class[] DEFAULT_EXTENSIONS = {
            WeatherExtension.class,
            GmailExtension.class,
            NextAlarmExtension.class,
    };

    private SharedPreferences mDefaultPreferences;

    private ExtensionManager(Context context) {
        mApplicationContext = context.getApplicationContext();
        mValuesPreferences = mApplicationContext.getSharedPreferences("extension_data", 0);
        mDefaultPreferences = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
        loadInternalActiveExtensionList();
    }

    public static ExtensionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExtensionManager(context);
        }

        return sInstance;
    }

    /**
     * De-activates active extensions that are unsupported or are no longer installed.
     */
    public boolean cleanupExtensions() {
        Set<ComponentName> availableExtensions = new HashSet<>();
        for (ExtensionListing info : getAvailableExtensions()) {
            // Ensure the extension protocol version is supported. If it isn't, don't allow its use.
            if (!info.compatible()) {
                LOGW(TAG, "Extension '" + info.title() + "' using unsupported protocol version "
                        + info.protocolVersion() + ".");
                continue;
            }
            availableExtensions.add(info.componentName());
        }

        boolean cleanupRequired = false;
        Set<ComponentName> newActiveExtensions = new HashSet<>();

        synchronized (mActiveExtensions) {
            for (ExtensionWithData ewd : mActiveExtensions) {
                if (availableExtensions.contains(ewd.listing.componentName())) {
                    newActiveExtensions.add(ewd.listing.componentName());
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

    /**
     * Replaces the set of active extensions with the given list.
     */
    public void setActiveExtensions(Set<ComponentName> extensions) {
        // Join external and internal extensions
        Set<ComponentName> allExtensions = new HashSet<>(getInternalActiveExtensionNames());
        for (ComponentName cn : extensions) {
            if (!allExtensions.contains(cn)) {
                allExtensions.add(cn);
            }
        }

        Map<ComponentName, ExtensionListing> infos = new HashMap<>();
        for (ExtensionListing info : getAvailableExtensions()) {
            infos.put(info.componentName(), info);
        }

        Set<ComponentName> activeExtensionNames = getActiveExtensionNames();
        if (activeExtensionNames.equals(allExtensions)) {
            LOGD(TAG, "No change to list of active extensions.");
            return;
        }

        // Clear cached data for any no-longer-active extensions.
        for (ComponentName cn : activeExtensionNames) {
            if (!allExtensions.contains(cn)) {
                destroyExtensionData(cn);
            }
        }

        // Set the new list of active extensions, loading cached data if necessary.
        List<ExtensionWithData> newActiveExtensions = new ArrayList<>();

        for (ComponentName cn : allExtensions) {
            if (mExtensionInfoMap.containsKey(cn)) {
                newActiveExtensions.add(mExtensionInfoMap.get(cn));
            } else {
                ExtensionWithData ewd = new ExtensionWithData();
                ewd.listing = infos.get(cn);
                if (ewd.listing == null) {
                    ewd.listing = new ExtensionListing();
                    ewd.listing.componentName(cn);
                }
                ewd.latestData = deserializeExtensionData(ewd.listing.componentName());
                newActiveExtensions.add(ewd);
            }
        }

        mExtensionInfoMap.clear();
        for (ExtensionWithData ewd : newActiveExtensions) {
            mExtensionInfoMap.put(ewd.listing.componentName(), ewd);
        }

        synchronized (mActiveExtensions) {
            mActiveExtensions.clear();
            mActiveExtensions.addAll(newActiveExtensions);
        }

        LOGD(TAG, "List of active extensions has changed.");
        notifyOnChangeListeners(null);
    }

    /**
     * Updates and caches the user-visible data for a given extension.
     */
    public boolean updateExtensionData(ComponentName cn, ExtensionData data) {
        data.clean();

        ExtensionWithData ewd = mExtensionInfoMap.get(cn);
        if (ewd != null && !ExtensionData.equals(ewd.latestData, data)) {
            ewd.latestData = data;
            serializeExtensionData(ewd.listing.componentName(), data);
            notifyOnChangeListeners(ewd.listing.componentName());
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
                    .apply();
        } catch (JSONException e) {
            LOGE(TAG, "Error storing extension data cache for " + componentName + ".", e);
        }
    }

    private void destroyExtensionData(ComponentName componentName) {
        mValuesPreferences.edit()
                .remove(componentName.flattenToString())
                .apply();
    }

    public ExtensionWithData getExtensionWithData(ComponentName extension) {
        return mExtensionInfoMap.get(extension);
    }

    public List<ExtensionWithData> getActiveExtensionsWithData() {
        ArrayList<ExtensionWithData> activeExtensions;
        synchronized (mActiveExtensions) {
            activeExtensions = new ArrayList<>(mActiveExtensions);
        }
        return activeExtensions;
    }

    public List<ExtensionWithData> getInternalActiveExtensionsWithData() {
        // Extract the data from the all active extension cache
        List<ComponentName> internalActiveExtensions = getInternalActiveExtensionNames();
        ArrayList<ExtensionWithData> activeExtensions = new ArrayList<>(
                Arrays.asList(new ExtensionWithData[internalActiveExtensions.size()]));
        synchronized (mActiveExtensions) {
            for (ExtensionWithData ewd : mActiveExtensions) {
                int pos = internalActiveExtensions.indexOf(ewd.listing.componentName());
                if (pos >= 0) {
                    activeExtensions.set(pos, ewd);
                }
            }
        }

        // Clean any null/unset data
        int count = activeExtensions.size();
        for (int i = count - 1; i >= 0; i--) {
            if (activeExtensions.get(i) == null) {
                activeExtensions.remove(i);
            }
        }
        return activeExtensions;
    }

    public Set<ComponentName> getActiveExtensionNames() {
        Set<ComponentName> list = new HashSet<>();
        for (ExtensionWithData ci : mActiveExtensions) {
            list.add(ci.listing.componentName());
        }
        return list;
    }

    public List<ComponentName> getInternalActiveExtensionNames() {
        return new ArrayList<>(mInternalActiveExtensions);
    }

    /**
     * Returns a listing of all available (installed) extensions, including those that aren't
     * world-readable.
     */
    public List<ExtensionListing> getAvailableExtensions() {
        List<ExtensionListing> availableExtensions = new ArrayList<>();
        PackageManager pm = mApplicationContext.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(DashClockExtension.ACTION_EXTENSION), PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing info = new ExtensionListing();
            info.componentName(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
            info.title(resolveInfo.loadLabel(pm).toString());
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                info.compatible(ExtensionHost.supportsProtocolVersion(
                        metaData.getInt("protocolVersion")));
                info.worldReadable(metaData.getBoolean("worldReadable", false));
                info.description(metaData.getString("description"));
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    info.settingsActivity(ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity));
                }
            }

            info.icon(resolveInfo.getIconResource());
            availableExtensions.add(info);
        }

        return availableExtensions;
    }


    private void loadInternalActiveExtensionList() {
        List<ComponentName> activeExtensions = new ArrayList<>();
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
        setInternalActiveExtensions(activeExtensions);
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

    /**
     * Replaces the set of active extensions with the given list.
     */
    public void setInternalActiveExtensions(List<ComponentName> extensions) {
        StringBuilder sb = new StringBuilder();

        for (ComponentName extension : extensions) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(extension.flattenToString());
        }

        mDefaultPreferences.edit()
                .putString(PREF_ACTIVE_EXTENSIONS, sb.toString())
                .apply();
        new BackupManager(mApplicationContext).dataChanged();

        mInternalActiveExtensions.clear();
        mInternalActiveExtensions.addAll(extensions);
        setActiveExtensions(getActiveExtensionNames());
    }

    public List<ExtensionWithData> getVisibleExtensionsWithData() {
        ArrayList<ExtensionWithData> visibleExtensions = new ArrayList<>();
        for (ExtensionWithData ewd : getInternalActiveExtensionsWithData()) {
            if (ewd.latestData.visible()) {
                visibleExtensions.add(ewd);
            }
        }
        return visibleExtensions;
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

    public interface OnChangeListener {
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
}
