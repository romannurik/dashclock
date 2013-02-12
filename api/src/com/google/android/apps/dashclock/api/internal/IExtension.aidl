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

package com.google.android.apps.dashclock.api.internal;

import com.google.android.apps.dashclock.api.internal.IExtensionHost;
import com.google.android.apps.dashclock.api.ExtensionData;
import android.content.Intent;

interface IExtension {
    /**
     * Since there might be a case where new versions of DashClock use extensions running
     * old versions of the protocol (and thus old versions of this AIDL), there are a few things
     * to keep in mind when editing this class:
     *
     * - Order of functions defined below matters. New methods added in new protocol versions must
     *   be added below all other methods.
     * - Do NOT modify a signature once a protocol version is finalized.
     */
    // Protocol version 1 below
    oneway void onInitialize(in IExtensionHost host, boolean isReconnect);
    oneway void onUpdate(int reason);
    // Protocol version 2 below
}
