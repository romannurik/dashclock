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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Helper methods that make logging more consistent throughout the app.
 */
public class LogUtils {
    private static final String TAG = makeLogTag(LogUtils.class);

    private static final String LOG_PREFIX = "dashclock_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;

    public static final boolean FORCE_DEBUG = false;

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
        if (FORCE_DEBUG || BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public static void LOGD(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (FORCE_DEBUG || BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message, cause);
        }
    }

    public static void LOGV(final String tag, String message) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if ((FORCE_DEBUG || BuildConfig.DEBUG) && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message);
        }
    }

    public static void LOGV(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if ((FORCE_DEBUG || BuildConfig.DEBUG) && Log.isLoggable(tag, Log.VERBOSE)) {
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
    public static void sendLogs(Context context) {
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
            context.startActivity(Intent.createChooser(sendIntent, "Send logs using"));

        } catch (IOException e) {
            LOGE(TAG, "Error accessing or sending app's logs.", e);
            Toast.makeText(context, "Error accessing or sending app's logs.", Toast.LENGTH_SHORT)
                    .show();
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

    /**
     * Content provider for exposing log files as attachments.
     */
    public static class LogAttachmentProvider extends ContentProvider {
        static final String AUTHORITY = "com.google.android.apps.dashclock.logs";

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String orderBy) {
            // Ensure logfile exists
            List<String> pathSegments = uri.getPathSegments();
            String fileName = pathSegments.get(0);
            File logFile = getContext().getCacheDir();
            if (logFile == null) {
                LOGE(TAG, "No cache dir.");
                return null;
            }

            logFile = new File(new File(logFile, "logs"), fileName);
            if (!logFile.exists()) {
                LOGE(TAG, "Requested log file doesn't exist.");
                return null;
            }

            // Create matrix cursor
            if (projection == null) {
                projection = new String[]{
                        "_data",
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE,
                };
            }

            MatrixCursor matrixCursor = new MatrixCursor(projection, 1);
            Object[] row = new Object[projection.length];
            for (int col = 0; col < projection.length; col++) {
                if ("_data".equals(projection[col])) {
                    row[col] = logFile.getAbsolutePath();
                } else if (OpenableColumns.DISPLAY_NAME.equals(projection[col])) {
                    row[col] = fileName;
                } else if (OpenableColumns.SIZE.equals(projection[col])) {
                    row[col] = logFile.length();
                }
            }
            matrixCursor.addRow(row);
            return matrixCursor;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            return openFileHelper(uri, "r");
        }

        @Override
        public String getType(Uri uri) {
            return "text/plain";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException("insert not supported");
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException("delete not supported");
        }

        @Override
        public int update(Uri uri, ContentValues contentValues, String selection,
                String[] selectionArgs) {
            throw new UnsupportedOperationException("update not supported");
        }
    }
}
