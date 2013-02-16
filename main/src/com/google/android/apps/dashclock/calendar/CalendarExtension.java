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

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * Calendar "upcoming appointment" extension.
 */
public class CalendarExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(CalendarExtension.class);

    public static final String PREF_CALENDARS = "pref_calendars";
    public static final String PREF_LOOK_AHEAD_HOURS = "pref_calendar_look_ahead_hours";

    private static final long MINUTE_MILLIS = 60 * 1000;
    private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;

    private static final int DEFAULT_LOOK_AHEAD_HOURS = 6;
    private int mLookAheadHours = DEFAULT_LOOK_AHEAD_HOURS;

    static String[] getAllCalendars(Context context) {
        // Only return calendars that are marked as synced to device. (This is different from the display flag)
        Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                CalendarsQuery.PROJECTION,
                CalendarContract.Calendars.SYNC_EVENTS + "==1",
                null,
                null
        );

        String[] calendars = new String[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            calendars[i++] = cursor.getString(CalendarsQuery.ID);
        }

        cursor.close();
        return calendars;
    }

    private Set<String> getSelectedCalendars() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final String[] allCalendars = getAllCalendars(this);

        //build a set of all calendars in the event we don't have a selection set in the preferences.
        Set<String> calendarSet = new HashSet<String>();
        calendarSet.addAll(Arrays.asList(allCalendars));
        return sp.getStringSet(PREF_CALENDARS, calendarSet);
    }

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    CalendarContract.Events.CONTENT_URI.toString()
            });
        }

        setUpdateWhenScreenOn(true);
    }

    @Override
    protected void onUpdateData(int reason) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            mLookAheadHours = Integer.parseInt(sp.getString(PREF_LOOK_AHEAD_HOURS,
                    Integer.toString(mLookAheadHours)));
        } catch (NumberFormatException e) {
            mLookAheadHours = DEFAULT_LOOK_AHEAD_HOURS;
        }

        Cursor cursor = openEventsCursor();
        if (cursor == null) {
            LOGW(TAG, "Null events cursor, short-circuiting.");
            return;
        }

        long currentTimestamp = getCurrentTimestamp();
        long nextTimestamp = 0;
        long timeUntilNextAppointent = 0;
        while (cursor.moveToNext()) {
            nextTimestamp = cursor.getLong(EventsQuery.BEGIN);
            timeUntilNextAppointent = nextTimestamp - currentTimestamp;
            if (timeUntilNextAppointent >= 0) {
                break;
            }

            // Skip over events that are not ALL_DAY but span multiple days, including
            // the next 6 hours. An example is an event that starts at 4pm yesterday
            // day and ends 6pm tomorrow.
        }

        if (cursor.isAfterLast()) {
            LOGD(TAG, "No upcoming appointments found.");
            cursor.close();
            publishUpdate(new ExtensionData());
            return;
        }

        int minutesUntilNextAppointment = (int) (timeUntilNextAppointent / MINUTE_MILLIS);

        String untilString;
        if (minutesUntilNextAppointment < 60) {
            untilString = getResources().getQuantityString(
                    R.plurals.calendar_template_mins,
                    minutesUntilNextAppointment,
                    minutesUntilNextAppointment);
        } else {
            int hours = Math.round(minutesUntilNextAppointment / 60f);
            if (hours < 24) {
                untilString = getResources().getQuantityString(
                        R.plurals.calendar_template_hours, hours, hours);
            } else {
                int days = hours / 24; // floor, not round for days
                untilString = getResources().getQuantityString(
                        R.plurals.calendar_template_days, days, days);
            }
        }
        String eventTitle = cursor.getString(EventsQuery.TITLE);
        String eventLocation = cursor.getString(EventsQuery.EVENT_LOCATION);

        long eventId = cursor.getLong(EventsQuery.EVENT_ID);
        long eventBegin = cursor.getLong(EventsQuery.BEGIN);
        long eventEnd = cursor.getLong(EventsQuery.END);
        cursor.close();

        Calendar nextEventCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        nextEventCalendar.setTimeInMillis(nextTimestamp);

        StringBuilder expandedBodyFormat = new StringBuilder();
        if (nextTimestamp - currentTimestamp > 24 * HOUR_MILLIS) {
            expandedBodyFormat.append("EEEE, ");
        }

        if (DateFormat.is24HourFormat(this)) {
            expandedBodyFormat.append("HH:mm");
        } else {
            expandedBodyFormat.append("h:mm a");
        }

        String expandedTime = new SimpleDateFormat(expandedBodyFormat.toString())
                .format(nextEventCalendar.getTime());
        String expandedBody = String.format("%s - %s", expandedTime, eventLocation);

        publishUpdate(new ExtensionData()
                .visible(timeUntilNextAppointent >= 0
                        && timeUntilNextAppointent <= mLookAheadHours * HOUR_MILLIS)
                .icon(R.drawable.ic_extension_calendar)
                .status(untilString)
                .expandedTitle(eventTitle)
                .expandedBody(expandedBody)
                .clickIntent(new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI,
                                Long.toString(eventId)))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, eventBegin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, eventEnd)));
    }

    private static long getCurrentTimestamp() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
    }

    private Cursor openEventsCursor() {
        String calendarSelection = generateCalendarSelection();

        Set<String> calendarSet = getSelectedCalendars();
        String[] calendars = calendarSet.toArray(new String[calendarSet.size()]);

        long now = getCurrentTimestamp();
        return getContentResolver().query(
                CalendarContract.Instances.CONTENT_URI.buildUpon()
                        .appendPath(Long.toString(now))
                        .appendPath(Long.toString(now + mLookAheadHours * HOUR_MILLIS))
                        .build(),
                EventsQuery.PROJECTION,
                CalendarContract.Instances.ALL_DAY + "=0 AND "
                        + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!="
                        + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED + " AND "
                        + "IFNULL(" + CalendarContract.Instances.STATUS + ",0)!="
                        + CalendarContract.Instances.STATUS_CANCELED + " AND "
                        + CalendarContract.Instances.VISIBLE + "!=0 AND ("
                        + calendarSelection + ")",
                calendars,
                CalendarContract.Instances.BEGIN);
    }

    private String generateCalendarSelection() {
        Set<String> calendars = getSelectedCalendars();
        int count = calendars.size();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < count; i++) {
            if (i != 0) {
                sb.append(" OR ");
            }

            sb.append(CalendarContract.Events.CALENDAR_ID);
            sb.append(" = ?");
        }

        return sb.toString();
    }

    private interface EventsQuery {
        String[] PROJECTION = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION
        };

        int EVENT_ID = 0;
        int BEGIN = 1;
        int END = 2;
        int TITLE = 3;
        int EVENT_LOCATION = 4;
    }

    private interface CalendarsQuery {
        String[] PROJECTION = {
                CalendarContract.Calendars._ID
        };

        int ID = 0;
    }
}
