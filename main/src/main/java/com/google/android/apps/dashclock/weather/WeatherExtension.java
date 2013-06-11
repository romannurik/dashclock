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
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.configuration.AppChooserPreference;

import net.nurik.roman.dashclock.R;

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
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;

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
    public static final String PREF_WEATHER_LOCATION = "pref_weather_location";

    public static final Intent DEFAULT_WEATHER_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather"));

    public static final String STATE_WEATHER_LAST_BACKOFF_MILLIS
            = "state_weather_last_backoff_millis";
    public static final String STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS
            = "state_weather_last_update_elapsed_millis";

    private static final int UPDATE_THROTTLE_MILLIS = 10 * 3600000; // At least 10 min b/w updates

    private static final long STALE_LOCATION_NANOS = 10l * 60000000000l; // 10 minutes

    private static final int INITIAL_BACKOFF_MILLIS = 30000; // 30 seconds for first error retry

    private static final int LOCATION_TIMEOUT_MILLIS = 60000; // 60 sec timeout for location attempt

    private static final Criteria sLocationCriteria;

    private static String sWeatherUnits = "f";
    private static Intent sWeatherIntent;
    private static String sManualLocationWoeid = null;

    private boolean mOneTimeLocationListenerActive = false;

    private Handler mTimeoutHandler = new Handler();

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
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
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits);
        sWeatherIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_WEATHER_SHORTCUT, null), DEFAULT_WEATHER_INTENT);
        sManualLocationWoeid = WeatherLocationPreference.getWoeidFromValue(
                sp.getString(PREF_WEATHER_LOCATION, null));

        long lastUpdateElapsedMillis = sp.getLong(STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
                -UPDATE_THROTTLE_MILLIS);
        long nowElapsedMillis = SystemClock.elapsedRealtime();
        if (reason != UPDATE_REASON_INITIAL && reason != UPDATE_REASON_MANUAL &&
                nowElapsedMillis < lastUpdateElapsedMillis + UPDATE_THROTTLE_MILLIS) {
            LOGD(TAG, "Throttling weather update attempt.");
            return;
        }

        LOGD(TAG, "Attempting weather update; reason=" + reason);

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

            // Time-out single location update request
            mTimeoutHandler.removeCallbacksAndMessages(null);
            mTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    LOGE(TAG, "Location request timed out.");
                    disableOneTimeLocationListener();
                    scheduleRetry();
                }
            }, LOCATION_TIMEOUT_MILLIS);
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
            mTimeoutHandler.removeCallbacksAndMessages(null);
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
            YahooWeatherApiClient.setWeatherUnits(sWeatherUnits);
            WeatherData weatherData;
            if (TextUtils.isEmpty(sManualLocationWoeid)) {
                weatherData = YahooWeatherApiClient.getWeatherForLocation(location);
            } else {
                weatherData = YahooWeatherApiClient.getWeatherForWoeid(sManualLocationWoeid, null);
            }
            publishWeatherUpdate(weatherData);
            resetAndCancelRetries();

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            sp.edit().putLong(STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
                    SystemClock.elapsedRealtime()).commit();
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
        String temperature = (weatherData.temperature != WeatherData.INVALID_TEMPERATURE)
                ? getString(R.string.temperature_template, weatherData.temperature)
                : getString(R.string.status_none);
        StringBuilder expandedBody = new StringBuilder();

        if (weatherData.low != WeatherData.INVALID_TEMPERATURE
                && weatherData.high != WeatherData.INVALID_TEMPERATURE) {
            expandedBody.append(getString(R.string.weather_low_high_template,
                    getString(R.string.temperature_template, weatherData.low),
                    getString(R.string.temperature_template, weatherData.high)));
        }

        int conditionIconId = WeatherData.getConditionIconId(weatherData.conditionCode);
        if (WeatherData.getConditionIconId(weatherData.todayForecastConditionCode)
                == R.drawable.ic_weather_raining) {
            // Show rain if it will rain today.
            conditionIconId = R.drawable.ic_weather_raining;

            if (expandedBody.length() > 0) {
                expandedBody.append(", ");
            }
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
}
