/*
 * Copyright 2014 Google Inc.
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
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.apps.dashclock.api.DashClockExtension;

/**
 * A proxy activity to launch the settings activity for an extension.
 */
public class ExtensionSettingActivityProxy extends Activity {

    public static final String EXTRA_SETTINGS_ACTIVITY = "settings_activity";

    private static final int RESULT_EXTENSION_SETTINGS = 0;

    private String mExtension;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);

        mExtension = getIntent().getStringExtra(DashClockService.EXTRA_COMPONENT_NAME);
        String activity = getIntent().getStringExtra(EXTRA_SETTINGS_ACTIVITY);
        if (mExtension == null || activity == null) {
            finish();
            return;
        }

        try {
            Intent i = new Intent();
            i.setComponent(ComponentName.unflattenFromString(activity));
            startActivityForResult(i, RESULT_EXTENSION_SETTINGS);
        } catch (ActivityNotFoundException ex) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_EXTENSION_SETTINGS) {
            final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
            Intent intent = new Intent(DashClockService.ACTION_EXTENSION_UPDATE_REQUESTED);
            intent.putExtra(DashClockService.EXTRA_COMPONENT_NAME, mExtension);
            intent.putExtra(DashClockService.EXTRA_UPDATE_REASON,
                    DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED);
            lbm.sendBroadcast(intent);
        }
        finish();
    }
}
