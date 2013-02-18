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

package com.google.android.apps.dashclock.configuration;

import com.google.android.apps.dashclock.DashClockService;
import com.google.android.apps.dashclock.HelpUtils;
import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.api.DashClockExtension;

import net.nurik.roman.dashclock.R;

import android.app.Activity;
import android.app.Fragment;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * The primary widget configuration activity. Serves as an interstitial when adding the widget, and
 * shows when pressing the settings button in the widget.
 */
public class ConfigurationActivity extends Activity {
    private static final String TAG = LogUtils.makeLogTag(ConfigurationActivity.class);

    private static final int[] SECTION_LABELS = new int[]{
            R.string.section_extensions,
            R.string.section_appearance,
            R.string.section_advanced,
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends Fragment>[] SECTION_FRAGMENTS = new Class[]{
            ConfigureExtensionsFragment.class,
            ConfigureAppearanceFragment.class,
            ConfigureAdvancedFragment.class,
    };

    // only used when adding a new widget
    private int mNewWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private boolean mBackgroundCleared = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            Intent.ShortcutIconResource icon = new Intent.ShortcutIconResource();
            icon.packageName = getPackageName();
            icon.resourceName = getResources().getResourceName(R.drawable.ic_launcher);
            setResult(RESULT_OK, new Intent()
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, getTitle())
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                            new Intent(this, ConfigurationActivity.class)));
            finish();
            return;
        }

        setContentView(R.layout.activity_configure);

        if (intent != null
                && AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction())) {
            mNewWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId);
            // See http://code.google.com/p/android/issues/detail?id=2539
            setResult(RESULT_CANCELED, new Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId));
        }

        // Set up UI widgets
        setupActionBar();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        showWallpaper();
    }

    public void setTranslucentActionBar(boolean translucentActionBar) {
        findViewById(R.id.actionbar_container).setBackgroundResource(translucentActionBar
                ? R.drawable.ab_background_translucent : R.drawable.ab_background);
        showWallpaper();
    }

    private void showWallpaper() {
        if (!mBackgroundCleared) {
            // We initially show a background so that the activity transition (zoom-up animation
            // in Android 4.1) doesn't show the system wallpaper (which is needed in the
            // appearance configuration fragment). Upon user interaction (i.e. once we know the
            // activity transition has finished), clear the background so that the system wallpaper
            // can be seen when the appearance configuration fragment is shown.
            findViewById(R.id.container).setBackground(null);
            mBackgroundCleared = true;
        }
    }

    private void setupActionBar() {
        final Context darkThemeContext = new ContextThemeWrapper(this, android.R.style.Theme_Holo);
        final LayoutInflater inflater = (LayoutInflater) darkThemeContext
                .getSystemService(LAYOUT_INFLATER_SERVICE);
        final ViewGroup actionBarContainer = (ViewGroup) findViewById(R.id.actionbar_container);
        inflater.inflate(R.layout.include_configure_actionbar, actionBarContainer, true);

        actionBarContainer.findViewById(R.id.actionbar_done).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // "Done"
                        if (mNewWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            setResult(RESULT_OK, new Intent()
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId));
                        }

                        finish();
                    }
                });

        actionBarContainer.findViewById(R.id.action_overflow).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu actionOverflowMenu = new PopupMenu(darkThemeContext, view);
                        actionOverflowMenu.inflate(R.menu.configure_overflow);
                        actionOverflowMenu.show();
                        actionOverflowMenu.setOnMenuItemClickListener(mActionOverflowClickListener);
                    }
                });
        Spinner sectionSpinner = (Spinner) actionBarContainer.findViewById(R.id.section_spinner);
        sectionSpinner.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return SECTION_LABELS.length;
            }

            @Override
            public Object getItem(int position) {
                return SECTION_LABELS[position];
            }

            @Override
            public long getItemId(int position) {
                return position + 1;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item_configure_ab_spinner,
                            parent, false);
                }
                ((TextView) convertView.findViewById(android.R.id.text1)).setText(
                        getString(SECTION_LABELS[position]));
                return convertView;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item_configure_ab_spinner_dropdown,
                            parent, false);
                }
                ((TextView) convertView.findViewById(android.R.id.text1)).setText(
                        getString(SECTION_LABELS[position]));
                return convertView;
            }
        });
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
                Class<? extends Fragment> fragmentClass = SECTION_FRAGMENTS[position];
                try {
                    Fragment newFragment = fragmentClass.newInstance();
                    getFragmentManager().beginTransaction()
                            .replace(R.id.content_container, newFragment)
                            .commitAllowingStateLoss();
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> spinner) {
            }
        });
    }

    private PopupMenu.OnMenuItemClickListener mActionOverflowClickListener
            = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_get_more_extensions:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/search?q=DashClock+Extension"
                                    + "&c=apps")));
                    return true;

                case R.id.action_about:
                    HelpUtils.showAboutDialog(
                            ConfigurationActivity.this);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        // Update extensions (settings may have changed)
        // TODO: update only those extensions whose settings have changed
        Intent updateExtensionsIntent = new Intent(this, DashClockService.class);
        updateExtensionsIntent.setAction(DashClockService.ACTION_UPDATE_EXTENSIONS);
        updateExtensionsIntent.putExtra(DashClockService.EXTRA_UPDATE_REASON,
                DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED);
        startService(updateExtensionsIntent);

        // Update all widgets, including a new one if it was just added
        // We can't only update the new one because settings affecting all widgets may have
        // been changed.
        LOGD(TAG, "Updating all widgets");

        Intent widgetUpdateIntent = new Intent(this, DashClockService.class);
        widgetUpdateIntent.setAction(DashClockService.ACTION_UPDATE_WIDGETS);
        startService(widgetUpdateIntent);
    }
}
