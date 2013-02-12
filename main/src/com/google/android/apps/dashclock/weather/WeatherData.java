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

import net.nurik.roman.dashclock.R;

/**
 * A helper class representing weather data, for use with {@link WeatherExtension}.
 */
public class WeatherData {
    public static final int INVALID_TEMPERATURE = Integer.MIN_VALUE;
    public static final int INVALID_CONDITION = -1;

    public int temperature = INVALID_TEMPERATURE;
    public int conditionCode = INVALID_CONDITION;
    public int todayForecastConditionCode = INVALID_CONDITION;
    public String conditionText;
    public String forecastText;
    public String location;

    public WeatherData() {
    }

    public boolean hasValidTemperature() {
        return temperature > Integer.MIN_VALUE;
    }

    public static int getConditionIconId(int conditionCode) {
        // http://developer.yahoo.com/weather/
        switch (conditionCode) {
            case 20: // foggy
            case 21: // haze
            case 22: // smoky
                return R.drawable.ic_weather_foggy;
            case 23: // blustery
            case 24: // windy
                return R.drawable.ic_weather_windy;
            case 25: // cold
            case 26: // cloudy
            case 27: // mostly cloudy (night)
            case 28: // mostly cloudy (day)
                return R.drawable.ic_weather_cloudy;
            case 29: // partly cloudy (night)
            case 30: // partly cloudy (day)
            case 44: // partly cloudy
                return R.drawable.ic_weather_partly_cloudy;
            case 31: // clear (night)
            case 33: // fair (night)
            case 34: // fair (day)
                return R.drawable.ic_weather_clear;
            case 32: // sunny
            case 36: // hot
                return R.drawable.ic_weather_sunny;
            case 0: // tornado
            case 1: // tropical storm
            case 2: // hurricane
            case 3: // severe thunderstorms
            case 4: // thunderstorms
            case 5: // mixed rain and snow
            case 6: // mixed rain and sleet
            case 7: // mixed snow and sleet
            case 8: // freezing drizzle
            case 9: // drizzle
            case 10: // freezing rain
            case 11: // showers
            case 12: // showers
            case 17: // hail
            case 18: // sleet
            case 19: // dust
            case 35: // mixed rain and hail
            case 37: // isolated thunderstorms
            case 38: // scattered thunderstorms
            case 39: // scattered thunderstorms
            case 40: // scattered showers
            case 45: // thundershowers
            case 47: // isolated thundershowers
                return R.drawable.ic_weather_raining;
            case 13: // snow flurries
            case 14: // light snow showers
            case 15: // blowing snow
            case 16: // snow
            case 41: // heavy snow
            case 42: // scattered snow showers
            case 43: // heavy snow
            case 46: // snow showers
                return R.drawable.ic_weather_snow;
        }

        return R.drawable.ic_weather_clear;
    }
}
