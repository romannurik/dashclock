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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

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
    public static final int PARCELABLE_VERSION = 2;

    private static final String KEY_VISIBLE = "visible";
    private static final String KEY_ICON = "icon";
    private static final String KEY_ICON_URI = "icon_uri";
    private static final String KEY_STATUS = "status";
    private static final String KEY_EXPANDED_TITLE = "title";
    private static final String KEY_EXPANDED_BODY = "body";
    private static final String KEY_CLICK_INTENT = "click_intent";
    private static final String KEY_CONTENT_DESCRIPTION = "content_description";

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

    /**
     * The maximum length for {@link #contentDescription(String)}. Enforced by {@link #clean()}.
     */
    public static final int MAX_CONTENT_DESCRIPTION_LENGTH = 32 +
            MAX_STATUS_LENGTH + MAX_EXPANDED_TITLE_LENGTH + MAX_EXPANDED_BODY_LENGTH;

    private boolean mVisible = false;
    private int mIcon = 0;
    private Uri mIconUri = null;
    private String mStatus = null;
    private String mExpandedTitle = null;
    private String mExpandedBody = null;
    private Intent mClickIntent = null;
    private String mContentDescription = null;

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
     * Returns the ID of the drawable resource within the extension's package that represents this
     * data. Default 0.
     */
    public int icon() {
        return mIcon;
    }

    /**
     * Sets the ID of the drawable resource within the extension's package that represents this
     * data. The icon should be entirely white, with alpha, and about 48x48 dp. It will be
     * scaled down as needed. If there is no contextual icon representation of the data, simply
     * use the extension or app icon. If an {@link #iconUri(Uri) iconUri} is provided, it
     * will take precedence over this value. Default 0.
     *
     * @see #iconUri(Uri)
     */
    public ExtensionData icon(int icon) {
        mIcon = icon;
        return this;
    }

    /**
     * Returns the content:// URI of a bitmap representing this data. Default null.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    public Uri iconUri() {
        return mIconUri;
    }

    /**
     * Sets the content:// URI of the bitmap representing this data. This takes precedence over
     * the regular {@link #icon(int) icon resource ID} if set. This resource will be loaded
     * using {@link android.content.ContentResolver#openFileDescriptor(android.net.Uri, String)} and
     * {@link android.graphics.BitmapFactory#decodeFileDescriptor(java.io.FileDescriptor)}. See the
     * {@link #icon(int) icon} method for guidelines on the styling of this bitmap.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    public ExtensionData iconUri(Uri iconUri) {
        mIconUri = iconUri;
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
     * "45°, Sunny", your status could simply be "45°". Alternatively, if the status contains a
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
     * Returns the content description for this data, used for accessibility purposes.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    public String contentDescription() {
        return mContentDescription;
    }

    /**
     * Sets the content description for this data. This content description will replace the
     * {@link #status()}, {@link #expandedTitle()} and {@link #expandedBody()} for accessibility
     * purposes.
     *
     * @see android.view.View#setContentDescription(CharSequence)
     * @since Protocol Version 2 (API v2.x)
     */
    public ExtensionData contentDescription(String contentDescription) {
        mContentDescription = contentDescription;
        return this;
    }

    /**
     * Serializes the contents of this object to JSON.
     */
    public JSONObject serialize() throws JSONException {
        JSONObject data = new JSONObject();
        data.put(KEY_VISIBLE, mVisible);
        data.put(KEY_ICON, mIcon);
        data.put(KEY_ICON_URI, (mIconUri == null ? null : mIconUri.toString()));
        data.put(KEY_STATUS, mStatus);
        data.put(KEY_EXPANDED_TITLE, mExpandedTitle);
        data.put(KEY_EXPANDED_BODY, mExpandedBody);
        data.put(KEY_CLICK_INTENT, (mClickIntent == null) ? null : mClickIntent.toUri(0));
        data.put(KEY_CONTENT_DESCRIPTION, mContentDescription);
        return data;
    }

    /**
     * Deserializes the given JSON representation of extension data, populating this
     * object.
     */
    public void deserialize(JSONObject data) throws JSONException {
        this.mVisible = data.optBoolean(KEY_VISIBLE);
        this.mIcon = data.optInt(KEY_ICON);
        String iconUriString = data.optString(KEY_ICON_URI);
        this.mIconUri = TextUtils.isEmpty(iconUriString) ? null : Uri.parse(iconUriString);
        this.mStatus = data.optString(KEY_STATUS);
        this.mExpandedTitle = data.optString(KEY_EXPANDED_TITLE);
        this.mExpandedBody = data.optString(KEY_EXPANDED_BODY);
        try {
            this.mClickIntent = Intent.parseUri(data.optString(KEY_CLICK_INTENT), 0);
        } catch (URISyntaxException ignored) {
        }
        this.mContentDescription = data.optString(KEY_CONTENT_DESCRIPTION);
    }

    /**
     * Serializes the contents of this object to a {@link Bundle}.
     */
    public Bundle toBundle() {
        Bundle data = new Bundle();
        data.putBoolean(KEY_VISIBLE, mVisible);
        data.putInt(KEY_ICON, mIcon);
        data.putString(KEY_ICON_URI, (mIconUri == null ? null : mIconUri.toString()));
        data.putString(KEY_STATUS, mStatus);
        data.putString(KEY_EXPANDED_TITLE, mExpandedTitle);
        data.putString(KEY_EXPANDED_BODY, mExpandedBody);
        data.putString(KEY_CLICK_INTENT, (mClickIntent == null) ? null : mClickIntent.toUri(0));
        data.putString(KEY_CONTENT_DESCRIPTION, mContentDescription);
        return data;
    }

    /**
     * Deserializes the given {@link Bundle} representation of extension data, populating this
     * object.
     */
    public void fromBundle(Bundle src) {
        this.mVisible = src.getBoolean(KEY_VISIBLE, true);
        this.mIcon = src.getInt(KEY_ICON);
        String iconUriString = src.getString(KEY_ICON_URI);
        this.mIconUri = TextUtils.isEmpty(iconUriString) ? null : Uri.parse(iconUriString);
        this.mStatus = src.getString(KEY_STATUS);
        this.mExpandedTitle = src.getString(KEY_EXPANDED_TITLE);
        this.mExpandedBody = src.getString(KEY_EXPANDED_BODY);
        try {
            this.mClickIntent = Intent.parseUri(src.getString(KEY_CLICK_INTENT), 0);
        } catch (URISyntaxException ignored) {
        }
        this.mContentDescription = src.getString(KEY_CONTENT_DESCRIPTION);
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
        int startPosition = in.dataPosition();
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
        if (parcelableVersion >= 2) {
            this.mContentDescription = in.readString();
            if (TextUtils.isEmpty(this.mContentDescription)) {
                this.mContentDescription = null;
            }
            String iconUriString = in.readString();
            this.mIconUri = TextUtils.isEmpty(iconUriString) ? null : Uri.parse(iconUriString);
        }
        // Only advance the data position if the parcelable version is >= 2. In v1 of the
        // parcelable, there was an awful bug where the parcelableSize was complete nonsense.
        if (parcelableVersion >= 2) {
            in.setDataPosition(startPosition + parcelableSize);
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        /**
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * {@link #PARCELABLE_VERSION}.
         */
        parcel.writeInt(PARCELABLE_VERSION);
        // Inject a placeholder that will store the parcel size from this point on
        // (not including the size itself).
        int sizePosition = parcel.dataPosition();
        parcel.writeInt(0);
        int startPosition = parcel.dataPosition();
        // Version 1 below
        parcel.writeInt(mVisible ? 1 : 0);
        parcel.writeInt(mIcon);
        parcel.writeString(TextUtils.isEmpty(mStatus) ? "" : mStatus);
        parcel.writeString(TextUtils.isEmpty(mExpandedTitle) ? "" : mExpandedTitle);
        parcel.writeString(TextUtils.isEmpty(mExpandedBody) ? "" : mExpandedBody);
        parcel.writeString((mClickIntent == null) ? "" : mClickIntent.toUri(0));
        // Version 2 below
        parcel.writeString(TextUtils.isEmpty(mContentDescription) ? "" : mContentDescription);
        parcel.writeString(mIconUri == null ? "" : mIconUri.toString());
        // Go back and write the size
        int parcelableSize = parcel.dataPosition() - startPosition;
        parcel.setDataPosition(sizePosition);
        parcel.writeInt(parcelableSize);
        parcel.setDataPosition(startPosition + parcelableSize);
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
                    && objectEquals(other.mIconUri, mIconUri)
                    && TextUtils.equals(other.mStatus, mStatus)
                    && TextUtils.equals(other.mExpandedTitle, mExpandedTitle)
                    && TextUtils.equals(other.mExpandedBody, mExpandedBody)
                    && objectEquals(other.mClickIntent, mClickIntent)
                    && TextUtils.equals(other.mContentDescription, mContentDescription);

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

    @Override
    public String toString() {
        try {
            return serialize().toString();
        } catch (JSONException e) {
            return super.toString();
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    /**
     * Cleans up this object's data according to the size limits described by
     * {@link #MAX_STATUS_LENGTH}, {@link #MAX_EXPANDED_TITLE_LENGTH}, etc.
     */
    public void clean() {
        if (!TextUtils.isEmpty(mStatus)
                && mStatus.length() > MAX_STATUS_LENGTH) {
            mStatus = mStatus.substring(0, MAX_STATUS_LENGTH);
        }
        if (!TextUtils.isEmpty(mExpandedTitle)
                && mExpandedTitle.length() > MAX_EXPANDED_TITLE_LENGTH) {
            mExpandedTitle = mExpandedTitle.substring(0, MAX_EXPANDED_TITLE_LENGTH);
        }
        if (!TextUtils.isEmpty(mExpandedBody)
                && mExpandedBody.length() > MAX_EXPANDED_BODY_LENGTH) {
            mExpandedBody = mExpandedBody.substring(0, MAX_EXPANDED_BODY_LENGTH);
        }
        if (!TextUtils.isEmpty(mContentDescription)
                && mContentDescription.length() > MAX_EXPANDED_BODY_LENGTH) {
            mContentDescription = mContentDescription.substring(0, MAX_CONTENT_DESCRIPTION_LENGTH);
        }
    }
}
