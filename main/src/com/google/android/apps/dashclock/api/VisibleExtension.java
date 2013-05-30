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
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public class VisibleExtension implements Parcelable {
    // TODO: if this ever becomes a public API, make sure to add parcel versioning.
    private ComponentName mComponentName;
    private ExtensionData mData;

    public VisibleExtension() {
    }

    public ComponentName componentName() {
        return this.mComponentName;
    }

    public VisibleExtension componentName(final ComponentName mComponentName) {
        this.mComponentName = mComponentName;
        return this;
    }

    public ExtensionData data() {
        return this.mData;
    }

    public VisibleExtension data(final ExtensionData mData) {
        this.mData = mData;
        return this;
    }


    public static final Creator<VisibleExtension> CREATOR
            = new Creator<VisibleExtension>() {
        public VisibleExtension createFromParcel(Parcel in) {
            return new VisibleExtension(in);
        }

        public VisibleExtension[] newArray(int size) {
            return new VisibleExtension[size];
        }
    };

    private VisibleExtension(Parcel in) {
        // Read extension data
        boolean dataPresent = in.readInt() == 1;
        this.mData = (ExtensionData) (dataPresent
                ? in.readParcelable(ExtensionData.class.getClassLoader())
                : null);

        // Read component name
        String componentName = in.readString();
        if (!TextUtils.isEmpty(componentName)) {
            mComponentName = ComponentName.unflattenFromString(componentName);
        } else {
            mComponentName = null;
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt((mData != null) ? 1 : 0);
        if (mData != null) {
            parcel.writeParcelable(mData, 0);
        }
        parcel.writeString((mComponentName == null) ? "" : mComponentName.flattenToString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        try {
            VisibleExtension other = (VisibleExtension) o;
            return objectEquals(other.mComponentName, mComponentName)
                    && objectEquals(other.mData, mData);

        } catch (ClassCastException e) {
            return false;
        }
    }

    private static boolean objectEquals(Object x, Object y) {
        if (x == null || y == null) {
            return x == y;
        } else {
            return x.equals(y);
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }
}
