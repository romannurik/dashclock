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

import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.host.ExtensionListing;

/**
 * The callback used by the {@link IDataConsumerHost} to be notified
 * of updates and changes to extensions
 */
interface IDataConsumerHostCallback {
    /**
     * Invoked when an extension has new data to notify the host.
     *
     * @param data the latest update data of a extension
     */
    oneway void notifyUpdate(in ComponentName source, in ExtensionData data);

    /**
     * Invoked when available extensions changed (added, modified or removed).
     *
     * @param extensions the list of the current available {@link ExtensionListing} classes.
     */
    oneway void notifyAvailableExtensionChanged(in List<ExtensionListing> extensions, in boolean nonWorldReadableExtensionsVisible);
}
