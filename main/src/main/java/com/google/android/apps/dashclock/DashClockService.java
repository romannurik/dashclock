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

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.host.DashClockHost;
import com.google.android.apps.dashclock.api.DashClockSignature;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.host.ExtensionListing;
import com.google.android.apps.dashclock.api.internal.IDataConsumerHost;
import com.google.android.apps.dashclock.api.internal.IDataConsumerHostCallback;
import com.google.android.apps.dashclock.render.WidgetRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * The primary service for DashClock. This service is in charge of updating widget UI (see {@link
 * #ACTION_UPDATE_WIDGETS}) and updating extension data (see {@link #ACTION_UPDATE_EXTENSIONS}).
 */
public class DashClockService extends Service implements
        ExtensionManager.OnChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
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
     * Related to the Read API.
     */
    protected static final String ACTION_EXTENSION_UPDATE_REQUESTED =
            "com.google.android.apps.dashclock.action.EXTENSION_UPDATE_REQUESTED";

    /**
     * Broadcast intent action that's triggered when the set of visible extensions or their
     * data change.
     */
    public static final String ACTION_EXTENSIONS_CHANGED =
            "com.google.android.apps.dashclock.action.EXTENSIONS_CHANGED";

    /**
     * The amount of time to wait after something has changed before recognizing it as an individual
     * event. Any changes within this time window will be collapsed, and will further delay the
     * handling of the event.
     */
    public static final int UPDATE_COLLAPSE_TIME_MILLIS = 500;

    /**
     * Force all extensions to be readable by external apps.
     */
    public static final String PREF_FORCE_WORLD_READABLE = "pref_force_world_readable";

    private ExtensionHost mExtensionHost;
    private ExtensionManager mExtensionManager;
    private CallbackList mCallbacks;
    private Map<IBinder, CallbackData> mRegisteredCallbacks;
    private Handler mHandler = new Handler();
    private boolean mForceWorldReadable;

    @Override
    public void onCreate() {
        super.onCreate();
        LOGD(TAG, "onCreate");

        // Initialize the extensions components (host and manager)
        mCallbacks = new CallbackList();
        mRegisteredCallbacks = new HashMap<>();
        mExtensionManager = ExtensionManager.getInstance(this);
        mExtensionManager.addOnChangeListener(this);
        mExtensionHost = new ExtensionHost(this);

        IntentFilter filter = new IntentFilter(ACTION_EXTENSION_UPDATE_REQUESTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mExtensionEventsReceiver, filter);

        // Start a periodic refresh of all the extensions
        // FIXME: only do this if there are any active extensions
        PeriodicExtensionRefreshReceiver.updateExtensionsAndEnsurePeriodicRefresh(this);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(sp, PREF_FORCE_WORLD_READABLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOGD(TAG, "onDestroy");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mExtensionEventsReceiver);
        PeriodicExtensionRefreshReceiver.cancelPeriodicRefresh(this);

        mExtensionHost.destroy();
        mCallbacks.kill();

        mUpdateHandler.removeCallbacksAndMessages(null);
        mExtensionManager.removeOnChangeListener(this);

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOGD(TAG, "onStartCommand: " + (intent != null ? intent.toString() : "no intent"));
        enforceCallingPermission(DashClockExtension.PERMISSION_READ_EXTENSION_DATA);

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_UPDATE_WIDGETS.equals(action)) {
                handleUpdateWidgets(intent);

            } else if (ACTION_UPDATE_EXTENSIONS.equals(action)) {
                handleUpdateExtensions(intent);
            }

            // If started by a wakeful broadcast receiver, release the wake lock it acquired.
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }

        return START_STICKY;
    }

    private Handler mUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LOGD(TAG, "onExtensionsChanged from "
                    + (msg.obj != null ? "extension " + msg.obj : "DashClock"));
            sendBroadcast(new Intent(ACTION_EXTENSIONS_CHANGED));
            handleUpdateWidgets(new Intent());
            WidgetRenderer.notifyDataSetChanged(DashClockService.this);
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
        String updateExtension = intent.getStringExtra(EXTRA_COMPONENT_NAME);

        LOGD(TAG, String.format("handleUpdateExtensions [action=%s, reason=%d, extension=%s]",
                intent.getAction(), reason, updateExtension == null ? "" : updateExtension));

        // Either update all extensions, or only the requested one.
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
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (PREF_FORCE_WORLD_READABLE.equals(key)) {
            mForceWorldReadable = sp.getBoolean(PREF_FORCE_WORLD_READABLE, false);
            onExtensionsChanged(null);
        }
    }

    /*
     * Read API
     */

    private static class CallbackData {
        int mUid;
        String mPackage;
        boolean mHasDashClockSignature;
        List<ComponentName> mExtensions;
    }

    private IDataConsumerHost.Stub mBinder = new IDataConsumerHost.Stub() {
        @Override
        public void listenTo(final List<ComponentName> extensions,
                             final IDataConsumerHostCallback cb) throws RemoteException {
            if (cb == null) {
                throw new NullPointerException("Callback must not be null");
            }
            enforceCallingPermission(DashClockHost.BIND_DATA_CONSUMER_PERMISSION);

            final int callingUid = Binder.getCallingUid();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    CallbackData data = createCallbackData(callingUid);
                    data.mExtensions = extensions;
                    mCallbacks.update(cb, data);
                }
            });
        }

        @Override
        public void showExtensionSettings(ComponentName extension,
                                          final IDataConsumerHostCallback cb) throws RemoteException {
            // Check that callback was registered and that extension was enabled
            enforceEnabledExtensionForCallback(cb, extension);

            // Make sure we know about the passed in extension
            ExtensionListing info = findExtensionInfo(extension);
            if (info == null) {
                throw new NullPointerException("ExtensionInfo doesn't exists");
            }
            if (info.settingsActivity() == null) {
                // Nothing to show
                return;
            }

            // Start the proxy activity
            Intent i = new Intent(DashClockService.this, ExtensionSettingActivityProxy.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(EXTRA_COMPONENT_NAME, info.componentName().flattenToString());
            i.putExtra(ExtensionSettingActivityProxy.EXTRA_SETTINGS_ACTIVITY,
                    info.settingsActivity().flattenToString());
            startActivity(i);
        }

        @Override
        public void requestExtensionUpdate(List<ComponentName> extensions,
                                           final IDataConsumerHostCallback cb) throws RemoteException {
            enforceRegisteredCallingCallback(cb);
            internalRequestUpdateData(cb, extensions);
        }

        @Override
        public List<ExtensionListing> getAvailableExtensions() throws RemoteException {
            return mExtensionManager.getAvailableExtensions();
        }

        @Override
        public boolean areNonWorldReadableExtensionsVisible() throws RemoteException {
            return mForceWorldReadable;
        }
    };

    private class CallbackList extends RemoteCallbackList<IDataConsumerHostCallback> {
        public void update(IDataConsumerHostCallback cb, CallbackData data) {
            final IBinder binder = cb.asBinder();
            if (data.mExtensions == null) {
                if (mRegisteredCallbacks.containsKey(binder)) {
                    unregister(cb);
                    mRegisteredCallbacks.remove(binder);
                }
            } else {
                boolean isNewCallback = false;
                if (!mRegisteredCallbacks.containsKey(binder)) {
                    isNewCallback = true;
                    register(cb);
                }

                // Notify callback of data for extensions that it newly registered
                List<ComponentName> prevExtensions = isNewCallback
                        ? new ArrayList<ComponentName>()
                        : mRegisteredCallbacks.get(binder).mExtensions;
                Map<ComponentName, ExtensionManager.ExtensionWithData> availableData =
                        determineDataForAlreadyActiveExtensions(data.mExtensions, prevExtensions);

                try {
                    for (ComponentName cn : availableData.keySet()) {
                        ExtensionManager.ExtensionWithData e = availableData.get(cn);
                        // Do not leak data if extension expressly denied access
                        // to non-dashclock apps
                        if (e != null && e.latestData != null &&
                                isExtensionReadableByHost(e, data)) {
                            cb.notifyUpdate(e.listing.componentName(), e.latestData);
                        } else {
                            final ExtensionData notData = new ExtensionData();
                            cb.notifyUpdate(cn, notData);
                        }
                    }
                } catch (RemoteException e) {
                    // ignored, cb is dead anyway
                }
                mRegisteredCallbacks.put(binder, data);
            }

            recalculateActiveExtensions();
        }

        @Override
        public void onCallbackDied(IDataConsumerHostCallback cb) {
            super.onCallbackDied(cb);
            mRegisteredCallbacks.remove(cb.asBinder());
            recalculateActiveExtensions();
        }
    }

    private boolean isExtensionReadableByHost(ExtensionManager.ExtensionWithData e, CallbackData data) {
        return mForceWorldReadable
                || e.listing.worldReadable()
                || (!e.listing.worldReadable() && data.mHasDashClockSignature);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onExtensionsChanged(ComponentName sourceExtension) {
        LOGD(TAG, "onExtensionsChanged: source = " + sourceExtension);

        mUpdateHandler.removeCallbacksAndMessages(null);
        mUpdateHandler.sendMessageDelayed(
                mUpdateHandler.obtainMessage(0, sourceExtension),
                UPDATE_COLLAPSE_TIME_MILLIS);

        if (sourceExtension == null) {
            broadcastExtensionListChange(
                    mExtensionManager.getAvailableExtensions());
        } else {
            ExtensionManager.ExtensionWithData data = mExtensionManager.getExtensionWithData(sourceExtension);
            if (data != null && data.latestData != null) {
                broadcastDataChange(sourceExtension, data);
            }
        }
    }

    private void broadcastExtensionListChange(List<ExtensionListing> extensions) {
        int count = mCallbacks.beginBroadcast();
        for (int i = 0; i < count; i++) {
            try {
                mCallbacks.getBroadcastItem(i).notifyAvailableExtensionChanged(
                        extensions, mForceWorldReadable);
            } catch (RemoteException e) {
                // ignored
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void broadcastDataChange(ComponentName source, ExtensionManager.ExtensionWithData ewd) {
        int count = mCallbacks.beginBroadcast();
        for (int i = 0; i < count; i++) {
            try {
                IDataConsumerHostCallback cb = mCallbacks.getBroadcastItem(i);
                CallbackData cbData = mRegisteredCallbacks.get(cb.asBinder());
                List<ComponentName> extension = cbData.mExtensions;
                if (extension != null && extension.contains(source)) {
                    // Do not leak data if extension expressly denied access
                    // to non-dashclock apps
                    if (isExtensionReadableByHost(ewd, cbData)) {
                        cb.notifyUpdate(source, ewd.latestData);
                    }
                }
            } catch (RemoteException e) {
                // ignored
            }
        }
        mCallbacks.finishBroadcast();
    }

    private Map<ComponentName, ExtensionManager.ExtensionWithData> determineDataForAlreadyActiveExtensions(
            List<ComponentName> extensions, List<ComponentName> excludedExtensions) {
        Map<ComponentName, ExtensionManager.ExtensionWithData> result = new HashMap<>();
        HashMap<ComponentName, ExtensionManager.ExtensionWithData> map = new HashMap<>();
        for (ExtensionManager.ExtensionWithData e : mExtensionManager.getActiveExtensionsWithData()) {
            if (e.latestData != null) {
                map.put(e.listing.componentName(), e);
            }
        }
        for (ComponentName extension : extensions) {
            if (excludedExtensions != null && excludedExtensions.contains(extension)) {
                continue;
            }
            result.put(extension, map.get(extension));
        }
        return result;
    }


    private void recalculateActiveExtensions() {
        HashSet<ComponentName> extensions = new HashSet<>();
        for (CallbackData entry : mRegisteredCallbacks.values()) {
            for (ComponentName extension : entry.mExtensions) {
                if (extension != null) {
                    extensions.add(extension);
                }
            }
        }
        LOGD(TAG, "recalculateActiveExtensions: determined list = " + extensions);
        mExtensionManager.setActiveExtensions(extensions);
    }

    private ExtensionListing findExtensionInfo(ComponentName extension) {
        for (ExtensionListing info : mExtensionManager.getAvailableExtensions()) {
            if (extension.equals(info.componentName())) {
                return info;
            }
        }
        return null;
    }

    private void enforceRegisteredCallingCallback(IDataConsumerHostCallback cb) {
        if (cb == null || !mRegisteredCallbacks.containsKey(cb.asBinder())) {
            throw new SecurityException("Caller should provide a registered callback.");
        }
    }

    private void enforceEnabledExtensionForCallback(IDataConsumerHostCallback cb,
                                                    ComponentName extension) {
        enforceRegisteredCallingCallback(cb);
        List<ComponentName> extensions = mRegisteredCallbacks.get(cb.asBinder()).mExtensions;
        for (ComponentName ext : extensions) {
            if (ext.equals(extension)) {
                return;
            }
        }
        throw new SecurityException("Extension is not enabled for caller.");
    }

    private void enforceCallingPermission(String permission) throws SecurityException {
        // We need to check that any of the packages of the caller has
        // the request permission
        final PackageManager pm = getPackageManager();
        try {
            String[] packages = pm.getPackagesForUid(Binder.getCallingUid());
            if (packages != null) {
                for (String pkg : packages) {
                    PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                    if (pi.requestedPermissions != null) {
                        for (String requestedPermission : pi.requestedPermissions) {
                            if (requestedPermission.equals(permission)) {
                                // The caller has the request permission
                                return;
                            }
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException ex) {
            // Ignore. Package wasn't found
        }
        throw new SecurityException("Caller doesn't have the request permission \""
                + permission + "\"");
    }

    private CallbackData createCallbackData(int uid) {
        boolean hasDashClockSignature = false;
        String packageName = null;
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null && packages.length > 0) {
            try {
                PackageInfo pi = pm.getPackageInfo(packages[0],
                        PackageManager.GET_SIGNATURES);
                packageName = pi.packageName;
                if (pi.signatures != null
                        && pi.signatures.length == 1
                        && DashClockSignature.SIGNATURE.equals(pi.signatures[0])) {
                    hasDashClockSignature = true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }


        CallbackData data = new CallbackData();
        data.mUid = uid;
        data.mPackage = packageName;
        data.mHasDashClockSignature = hasDashClockSignature;
        return data;
    }

    private void internalRequestUpdateData(final IDataConsumerHostCallback cb,
                                           List<ComponentName> extensions) {
        // Recover the updatable extensions for this caller
        List<ComponentName> updatableExtensions = new ArrayList<>();
        List<ComponentName> registeredExtensions =
                mRegisteredCallbacks.get(cb.asBinder()).mExtensions;
        if (extensions == null) {
            updatableExtensions.addAll(registeredExtensions);
        } else {
            for (ComponentName extension : extensions) {
                if (registeredExtensions.contains(extension)) {
                    updatableExtensions.add(extension);
                }
            }
        }

        // Request an update of all the extensions in the list
        final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        for (ComponentName updatableExtension: updatableExtensions) {
            Intent intent = new Intent(ACTION_EXTENSION_UPDATE_REQUESTED);
            intent.putExtra(EXTRA_COMPONENT_NAME, updatableExtension.flattenToString());
            intent.putExtra(EXTRA_UPDATE_REASON, DashClockExtension.UPDATE_REASON_MANUAL);
            lbm.sendBroadcast(intent);
        }
    }
    private final BroadcastReceiver mExtensionEventsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_EXTENSION_UPDATE_REQUESTED.equals(intent.getAction())) {
                handleUpdateExtensions(intent);
            }
        }
    };
}
