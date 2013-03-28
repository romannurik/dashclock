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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import static com.google.android.apps.dashclock.LogUtils.makeLogTag;

/**
 * Content provider for exposing log files as attachments.
 */
public class LogAttachmentProvider extends ContentProvider {
    private static final String TAG = makeLogTag(LogAttachmentProvider.class);

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
            LogUtils.LOGE(TAG, "No cache dir.");
            return null;
        }

        logFile = new File(new File(logFile, "logs"), fileName);
        if (!logFile.exists()) {
            LogUtils.LOGE(TAG, "Requested log file doesn't exist.");
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
