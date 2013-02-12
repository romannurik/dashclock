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

import net.nurik.roman.dashclock.BuildConfig;
import net.nurik.roman.dashclock.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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

    private static final long STALE_LOCATION_NANOS = 10l * 60000000000l; // 10 minutes

    private static XmlPullParserFactory sXmlPullParserFactory;

    private static final Criteria sLocationCriteria;

    private static String sWeatherUnits = "f";

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

    @Override
    protected void onUpdateData(int reason) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits);

        NetworkInfo ni = ((ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String provider = lm.getBestProvider(sLocationCriteria, true);
        if (TextUtils.isEmpty(provider)) {
            LOGE(TAG, "No available location providers matching criteria.");
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
            getWeatherAndTryPublishUpdate(lastLocation);
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
            getWeatherAndTryPublishUpdate(location);
            disableOneTimeLocationListener();
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        disableOneTimeLocationListener();
    }

    private void getWeatherAndTryPublishUpdate(Location location) {
        try {
            WeatherData weatherData = getWeatherForLocation(location);
            publishUpdate(renderExtensionData(weatherData));
        } catch (InvalidLocationException e) {
            LOGW(TAG, "Could not determine a valid location for weather.", e);
        } catch (IOException e) {
            LOGW(TAG, "Generic read error while retrieving weather information.", e);
        }
    }

    private ExtensionData renderExtensionData(WeatherData weatherData) {
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

        return new ExtensionData()
                .visible(true)
                .status(temperature)
                .expandedTitle(getString(R.string.weather_expanded_title_template,
                        temperature + sWeatherUnits.toUpperCase(Locale.US),
                        weatherData.conditionText))
                .icon(conditionIconId)
                .expandedBody(expandedBody.toString())
                .clickIntent(new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse("https://www.google.com/search?q=weather")));
    }

    private static WeatherData getWeatherForLocation(Location location)
            throws InvalidLocationException, IOException {

        if (BuildConfig.DEBUG) {
            LOGD(TAG, "Using location: " + location.getLatitude()
                    + "," + location.getLongitude());
        }

        // Honolulu = 2423945
        // Paris = 615702
        // London = 44418
        // New York = 2459115
        // San Francisco = 2487956
        LocationInfo locationInfo = getLocationInfo(location);

        LOGD(TAG, "Using WOEID: " + locationInfo.woeid);

        return getWeatherDataForLocation(locationInfo);
    }

    private static WeatherData getWeatherDataForLocation(LocationInfo li) throws IOException {
        HttpURLConnection connection = Utils.openUrlConnection(buildWeatherQueryUrl(li.woeid));

        try {
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
                    String region = "--";
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        if ("city".equals(xpp.getAttributeName(i))) {
                            cityOrVillage = xpp.getAttributeValue(i);
                        } else if ("region".equals(xpp.getAttributeName(i))) {
                            region = xpp.getAttributeValue(i);
                        }
                    }
                    if (!TextUtils.isEmpty(li.town) && !li.town.equals(cityOrVillage)) {
                        data.location = cityOrVillage + ", " + li.town + ", " + region;
                    } else {
                        data.location = cityOrVillage + ", " + region;
                    }
                }
                eventType = xpp.next();
            }

            return data;

        } catch (XmlPullParserException e) {
            throw new IOException("Error parsing weather feed XML.", e);
        } finally {
            connection.disconnect();
        }
    }

    private static LocationInfo getLocationInfo(Location location)
            throws IOException, InvalidLocationException {
        LocationInfo li = new LocationInfo();
        HttpURLConnection connection = Utils.openUrlConnection(buildPlaceSearchUrl(location));
        try {
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();
            xpp.setInput(new InputStreamReader(connection.getInputStream()));

            boolean inWoe = false;
            boolean inTown = false;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "woeid".equals(xpp.getName())) {
                    inWoe = true;
                } else if (eventType == XmlPullParser.TEXT && inWoe) {
                    li.woeid = xpp.getText();
                }

                if (eventType == XmlPullParser.START_TAG && xpp.getName().startsWith("locality")) {
                    for (int i = xpp.getAttributeCount() - 1; i >= 0; i--) {
                        if ("type".equals(xpp.getAttributeName(i))
                                && "Town".equals(xpp.getAttributeValue(i))) {
                            inTown = true;
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

            if (!TextUtils.isEmpty(li.woeid)) {
                return li;
            }

            throw new InvalidLocationException();

        } catch (XmlPullParserException e) {
            throw new IOException("Error parsing location XML response.", e);
        } finally {
            connection.disconnect();
        }
    }

    private static String buildWeatherQueryUrl(String woeid) throws MalformedURLException {
        // http://developer.yahoo.com/weather/
        return "http://weather.yahooapis.com/forecastrss?w=" + woeid + "&u=" + sWeatherUnits;
    }

    private static String buildPlaceSearchUrl(Location l) throws MalformedURLException {
        // GeoPlanet API
        return "http://where.yahooapis.com/v1/places.q('"
                + l.getLatitude() + "," + l.getLongitude() + "')"
                + "?appid=kGO140TV34HVTae_DDS93fM_w3AJmtmI23gxUFnHKWyrOGcRzoFjYpw8Ato6BxhvbTg-";
    }

    private static class LocationInfo {
        String woeid;
        String town;
    }

    public static class InvalidLocationException extends Exception {
        public InvalidLocationException() {
        }

        public InvalidLocationException(String detailMessage) {
            super(detailMessage);
        }

        public InvalidLocationException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public InvalidLocationException(Throwable throwable) {
            super(throwable);
        }
    }
}
