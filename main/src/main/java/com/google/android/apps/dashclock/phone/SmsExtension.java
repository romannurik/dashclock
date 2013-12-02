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

package com.google.android.apps.dashclock.phone;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import net.nurik.roman.dashclock.R;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * Unread SMS and MMS's extension.
 */
public class SmsExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(SmsExtension.class);

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    TelephonyProviderConstants.MmsSms.CONTENT_URI.toString(),
            });
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        long lastUnreadThreadId = 0;
        Set<Long> unreadThreadIds = new HashSet<Long>();
        Set<String> unreadThreadParticipantNames = new HashSet<String>();
        boolean showingAllConversationParticipants = false;

        Cursor cursor = tryOpenSimpleThreadsCursor();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (cursor.getInt(SimpleThreadsQuery.READ) == 0) {
                    long threadId = cursor.getLong(SimpleThreadsQuery._ID);
                    unreadThreadIds.add(threadId);
                    lastUnreadThreadId = threadId;

                    // Some devices will fail on tryOpenMmsSmsCursor below, so
                    // store a list of participants on unread threads as a fallback.
                    String recipientIdsStr = cursor.getString(SimpleThreadsQuery.RECIPIENT_IDS);
                    if (!TextUtils.isEmpty(recipientIdsStr)) {
                        String[] recipientIds = TextUtils.split(recipientIdsStr, " ");
                        for (String recipientId : recipientIds) {
                            Cursor canonAddrCursor = tryOpenCanonicalAddressCursorById(
                                    Long.parseLong(recipientId));
                            if (canonAddrCursor == null) {
                                continue;
                            }
                            if (canonAddrCursor.moveToFirst()) {
                                String address = canonAddrCursor.getString(
                                        CanonicalAddressQuery.ADDRESS);
                                String displayName = getDisplayNameForContact(0, address);
                                if (!TextUtils.isEmpty(displayName)) {
                                    unreadThreadParticipantNames.add(displayName);
                                }
                            }
                            canonAddrCursor.close();
                        }
                    }
                }
            }
            cursor.close();

            LOGD(TAG, "Unread thread IDs: [" + TextUtils.join(", ", unreadThreadIds) + "]");
        }

        int unreadConversations = 0;
        StringBuilder names = new StringBuilder();
        cursor = tryOpenMmsSmsCursor();
        if (cursor != null) {
            // Most devices will hit this code path.
            while (cursor.moveToNext()) {
                // Get display name. SMS's are easy; MMS's not so much.
                long id = cursor.getLong(MmsSmsQuery._ID);
                long contactId = cursor.getLong(MmsSmsQuery.PERSON);
                String address = cursor.getString(MmsSmsQuery.ADDRESS);
                long threadId = cursor.getLong(MmsSmsQuery.THREAD_ID);
                if (unreadThreadIds != null && !unreadThreadIds.contains(threadId)) {
                    // We have the list of all thread IDs (same as what the messaging app uses), and
                    // this supposedly unread message's thread isn't in the list. This message is
                    // likely an orphaned message whose thread was deleted. Not skipping it is
                    // likely the cause of http://code.google.com/p/dashclock/issues/detail?id=8
                    LOGD(TAG, "Skipping probably orphaned message " + id + " with thread ID "
                            + threadId);
                    continue;
                }

                ++unreadConversations;
                lastUnreadThreadId = threadId;

                if (contactId == 0 && TextUtils.isEmpty(address) && id != 0) {
                    // Try MMS addr query
                    Cursor addrCursor = tryOpenMmsAddrCursor(id);
                    if (addrCursor != null) {
                        if (addrCursor.moveToFirst()) {
                            contactId = addrCursor.getLong(MmsAddrQuery.CONTACT_ID);
                            address = addrCursor.getString(MmsAddrQuery.ADDRESS);
                        }
                        addrCursor.close();
                    }
                }

                String displayName = getDisplayNameForContact(contactId, address);

                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(displayName);
            }
            cursor.close();

        } else {
            // In case the cursor is null (some Samsung devices like the Galaxy S4), use the
            // fall back on the list of participants in unread threads.
            unreadConversations = unreadThreadIds.size();
            names.append(TextUtils.join(", ", unreadThreadParticipantNames));
            showingAllConversationParticipants = true;
        }

        PackageManager pm = getPackageManager();
        Intent clickIntent = null;
        if (unreadConversations == 1 && lastUnreadThreadId > 0) {
            clickIntent = new Intent(Intent.ACTION_VIEW,
                    TelephonyProviderConstants.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon()
                            .appendPath(Long.toString(lastUnreadThreadId)).build());
        }

        // If the default SMS app doesn't support ACTION_VIEW on the conversation URI,
        // or if there are multiple unread conversations, try opening the app landing screen
        // by implicit intent.
        if (clickIntent == null || pm.resolveActivity(clickIntent, 0) == null) {
            clickIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MESSAGING);

            // If the default SMS app doesn't support CATEGORY_APP_MESSAGING, try KitKat's
            // new API to get the default package (if the API is available).
            if (pm.resolveActivity(clickIntent, 0) == null) {
                clickIntent = tryGetKitKatDefaultSmsActivity();
            }
        }

        publishUpdate(new ExtensionData()
                .visible(unreadConversations > 0)
                .icon(R.drawable.ic_extension_sms)
                .status(Integer.toString(unreadConversations))
                .expandedTitle(
                        getResources().getQuantityString(
                                R.plurals.sms_title_template, unreadConversations,
                                unreadConversations))
                .expandedBody(getString(showingAllConversationParticipants
                        ? R.string.sms_body_all_participants_template
                        : R.string.sms_body_template,
                        names.toString()))
                .clickIntent(clickIntent));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private Intent tryGetKitKatDefaultSmsActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
            if (TextUtils.isEmpty(smsPackage)) {
                return null;
            }

            return new Intent()
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(smsPackage);
        }
        return null;
    }

    /**
     * Returns the display name for the contact with the given ID and/or the given address
     * (phone number). One or both parameters should be provided.
     */
    private String getDisplayNameForContact(long contactId, String address) {
        String displayName = address;

        if (contactId > 0) {
            Cursor contactCursor = tryOpenContactsCursorById(contactId);
            if (contactCursor != null) {
                if (contactCursor.moveToFirst()) {
                    displayName = contactCursor.getString(RawContactsQuery.DISPLAY_NAME);
                } else {
                    contactId = 0;
                }
                contactCursor.close();
            }
        }

        if (contactId <= 0) {
            Cursor contactCursor = tryOpenContactsCursorByAddress(address);
            if (contactCursor != null) {
                if (contactCursor.moveToFirst()) {
                    displayName = contactCursor.getString(ContactsQuery.DISPLAY_NAME);
                }
                contactCursor.close();
            }
        }

        return displayName;
    }

    private Cursor tryOpenMmsSmsCursor() {
        try {
            return getContentResolver().query(
                    TelephonyProviderConstants.MmsSms.CONTENT_CONVERSATIONS_URI,
                    MmsSmsQuery.PROJECTION,
                    TelephonyProviderConstants.Mms.READ + "=0 AND "
                            + TelephonyProviderConstants.Mms.THREAD_ID + "!=0 AND ("
                            + TelephonyProviderConstants.Mms.MESSAGE_BOX + "="
                            + TelephonyProviderConstants.Mms.MESSAGE_BOX_INBOX + " OR "
                            + TelephonyProviderConstants.Sms.TYPE + "="
                            + TelephonyProviderConstants.Sms.MESSAGE_TYPE_INBOX + ")",
                    null,
                    null);

        } catch (Exception e) {
            // Catch all exceptions because the SMS provider is crashy
            // From developer console: "SQLiteException: table spam_filter already exists"
            LOGE(TAG, "Error accessing conversations cursor in SMS/MMS provider", e);
            return null;
        }
    }

    private Cursor tryOpenSimpleThreadsCursor() {
        try {
            return getContentResolver().query(
                    TelephonyProviderConstants.Threads.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter("simple", "true")
                            .build(),
                    SimpleThreadsQuery.PROJECTION,
                    null,
                    null,
                    null);

        } catch (Exception e) {
            LOGW(TAG, "Error accessing simple SMS threads cursor", e);
            return null;
        }
    }

    private Cursor tryOpenCanonicalAddressCursorById(long id) {
        try {
            return getContentResolver().query(
                    TelephonyProviderConstants.CanonicalAddresses.CONTENT_URI.buildUpon()
                            .build(),
                    CanonicalAddressQuery.PROJECTION,
                    TelephonyProviderConstants.CanonicalAddresses._ID + "=?",
                    new String[]{Long.toString(id)},
                    null);

        } catch (Exception e) {
            LOGE(TAG, "Error accessing canonical addresses cursor", e);
            return null;
        }
    }

    private Cursor tryOpenMmsAddrCursor(long mmsMsgId) {
        try {
            return getContentResolver().query(
                    TelephonyProviderConstants.Mms.CONTENT_URI.buildUpon()
                            .appendPath(Long.toString(mmsMsgId))
                            .appendPath("addr")
                            .build(),
                    MmsAddrQuery.PROJECTION,
                    TelephonyProviderConstants.Mms.Addr.MSG_ID + "=?",
                    new String[]{Long.toString(mmsMsgId)},
                    null);

        } catch (Exception e) {
            // Catch all exceptions because the SMS provider is crashy
            // From developer console: "SQLiteException: table spam_filter already exists"
            LOGE(TAG, "Error accessing MMS addresses cursor", e);
            return null;
        }
    }

    private Cursor tryOpenContactsCursorById(long contactId) {
        try {
            return getContentResolver().query(
                    ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendPath(Long.toString(contactId))
                            .build(),
                    RawContactsQuery.PROJECTION,
                    null,
                    null,
                    null);

        } catch (Exception e) {
            LOGE(TAG, "Error accessing contacts provider", e);
            return null;
        }
    }

    private Cursor tryOpenContactsCursorByAddress(String phoneNumber) {
        try {
            return getContentResolver().query(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                            .appendPath(Uri.encode(phoneNumber)).build(),
                    ContactsQuery.PROJECTION,
                    null,
                    null,
                    null);

        } catch (Exception e) {
            // Can be called by the content provider (from Google Play crash/ANR console)
            // java.lang.IllegalArgumentException: URI: content://com.android.contacts/phone_lookup/
            LOGW(TAG, "Error looking up contact name", e);
            return null;
        }
    }

    private interface SimpleThreadsQuery {
        String[] PROJECTION = {
                TelephonyProviderConstants.Threads._ID,
                TelephonyProviderConstants.Threads.READ,
                TelephonyProviderConstants.Threads.RECIPIENT_IDS,
        };

        int _ID = 0;
        int READ = 1;
        int RECIPIENT_IDS = 2;
    }

    private interface CanonicalAddressQuery {
        String[] PROJECTION = {
                TelephonyProviderConstants.CanonicalAddresses._ID,
                TelephonyProviderConstants.CanonicalAddresses.ADDRESS,
        };

        int _ID = 0;
        int ADDRESS = 1;
    }

    private interface MmsSmsQuery {
        String[] PROJECTION = {
                TelephonyProviderConstants.Sms._ID,
                TelephonyProviderConstants.Sms.ADDRESS,
                TelephonyProviderConstants.Sms.PERSON,
                TelephonyProviderConstants.Sms.THREAD_ID,
        };

        int _ID = 0;
        int ADDRESS = 1;
        int PERSON = 2;
        int THREAD_ID = 3;
    }

    private interface MmsAddrQuery {
        String[] PROJECTION = {
                TelephonyProviderConstants.Mms.Addr.ADDRESS,
                TelephonyProviderConstants.Mms.Addr.CONTACT_ID,
        };

        int ADDRESS = 0;
        int CONTACT_ID = 1;
    }

    private interface RawContactsQuery {
        String[] PROJECTION = {
                ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
        };

        int DISPLAY_NAME = 0;
    }

    private interface ContactsQuery {
        String[] PROJECTION = {
                ContactsContract.Contacts.DISPLAY_NAME,
        };

        int DISPLAY_NAME = 0;
    }
}
