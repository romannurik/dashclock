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

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.net.URISyntaxException;

/**
 * A parcelable, serializable object representing data related to a {@link DashClockExtension} that
 * should be shown to the user.
 *
 * <p>
 * This class follows the <a href="http://en.wikipedia.org/wiki/Fluent_interface">fluent
 * interface</a> style, using method chaining to provide for more readable code. For example, to set
 * the status and visibility of this data, use {@link #status(String)} and {@link #visible(boolean)}
 * methods like so:
 *
 * <pre class="prettyprint">
 * ExtensionData data = new ExtensionData();
 * data.visible(true).status("hello");
 * </pre>
 *
 * Conversely, to get the status, use {@link #status()}. Setters and getters are thus overloads
 * (or overlords?) of the same method.
 *
 * <h3>Required fields</h3>
 *
 * While no fields are required, if the data is 'visible' (i.e. {@link #visible(boolean)} has been
 * called with <code>true</code>, at least the following fields should be populated:
 *
 * <ul>
 * <li>{@link #icon(int)}</li>
 * <li>{@link #status(String)}</li>
 * </ul>
 *
 * Really awesome extensions will also set these fields:
 *
 * <ul>
 * <li>{@link #expandedTitle(String)}</li>
 * <li>{@link #expandedBody(String)}</li>
 * <li>{@link #clickIntent(android.content.Intent)}</li>
 * </ul>
 *
 * @see DashClockExtension#publishUpdate(ExtensionData)
 */
public class ExtensionData implements Parcelable {
    /**
     * Since there might be a case where new versions of DashClock use extensions running
     * old versions of the protocol (and thus old versions of this class), we need a versioning
     * system for the parcels sent between the core app and its extensions.
     */
    public static final int PARCELABLE_VERSION = 1;

    /**
     * The number of fields in this version of the parcelable.
     */
    public static final int PARCELABLE_SIZE = 6;

    private static final String KEY_VISIBLE = "visible";
    private static final String KEY_ICON = "icon";
    private static final String KEY_STATUS = "status";
    private static final String KEY_EXPANDED_TITLE = "title";
    private static final String KEY_EXPANDED_BODY = "body";
    private static final String KEY_CLICK_INTENT = "click_intent";

    /**
     * The maximum length for {@link #status(String)}. Enforced by {@link #clean()}.
     */
    public static final int MAX_STATUS_LENGTH = 32;

    /**
     * The maximum length for {@link #expandedTitle(String)}. Enforced by {@link #clean()}.
     */
    public static final int MAX_EXPANDED_TITLE_LENGTH = 100;

    /**
     * The maximum length for {@link #expandedBody(String)}. Enforced by {@link #clean()}.
     */
    public static final int MAX_EXPANDED_BODY_LENGTH = 1000;

    private boolean mVisible = false;
    private int mIcon = 0;
    private String mStatus = null;
    private String mExpandedTitle = null;
    private String mExpandedBody = null;
    private Intent mClickIntent = null;

    public ExtensionData() {
    }

    /**
     * Returns whether or not the relevant extension should be visible (whether or not there is
     * relevant information to show to the user about the extension). Default false.
     */
    public boolean visible() {
        return mVisible;
    }

    /**
     * Sets whether or not the relevant extension should be visible (whether or not there is
     * relevant information to show to the user about the extension). Default false.
     */
    public ExtensionData visible(boolean visible) {
        mVisible = visible;
        return this;
    }

    /**
     * Returns the ID of the resource within the extension's package that represents this
     * data. Default 0.
     */
    public int icon() {
        return mIcon;
    }

    /**
     * Sets the ID of the resource within the extension's package that represents this
     * data. The icon should be entirely white, with alpha, and about 96x96 pixels. It will be
     * scaled down as needed. If there is no contextual icon representation of the data, simply
     * use the extension or app icon. Default 0.
     */
    public ExtensionData icon(int icon) {
        mIcon = icon;
        return this;
    }

    /**
     * Returns the short string representing this data, to be shown in DashClock's collapsed form.
     * Default null.
     */
    public String status() {
        return mStatus;
    }

    /**
     * Sets the short string representing this data, to be shown in DashClock's collapsed form.
     * Should be no longer than a few characters. For example, if your {@link #expandedTitle()} is
     * "45°, Sunny", your status could be simply "45°". Alternatively, if the status contains a
     * single newline, DashClock may break it up over two lines and use a smaller font. This should
     * be avoided where possible in favor of an {@link #expandedTitle(String)}. Default null.
     */
    public ExtensionData status(String status) {
        mStatus = status;
        return this;
    }

    /**
     * Returns the expanded title representing this data. Generally a longer form of
     * {@link #status()}. Default null.
     */
    public String expandedTitle() {
        return mExpandedTitle;
    }

    /**
     * Sets the expanded title representing this data. Generally a longer form of
     * {@link #status()}. Can be multiple lines, although DashClock will cap the number of lines
     * shown. If this is not set, DashClock will just use the {@link #status()}.
     * Default null.
     */
    public ExtensionData expandedTitle(String expandedTitle) {
        mExpandedTitle = expandedTitle;
        return this;
    }

    /**
     * Returns the expanded body text representing this data. Default null.
     */
    public String expandedBody() {
        return mExpandedBody;
    }

    /**
     * Sets the expanded body text (below the expanded title), representing this data. Can span
     * multiple lines, although DashClock will cap the number of lines shown. Default null.
     * @param expandedBody
     * @return
     */
    public ExtensionData expandedBody(String expandedBody) {
        mExpandedBody = expandedBody;
        return this;
    }

    /**
     * Returns the click intent to start (using
     * {@link android.content.Context#startActivity(android.content.Intent)}) when the user clicks
     * the status in DashClock. Default null.
     */
    public Intent clickIntent() {
        return mClickIntent;
    }

    /**
     * Sets the click intent to start (using
     * {@link android.content.Context#startActivity(android.content.Intent)}) when the user clicks
     * the status in DashClock. The activity represented by this intent will be started in a new
     * task and should be exported. Default null.
     */
    public ExtensionData clickIntent(Intent clickIntent) {
        mClickIntent = clickIntent;
        return this;
    }

    /**
     * Serializes the contents of this object to JSON.
     */
    public JSONObject serialize() throws JSONException {
        JSONObject data = new JSONObject();
        data.put(KEY_VISIBLE, mVisible);
        data.put(KEY_ICON, mIcon);
        data.put(KEY_STATUS, mStatus);
        data.put(KEY_EXPANDED_TITLE, mExpandedTitle);
        data.put(KEY_EXPANDED_BODY, mExpandedBody);
        data.put(KEY_CLICK_INTENT, (mClickIntent == null) ? null : mClickIntent.toUri(0));
        return data;
    }

    /**
     * Deserializes the given JSON representation of extension data, populating this
     * object.
     */
    public void deserialize(JSONObject data) throws JSONException {
        this.mVisible = data.optBoolean(KEY_VISIBLE);
        this.mIcon = data.optInt(KEY_ICON);
        this.mStatus = data.optString(KEY_STATUS);
        this.mExpandedTitle = data.optString(KEY_EXPANDED_TITLE);
        this.mExpandedBody = data.optString(KEY_EXPANDED_BODY);
        try {
            this.mClickIntent = Intent.parseUri(data.optString(KEY_CLICK_INTENT), 0);
        } catch (URISyntaxException ignored) {
        }
    }

    /**
     * Serializes the contents of this object to a {@link Bundle}.
     */
    public Bundle toBundle() {
        Bundle data = new Bundle();
        data.putBoolean(KEY_VISIBLE, mVisible);
        data.putInt(KEY_ICON, mIcon);
        data.putString(KEY_STATUS, mStatus);
        data.putString(KEY_EXPANDED_TITLE, mExpandedTitle);
        data.putString(KEY_EXPANDED_BODY, mExpandedBody);
        data.putString(KEY_CLICK_INTENT, (mClickIntent == null) ? null : mClickIntent.toUri(0));
        return data;
    }

    /**
     * Deserializes the given {@link Bundle} representation of extension data, populating this
     * object.
     */
    public void fromBundle(Bundle src) {
        this.mVisible = src.getBoolean(KEY_VISIBLE, true);
        this.mIcon = src.getInt(KEY_ICON);
        this.mStatus = src.getString(KEY_STATUS);
        this.mExpandedTitle = src.getString(KEY_EXPANDED_TITLE);
        this.mExpandedBody = src.getString(KEY_EXPANDED_BODY);
        try {
            this.mClickIntent = Intent.parseUri(src.getString(KEY_CLICK_INTENT), 0);
        } catch (URISyntaxException ignored) {
        }
    }

    /**
     * @see Parcelable
     */
    public static final Creator<ExtensionData> CREATOR
            = new Creator<ExtensionData>() {
        public ExtensionData createFromParcel(Parcel in) {
            return new ExtensionData(in);
        }

        public ExtensionData[] newArray(int size) {
            return new ExtensionData[size];
        }
    };

    private ExtensionData(Parcel in) {
        int parcelableVersion = in.readInt();
        int parcelableSize = in.readInt();
        // Version 1 below
        if (parcelableVersion >= 1) {
            this.mVisible = (in.readInt() != 0);
            this.mIcon = in.readInt();
            this.mStatus = in.readString();
            if (TextUtils.isEmpty(this.mStatus)) {
                this.mStatus = null;
            }
            this.mExpandedTitle = in.readString();
            if (TextUtils.isEmpty(this.mExpandedTitle)) {
                this.mExpandedTitle = null;
            }
            this.mExpandedBody = in.readString();
            if (TextUtils.isEmpty(this.mExpandedBody)) {
                this.mExpandedBody = null;
            }
            try {
                this.mClickIntent = Intent.parseUri(in.readString(), 0);
            } catch (URISyntaxException ignored) {
            }
        }
        // Version 2 below

        // Skip any fields we don't know about. For example, if our current version's
        // PARCELABLE_SIZE is 6 and the input parcelableSize is 12, skip the 6 fields we
        // haven't read yet (from above) since we don't know about them.
        in.setDataPosition(in.dataPosition() + (PARCELABLE_SIZE - parcelableSize));
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        /**
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * {@link #PARCELABLE_VERSION} and modify {@link #PARCELABLE_SIZE}.
         */
        parcel.writeInt(PARCELABLE_VERSION);
        parcel.writeInt(PARCELABLE_SIZE);
        // Version 1 below
        parcel.writeInt(mVisible ? 1 : 0);
        parcel.writeInt(mIcon);
        parcel.writeString(TextUtils.isEmpty(mStatus) ? "" : mStatus);
        parcel.writeString(TextUtils.isEmpty(mExpandedTitle) ? "" : mExpandedTitle);
        parcel.writeString(TextUtils.isEmpty(mExpandedBody) ? "" : mExpandedBody);
        parcel.writeString((mClickIntent == null) ? "" : mClickIntent.toUri(0));
        // Version 2 below
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
            ExtensionData other = (ExtensionData) o;
            return other.mVisible == mVisible
                    && other.mIcon == mIcon
                    && TextUtils.equals(other.mStatus, mStatus)
                    && TextUtils.equals(other.mExpandedTitle, mExpandedTitle)
                    && TextUtils.equals(other.mExpandedBody, mExpandedBody)
                    && intentEquals(other.mClickIntent, mClickIntent);

        } catch (ClassCastException e) {
            return false;
        }
    }

    private static boolean intentEquals(Intent x, Intent y) {
        if (x == null || y == null) {
            return x == y;
        } else {
            return x.equals(y);
        }
    }

    /**
     * Returns true if the two provided data objects are equal (or both null).
     */
    public static boolean equals(ExtensionData x, ExtensionData y) {
        if (x == null || y == null) {
            return x == y;
        } else {
            return x.equals(y);
        }
    }

    /**
     * Cleans up this object's data according to the size limits described by
     * {@link #MAX_STATUS_LENGTH}, {@link #MAX_EXPANDED_TITLE_LENGTH}, etc.
     */
    public void clean() {
        if (!TextUtils.isEmpty(mStatus) && mStatus.length() > MAX_STATUS_LENGTH) {
            mStatus = mStatus.substring(0, MAX_STATUS_LENGTH);
        }
        if (!TextUtils.isEmpty(mExpandedTitle) && mStatus.length() > MAX_EXPANDED_TITLE_LENGTH) {
            mExpandedTitle = mExpandedTitle.substring(0, MAX_EXPANDED_TITLE_LENGTH);
        }
        if (!TextUtils.isEmpty(mExpandedBody) && mStatus.length() > MAX_EXPANDED_BODY_LENGTH) {
            mExpandedBody = mExpandedBody.substring(0, MAX_EXPANDED_BODY_LENGTH);
        }
    }
}
