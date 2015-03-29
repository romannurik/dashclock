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

package com.example.dashclock.examplehost;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.apps.dashclock.api.DashClockHost;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.api.ExtensionListing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExampleHostActivity extends Activity implements View.OnClickListener,
        AdapterView.OnItemClickListener {
    private static final String TAG = "ExampleHost";

    private static final String ACTION_MULTIPLEXER_PACKAGE_CHANGED =
            "com.example.dashclock.examplehost.MULTIPLEXER_PACKAGE_CHANGED";

    private ExtensionAdapter mAdapter;
    private Host mHost;

    private MaterialDialog mMultiplexerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        ListView listView = (ListView) findViewById(R.id.extensions);
        listView.setOnItemClickListener(this);

        try {
            mHost = new Host();
            mAdapter = new ExtensionAdapter(this, mHost, this);
            listView.setAdapter(mAdapter);
            mAdapter.notifyDataSetChanged();

        } catch (SecurityException ex) {
            Toast.makeText(this, "Not enough permissions", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Not enough permissions", ex);
        }

        // Listen for new multiplexer package changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_MULTIPLEXER_PACKAGE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMultiplexerEventsReceiver, filter);
    }

    private final BroadcastReceiver mMultiplexerEventsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHost.handleMultiplexerPackageChanged();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMultiplexerEventsReceiver);
        mHost.destroy();
    }

    @Override
    public void onClick(View v) {
        if (v.getTag() instanceof ExtensionListing) {
            mHost.startSettingsActivityForExtension((ExtensionListing) v.getTag());
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ExtensionListing info = mAdapter.getItem(position);
        if (info.worldReadable()) {
            List<ComponentName> updatableExtensions = new ArrayList<>();
            updatableExtensions.add(info.componentName());
            mHost.requestExtensionUpdate(updatableExtensions);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh_all:
                mHost.requestExtensionUpdate(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayMultiplexerDownloadDialog() {
        if (mMultiplexerDialog != null && mMultiplexerDialog.isShowing()) {
            mMultiplexerDialog.dismiss();
        }
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .iconRes(R.drawable.ic_mux_dialog_icon)
                .negativeText(android.R.string.cancel)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        try {
                            startActivity(mHost.getMultiplexerDownloadIntent());
                        } catch (ActivityNotFoundException ex) {
                            // wtf (there isn't a browser)
                            Toast.makeText(ExampleHostActivity.this,
                                    R.string.multiplexer_dialog_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        Intent muxDownloadIntent = mHost.getMultiplexerDownloadIntent();
        if (muxDownloadIntent != null) {
            if (!mHost.isDashClockPresent(this)) {
                // Install Multiplexer app
                builder.title(R.string.multiplexer_dialog_install_title);
                builder.content(R.string.multiplexer_dialog_install_message);
                builder.positiveText(R.string.multiplexer_dialog_install_positive_button);
            } else {
                // Update DashClock
                builder.title(R.string.multiplexer_dialog_upgrade_title);
                builder.content(R.string.multiplexer_dialog_upgrade_message);
                builder.positiveText(R.string.multiplexer_dialog_upgrade_positive_button);
            }
        } else {
            // Not available update
            List<String> pkgs = mHost.getOtherAppsWithReadDataExtensionsPermission(this);
            String appNames = packagesNameListToAppNameString(this, pkgs);
            builder.title(R.string.multiplexer_dialog_other_title);
            builder.content(R.string.multiplexer_dialog_other_message, appNames);
        }
        mMultiplexerDialog = builder.build();
        mMultiplexerDialog.show();
    }

    private static String packagesNameListToAppNameString(Context context, List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        final PackageManager pm = context.getPackageManager();
        for (String pkg : pkgs) {
            sb.append("\n\n\t");
            try {
                ApplicationInfo pi = pm.getApplicationInfo(pkg, 0);
                CharSequence name = pi.loadLabel(pm);
                if (name == null) {
                    sb.append(pkg);
                } else {
                    sb.append(name);
                }
            } catch (PackageManager.NameNotFoundException ex) {
                sb.append(pkg);
            }
        }
        return sb.toString();
    }

    public static class MultiplexerPackageChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent notifyIntent = new Intent(ACTION_MULTIPLEXER_PACKAGE_CHANGED);
            LocalBroadcastManager.getInstance(context).sendBroadcast(notifyIntent);
        }
    }

    private class Host extends DashClockHost {
        public Host() {
            super(ExampleHostActivity.this);
        }

        @Override
        public void onAvailableExtensionsChanged() {
            super.onAvailableExtensionsChanged();

            List<ExtensionListing> availableExtensions = getAvailableExtensions(false);
            Set<ComponentName> worldReadableExtensions = new HashSet<>();
            for (ExtensionListing info : availableExtensions) {
                worldReadableExtensions.add(info.componentName());
            }

            mAdapter.clear();
            mAdapter.addAll(availableExtensions);
            mHost.listenTo(worldReadableExtensions);
        }

        @Override
        protected void onExtensionDataChanged(ComponentName extension) {
            super.onExtensionDataChanged(extension);
            Log.d(TAG, "Extension data changed for " + extension.flattenToString());
            mAdapter.notifyDataSetChanged();mAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onMultiplexerChangedDetected(boolean available) {
            // Before to notify the implementation the multiplexer change, hide
            // the multiplexer dialog if is showing, so the implementation can
            // decide again what is the better method to advise the user
            if (mMultiplexerDialog != null && mMultiplexerDialog.isShowing()) {
                mMultiplexerDialog.dismiss();
            }

            if (!available) {
                // Not avaliable clear the dataset
                mAdapter.clear();

                // Use the provided implementation
                displayMultiplexerDownloadDialog();
            }
        }
    }

    private static class ExtensionAdapter extends ArrayAdapter<ExtensionListing> {
        private LayoutInflater mInflater;
        private DashClockHost mHost;
        private View.OnClickListener mListener;

        public ExtensionAdapter(Context context, DashClockHost host, View.OnClickListener listener) {
            super(context, 0);
            mHost = host;
            mListener = listener;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.extension_item, parent, false);
                holder = new ViewHolder();
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                holder.description = (TextView) convertView.findViewById(R.id.description);
                holder.status = (TextView) convertView.findViewById(R.id.status);
                holder.expandedTitle = (TextView) convertView.findViewById(R.id.expandedTitle);
                holder.expandedBody = (TextView) convertView.findViewById(R.id.expandedBody);
                holder.worldReadable = (ImageView) convertView.findViewById(R.id.world_readable);
                holder.settings = (ImageView) convertView.findViewById(R.id.settings);
                holder.settings.setOnClickListener(mListener);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ExtensionListing info = getItem(position);
            holder.title.setText(info.title());
            holder.description.setText(info.description());

            try {
                if (info.icon() != 0) {
                    Resources res = getContext().createPackageContext(
                            info.componentName().getPackageName(), 0).getResources();
                    Drawable dw = res.getDrawable(info.icon());
                    dw.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
                    holder.icon.setImageDrawable(dw);
                } else {
                    holder.icon.setImageDrawable(null);
                }
            } catch (PackageManager.NameNotFoundException e) {
                holder.icon.setImageDrawable(null);
            }

            boolean hasSettings = info.settingsActivity() != null;
            holder.settings.setVisibility(hasSettings ? View.VISIBLE : View.GONE);
            holder.settings.setTag(info);

            holder.worldReadable.setVisibility(info.worldReadable() ? View.VISIBLE : View.GONE);

            ExtensionData data = mHost.getExtensionData(info.componentName());
            if (data != null) {
                if (!data.visible()) {
                    String dataText = getContext().getString(R.string.data_invalid);
                    holder.status.setText(dataText);
                    holder.status.setVisibility(
                            TextUtils.isEmpty(dataText) ? View.GONE : View.VISIBLE);
                    holder.expandedTitle.setVisibility(View.GONE);
                    holder.expandedBody.setVisibility(View.GONE);
                } else {
                    holder.status.setText(data.status());
                    holder.status.setVisibility(
                            TextUtils.isEmpty(data.status()) ? View.GONE : View.VISIBLE);
                    holder.expandedTitle.setText(data.expandedTitle());
                    holder.expandedTitle.setVisibility(
                            TextUtils.isEmpty(data.expandedTitle()) ? View.GONE : View.VISIBLE);
                    holder.expandedBody.setText(data.expandedBody());
                    holder.expandedBody.setVisibility(
                            TextUtils.isEmpty(data.expandedBody()) ? View.GONE : View.VISIBLE);
                }
            }

            return convertView;
        }

        private static class ViewHolder {
            ImageView icon;
            TextView title;
            TextView description;
            TextView status;
            TextView expandedTitle;
            TextView expandedBody;
            ImageView worldReadable;
            ImageView settings;
        }
    }
}
