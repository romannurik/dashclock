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
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.internal.IExtension;
import com.google.android.apps.dashclock.api.internal.IExtensionHost;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * The primary local-process endpoint that deals with extensions. Instances of this class are in
 * charge of maintaining a {@link ServiceConnection} with connected extensions. There should
 * only be one instance of this class in the app.
 * <p>
 * This class is intended to be used as part of a containing service. Make sure to call
 * {@link #destroy()} in the service's {@link android.app.Service#onDestroy()}.
 */
public class ExtensionHost {
    // TODO: this class badly needs inline docs
    private static final String TAG = LogUtils.makeLogTag(ExtensionHost.class);

    private static final int CURRENT_EXTENSION_PROTOCOL_VERSION = 1;

    private Context mContext;
    private Handler mClientThreadHandler = new Handler();

    private ExtensionManager mExtensionManager;

    private Map<ComponentName, Connection> mExtensionConnections
            = new HashMap<ComponentName, Connection>();

    private final Set<ComponentName> mExtensionsToUpdateWhenScreenOn = new HashSet<ComponentName>();
    private boolean mScreenOnReceiverRegistered = false;

    private volatile Looper mAsyncLooper;
    private volatile Handler mAsyncHandler;

    public ExtensionHost(Service context) {
        mContext = context;
        mExtensionManager = ExtensionManager.getInstance(context);
        mExtensionManager.addOnChangeListener(mChangeListener);

        HandlerThread thread = new HandlerThread("ExtensionHost");
        thread.start();
        mAsyncLooper = thread.getLooper();
        mAsyncHandler = new Handler(mAsyncLooper);

        mChangeListener.onExtensionsChanged();
        mExtensionManager.cleanupExtensions();
    }

    public void destroy() {
        mExtensionManager.removeOnChangeListener(mChangeListener);
        if (mScreenOnReceiverRegistered) {
            mContext.unregisterReceiver(mScreenOnReceiver);
            mScreenOnReceiverRegistered = false;
        }
        establishAndDestroyConnections(new ArrayList<ComponentName>());
        mAsyncLooper.quit();
    }

    private void establishAndDestroyConnections(List<ComponentName> newExtensionNames) {
        // Get the list of active extensions
        Set<ComponentName> activeSet = new HashSet<ComponentName>();
        activeSet.addAll(newExtensionNames);

        // Get the list of connected extensions
        Set<ComponentName> connectedSet = new HashSet<ComponentName>();
        connectedSet.addAll(mExtensionConnections.keySet());

        for (final ComponentName cn : activeSet) {
            if (connectedSet.contains(cn)) {
                continue;
            }

            // Bind anything not currently connected (this is the initial connection
            // to the now-added extension)
            Connection conn = createConnection(cn, false);
            if (conn != null) {
                mExtensionConnections.put(cn, conn);
            }
        }

        // Remove active items from the connected set, leaving only newly-inactive items
        // to be disconnected below.
        connectedSet.removeAll(activeSet);

        for (ComponentName cn : connectedSet) {
            Connection conn = mExtensionConnections.get(cn);

            // Unbind the now-disconnected extension
            destroyConnection(conn);
            mExtensionConnections.remove(cn);
        }
    }

    private Connection createConnection(final ComponentName cn, final boolean isReconnect) {
        final Connection conn = new Connection();
        conn.componentName = cn;
        conn.contentObserver = new ContentObserver(mClientThreadHandler) {
            @Override
            public void onChange(boolean selfChange) {
                execute(conn.componentName,
                        UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_CONTENT_CHANGED));
            }
        };
        conn.hostInterface = makeHostInterface(conn);
        conn.serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                conn.ready = true;
                conn.binder = IExtension.Stub.asInterface(iBinder);

                // Initialize the service
                execute(conn, new Operation() {
                    @Override
                    public void run(IExtension extension) throws RemoteException {
                        // Note that this is protected from ANRs since it runs in the
                        // AsyncHandler thread. Also, since this is a 'oneway' call,
                        // when used with remote extensions, this call does not block.
                        extension.onInitialize(conn.hostInterface, isReconnect);
                    }
                });

                if (!isReconnect) {
                    execute(conn.componentName,
                            UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_INITIAL));
                }

                // Execute operations that were deferred until the service was available.
                // TODO: handle service disruptions that occur here
                synchronized (conn.deferredOps) {
                    Iterator<Operation> it = conn.deferredOps.iterator();
                    while (it.hasNext()) {
                        if (conn.ready) {
                            execute(conn, it.next());
                            it.remove();
                        }
                    }
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName componentName) {
                conn.serviceConnection = null;
                conn.binder = null;
                conn.ready = false;
                mClientThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mExtensionConnections.remove(componentName);
                    }
                });
            }
        };

        try {
            if (!mContext.bindService(new Intent().setComponent(cn), conn.serviceConnection,
                    Context.BIND_AUTO_CREATE)) {
                LOGE(TAG, "Error binding to extension " + cn.flattenToShortString());
                return null;
            }
        } catch (SecurityException e) {
            LOGE(TAG, "Error binding to extension " + cn.flattenToShortString(), e);
            return null;
        }

        return conn;
    }

    private IExtensionHost makeHostInterface(final Connection conn) {
        return new IExtensionHost.Stub() {
            @Override
            public void publishUpdate(ExtensionData data) throws RemoteException {
                if (data == null) {
                    data = new ExtensionData();
                }

                // TODO: this needs to be thread-safe
                mExtensionManager.updateExtensionData(conn.componentName, data);
            }

            @Override
            public void addWatchContentUris(String[] contentUris) throws RemoteException {
                if (contentUris != null && contentUris.length > 0) {
                    ContentResolver resolver = mContext.getContentResolver();
                    for (String uri : contentUris) {
                        if (TextUtils.isEmpty(uri)) {
                            continue;
                        }

                        resolver.registerContentObserver(Uri.parse(uri), true,
                                conn.contentObserver);
                    }
                }
            }

            @Override
            public void setUpdateWhenScreenOn(boolean updateWhenScreenOn) throws RemoteException {
                synchronized (mExtensionsToUpdateWhenScreenOn) {
                    if (updateWhenScreenOn) {
                        if (mExtensionsToUpdateWhenScreenOn.size() == 0) {
                            IntentFilter filter = new IntentFilter();
                            filter.addAction(Intent.ACTION_SCREEN_ON);
                            mContext.registerReceiver(mScreenOnReceiver, filter);
                            mScreenOnReceiverRegistered = true;
                        }

                        mExtensionsToUpdateWhenScreenOn.add(conn.componentName);

                    } else {
                        mExtensionsToUpdateWhenScreenOn.remove(conn.componentName);

                        if (mExtensionsToUpdateWhenScreenOn.size() == 0) {
                            mContext.unregisterReceiver(mScreenOnReceiver);
                            mScreenOnReceiverRegistered = false;
                        }
                    }
                }
            }
        };
    }

    private void destroyConnection(Connection conn) {
        if (conn.contentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(conn.contentObserver);
            conn.contentObserver = null;
        }

        conn.binder = null;
        mContext.unbindService(conn.serviceConnection);
        conn.serviceConnection = null;
    }

    private ExtensionManager.OnChangeListener mChangeListener
            = new ExtensionManager.OnChangeListener() {
        @Override
        public void onExtensionsChanged() {
            establishAndDestroyConnections(mExtensionManager.getActiveExtensionNames());
        }
    };

    private void execute(final Connection conn, final Operation operation) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (conn.binder == null) {
                        throw new RemoteException("Binder is unavailable.");
                    }
                    operation.run(conn.binder);
                } catch (RemoteException e) {
                    LOGE(TAG, "Couldn't execute operation; scheduling for retry upon service "
                            + "reconnection.", e);
                    // TODO: exponential backoff for retrying the same operation, or fail after
                    // n attempts (in case the remote service consistently crashes when
                    // executing this operation)
                    synchronized (conn.deferredOps) {
                        conn.deferredOps.add(operation);
                    }
                }
            }
        };

        if (conn.ready) {
            mAsyncHandler.post(runnable);
        } else {
            mAsyncHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (conn.deferredOps) {
                        conn.deferredOps.add(operation);
                    }
                }
            });
        }
    }

    public void execute(ComponentName cn, Operation operation) {
        Connection conn = mExtensionConnections.get(cn);
        if (conn == null) {
            conn = createConnection(cn, true);
            if (conn != null) {
                mExtensionConnections.put(cn, conn);
            } else {
                LOGE(TAG, "Couldn't connect to extension to perform operation; operation "
                        + "canceled.");
                return;
            }
        }

        execute(conn, operation);
    }

    private final BroadcastReceiver mScreenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mExtensionsToUpdateWhenScreenOn) {
                for (ComponentName cn : mExtensionsToUpdateWhenScreenOn) {
                    execute(cn, UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_SCREEN_ON));
                }
            }
        }
    };

    static final SparseArray<Operation> UPDATE_OPERATIONS = new SparseArray<Operation>();

    static {
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_UNKNOWN);
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_INITIAL);
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_PERIODIC);
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED);
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_CONTENT_CHANGED);
        _createUpdateOperation(DashClockExtension.UPDATE_REASON_SCREEN_ON);
    }

    private static void _createUpdateOperation(final int reason) {
        UPDATE_OPERATIONS.put(reason, new ExtensionHost.Operation() {
            @Override
            public void run(IExtension extension) throws RemoteException {
                // Note that this is protected from ANRs since it runs in the AsyncHandler thread.
                // Also, since this is a 'oneway' call, when used with remote extensions, this call
                // does not block.
                extension.onUpdate(reason);
            }
        });
    }

    public static boolean supportsProtocolVersion(int protocolVersion) {
        return protocolVersion > 0 && protocolVersion <= CURRENT_EXTENSION_PROTOCOL_VERSION;
    }

    /**
     * Will be run on a worker thread.
     */
    public static interface Operation {
        void run(IExtension extension) throws RemoteException;
    }

    private static class Connection {
        boolean ready = false;
        ComponentName componentName;
        ServiceConnection serviceConnection;
        IExtension binder;
        IExtensionHost hostInterface;
        ContentObserver contentObserver;

        /**
         * Only access on the async thread.
         */
        final Queue<Operation> deferredOps = new LinkedList<Operation>();
    }
}
