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

package com.google.android.apps.dashclock.api.host;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.DashClockSignature;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.internal.IDataConsumerHost;
import com.google.android.apps.dashclock.api.internal.IDataConsumerHostCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.content.pm.PackageManager.NameNotFoundException;

/**
 * Base class to be used by data consumer host implementations in order to get
 * updates for installed extensions and their data.<br/>
 * <p>
 * Subclasses MUST call the {@link #listenTo(java.util.Set)} method to start listening
 * for extension updates, and MUST call {@link #destroy()} when the instance is not
 * used anymore.
 * <p>
 * Subclasses should implement {@link #onExtensionDataChanged(android.content.ComponentName)}
 * in order to receive updates for the registered extensions passed to
 * {@link #listenTo(java.util.Set)}.<br/>
 * <p>
 * Subclasses should implement {@link #onAvailableExtensionsChanged()} to get notifications
 * of additions or removals of DashClock extensions installed on the device.<br/>
 * <p>
 * Subclasses should override {@link #onMultiplexerChangedDetected(boolean)}} to get notifications
 * about changes in multiplexer service app availability.<br/>
 * Subclasses are responsible for redirecting the user to the Play Store to download the official
 * DashClock app. Subclasses should use {@link #getMultiplexerDownloadIntent()} to to get an
 * intent to download the official app. An example UI can be found in the {@code example-host}
 * project.
 * <p>
 * Lastly, there are a few <code>&lt;meta-data&gt;</code> elements that
 * you should add to your service definition:
 * <ul>
 * <li><code>protocolVersion</code> (required): should be <strong>1</strong>.</li>
 * </ul>
 */
public abstract class DashClockHost {
    /**
     * The required permission to bind to the {@link IDataConsumerHost} service
     */
    public static final String BIND_DATA_CONSUMER_PERMISSION =
            "com.google.android.apps.dashclock.permission.BIND_DATA_CONSUMER";

    // The list of well-known multiplexer hosts
    private static final ComponentName MULTIPLEXER_HOST_SERVICE =
            new ComponentName("net.nurik.roman.dashclock",
                    "com.google.android.apps.dashclock.DashClockService");

    private static final String ACTION_ASK_ENABLE_FORCE_WORLD_READABLE
            = "com.google.android.apps.dashclock.action.ASK_ENABLE_FORCE_WORLD_READABLE";

    /*
     * PUBLIC API
     */

    /**
     * Exception thrown if the host multiplexer service is not available.
     */
    public static class NoMultiplexerAvailableException extends RuntimeException {
        private NoMultiplexerAvailableException(String message) {
            super(message);
        }
    }

    /**
     * Returns whether a multiplexer host service app is present in the system.
     */
    public static boolean isMultiplexerServicePresent(Context context) {
        return getMultiplexerService(context) != null;
    }

    /**
     * Return a list of packages that implement the {@link
     * DashClockExtension#PERMISSION_READ_EXTENSION_DATA} permission and aren't DashClock
     */
    public static List<String> getOtherAppsWithReadDataExtensionsPermission(Context context) {
        List<String> installedApps = new ArrayList<>();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // There is no problem with the PERMISSION_READ_EXTENSION_DATA if
            // the api supports multiple apps defining the same permission (< Lollipop)
            return installedApps;
        }
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : apps) {
            try {
                PackageInfo pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS);
                PermissionInfo[] perms =  pi.permissions;
                if (perms != null) {
                    for (PermissionInfo perm : perms) {
                        if (perm.name.equals(DashClockExtension.PERMISSION_READ_EXTENSION_DATA)) {
                            if (!ai.packageName.equals(MULTIPLEXER_HOST_SERVICE.getPackageName())) {
                                installedApps.add(ai.packageName);
                                break;
                            }
                        }
                    }
                }

            } catch (NameNotFoundException ex) {
                // Ignore
            }
        }
        return installedApps;
    }

    protected DashClockHost(Context context) throws SecurityException {
        mContext = context;
        mHandler = new Handler(mHandlerCallback);
        mDataCache = new HashMap<>();
        mAvailableExtensions = new ArrayList<>();
        try {
            if (!connect()) {
                mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY);
            }
        } catch (NoMultiplexerAvailableException | SecurityException ex) {
            // Notify the implementation that the multiplexer isn't available
            mHandler.obtainMessage(MSG_NOTIFY_MUX_NOT_AVAILABLE).sendToTarget();
        }
    }

    /**
     * Destroy this host instance, freeing its resources. After calling
     * this, the host can no longer receive extension updates.
     * This should be used in the onDestroy() method of the Activity or
     * Service holding this host.
     */
    public void destroy() {
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mService != null) {
            try {
                mService.listenTo(null, mCallback);
                mContext.unbindService(mConnection);
            } catch (RemoteException e) {
                // ignored
            }
        }
    }

    /**
     * Returns whether the user has expressly allowed non-world-readable
     * extensions to be visible to all apps.
     *
     * @see #getEnableForceWorldReadabilityIntent()
     */
    public boolean areNonWorldReadableExtensionsVisible() {
        return mNonWorldReadableExtensionsVisible;
    }

    /**
     * Returns an activity intent that can be called to ask the user to force
     * world-readability enabled (that is, to set {@link #areNonWorldReadableExtensionsVisible()}
     * to true.
     *
     * @see #areNonWorldReadableExtensionsVisible()
     */
    public Intent getEnableForceWorldReadabilityIntent() {
        return new Intent(ACTION_ASK_ENABLE_FORCE_WORLD_READABLE);
    }

    /**
     * Get the list of known extensions.
     *
     * @return List of currently known extensions
     *
     * @see #onAvailableExtensionsChanged
     */
    public List<ExtensionListing> getAvailableExtensions(boolean onlyWorldReadableExtensions) {
        if (!onlyWorldReadableExtensions) {
            return new ArrayList<>(mAvailableExtensions);
        }
        List<ExtensionListing> eis = new ArrayList<>(mAvailableExtensions);
        int count = eis.size() - 1;
        for (int i = count; i >= 0; i--) {
            ExtensionListing ei = eis.get(i);
            if (!ei.worldReadable()) {
                eis.remove(i);
            }
        }
        return eis;
    }

    /**
     * Get the extension data for a given extension.
     *
     * @param extension  Extension to get data for
     * @return  Known extension data or null
     *
     * @see #onExtensionDataChanged
     */
    public ExtensionData getExtensionData(ComponentName extension) {
        synchronized (mDataCache) {
            return mDataCache.get(extension);
        }
    }

    /**
     * Update list of extensions to get data updates for.
     *
     * @param extensions  List of extensions to monitor.
     * @see #onExtensionDataChanged
     */
    public void listenTo(Set<ComponentName> extensions) {
        mListenedExtensions = extensions;
        List<ComponentName> extensionList =
                extensions == null ? null : new ArrayList<>(extensions);
        try {
            if (mService != null) {
                mService.listenTo(extensionList, mCallback);
            }
        } catch (RemoteException e) {
            // Ignore
        }
    }

    /**
     * Start the settings activity of a DashClock extension.
     *
     * If the extension doesn't define a settings activity, this is a no-op.
     *
     * @param extension The extension to show the settings for.
     * @return Whether the operation was successful.
     */
    public boolean startSettingsActivityForExtension(ExtensionListing extension) {
        if (mService != null && extension.settingsActivity() != null) {
            try {
                mService.showExtensionSettings(extension.componentName(), mCallback);
                return true;
            } catch (RemoteException e) {
                // Ignore
            }
        }
        return false;
    }

    /**
     * Request a manual update of extensions.
     *
     * @param extensions The list of extensions to update, or {@code null} to update all the
     *                   active extensions for this host.
     * @return Whether the operation was successful.
     */
    public boolean requestExtensionUpdate(List<ComponentName> extensions) {
        try {
            if (mService != null) {
                mService.requestExtensionUpdate(extensions, mCallback);
            }
            return true;
        } catch (RemoteException ex) {
            // Ignore
        }
        return false;
    }

    /**
     * Called when the list of available extensions is updated.
     *
     * @see #getAvailableExtensions
     */
    protected void onAvailableExtensionsChanged() {

    }

    /**
     * Called when extension data for an extension is updated.
     *
     * @param extension  Extension that was updated
     *
     * @see #getExtensionData
     */
    protected void onExtensionDataChanged(ComponentName extension) {

    }

    /**
     * Called when a multiplexer service package change was detected. That means
     * a multiplexer service package was installed or removed.<br/>
     * <br/>
     * The default implementation ignores these events, so implementers of this
     * class are encouraged to override this method and handle this event
     * in a proper way.<br/>
     * <br/>
     * When there isn't an installed package providing a Multiplexer Service implementation,
     * implementations <b>MUST</b> redirect the user to the official app in the Play Store.<br/>
     * Implementations can obtain an {@link Intent} calling {@link #getMultiplexerDownloadIntent()}.
     * <br/>
     * An example UI to redirect the user can be found in the {@code example-host} project.
     *
     * @param available  Indicates whether a multiplexer service is present.
     *
     * @see #isMultiplexerServicePresent(android.content.Context)
     * @see #getMultiplexerDownloadIntent()
     */
    protected void onMultiplexerChangedDetected(boolean available) {

    }

    /**
     * Return an {@link android.content.Intent} reference to redirect the user
     * to the Play Store to download the official DashClock app.<br/>
     * Implementers should call to {@link Context#startActivity(android.content.Intent)}.
     *
     * @return The download {@link android.content.Intent} or {@code null} if the the multiplexer
     * cannot be installed because another app that defines the {@link
     * DashClockExtension#PERMISSION_READ_EXTENSION_DATA} is already installed.
     *
     * @see #getOtherAppsWithReadDataExtensionsPermission(android.content.Context)
     */
    public final Intent getMultiplexerDownloadIntent() {
        // First we need to check if there are other apps that declare the READ_EXTENSION_DATA
        // permission. In that case, the update for DashClock or the installation of the mux
        // will not work. Users MUST uninstall the app
        List<String> apps = getOtherAppsWithReadDataExtensionsPermission(mContext);
        if (!apps.isEmpty()) {
            return null;
        }

        final String pkgName = MULTIPLEXER_HOST_SERVICE.getPackageName();
        Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + pkgName);
        return new Intent("android.intent.action.VIEW", uri);
    }

    /*
     * INTERNAL IMPLEMENTATION
     */

    private Context mContext;
    private IDataConsumerHost mService;
    private List<ExtensionListing> mAvailableExtensions;
    private boolean mNonWorldReadableExtensionsVisible;
    private final Map<ComponentName, ExtensionData> mDataCache;
    private Set<ComponentName> mListenedExtensions;
    private boolean mDestroyed;

    // We assume that multiplexer is initially present. This makes sure
    // onMultiplexerChangedDetected is called if the multiplexer isn't present initially
    private boolean mIsMultiplexerPresent = true;

    private final Handler mHandler;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IDataConsumerHost.Stub.asInterface(service);
            try {
                if (mListenedExtensions != null) {
                    listenTo(mListenedExtensions);
                }
                Message msg = mHandler.obtainMessage(MSG_NOTIFY_EXTENSION_LIST_CHANGE,
                        mService.areNonWorldReadableExtensionsVisible() ? 0 : 1,
                        0,
                        mService.getAvailableExtensions());
                msg.sendToTarget();
            } catch (RemoteException ex) {
                mService = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mNonWorldReadableExtensionsVisible = false;
            mAvailableExtensions.clear();
            mService = null;
            if (!mDestroyed) {
                mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY);
            }
        }
    };

    public void handleMultiplexerPackageChanged() {
        boolean isMultiplexerPresent = isMultiplexerServicePresent(mContext);
        if (mIsMultiplexerPresent != isMultiplexerPresent) {
            if (isMultiplexerPresent && mService == null) {
                // Reconnect the service
                Message msg = mHandler.obtainMessage(MSG_RECONNECT);
                msg.sendToTarget();
            } else if (!isMultiplexerPresent && mService != null) {
                mContext.unbindService(mConnection);
            }

            onMultiplexerChangedDetected(isMultiplexerPresent);
            mIsMultiplexerPresent = isMultiplexerPresent;
        }
    }

    private final IDataConsumerHostCallback.Stub mCallback = new IDataConsumerHostCallback.Stub() {
        @Override
        public void notifyUpdate(ComponentName source, ExtensionData data) {
            synchronized (mDataCache) {
                mDataCache.put(source, data);
            }
            mHandler.obtainMessage(MSG_NOTIFY_DATA_CHANGE, source).sendToTarget();
        }

        @Override
        public void notifyAvailableExtensionChanged(List<ExtensionListing> extensions,
                                                    boolean nonWorldReadableExtensionsVisible) {
            mHandler.obtainMessage(MSG_NOTIFY_EXTENSION_LIST_CHANGE,
                    nonWorldReadableExtensionsVisible ? 0 : 1,
                    0,
                    extensions).sendToTarget();
        }
    };

    private static final int AUTO_RECONNECT_DELAY = 5000;

    private static final int MSG_NOTIFY_EXTENSION_LIST_CHANGE = 1;
    private static final int MSG_NOTIFY_DATA_CHANGE = 2;
    private static final int MSG_RECONNECT = 3;
    private static final int MSG_NOTIFY_MUX_NOT_AVAILABLE = 4;

    private final Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        @SuppressWarnings("unchecked")
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_NOTIFY_EXTENSION_LIST_CHANGE:
                    mAvailableExtensions.clear();
                    mAvailableExtensions.addAll((List<ExtensionListing>) msg.obj);
                    mNonWorldReadableExtensionsVisible = msg.arg1 == 0;
                    onAvailableExtensionsChanged();
                    return true;
                case MSG_NOTIFY_DATA_CHANGE:
                    onExtensionDataChanged((ComponentName) msg.obj);
                    return true;
                case MSG_RECONNECT:
                    try {
                        if (!connect()) {
                            mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY);
                        }
                    } catch (NoMultiplexerAvailableException | SecurityException e) {
                        // Reconnect will not work, so stop here
                    }
                    return true;
                case MSG_NOTIFY_MUX_NOT_AVAILABLE:
                    onMultiplexerChangedDetected(false);
                    mIsMultiplexerPresent = false;
                    return true;
            }
            return false;
        }
    };

    /**
     * Connects to the {@link IDataConsumerHost} multiplexer service.
     *
     * @return If connection to the multiplexer service was successful.
     */
    private boolean connect() throws SecurityException, NoMultiplexerAvailableException {
        ComponentName cn = getMultiplexerService(mContext);
        if (cn == null) {
            throw new NoMultiplexerAvailableException("Multiplexer service not installed");
        }

        // The multiplexer host service checks this, but we want to receive
        // this prior to binding to the service.
        enforcePermission(mContext, BIND_DATA_CONSUMER_PERMISSION);

        // Instantiate the multiplexer service
        Intent intent = new Intent();
        intent.setComponent(cn);
        return mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Returns the name of a MultiplexerHostService present in the system or {@code null}
     * if there isn't service available.
     */
    private static ComponentName getMultiplexerService(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean debuggable =
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        try {
            PackageInfo pi = pm.getPackageInfo(MULTIPLEXER_HOST_SERVICE.getPackageName(),
                    PackageManager.GET_SIGNATURES | PackageManager.GET_SERVICES);
            if (pi.applicationInfo.enabled) {
                for (ServiceInfo si : pi.services) {
                    if (MULTIPLEXER_HOST_SERVICE.getClassName().equals(si.name) && si.enabled) {
                        if (debuggable) {
                            return MULTIPLEXER_HOST_SERVICE;
                        }

                        if (pi.signatures != null
                                && pi.signatures.length == 1
                                && DashClockSignature.SIGNATURE.equals(pi.signatures[0])) {
                            return MULTIPLEXER_HOST_SERVICE;
                        }
                    }
                }
            }
        } catch (NameNotFoundException e) {
            // ignored
        }
        return null;
    }

    private static void enforcePermission(Context context, String permission)
            throws SecurityException {
        // Check whether any of the caller's packages requests the expected permission
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            int count = pi.requestedPermissions != null ? pi.requestedPermissions.length : 0;
            for (int i = 0; i < count; i++) {
                if (pi.requestedPermissions[i].equals(permission)) {
                    // The caller has requested the permission
                    return;
                }
            }
        } catch (NameNotFoundException e) {
            // Ignore. Package wasn't found
        }
        throw new SecurityException("Caller didn't request the permission \"" + permission + "\"");
    }

    public static boolean isDashClockPresent(Context context) {
        try {
            // Check whether the DashClock multiplexer service is installed or not:
            context.getPackageManager().getPackageInfo(
                    MULTIPLEXER_HOST_SERVICE.getPackageName(), 0);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}
