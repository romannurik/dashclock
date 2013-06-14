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

import android.location.Location;
import android.text.TextUtils;
import android.util.Pair;

import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.Utils;

import net.nurik.roman.dashclock.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * Client code for the Yahoo! Weather RSS feeds and GeoPlanet API.
 */
class YahooWeatherApiClient {
    private static final String TAG = LogUtils.makeLogTag(YahooWeatherApiClient.class);

    private static String sWeatherUnits;

    private static XmlPullParserFactory sXmlPullParserFactory;

    private static final int MAX_SEARCH_RESULTS = 10;

    static {
        try {
            sXmlPullParserFactory = XmlPullParserFactory.newInstance();
            sXmlPullParserFactory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            LOGE(TAG, "Could not instantiate XmlPullParserFactory", e);
        }
    }

    public static void setWeatherUnits(String weatherUnits) {
        sWeatherUnits = weatherUnits;
    }

    public static WeatherData getWeatherForLocationInfo(LocationInfo locationInfo)
            throws CantGetWeatherException {
        // Loop through the woeids (they're in descending precision order) until weather data
        // is found.
        for (String woeid : locationInfo.woeids) {
            LOGD(TAG, "Trying WOEID: " + woeid);
            WeatherData data = YahooWeatherApiClient.getWeatherForWoeid(woeid, locationInfo.town);
            if (data != null
                    && data.conditionCode != WeatherData.INVALID_CONDITION
                    && data.temperature != WeatherData.INVALID_TEMPERATURE) {
                return data;
            }
        }

        // No weather could be found :(
        throw new CantGetWeatherException(true, R.string.no_weather_data);
    }

    public static WeatherData getWeatherForWoeid(String woeid, String town)
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
                        } else if ("low".equals(xpp.getAttributeName(i))) {
                            data.low = Integer.parseInt(xpp.getAttributeValue(i));
                        } else if ("high".equals(xpp.getAttributeName(i))) {
                            data.high = Integer.parseInt(xpp.getAttributeValue(i));
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
        } catch (NumberFormatException e) {
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

    public static LocationInfo getLocationInfo(Location location) throws CantGetWeatherException {
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

    private static final int PARSE_STATE_NONE = 0;
    private static final int PARSE_STATE_PLACE = 1;
    private static final int PARSE_STATE_WOEID = 2;
    private static final int PARSE_STATE_NAME = 3;
    private static final int PARSE_STATE_COUNTRY = 4;
    private static final int PARSE_STATE_ADMIN1 = 5;

    public static List<LocationSearchResult> findLocationsAutocomplete(String startsWith) {
        LOGD(TAG, "Autocompleting locations starting with '" + startsWith + "'");

        List<LocationSearchResult> results = new ArrayList<LocationSearchResult>();

        HttpURLConnection connection = null;
        try {
            connection = Utils.openUrlConnection(buildPlaceSearchStartsWithUrl(startsWith));
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();
            xpp.setInput(new InputStreamReader(connection.getInputStream()));

            LocationSearchResult result = null;
            String name = null, country = null, admin1 = null;
            StringBuilder sb = new StringBuilder();

            int state = PARSE_STATE_NONE;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = xpp.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    switch (state) {
                        case PARSE_STATE_NONE:
                            if ("place".equals(tagName)) {
                                state = PARSE_STATE_PLACE;
                                result = new LocationSearchResult();
                                name = country = admin1 = null;
                            }
                            break;

                        case PARSE_STATE_PLACE:
                            if ("name".equals(tagName)) {
                                state = PARSE_STATE_NAME;
                            } else if ("woeid".equals(tagName)) {
                                state = PARSE_STATE_WOEID;
                            } else if ("country".equals(tagName)) {
                                state = PARSE_STATE_COUNTRY;
                            } else if ("admin1".equals(tagName)) {
                                state = PARSE_STATE_ADMIN1;
                            }
                            break;
                    }

                } else if (eventType == XmlPullParser.TEXT) {
                    switch (state) {
                        case PARSE_STATE_WOEID:
                            result.woeid = xpp.getText();
                            break;

                        case PARSE_STATE_NAME:
                            name = xpp.getText();
                            break;

                        case PARSE_STATE_COUNTRY:
                            country = xpp.getText();
                            break;

                        case PARSE_STATE_ADMIN1:
                            admin1 = xpp.getText();
                            break;
                    }

                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("place".equals(tagName)) {
//                        // Sort by descending tag name to order by decreasing precision
//                        // (locality3, locality2, locality1, admin3, admin2, admin1, etc.)
//                        Collections.sort(alternateWoeids, new Comparator<Pair<String, String>>() {
//                            @Override
//                            public int compare(Pair<String, String> pair1,
//                                    Pair<String, String> pair2) {
//                                return pair1.first.compareTo(pair2.first);
//                            }
//                        });
                        sb.setLength(0);
                        if (!TextUtils.isEmpty(name)) {
                            sb.append(name);
                        }
                        if (!TextUtils.isEmpty(admin1)) {
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append(admin1);
                        }
                        result.displayName = sb.toString();
                        result.country = country;
                        results.add(result);
                        state = PARSE_STATE_NONE;

                    } else if (state != PARSE_STATE_NONE) {
                        state = PARSE_STATE_PLACE;
                    }
                }

                eventType = xpp.next();
            }

        } catch (IOException e) {
            LOGW(TAG, "Error parsing place search XML");
        } catch (XmlPullParserException e) {
            LOGW(TAG, "Error parsing place search XML");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return results;
    }

    private static String buildWeatherQueryUrl(String woeid) {
        // http://developer.yahoo.com/weather/
        return "http://weather.yahooapis.com/forecastrss?w=" + woeid + "&u=" + sWeatherUnits;
    }

    private static String buildPlaceSearchUrl(Location l) {
        // GeoPlanet API
        return "http://where.yahooapis.com/v1/places.q('"
                + l.getLatitude() + "," + l.getLongitude() + "')"
                + "?appid=" + YahooWeatherApiConfig.APP_ID;
    }

    private static String buildPlaceSearchStartsWithUrl(String startsWith) {
        // GeoPlanet API
        startsWith = startsWith.replaceAll("[^\\w ]+", "").replaceAll(" ", "%20");
        return "http://where.yahooapis.com/v1/places.q('" + startsWith + "%2A');"
                + "count=" + MAX_SEARCH_RESULTS
                + "?appid=" + YahooWeatherApiConfig.APP_ID;
    }

    public static class LocationInfo {
        // Sorted by decreasing precision
        // (point of interest, locality3, locality2, locality1, admin3, admin2, admin1, etc.)
        List<String> woeids = new ArrayList<String>();
        String town;
    }

    public static class LocationSearchResult {
        String woeid;
        String displayName;
        String country;
    }
}
