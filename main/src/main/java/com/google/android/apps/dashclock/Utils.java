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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.phone.MissedCallsExtension;
import com.google.android.apps.dashclock.phone.SmsExtension;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * Because every project needs a Utils class.
 */
public class Utils {
    private static final String TAG = LogUtils.makeLogTag(Utils.class);

    private static final String USER_AGENT = "DashClock/0.0";

    public static final int EXTENSION_ICON_SIZE = 128;

    private static final String[] CLOCK_PACKAGES = new String[] {
            "com.google.android.deskclock",
            "com.android.deskclock",
    };

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

    public static Bitmap recolorBitmap(BitmapDrawable drawable, int color) {
        if (drawable == null) {
            return null;
        }

        Bitmap outBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outBitmap);
        drawable.setBounds(0, 0, outBitmap.getWidth(), outBitmap.getHeight());
        drawable.setColorFilter(color,
                PorterDuff.Mode.SRC_IN);
        drawable.draw(canvas);
        drawable.setColorFilter(null);
        drawable.setCallback(null); // free up any references
        return outBitmap;
    }

    public static Intent getDefaultClockIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0);
                return pm.getLaunchIntentForPackage(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Intent getDefaultAlarmsIntent(Context context) {
        // TODO: consider using AlarmClock.ACTION_SET_ALARM, although it requires a permission
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                ComponentName cn = new ComponentName(packageName,
                        "com.android.deskclock.AlarmClock");
                pm.getActivityInfo(cn, 0);
                return Intent.makeMainActivity(cn);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return getDefaultClockIntent(context);
    }

    public static Bitmap loadExtensionIcon(Context context, ComponentName extension,
            int icon, Uri iconUri, int color) {
        if (iconUri != null) {
            return loadExtensionIconFromUri(context, iconUri);
        }

        if (icon <= 0) {
            return null;
        }

        String packageName = extension.getPackageName();
        try {
            Context packageContext = context.createPackageContext(packageName, 0);
            Resources packageRes = packageContext.getResources();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(packageRes, icon, options);

            // Cut down the icon to a smaller size.
            int sampleSize = 1;
            while (true) {
                if (options.outHeight / (sampleSize * 2) > Utils.EXTENSION_ICON_SIZE / 2) {
                    sampleSize *= 2;
                } else {
                    break;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            return Utils.flattenExtensionIcon(
                    context,
                    BitmapFactory.decodeResource(packageRes, icon, options),
                    color);

        } catch (PackageManager.NameNotFoundException e) {
            LOGE(TAG, "Couldn't access extension's package while loading icon data.");
        }

        return null;
    }

    public static Bitmap loadExtensionIconFromUri(Context context, Uri iconUri) {
        try {
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(iconUri, "r");

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);

            // Cut down the icon to a smaller size.
            int sampleSize = 1;
            while (true) {
                if (options.outHeight / (sampleSize * 2) > Utils.EXTENSION_ICON_SIZE / 2) {
                    sampleSize *= 2;
                } else {
                    break;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            return Utils.flattenExtensionIcon(
                    context,
                    BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options),
                    0xffffffff);

        } catch (IOException e) {
            LOGE(TAG, "Couldn't read icon from content URI.", e);
        } catch (SecurityException e) {
            LOGE(TAG, "Couldn't read icon from content URI.", e);
        }

        return null;
    }

    public static String expandedTitleOrStatus(ExtensionData data) {
        String expandedTitle = data.expandedTitle();
        if (TextUtils.isEmpty(expandedTitle)) {
            expandedTitle = data.status();
            if (!TextUtils.isEmpty(expandedTitle)) {
                expandedTitle = expandedTitle.replace("\n", " ");
            }
        }
        return expandedTitle;
    }

    public static void traverseAndRecolor(View root, int color, boolean withStates) {
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                traverseAndRecolor(parent.getChildAt(i), color, withStates);
            }

        } else if (root instanceof ImageView) {
            ImageView imageView = (ImageView) root;
            Drawable sourceDrawable = imageView.getDrawable();
            if (withStates && sourceDrawable != null && sourceDrawable instanceof BitmapDrawable) {
                BitmapDrawable sourceBitmapDrawable = (BitmapDrawable) sourceDrawable;

                Bitmap newBitmap = Bitmap.createBitmap(sourceBitmapDrawable.getBitmap());
                Canvas newCanvas = new Canvas(newBitmap);
                Paint paint = new Paint();
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
                paint.setColor(color);
                newCanvas.drawRect(0, 0, newBitmap.getWidth(), newBitmap.getHeight(), paint);
                BitmapDrawable recoloredDrawable = new BitmapDrawable(
                        root.getContext().getResources(), newBitmap);

                StateListDrawable stateDrawable = new StateListDrawable();
                stateDrawable.addState(new int[]{android.R.attr.state_pressed},
                        sourceDrawable);
                stateDrawable.addState(new int[]{android.R.attr.state_focused},
                        sourceDrawable);
                stateDrawable.addState(new int[]{}, recoloredDrawable);
                imageView.setImageDrawable(stateDrawable);
            } else {
                imageView.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }

        } else if (root instanceof TextView) {
            TextView textView = (TextView) root;
            if (withStates) {
                int sourceColor = textView.getCurrentTextColor();
                ColorStateList colorStateList = new ColorStateList(new int[][]{
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                }, new int[]{
                        sourceColor,
                        sourceColor,
                        color
                });
                textView.setTextColor(colorStateList);
            } else {
                textView.setTextColor(color);
            }
        }
    }

    private static Class[] sPhoneOnlyExtensions = {
            SmsExtension.class,
            MissedCallsExtension.class,
    };

    public static void enableDisablePhoneOnlyExtensions(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        for (Class ext : sPhoneOnlyExtensions) {
            pm.setComponentEnabledSetting(new ComponentName(context, ext), hasTelephony
                    ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
