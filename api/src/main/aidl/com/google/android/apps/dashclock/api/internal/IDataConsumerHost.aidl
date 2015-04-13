/*
 * Copyright 2014 Google Inc.
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

import android.content.ComponentName;
import com.google.android.apps.dashclock.api.host.ExtensionListing;
import com.google.android.apps.dashclock.api.internal.IDataConsumerHostCallback;

interface IDataConsumerHost {
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

    /**
     * Notify the multiplexer service about which {@link IExtension} instances we want to get
     * data updates for.
     *
     * @param extension  New list of extensions to get updates for. The callback is unregistered
     *                   if it is null or empty.
     * @param cb  Callback to invoke on extension data changes or changes to the
     *           list of available extensions.
     */
    oneway void listenTo(in List<ComponentName> extensions, in IDataConsumerHostCallback cb);

    /**
     * Opens the associated settings of the passed in extension.
     * The extension must be listened to before by passing it into {@code #listenTo()}.
     *
     * @param extension The extension to open the settings for.
     * @param cb A registered callback passed to {@code #listenTo()}.
     */
    oneway void showExtensionSettings(in ComponentName extension, in IDataConsumerHostCallback cb);

    /**
     * Request an update of all the passed in extensions.
     * Only extensions registered by the caller will be updated.
     *
     * @param extensions The extensions to request an update for.
     * @param cb A registered callback passed to {@code #listenTo()}.
     */
    oneway void requestExtensionUpdate(in List<ComponentName> extensions,
            in IDataConsumerHostCallback cb);

    /**
     * Get a list of available installed extensions.
     */
    List<ExtensionListing> getAvailableExtensions();

    /**
     * Returns whether the user has expressly allowed non-world-readable
     * extensions to be visible to all apps.
     */
    boolean areNonWorldReadableExtensionsVisible();

   // Protocol version 2 below
}
