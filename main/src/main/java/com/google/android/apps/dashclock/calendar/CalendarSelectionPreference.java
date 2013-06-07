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

package com.google.android.apps.dashclock.calendar;

import net.nurik.roman.dashclock.R;

import android.app.AlertDialog.Builder;
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.preference.MultiSelectListPreference;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarSelectionPreference extends MultiSelectListPreference {
    private CalendarListAdapter mAdapter;

    private QueryHandler mQueryHandler;
    private Set<String> mSelectedCalendars;

    public CalendarSelectionPreference(Context context) {
        this(context, null);
    }

    public CalendarSelectionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        List<Pair<String, Boolean>> allCalendars = CalendarExtension.getAllCalendars(context);
        Set<String> allVisibleCalendarsSet = new HashSet<String>();
        for (Pair<String, Boolean> pair : allCalendars) {
            if (pair.second) {
                allVisibleCalendarsSet.add(pair.first);
            }
        }

        mSelectedCalendars = new HashSet<String>(PreferenceManager
                .getDefaultSharedPreferences(context)
                .getStringSet(CalendarExtension.PREF_SELECTED_CALENDARS, allVisibleCalendarsSet));

        mAdapter = new CalendarListAdapter(context);
        mQueryHandler = new QueryHandler(context, mAdapter);

        mQueryHandler.startQuery(0,
                null,
                CalendarContract.Calendars.CONTENT_URI,
                CalendarQuery.PROJECTION,
                CalendarContract.Calendars.SYNC_EVENTS + "=1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        builder.setAdapter(mAdapter, null);
        builder.setTitle(R.string.pref_calendar_selected_title);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
                sp.edit().putStringSet(CalendarExtension.PREF_SELECTED_CALENDARS,
                        mSelectedCalendars).commit();

                // since we have extended the list preference, it is our responsibility to inform the change listener.
                if (getOnPreferenceChangeListener() != null) {
                    getOnPreferenceChangeListener()
                            .onPreferenceChange(CalendarSelectionPreference.this,
                                    mSelectedCalendars);
                }
            }
        });
    }

    static class QueryHandler extends AsyncQueryHandler {
        private final CalendarListAdapter mAdapter;

        public QueryHandler(Context context, CalendarListAdapter adapter) {
            super(context.getContentResolver());
            mAdapter = adapter;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            mAdapter.swapCursor(cursor);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // No support for default value attribute
        return null;
    }

    public class CalendarListAdapter extends ResourceCursorAdapter {
        public CalendarListAdapter(Context context) {
            super(context, R.layout.list_item_calendar, null, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView calendarNameView = (TextView) view.findViewById(android.R.id.text1);
            calendarNameView.setText(cursor.getString(CalendarQuery.CALENDAR_DISPLAY_NAME));

            TextView accountNameView = (TextView) view.findViewById(android.R.id.text2);
            accountNameView.setText(cursor.getString(CalendarQuery.ACCOUNT_NAME));

            final String calendarId = cursor.getString(CalendarQuery.ID);

            final CheckBox checkBox = (CheckBox) view.findViewById(R.id.calendar_checkbox);
            checkBox.setChecked(mSelectedCalendars.contains(calendarId));

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectedCalendars.contains(calendarId)) {
                        mSelectedCalendars.remove(calendarId);
                        checkBox.setChecked(false);
                    } else {
                        mSelectedCalendars.add(calendarId);
                        checkBox.setChecked(true);
                    }
                }
            });
        }
    }

    interface CalendarQuery {
        public String[] PROJECTION = new String[] {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
        };

        public int ID = 0;
        public int CALENDAR_DISPLAY_NAME = 1;
        public int ACCOUNT_NAME = 2;
    }
}
