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

import android.app.AlarmManager;
import android.app.PendingIntent;
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
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.configuration.AppChooserPreference;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.nurik.roman.dashclock.BuildConfig;
import net.nurik.roman.dashclock.R;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;
import static com.google.android.apps.dashclock.Utils.MINUTES_MILLIS;
import static com.google.android.apps.dashclock.Utils.MILLIS_NANOS;
import static com.google.android.apps.dashclock.Utils.SECONDS_MILLIS;
import static com.google.android.apps.dashclock.weather.YahooWeatherApiClient.LocationInfo;
import static com.google.android.apps.dashclock.weather.YahooWeatherApiClient.getLocationInfo;
import static com.google.android.apps.dashclock.weather.YahooWeatherApiClient.getWeatherForLocationInfo;
import static com.google.android.apps.dashclock.weather.YahooWeatherApiClient.setWeatherUnits;
import static com.google.android.gms.location.LocationServices.FusedLocationApi;

/**
 * A local weather and forecast extension.
 */
public class WeatherExtension extends DashClockExtension {
    private static final String TAG = LogUtils.makeLogTag(WeatherExtension.class);

    public static final String ACTION_RECEIVED_LOCATION
            = "com.google.android.apps.dashclock.action.RECEIVED_LOCATION";

    public static final String PREF_WEATHER_UNITS = "pref_weather_units";
    public static final String PREF_WEATHER_SHORTCUT = "pref_weather_shortcut";
    public static final String PREF_WEATHER_LOCATION = "pref_weather_location";

    public static final Intent DEFAULT_WEATHER_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather"));

    public static final String STATE_WEATHER_LAST_BACKOFF_MILLIS
            = "state_weather_last_backoff_millis";
    public static final String STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS
            = "state_weather_last_update_elapsed_millis";

    // At least 10 min b/w updates
    private static final int UPDATE_THROTTLE_MILLIS = 10 * MINUTES_MILLIS;

    private static final long STALE_LOCATION_NANOS = 10 * MINUTES_MILLIS * MILLIS_NANOS;

    // 30 seconds for first error retry
    private static final int INITIAL_BACKOFF_MILLIS = 30 * SECONDS_MILLIS;

    // 60 sec timeout for location attempt
    private static final int LOCATION_TIMEOUT_MILLIS = 60 * SECONDS_MILLIS;

    private static final Criteria sLocationCriteria;
    private GoogleApiClient mLocationClient;
    private LocationRequest mLocationRequest;

    private static String sWeatherUnits = "f";
    private static Intent sWeatherIntent;

    private Handler mServiceThreadHandler;
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
        LOGD(TAG, "Scheduling weather retry in " + (backoffMillis / SECONDS_MILLIS) + " second(s)");
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + backoffMillis,
                WeatherRetryReceiver.getPendingIntent(this));
    }

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
    }

    @Override
    protected void onUpdateData(int reason) {
        if (mServiceThreadHandler == null) {
            // Get handle to background thread
            mServiceThreadHandler = new Handler(Looper.myLooper());
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits);
        sWeatherIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_WEATHER_SHORTCUT, null), DEFAULT_WEATHER_INTENT);

        setWeatherUnits(sWeatherUnits);

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

        String manualLocationWoeid = WeatherLocationPreference.getWoeidFromValue(
                sp.getString(PREF_WEATHER_LOCATION, null));
        if (!TextUtils.isEmpty(manualLocationWoeid)) {
            // WOEIDs
            // Honolulu = 2423945
            // Paris = 615702
            // London = 44418
            // New York = 2459115
            // San Francisco = 2487956
            LocationInfo locationInfo = new LocationInfo();
            locationInfo.woeids = Arrays.asList(manualLocationWoeid);
            tryPublishWeatherUpdateFromLocationInfo(locationInfo);
            return;
        }

        // Get the user's location, then get the weather for that location.
        tryGooglePlayServicesGetLocationAndPublishWeatherUpdate(new Runnable() {
            @Override
            public void run() {
                // If there was an error with Play Services, try LocationManager
                tryLocationManagerGetLocationAndPublishWeatherUpdate();
            }
        });
    }

    private void tryGooglePlayServicesGetLocationAndPublishWeatherUpdate(
            final Runnable errorRunnable) {
        int playServicesResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (playServicesResult != ConnectionResult.SUCCESS) {
            LOGW(TAG, "Google Play Services was unavailable (code " + playServicesResult + ").");
            if (errorRunnable != null) {
                errorRunnable.run();
            }
            return;
        }

        if (mLocationClient != null) {
            // Already trying to obtain a location. Don't call error runnable since this isn't
            // an error.
            return;
        }

        LOGD(TAG, "Getting location using Google Play Services.");
        mLocationClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        if (mServiceThreadHandler == null) {
                            LOGW(TAG, "Empty Service thread handler; Play Services unavailable.");
                            mLocationClient.disconnect();
                            mLocationClient = null;
                            if (errorRunnable != null) {
                                errorRunnable.run();
                            }
                            return;
                        }

                        mServiceThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onHasLocation();
                            }
                        });
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }

                    private void onHasLocation() {
                        Location lastLocation = FusedLocationApi.getLastLocation(mLocationClient);
                        if (lastLocation == null || (SystemClock.elapsedRealtimeNanos()
                                - lastLocation.getElapsedRealtimeNanos()) >= STALE_LOCATION_NANOS) {
                            LOGW(TAG, "Stale or missing last-known location; requesting " +
                                    "single location update.");

                            Intent intent = new Intent(
                                    WeatherExtension.this, WeatherExtension.class);
                            intent.setAction(ACTION_RECEIVED_LOCATION);
                            PendingIntent locationPendingIntent = PendingIntent.getService(
                                    WeatherExtension.this, 0, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT);
                            FusedLocationApi.requestLocationUpdates(mLocationClient,
                                    mLocationRequest, locationPendingIntent);

                            // Schedule a retry if timing out. When the location request expires,
                            // updates will simply stop, and we won't get any notification of this,
                            // so handle it separately.
                            mTimeoutHandler.removeCallbacksAndMessages(null);
                            mTimeoutHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    LOGE(TAG, "Play Services location request timed out.");
                                    disableOneTimeLocationListener();
                                    scheduleRetry();
                                }
                            }, LOCATION_TIMEOUT_MILLIS);
                        } else {
                            tryPublishWeatherUpdateFromGeolocation(lastLocation);
                        }

                        mLocationClient.disconnect();
                        mLocationClient = null;
                    }
                })
                .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        mLocationClient = null;
                        if (errorRunnable != null) {
                            errorRunnable.run();
                        }
                    }
                })
                .build();

        // Create a location request
        mLocationRequest = LocationRequest.create()
                .setExpirationDuration(LOCATION_TIMEOUT_MILLIS - 1000)
                .setFastestInterval(0)
                .setInterval(0)
                .setNumUpdates(1)
                .setSmallestDisplacement(0)
                .setPriority(LocationRequest.PRIORITY_LOW_POWER);

        // Connect to the location api
        mLocationClient.connect();
    }

    private void tryLocationManagerGetLocationAndPublishWeatherUpdate() {
        LOGD(TAG, "Getting location using LocationManager");
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
                    LOGE(TAG, "LocationManager location request timed out.");
                    disableOneTimeLocationListener();
                    scheduleRetry();
                }
            }, LOCATION_TIMEOUT_MILLIS);
        } else {
            tryPublishWeatherUpdateFromGeolocation(lastLocation);
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
            tryPublishWeatherUpdateFromGeolocation(location);
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RECEIVED_LOCATION.equals(intent.getAction())) {
            final Location location = intent.getParcelableExtra(
                    LocationManager.KEY_LOCATION_CHANGED);
            if (mServiceThreadHandler == null) {
                LOGW(TAG, "Can't process location update because onUpdateData hasn't been called "
                        + "on this service instance.");
            } else if (location != null) {
                // A location update request succeeded; try publishing weather from here.
                LOGD(TAG, "Got a Play Services location update; trying weather update.");
                mTimeoutHandler.removeCallbacksAndMessages(null);
                mServiceThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tryPublishWeatherUpdateFromGeolocation(location);
                    }
                });
            }

            stopSelf(startId);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocationClient != null) {
            mLocationClient.disconnect();
        }
        mLocationClient = null;
        disableOneTimeLocationListener();
    }

    private void tryPublishWeatherUpdateFromGeolocation(Location location) {
        try {
            LOGD(TAG, "Using location: " + location.getLatitude() + "," + location.getLongitude());
            tryPublishWeatherUpdateFromLocationInfo(getLocationInfo(location));
        } catch (CantGetWeatherException e) {
            publishErrorUpdate(e);
            if (e.isRetryable()) {
                scheduleRetry();
            }
        }
    }

    private void tryPublishWeatherUpdateFromLocationInfo(LocationInfo locationInfo) {
        try {
            publishWeatherUpdate(getWeatherForLocationInfo(locationInfo));
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

        if (BuildConfig.DEBUG) {
            expandedBody.append("\n")
                    .append(SimpleDateFormat.getDateTimeInstance().format(new Date()));
        }

        publishUpdate(new ExtensionData()
                .visible(true)
                .clickIntent(sWeatherIntent)
                .status(temperature)
                .expandedTitle(getString(R.string.weather_expanded_title_template,
                        temperature + sWeatherUnits.toUpperCase(Locale.US),
                        weatherData.conditionText))
                .icon(conditionIconId)
                .expandedBody(expandedBody.toString()));

        // Mark that a successful weather update has been pushed
        resetAndCancelRetries();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putLong(STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
                SystemClock.elapsedRealtime()).commit();
    }
}
