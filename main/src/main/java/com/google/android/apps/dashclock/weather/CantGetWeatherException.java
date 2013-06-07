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

/**
* Generic exception indicating that for some reason, weather information was not obtainable.
*/
public class CantGetWeatherException extends Exception {
    private int mUserFacingErrorStringId;

    private boolean mRetryable;

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
