/*
 * Copyright 2015 Google Inc.
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import net.nurik.roman.dashclock.R;

/**
 * A dialog allowing the user to enable "force world readable" mode,
 * intended to be called from other hosts.
 */
public class EnableForceWorldReadableDialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            new ForceWorldReadableDialog().show(getFragmentManager().beginTransaction(), "dialog");
        }
    }

    public static class ForceWorldReadableDialog extends DialogFragment {
        public ForceWorldReadableDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity(), R.style.Theme_Dialog)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.force_world_readable_dialog_description)
                    .setNegativeButton(R.string.force_world_readable_dialog_no,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .setPositiveButton(R.string.force_world_readable_dialog_yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    enableForceWorldReadable();
                                    getActivity().setResult(Activity.RESULT_OK);
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (getActivity() != null) {
                getActivity().finish();
            }
        }

        private void enableForceWorldReadable() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            sp.edit().putBoolean(DashClockService.PREF_FORCE_WORLD_READABLE, true).apply();
        }
    }
}
