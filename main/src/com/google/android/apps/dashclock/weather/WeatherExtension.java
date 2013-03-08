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

package com.google.android.apps.dashclock.weather;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.Utils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.configuration.AppChooserPreference;

import net.nurik.roman.dashclock.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * A local weather and forecast extension.
 */
public class WeatherExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(WeatherExtension.class);

    public static final String PREF_WEATHER_UNITS = "pref_weather_units";
    public static final String PREF_WEATHER_SHORTCUT = "pref_weather_shortcut";
    public static final Intent DEFAULT_WEATHER_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather"));

    public static final String STATE_WEATHER_LAST_BACKOFF_MILLIS
            = "state_weather_last_backoff_millis";

    private static final long STALE_LOCATION_NANOS = 10l * 60000000000l; // 10 minutes

    private static final int INITIAL_BACKOFF_MILLIS = 30000; // 30 seconds for first error retry

    private static XmlPullParserFactory sXmlPullParserFactory;

    private static final Criteria sLocationCriteria;

    private static String sWeatherUnits = "f";
    private static Intent sWeatherIntent;

    private boolean mOneTimeLocationListenerActive = false;

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    static {
        try {
            sXmlPullParserFactory = XmlPullParserFactory.newInstance();
            sXmlPullParserFactory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            LOGE(TAG, "Could not instantiate XmlPullParserFactory", e);
        }
    }

    private void resetAndCancelRetries() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().remove(STATE_WEATHER_LAST_BACKOFF_MILLIS).apply();
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(WeatherRetryReceiver.getPendingIntent(this));
    }

    private void scheduleRetry() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int lastBackoffMillis = sp.getInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, 0);
        int backoffMillis = (lastBackoffMillis > 0)
                ? lastBackoffMillis * 2
                : INITIAL_BACKOFF_MILLIS;
        sp.edit().putInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, backoffMillis).apply();
        LOGD(TAG, "Scheduling weather retry in " + (backoffMillis / 1000) + " second(s)");
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + backoffMillis,
                WeatherRetryReceiver.getPendingIntent(this));
    }

    @Override
    protected void onUpdateData(int reason) {
        LOGD(TAG, "Attempting weather update; reason=" + reason);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits);
        sWeatherIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_WEATHER_SHORTCUT, null), DEFAULT_WEATHER_INTENT);

        NetworkInfo ni = ((ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            LOGD(TAG, "No network connection; not attempting to update weather.");
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String provider = lm.getBestProvider(sLocationCriteria, true);
        if (TextUtils.isEmpty(provider)) {
            publishErrorUpdate(new CantGetWeatherException(false, R.string.no_location_data,
                    "No available location providers matching criteria."));
            return;
        }

        final Location lastLocation = lm.getLastKnownLocation(provider);
        if (lastLocation == null ||
                (SystemClock.elapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos())
                        >= STALE_LOCATION_NANOS) {
            LOGW(TAG, "Stale or missing last-known location; requesting single coarse location "
                    + "update.");
            disableOneTimeLocationListener();
            mOneTimeLocationListenerActive = true;
            lm.requestSingleUpdate(provider, mOneTimeLocationListener, null);
        } else {
            getWeatherAndPublishUpdate(lastLocation);
        }
    }

    private void disableOneTimeLocationListener() {
        if (mOneTimeLocationListenerActive) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mOneTimeLocationListener);
            mOneTimeLocationListenerActive = false;
        }
    }

    private LocationListener mOneTimeLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            LOGD(TAG, "Got network location update");
            getWeatherAndPublishUpdate(location);
            disableOneTimeLocationListener();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            LOGD(TAG, "Network location provider status change: " + status);
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                scheduleRetry();
                disableOneTimeLocationListener();
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        disableOneTimeLocationListener();
    }

    private void getWeatherAndPublishUpdate(Location location) {
        try {
            WeatherData weatherData = getWeatherForLocation(location);
            publishWeatherUpdate(weatherData);
            resetAndCancelRetries();
        } catch (CantGetWeatherException e) {
            publishErrorUpdate(e);
            if (e.isRetryable()) {
                scheduleRetry();
            }
        }
    }

    private void publishErrorUpdate(CantGetWeatherException e) {
        LOGE(TAG, "Showing a weather extension error", e);
        publishUpdate(new ExtensionData()
                .visible(true)
                .clickIntent(sWeatherIntent)
                .icon(R.drawable.ic_weather_clear)
                .status(getString(R.string.status_none))
                .expandedBody(getString(e.getUserFacingErrorStringId())));
    }

    private void publishWeatherUpdate(WeatherData weatherData) {
        String temperature = weatherData.hasValidTemperature()
                ? getString(R.string.temperature_template, weatherData.temperature)
                : getString(R.string.status_none);
        StringBuilder expandedBody = new StringBuilder();

        int conditionIconId = WeatherData.getConditionIconId(weatherData.conditionCode);
        if (WeatherData.getConditionIconId(weatherData.todayForecastConditionCode)
                == R.drawable.ic_weather_raining) {
            // Show rain if it will rain today.
            conditionIconId = R.drawable.ic_weather_raining;
            expandedBody.append(
                    getString(R.string.later_forecast_template, weatherData.forecastText));
        }

        if (expandedBody.length() > 0) {
            expandedBody.append("\n");
        }
        expandedBody.append(weatherData.location);

        publishUpdate(new ExtensionData()
                .visible(true)
                .clickIntent(sWeatherIntent)
                .status(temperature)
                .expandedTitle(getString(R.string.weather_expanded_title_template,
                        temperature + sWeatherUnits.toUpperCase(Locale.US),
                        weatherData.conditionText))
                .icon(conditionIconId)
                .expandedBody(expandedBody.toString()));
    }

    private static WeatherData getWeatherForLocation(Location location)
            throws CantGetWeatherException {

        LOGD(TAG, "Using location: " + location.getLatitude() + "," + location.getLongitude());

        // Honolulu = 2423945
        // Paris = 615702
        // London = 44418
        // New York = 2459115
        // San Francisco = 2487956
        LocationInfo locationInfo = getLocationInfo(location);

        // Loop through the woeids (they're in descending precision order) until weather data
        // is found.
        for (String woeid : locationInfo.woeids) {
            LOGD(TAG, "Trying WOEID: " + woeid);
            WeatherData data = getWeatherDataForWoeid(woeid, locationInfo.town);
            if (data != null
                    && data.conditionCode != WeatherData.INVALID_CONDITION
                    && data.temperature != WeatherData.INVALID_TEMPERATURE) {
                return data;
            }
        }

        // No weather could be found :(
        throw new CantGetWeatherException(true, R.string.no_weather_data);
    }

    private static WeatherData getWeatherDataForWoeid(String woeid, String town)
            throws CantGetWeatherException {
        HttpURLConnection connection = null;
        try {
            connection = Utils.openUrlConnection(buildWeatherQueryUrl(woeid));
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();
            xpp.setInput(new InputStreamReader(connection.getInputStream()));

            WeatherData data = new WeatherData();
            boolean hasTodayForecast = false;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG
                        && "condition".equals(xpp.getName())) {
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        if ("temp".equals(xpp.getAttributeName(i))) {
                            data.temperature = Integer.parseInt(xpp.getAttributeValue(i));
                        } else if ("code".equals(xpp.getAttributeName(i))) {
                            data.conditionCode = Integer.parseInt(xpp.getAttributeValue(i));
                        } else if ("text".equals(xpp.getAttributeName(i))) {
                            data.conditionText = xpp.getAttributeValue(i);
                        }
                    }
                } else if (eventType == XmlPullParser.START_TAG
                        && "forecast".equals(xpp.getName())
                        && !hasTodayForecast) {
                    // TODO: verify this is the forecast for today (this currently assumes the
                    // first forecast is today's forecast)
                    hasTodayForecast = true;
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        if ("code".equals(xpp.getAttributeName(i))) {
                            data.todayForecastConditionCode
                                    = Integer.parseInt(xpp.getAttributeValue(i));
                        } else if ("text".equals(xpp.getAttributeName(i))) {
                            data.forecastText = xpp.getAttributeValue(i);
                        }
                    }
                } else if (eventType == XmlPullParser.START_TAG
                        && "location".equals(xpp.getName())) {
                    String cityOrVillage = "--";
                    String region = null;
                    String country = "--";
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        if ("city".equals(xpp.getAttributeName(i))) {
                            cityOrVillage = xpp.getAttributeValue(i);
                        } else if ("region".equals(xpp.getAttributeName(i))) {
                            region = xpp.getAttributeValue(i);
                        } else if ("country".equals(xpp.getAttributeName(i))) {
                            country = xpp.getAttributeValue(i);
                        }
                    }

                    if (TextUtils.isEmpty(region)) {
                        // If no region is available, show the country. Otherwise, don't
                        // show country information.
                        region = country;
                    }

                    if (!TextUtils.isEmpty(town) && !town.equals(cityOrVillage)) {
                        // If a town is available and it's not equivalent to the city name,
                        // show it.
                        cityOrVillage = cityOrVillage + ", " + town;
                    }

                    data.location = cityOrVillage + ", " + region;
                }
                eventType = xpp.next();
            }

            if (TextUtils.isEmpty(data.location)) {
                data.location = town;
            }

            return data;

        } catch (IOException e) {
            throw new CantGetWeatherException(true, R.string.no_weather_data,
                    "Error parsing weather feed XML.", e);
        } catch (XmlPullParserException e) {
            throw new CantGetWeatherException(true, R.string.no_weather_data,
                    "Error parsing weather feed XML.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static LocationInfo getLocationInfo(Location location)
            throws CantGetWeatherException {
        LocationInfo li = new LocationInfo();

        // first=tagname (admin1, locality3) second=woeid
        String primaryWoeid = null;
        List<Pair<String,String>> alternateWoeids = new ArrayList<Pair<String, String>>();

        HttpURLConnection connection = null;
        try {
            connection = Utils.openUrlConnection(buildPlaceSearchUrl(location));
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();
            xpp.setInput(new InputStreamReader(connection.getInputStream()));

            boolean inWoe = false;
            boolean inTown = false;
            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = xpp.getName();

                if (eventType == XmlPullParser.START_TAG && "woeid".equals(tagName)) {
                    inWoe = true;
                } else if (eventType == XmlPullParser.TEXT && inWoe) {
                    primaryWoeid = xpp.getText();
                }

                if (eventType == XmlPullParser.START_TAG &&
                        (tagName.startsWith("locality") || tagName.startsWith("admin"))) {
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        String attrName = xpp.getAttributeName(i);
                        if ("type".equals(attrName)
                                && "Town".equals(xpp.getAttributeValue(i))) {
                            inTown = true;
                        } else if ("woeid".equals(attrName)) {
                            String woeid = xpp.getAttributeValue(i);
                            if (!TextUtils.isEmpty(woeid)) {
                                alternateWoeids.add(
                                        new Pair<String, String>(tagName, woeid));
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT && inTown) {
                    li.town = xpp.getText();
                }

                if (eventType == XmlPullParser.END_TAG) {
                    inWoe = false;
                    inTown = false;
                }

                eventType = xpp.next();
            }

            // Add the primary woeid if it was found.
            if (!TextUtils.isEmpty(primaryWoeid)) {
                li.woeids.add(primaryWoeid);
            }

            // Sort by descending tag name to order by decreasing precision
            // (locality3, locality2, locality1, admin3, admin2, admin1, etc.)
            Collections.sort(alternateWoeids, new Comparator<Pair<String, String>>() {
                @Override
                public int compare(Pair<String, String> pair1, Pair<String, String> pair2) {
                    return pair1.first.compareTo(pair2.first);
                }
            });

            for (Pair<String, String> pair : alternateWoeids) {
                li.woeids.add(pair.second);
            }

            if (li.woeids.size() > 0) {
                return li;
            }

            throw new CantGetWeatherException(true, R.string.no_weather_data,
                    "No WOEIDs found nearby.");

        } catch (IOException e) {
            throw new CantGetWeatherException(true, R.string.no_weather_data,
                    "Error parsing place search XML", e);
        } catch (XmlPullParserException e) {
            throw new CantGetWeatherException(true, R.string.no_weather_data,
                    "Error parsing place search XML", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildWeatherQueryUrl(String woeid) {
        // http://developer.yahoo.com/weather/
        return "http://weather.yahooapis.com/forecastrss?w=" + woeid + "&u=" + sWeatherUnits;
    }

    private static String buildPlaceSearchUrl(Location l) {
        // GeoPlanet API
        return "http://where.yahooapis.com/v1/places.q('"
                + l.getLatitude() + "," + l.getLongitude() + "')"
                + "?appid=kGO140TV34HVTae_DDS93fM_w3AJmtmI23gxUFnHKWyrOGcRzoFjYpw8Ato6BxhvbTg-";
    }

    private static class LocationInfo {
        // Sorted by decreasing precision
        // (point of interest, locality3, locality2, locality1, admin3, admin2, admin1, etc.)
        List<String> woeids = new ArrayList<String>();
        String town;
    }

    public static class CantGetWeatherException extends Exception {
        int mUserFacingErrorStringId;

        boolean mRetryable;

        public CantGetWeatherException(boolean retryable, int userFacingErrorStringId) {
            this(retryable, userFacingErrorStringId, null, null);
        }

        public CantGetWeatherException(boolean retryable, int userFacingErrorStringId,
                String detailMessage) {
            this(retryable, userFacingErrorStringId, detailMessage, null);
        }

        public CantGetWeatherException(boolean retryable, int userFacingErrorStringId,
                String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
            mUserFacingErrorStringId = userFacingErrorStringId;
            mRetryable = retryable;
        }

        public int getUserFacingErrorStringId() {
            return mUserFacingErrorStringId;
        }

        public boolean isRetryable() {
            return mRetryable;
        }
    }
}
