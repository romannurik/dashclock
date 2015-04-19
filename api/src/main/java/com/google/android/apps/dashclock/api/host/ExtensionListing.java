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

package com.google.android.apps.dashclock.api.host;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A parcelable, serializable object containing information about an
 * {@link com.google.android.apps.dashclock.api.DashClockExtension} shared with your
 * registered {@link DashClockHost}.
 *
 * <p>
 * This class follows the <a href="http://en.wikipedia.org/wiki/Fluent_interface">fluent
 * interface</a> style, using method chaining to provide for more readable code. For example, to set
 * the status and visibility of this data, use {@link #title(String)} and {@link #worldReadable(boolean)}
 * methods like so:
 *
 * <pre class="prettyprint">
 * ExtensionInfo data = new Extension()
 *     .title("GMail Extension")
 *     .description("Extension for reading count unread GMail emails");
 * </pre>
 *
 * @see DashClockHost
 */
public class ExtensionListing implements Parcelable {
    /**
     * Since there might be a case where new versions of DashClock use extensions running
     * old versions of the protocol (and thus old versions of this class), we need a versioning
     * system for the parcels sent between the core app and its extensions.
     */
    public static final int PARCELABLE_VERSION = 1;

    private ComponentName mComponentName;
    private int mProtocolVersion;
    private boolean mCompatible;
    private boolean mWorldReadable;
    private String mTitle;
    private String mDescription;
    private int mIcon;
    private ComponentName mSettingsActivity;

    public ExtensionListing() {
    }

    /**
     * Returns the full qualified component name of the extension.
     */
    public ComponentName componentName() {
        return mComponentName;
    }

    /**
     * Sets the full qualified component name of the extension.
     */
    public ExtensionListing componentName(ComponentName componentName) {
        mComponentName = componentName;
        return this;
    }

    /**
     * Returns the version of the {@link com.google.android.apps.dashclock.api.DashClockExtension}
     * protocol used by the extension.
     */
    public int protocolVersion() {
        return mProtocolVersion;
    }

    /**
     * Sets the version of the {@link com.google.android.apps.dashclock.api.DashClockExtension}
     * protocol used by the extension.
     */
    public ExtensionListing protocolVersion(int protocolVersion) {
        mProtocolVersion = protocolVersion;
        return this;
    }

    /**
     * Returns whether this extension is compatible to the host application; that is whether
     * the version of the {@link com.google.android.apps.dashclock.api.DashClockExtension}
     * protocol used by the extension matches what is used by the host application.
     */
    public boolean compatible() {
        return mCompatible;
    }

    /**
     * Sets whether this extension is considered compatible to the host application.
     */
    public ExtensionListing compatible(boolean compatible) {
        mCompatible = compatible;
        return this;
    }

    /**
     * Returns if the data of the ExtensionInfo is available to all hosts or only for the
     * DashClock app.
     */
    public boolean worldReadable() {
        return mWorldReadable;
    }

    /**
     * Sets if the data of the ExtensionInfo is available to all hosts or only for the
     * DashClock app.
     */
    public ExtensionListing worldReadable(boolean worldReadable) {
        mWorldReadable = worldReadable;
        return this;
    }

    /**
     * Returns the label of the extension.
     */
    public String title() {
        return mTitle;
    }

    /**
     * Sets the label of the extension.
     */
    public ExtensionListing title(String title) {
        mTitle = title;
        return this;
    }

    /**
     * Returns a description of the extension.
     */
    public String description() {
        return mDescription;
    }

    /**
     * Sets a description of the extension.
     */
    public ExtensionListing description(String description) {
        mDescription = description;
        return this;
    }

    /**
     * Returns the ID of the drawable resource within the extension's package that represents this
     * data. Default 0.
     */
    public int icon() {
        return mIcon;
    }

    /**
     * Sets the ID of the drawable resource within the extension's package that represents this
     * data. Default 0.
     */
    public ExtensionListing icon(int icon) {
        mIcon = icon;
        return this;
    }

    /**
     * Returns the full qualified component name of the settings class to configure
     * the extension.
     */
    public ComponentName settingsActivity() {
        return mSettingsActivity;
    }

    /**
     * Sets the full qualified component name of the settings class to configure
     * the extension.
     */
    public ExtensionListing settingsActivity(ComponentName settingsActivity) {
        this.mSettingsActivity = settingsActivity;
        return this;
    }

    /**
     * @see android.os.Parcelable
     */
    public static final Creator<ExtensionListing> CREATOR
            = new Creator<ExtensionListing>() {
        public ExtensionListing createFromParcel(Parcel in) {
            return new ExtensionListing(in);
        }

        public ExtensionListing[] newArray(int size) {
            return new ExtensionListing[size];
        }
    };

    private ExtensionListing(Parcel in) {
        int parcelableVersion = in.readInt();

        // Version 1 below
        if (parcelableVersion >= 1) {
            mComponentName = ComponentName.readFromParcel(in);
            mProtocolVersion = in.readInt();
            mCompatible = in.readInt() == 1;
            mWorldReadable = in.readInt() == 1;
            mTitle = in.readString();
            mDescription = in.readString();
            mIcon = in.readInt();
            boolean hasSettings = in.readInt() == 1;
            if (hasSettings) {
                mSettingsActivity = ComponentName.readFromParcel(in);
            }
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        /**
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * {@link #PARCELABLE_VERSION}.
         */
        parcel.writeInt(PARCELABLE_VERSION);

        // Version 1 below
        mComponentName.writeToParcel(parcel, 0);
        parcel.writeInt(mProtocolVersion);
        parcel.writeInt(mCompatible ? 1 : 0);
        parcel.writeInt(mWorldReadable ? 1 : 0);
        parcel.writeString(mTitle);
        parcel.writeString(mDescription);
        parcel.writeInt(mIcon);
        parcel.writeInt(mSettingsActivity != null ? 1 : 0);
        if (mSettingsActivity != null) {
            mSettingsActivity.writeToParcel(parcel, 0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExtensionListing)) return false;

        ExtensionListing that = (ExtensionListing) o;

        if (mIcon != that.mIcon) return false;
        if (mProtocolVersion != that.mProtocolVersion) return false;
        if (mCompatible != that.mCompatible) return false;
        if (mWorldReadable != that.mWorldReadable) return false;
        if (mComponentName != null ? !mComponentName.equals(that.mComponentName) : that.mComponentName != null)
            return false;
        if (mDescription != null ? !mDescription.equals(that.mDescription) : that.mDescription != null)
            return false;
        if (mSettingsActivity != null ? !mSettingsActivity.equals(that.mSettingsActivity) : that.mSettingsActivity != null)
            return false;
        if (mTitle != null ? !mTitle.equals(that.mTitle) : that.mTitle != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mComponentName != null ? mComponentName.hashCode() : 0;
        result = 31 * result + mProtocolVersion;
        result = 31 * result + (mCompatible ? 1 : 0);
        result = 31 * result + (mWorldReadable ? 1 : 0);
        result = 31 * result + (mTitle != null ? mTitle.hashCode() : 0);
        result = 31 * result + (mDescription != null ? mDescription.hashCode() : 0);
        result = 31 * result + mIcon;
        result = 31 * result + (mSettingsActivity != null ? mSettingsActivity.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExtensionListing[component=" + mComponentName
                + ", compatible=" + mCompatible
                + ", worldReadable=" + mWorldReadable
                + ", title=" + mTitle
                + "]";
    }
}
