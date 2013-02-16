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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

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
        int unreadConversations = 0;
        StringBuilder names = new StringBuilder();
        Cursor cursor = openMmsSmsCursor();
        long threadId = 0;

        while (cursor.moveToNext()) {
            ++unreadConversations;

            // Get display name. SMS's are easy; MMS's not so much.
            long id = cursor.getLong(MmsSmsQuery._ID);
            long contactId = cursor.getLong(MmsSmsQuery.PERSON);
            String address = cursor.getString(MmsSmsQuery.ADDRESS);
            threadId = cursor.getLong(MmsSmsQuery.THREAD_ID);

            if (contactId == 0 && TextUtils.isEmpty(address) && id != 0) {
                // Try MMS addr query
                Cursor addrCursor = openMmsAddrCursor(id);
                if (addrCursor.moveToFirst()) {
                    contactId = addrCursor.getLong(MmsAddrQuery.CONTACT_ID);
                    address = addrCursor.getString(MmsAddrQuery.ADDRESS);
                }
                addrCursor.close();
            }

            String displayName = address;

            if (contactId > 0) {
                Cursor contactCursor = openContactsCursorById(contactId);
                if (contactCursor.moveToFirst()) {
                    displayName = contactCursor.getString(RawContactsQuery.DISPLAY_NAME);
                } else {
                    contactId = 0;
                }
                contactCursor.close();
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

            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(displayName);
        }
        cursor.close();

        Intent clickIntent;
        if (unreadConversations == 1 && threadId > 0) {
            clickIntent = new Intent(Intent.ACTION_VIEW,
                    TelephonyProviderConstants.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon()
                            .appendPath(Long.toString(threadId)).build());
        } else {
            clickIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MESSAGING);
        }

        publishUpdate(new ExtensionData()
                .visible(unreadConversations > 0)
                .icon(R.drawable.ic_extension_sms)
                .status(Integer.toString(unreadConversations))
                .expandedTitle(
                        getResources().getQuantityString(
                                R.plurals.sms_title_template, unreadConversations,
                                unreadConversations))
                .expandedBody(getString(R.string.sms_body_template, names.toString()))
                .clickIntent(clickIntent));
    }


    private Cursor openMmsSmsCursor() {
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
    }

    private Cursor openMmsAddrCursor(long mmsMsgId) {
        return getContentResolver().query(
                TelephonyProviderConstants.Mms.CONTENT_URI.buildUpon()
                        .appendPath(Long.toString(mmsMsgId))
                        .appendPath("addr")
                        .build(),
                MmsAddrQuery.PROJECTION,
                TelephonyProviderConstants.Mms.Addr.MSG_ID + "=?",
                new String[]{Long.toString(mmsMsgId)},
                null);
    }

    private Cursor openContactsCursorById(long contactId) {
        return getContentResolver().query(
                ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                        .appendPath(Long.toString(contactId))
                        .build(),
                RawContactsQuery.PROJECTION,
                null,
                null,
                null);
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
        } catch (IllegalArgumentException e) {
            // Can be called by the content provider (from Google Play crash/ANR console)
            // java.lang.IllegalArgumentException: URI: content://com.android.contacts/phone_lookup/
            LogUtils.LOGW(TAG, "Error looking up contact name", e);
            return null;
        }
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
