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

import net.nurik.roman.dashclock.BuildConfig;
import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Helper methods that make logging more consistent throughout the app.
 */
public class LogUtils {
    private static final String TAG = makeLogTag(LogUtils.class);

    private static final String LOG_PREFIX = "dashclock_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;

    private LogUtils() {
    }

    public static String makeLogTag(String str) {
        if (str.length() > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1);
        }

        return LOG_PREFIX + str;
    }

    /**
     * WARNING: Don't use this when obfuscating class names with Proguard!
     */
    public static String makeLogTag(Class cls) {
        return makeLogTag(cls.getSimpleName());
    }

    public static void LOGD(final String tag, String message) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public static void LOGD(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message, cause);
        }
    }

    public static void LOGV(final String tag, String message) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message);
        }
    }

    public static void LOGV(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message, cause);
        }
    }

    public static void LOGI(final String tag, String message) {
        Log.i(tag, message);
    }

    public static void LOGI(final String tag, String message, Throwable cause) {
        Log.i(tag, message, cause);
    }

    public static void LOGW(final String tag, String message) {
        Log.w(tag, message);
    }

    public static void LOGW(final String tag, String message, Throwable cause) {
        Log.w(tag, message, cause);
    }

    public static void LOGE(final String tag, String message) {
        Log.e(tag, message);
    }

    public static void LOGE(final String tag, String message, Throwable cause) {
        Log.e(tag, message, cause);
    }

    /**
     * Only for use with debug versions of the app!
     */
    public static void sendDebugLog(Context context) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG) {
            StringBuilder log = new StringBuilder();

            // Append app version name
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            String versionName;
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                versionName = info.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                versionName = "??";
            }
            log.append("App version:\n").append(versionName).append("\n\n");

            // Append device build fingerprint
            log.append("Device fingerprint:\n").append(Build.FINGERPRINT).append("\n\n");

            try {
                // Append app's logs
                String[] logcatCmd = new String[]{ "logcat", "-v", "threadtime", "-d", };
                Process process = Runtime.getRuntime().exec(logcatCmd);
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    log.append(line);
                    log.append("\n");
                }

                // Write everything to a file
                File logsDir = context.getCacheDir();
                if (logsDir == null) {
                    throw new IOException("Cache directory inaccessible");
                }
                logsDir = new File(logsDir, "logs");
                deleteRecursive(logsDir);
                logsDir.mkdirs();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String fileName = "DashClock_log_" + sdf.format(new Date()) + ".txt";
                File logFile = new File(logsDir, fileName);

                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(logFile)));
                writer.write(log.toString());
                writer.close();

                // Send the file
                Intent sendIntent = new Intent(Intent.ACTION_SENDTO)
                        .setData(Uri.parse("mailto:dashclock+support@gmail.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, "DashClock debug log")
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(
                                "content://" + LogAttachmentProvider.AUTHORITY + "/" + fileName));
                context.startActivity(Intent.createChooser(sendIntent,
                        context.getString(R.string.send_logs_chooser_title)));

            } catch (IOException e) {
                LOGE(TAG, "Error accessing or sending app's logs.", e);
                Toast.makeText(context, "Error accessing or sending app's logs.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void deleteRecursive(File file) {
        if (file != null) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            } else {
                file.delete();
            }
        }
    }
}
