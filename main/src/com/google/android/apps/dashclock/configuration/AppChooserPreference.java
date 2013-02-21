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

import com.google.android.apps.dashclock.ui.SimplePagedTabsHelper;

import net.nurik.roman.dashclock.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A preference that allows the user to choose an application or shortcut.
 */
public class AppChooserPreference extends Preference {
    private boolean mAllowUseDefault = false;

    public AppChooserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs, 0);
    }

    public AppChooserPreference(Context context) {
        super(context);
        initAttrs(null, 0);
    }

    public AppChooserPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(attrs, defStyle);
    }

    private void initAttrs(AttributeSet attrs, int defStyle) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.AppChooserPreference, defStyle, defStyle);

        try {
            mAllowUseDefault = a.getBoolean(R.styleable.AppChooserPreference_allowUseDefault, true);
        } finally {
            a.recycle();
        }
    }

    public void setIntentValue(Intent intent) {
        setValue(intent == null ? "" : intent.toUri(Intent.URI_INTENT_SCHEME));
    }

    public void setValue(String value) {
        if (callChangeListener(value)) {
            persistString(value);
            notifyChanged();
        }
    }

    public static Intent getIntentValue(String value, Intent defaultIntent) {
        try {
            if (TextUtils.isEmpty(value)) {
                return defaultIntent;
            }

            return Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return defaultIntent;
        }
    }

    public static CharSequence getDisplayValue(Context context, String value) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(R.string.pref_shortcut_default);
        }

        Intent intent;
        try {
            intent = Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return context.getString(R.string.pref_shortcut_default);
        }

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        if (resolveInfos.size() == 0) {
            return null;
        }

        StringBuilder label = new StringBuilder();
        label.append(resolveInfos.get(0).loadLabel(pm));
        if (intent.getData() != null && intent.getData().getScheme() != null &&
                intent.getData().getScheme().startsWith("http")) {
            label.append(": ").append(intent.getDataString());
        }
        return label;
    }

    @Override
    protected void onClick() {
        super.onClick();

        AppChooserDialogFragment fragment = AppChooserDialogFragment.newInstance();
        fragment.setPreference(this);

        Activity activity = (Activity) getContext();
        activity.getFragmentManager().beginTransaction()
                .add(fragment, getFragmentTag())
                .commit();
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

        Activity activity = (Activity) getContext();
        AppChooserDialogFragment fragment = (AppChooserDialogFragment) activity
                .getFragmentManager().findFragmentByTag(getFragmentTag());
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
            setValue(restoreValue ? getPersistedString("") : (String) defaultValue);
    }

    public String getFragmentTag() {
        return "app_chooser_" + getKey();
    }

    public static class AppChooserDialogFragment extends DialogFragment {
        public static int REQUEST_CREATE_SHORTCUT = 1;

        private AppChooserPreference mPreference;

        private ActivityListAdapter mAppsAdapter;
        private ActivityListAdapter mShortcutsAdapter;

        private ListView mAppsList;
        private ListView mShortcutsList;

        public AppChooserDialogFragment() {
        }

        public static AppChooserDialogFragment newInstance() {
            return new AppChooserDialogFragment();
        }

        public void setPreference(AppChooserPreference preference) {
            mPreference = preference;
            tryBindLists();
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            tryBindLists();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Force Holo Light since ?android:actionBarXX would use dark action bar
            Context layoutContext = new ContextThemeWrapper(getActivity(),
                    android.R.style.Theme_Holo_Light);

            LayoutInflater layoutInflater = LayoutInflater.from(layoutContext);
            View rootView = layoutInflater.inflate(R.layout.dialog_app_chooser, null);
            final ViewGroup tabWidget = (ViewGroup) rootView.findViewById(android.R.id.tabs);
            final ViewPager pager = (ViewPager) rootView.findViewById(R.id.pager);
            pager.setPageMargin((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    getResources().getDisplayMetrics()));

            SimplePagedTabsHelper helper = new SimplePagedTabsHelper(layoutContext,
                    tabWidget, pager);
            helper.addTab(R.string.title_apps, R.id.apps_list);
            helper.addTab(R.string.title_shortcuts, R.id.shortcuts_list);

            // Set up apps
            mAppsList = (ListView) rootView.findViewById(R.id.apps_list);
            mAppsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> listView, View view,
                        int position, long itemId) {
                    Intent intent = mAppsAdapter.getIntent(position);
                    if (intent != null) {
                        intent = Intent.makeMainActivity(intent.getComponent());
                    }
                    mPreference.setIntentValue(intent);
                    dismiss();
                }
            });

            // Set up shortcuts
            mShortcutsList = (ListView) rootView.findViewById(R.id.shortcuts_list);
            mShortcutsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> listView, View view,
                        int position, long itemId) {
                    startActivityForResult(mShortcutsAdapter.getIntent(position),
                            REQUEST_CREATE_SHORTCUT);
                }
            });

            tryBindLists();

            return new AlertDialog.Builder(getActivity())
                    .setView(rootView)
                    .create();
        }

        private void tryBindLists() {
            if (mPreference == null) {
                return;
            }

            if (isAdded() && mAppsAdapter == null && mShortcutsAdapter == null) {
                mAppsAdapter = new ActivityListAdapter(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        mPreference.mAllowUseDefault);
                mShortcutsAdapter = new ActivityListAdapter(
                        new Intent(Intent.ACTION_CREATE_SHORTCUT)
                                .addCategory(Intent.CATEGORY_DEFAULT),
                        false);
            }

            if (mAppsAdapter != null && mAppsList != null
                    && mShortcutsAdapter != null && mShortcutsList != null) {
                mAppsList.setAdapter(mAppsAdapter);
                mShortcutsList.setAdapter(mShortcutsAdapter);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CREATE_SHORTCUT && resultCode == Activity.RESULT_OK) {
                mPreference.setIntentValue(
                        (Intent) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT));
                dismiss();
            }
        }

        static class ActivityInfo {
            CharSequence label;
            Drawable icon;
            ComponentName componentName;
        }

        private class ActivityListAdapter extends BaseAdapter {
            private Intent mQueryIntent;
            private PackageManager mPackageManager;
            private List<ActivityInfo> mInfos;
            private boolean mAllowUseDefault;

            private ActivityListAdapter(Intent queryIntent, boolean allowUseDefault) {
                mQueryIntent = queryIntent;
                mPackageManager = getActivity().getPackageManager();
                mAllowUseDefault = allowUseDefault;

                mInfos = new ArrayList<ActivityInfo>();
                List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(queryIntent,
                        0);
                for (ResolveInfo ri : resolveInfos) {
                    ActivityInfo ai = new ActivityInfo();
                    ai.icon = ri.loadIcon(mPackageManager);
                    ai.label = ri.loadLabel(mPackageManager);
                    ai.componentName = new ComponentName(ri.activityInfo.packageName,
                            ri.activityInfo.name);
                    mInfos.add(ai);
                }

                Collections.sort(mInfos, new Comparator<ActivityInfo>() {
                    @Override
                    public int compare(ActivityInfo activityInfo, ActivityInfo activityInfo2) {
                        return activityInfo.label.toString().compareTo(
                                activityInfo2.label.toString());
                    }
                });
            }

            @Override
            public int getCount() {
                return mInfos.size() + (mAllowUseDefault ? 1 : 0);
            }

            @Override
            public Object getItem(int position) {
                if (mAllowUseDefault && position == 0) {
                    return null;
                }

                return mInfos.get(position - (mAllowUseDefault ? 1 : 0));
            }

            public Intent getIntent(int position) {
                if (mAllowUseDefault && position == 0) {
                    return null;
                }

                return new Intent(mQueryIntent)
                        .setComponent(mInfos.get(position - (mAllowUseDefault ? 1 : 0))
                                .componentName);
            }

            @Override
            public long getItemId(int position) {
                if (mAllowUseDefault && position == 0) {
                    return -1;
                }

                return mInfos.get(position - (mAllowUseDefault ? 1 : 0)).componentName.hashCode();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup container) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getActivity())
                            .inflate(R.layout.list_item_intent, container, false);
                }

                if (mAllowUseDefault && position == 0) {
                    ((TextView) convertView.findViewById(android.R.id.text1))
                            .setText(getString(R.string.pref_shortcut_default));
                    ((ImageView) convertView.findViewById(android.R.id.icon))
                            .setImageDrawable(null);
                } else {
                    ActivityInfo ai = mInfos.get(position - (mAllowUseDefault ? 1 : 0));
                    ((TextView) convertView.findViewById(android.R.id.text1))
                            .setText(ai.label);
                    ((ImageView) convertView.findViewById(android.R.id.icon))
                            .setImageDrawable(ai.icon);
                }

                return convertView;
            }
        }
    }
}
