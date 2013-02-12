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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.URISyntaxException;

import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * A basic proxy activity for handling widget clicks.
 */
public class WidgetClickProxyActivity extends Activity {
    private static final String TAG = LogUtils.makeLogTag(WidgetClickProxyActivity.class);

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            try {
                startActivity(
                        Intent.parseUri(getIntent().getData().toString(), Intent.URI_INTENT_SCHEME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        | Intent.FLAG_ACTIVITY_FORWARD_RESULT));
            } catch (URISyntaxException e) {
                LOGE(TAG, "Error parsing URI.", e);
            } catch (ActivityNotFoundException e) {
                LOGE(TAG, "Proxy'd activity not found.", e);
            }
            finish();
        }
        super.onWindowFocusChanged(hasFocus);
    }

    public static Intent wrap(Context context, Intent intent) {
        return new Intent(context, WidgetClickProxyActivity.class)
                .setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    public static Intent getTemplate(Context context) {
        return new Intent(context, WidgetClickProxyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    public static Intent getFillIntent(Intent clickIntent) {
        return new Intent().setData(Uri.parse(clickIntent.toUri(Intent.URI_INTENT_SCHEME)));
    }
}
