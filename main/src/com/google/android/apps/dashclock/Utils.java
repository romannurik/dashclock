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

package com.google.android.apps.dashclock;

import android.app.backup.BackupManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Because every project needs a Utils class.
 */
public class Utils {
    private static final String USER_AGENT = "DashClock/0.0";

    public static final int EXTENSION_ICON_SIZE = 128;

    // TODO: Let's use a *real* HTTP library, eh?
    public static HttpURLConnection openUrlConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setUseCaches(false);
        conn.setChunkedStreamingMode(0);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.connect();
        return conn;
    }

    public static Bitmap flattenExtensionIcon(Drawable baseIcon, int color) {
        if (baseIcon == null) {
            return null;
        }

        Bitmap outBitmap = Bitmap.createBitmap(EXTENSION_ICON_SIZE, EXTENSION_ICON_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outBitmap);
        baseIcon.setBounds(0, 0, EXTENSION_ICON_SIZE, EXTENSION_ICON_SIZE);
        baseIcon.setColorFilter(color,
                PorterDuff.Mode.SRC_IN);
        baseIcon.draw(canvas);
        baseIcon.setColorFilter(null);
        baseIcon.setCallback(null); // free up any references
        return outBitmap;
    }

    public static Bitmap flattenExtensionIcon(Context context, Bitmap baseIcon, int color) {
        return flattenExtensionIcon(new BitmapDrawable(context.getResources(), baseIcon), color);
    }
}
