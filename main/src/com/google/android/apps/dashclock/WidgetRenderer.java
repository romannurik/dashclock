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

import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.apps.dashclock.configuration.AppChooserPreference;
import com.google.android.apps.dashclock.configuration.AppearanceConfig;
import com.google.android.apps.dashclock.configuration.ConfigurationActivity;

import net.nurik.roman.dashclock.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData;
import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * Helper class in charge of rendering DashClock widgets, along with {@link ViewFactoryService}.
 */
public class WidgetRenderer {
    private static final String TAG = LogUtils.makeLogTag(WidgetRenderer.class);

    public static final String PREF_CLOCK_SHORTCUT = "pref_clock_shortcut";

    private static class CollapsedExtensionSlot {
        int targetId;
        int iconId;
        int textId;

        public CollapsedExtensionSlot(int targetId, int iconId, int textId) {
            this.targetId = targetId;
            this.iconId = iconId;
            this.textId = textId;
        }
    }

    private static CollapsedExtensionSlot[] COLLAPSED_EXTENSION_SLOTS = new CollapsedExtensionSlot[]{
            new CollapsedExtensionSlot(
                    R.id.collapsed_extension_1_target,
                    R.id.collapsed_extension_1_icon,
                    R.id.collapsed_extension_1_text),
            new CollapsedExtensionSlot(
                    R.id.collapsed_extension_2_target,
                    R.id.collapsed_extension_2_icon,
                    R.id.collapsed_extension_2_text),
            new CollapsedExtensionSlot(
                    R.id.collapsed_extension_3_target,
                    R.id.collapsed_extension_3_icon,
                    R.id.collapsed_extension_3_text),
    };

    /**
     * Renders the DashClock UI to the given app widget IDs.
     */
    public static void renderWidgets(Context context, int[] appWidgetIds) {
        final ExtensionManager extensionManager = ExtensionManager.getInstance(context);
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final Resources res = context.getResources();

        // Load some settings
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Intent clockIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_CLOCK_SHORTCUT, null), Utils.getDefaultClockIntent(context));

        // Load data from extensions
        List<ExtensionWithData> mExtensions = extensionManager.getActiveExtensionsWithData();

        // Determine if we're on a tablet or not (lock screen widgets can't be collapsed on
        // tablets).
        boolean isTablet = res.getConfiguration().smallestScreenWidthDp >= 600;

        // Pull high-level user-defined appearance options.
        int shadeColor = AppearanceConfig.getHomescreenBackgroundColor(context);
        boolean aggressiveCentering = AppearanceConfig.isAggressiveCenteringEnabled(context);

        int activeExtensions = mExtensions.size();

        int visibleExtensions = 0;
        for (ExtensionWithData ewd : mExtensions) {
            if (!ewd.latestData.visible()) {
                continue;
            }
            ++visibleExtensions;
        }

        for (int appWidgetId : appWidgetIds) {
            boolean isLockscreen = false;
            int widgetMinHeight = Integer.MAX_VALUE;
            Bundle widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
            if (widgetOptions != null) {
                widgetMinHeight = widgetOptions
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                isLockscreen = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD ==
                        widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY);
            }

            boolean isExpanded = (widgetMinHeight
                    >= res.getDimensionPixelSize(R.dimen.min_expanded_height) /
                    res.getDisplayMetrics().density);

            RemoteViews rv = new RemoteViews(context.getPackageName(),
                    isExpanded
                            ? (aggressiveCentering
                                    ? R.layout.widget_main_expanded_forced_center
                                    : R.layout.widget_main_expanded)
                            : (aggressiveCentering
                                    ? R.layout.widget_main_collapsed_forced_center
                                    : R.layout.widget_main_collapsed));
            rv.setInt(R.id.shade, "setBackgroundColor", shadeColor);
            rv.setViewVisibility(R.id.shade, (isLockscreen || shadeColor == 0)
                    ? View.GONE : View.VISIBLE);

            // Configure clock face
            rv.removeAllViews(R.id.time_container);
            rv.addView(R.id.time_container, new RemoteViews(context.getPackageName(),
                    AppearanceConfig.getCurrentTimeLayout(context)));
            rv.removeAllViews(R.id.date_container);
            rv.addView(R.id.date_container, new RemoteViews(context.getPackageName(),
                    AppearanceConfig.getCurrentDateLayout(context)));

            // Align the clock
            boolean isPortrait = res.getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;

            if (aggressiveCentering) {
                // Forced/aggressive centering rules
                rv.setViewVisibility(R.id.settings_button_center_displacement, View.VISIBLE);
                rv.setViewPadding(R.id.clock_row, 0, 0, 0, 0);
                rv.setInt(R.id.clock_target, "setGravity", Gravity.CENTER_HORIZONTAL);

            } else {
                // Basic centering rules
                boolean forceCentered = isTablet && isPortrait && isLockscreen;

                int clockInnerGravity = Gravity.CENTER_HORIZONTAL;
                if (activeExtensions > 0 && !forceCentered) {
                    // Extensions are visible, don't center clock
                    if (isLockscreen) {
                        // lock screen
                        clockInnerGravity = isTablet ? Gravity.LEFT : Gravity.RIGHT;
                    } else {
                        // home screen
                        clockInnerGravity = (isExpanded && isTablet) ? Gravity.LEFT : Gravity.RIGHT;
                    }
                }
                rv.setInt(R.id.clock_target, "setGravity", clockInnerGravity);

                boolean clockCentered = activeExtensions == 0 || forceCentered; // left otherwise
                rv.setInt(R.id.clock_row, "setGravity",
                        clockCentered ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT);
                rv.setViewVisibility(R.id.settings_button_center_displacement,
                        clockCentered ? View.INVISIBLE : View.GONE);

                int clockLeftMargin = res.getDimensionPixelSize(R.dimen.clock_left_margin);
                rv.setViewPadding(R.id.clock_row, clockCentered ? 0 : clockLeftMargin, 0, 0, 0);
            }

            rv.setViewVisibility(R.id.widget_divider,
                    (visibleExtensions > 0) ? View.VISIBLE : View.GONE);
            rv.setViewVisibility(R.id.collapsed_extensions_container,
                    (activeExtensions > 0 && !isExpanded) ? View.VISIBLE : View.GONE);

            // Clock
            if (clockIntent != null) {
                rv.setOnClickPendingIntent(R.id.clock_target, PendingIntent.getActivity(
                        context,
                        0,
                        clockIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT));
            }

            for (CollapsedExtensionSlot slot : COLLAPSED_EXTENSION_SLOTS) {
                rv.setViewVisibility(slot.targetId, View.GONE);
            }

            int slotIndex = 0;

            if (isExpanded) {
                Intent remoteAdapterIntent = new Intent(context, ViewFactoryService.class);
                remoteAdapterIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                // TODO: is this setData call really necessary?
                remoteAdapterIntent.setData(
                        Uri.parse(remoteAdapterIntent.toUri(Intent.URI_INTENT_SCHEME)));
                rv.setRemoteAdapter(R.id.expanded_extensions, remoteAdapterIntent);

                final Intent onClickIntent = WidgetClickProxyActivity.getTemplate(context);
                final PendingIntent onClickPendingIntent = PendingIntent.getActivity(context, 0,
                        onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                rv.setPendingIntentTemplate(R.id.expanded_extensions, onClickPendingIntent);

                // Settings button
                Intent settingsIntent = new Intent(context, ConfigurationActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                rv.setOnClickPendingIntent(R.id.settings_button,
                        PendingIntent.getActivity(
                                context, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT));

            } else {
                // Update status slots
                boolean ellipsisVisible = false;
                int extensionCollapsedTextSizeSingleLine = res
                        .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_single_line);
                int extensionCollapsedTextSizeTwoLine = res
                        .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_two_line);
                for (ExtensionWithData ewd : mExtensions) {
                    if (!ewd.latestData.visible()) {
                        continue;
                    }

                    if (slotIndex >= COLLAPSED_EXTENSION_SLOTS.length) {
                        ellipsisVisible = true;
                        break;
                    }

                    rv.setViewVisibility(COLLAPSED_EXTENSION_SLOTS[slotIndex].targetId,
                            View.VISIBLE);

                    String status = ewd.latestData.status();
                    if (TextUtils.isEmpty(status)) {
                        status = "";
                    }

                    int extensionTextId = COLLAPSED_EXTENSION_SLOTS[slotIndex].textId;
                    if (status.indexOf("\n") > 0) {
                        rv.setBoolean(extensionTextId, "setSingleLine", false);
                        rv.setInt(extensionTextId, "setMaxLines", 2);
                        rv.setTextViewTextSize(extensionTextId, TypedValue.COMPLEX_UNIT_PX,
                                extensionCollapsedTextSizeTwoLine);
                    } else {
                        rv.setBoolean(extensionTextId, "setSingleLine", true);
                        rv.setInt(extensionTextId, "setMaxLines", 1);
                        rv.setTextViewTextSize(extensionTextId, TypedValue.COMPLEX_UNIT_PX,
                                extensionCollapsedTextSizeSingleLine);
                    }

                    rv.setTextViewText(extensionTextId, status.toUpperCase(Locale.getDefault()));

                    StringBuilder statusContentDescription = new StringBuilder();
                    String expandedTitle = expandedTitleOrStatus(ewd.latestData);
                    if (!TextUtils.isEmpty(expandedTitle)) {
                        statusContentDescription.append(expandedTitle);
                    }
                    String expandedBody = ewd.latestData.expandedBody();
                    if (!TextUtils.isEmpty(expandedBody)) {
                        statusContentDescription.append(" ").append(expandedBody);
                    }
                    rv.setContentDescription(extensionTextId, statusContentDescription.toString());

                    rv.setImageViewBitmap(COLLAPSED_EXTENSION_SLOTS[slotIndex].iconId,
                            loadExtensionIcon(context, ewd.listing.componentName,
                                    ewd.latestData.icon()));
                    rv.setContentDescription(COLLAPSED_EXTENSION_SLOTS[slotIndex].iconId,
                            ewd.listing.title);

                    Intent clickIntent = ewd.latestData.clickIntent();
                    if (clickIntent != null) {
                        rv.setOnClickPendingIntent(COLLAPSED_EXTENSION_SLOTS[slotIndex].targetId,
                                PendingIntent.getActivity(context,
                                        slotIndex,
                                        WidgetClickProxyActivity.wrap(context, clickIntent,
                                                ewd.listing.componentName),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                    }

                    ++slotIndex;
                }

                rv.setViewVisibility(R.id.collapsed_extension_ellipsis,
                        ellipsisVisible ? View.VISIBLE : View.GONE);
            }

            appWidgetManager.updateAppWidget(appWidgetId, rv);
        }
    }

    /**
     * This is the service that provides the factory to be bound to the collection. Basically the
     * {@link android.widget.Adapter} for expanded DashClock extensions.
     */
    public static class ViewFactoryService extends RemoteViewsService {
        @Override
        public void onCreate() {
            super.onCreate();
        }

        @Override
        public RemoteViewsFactory onGetViewFactory(Intent intent) {
            return new WidgetRemoveViewsFactory(this);
        }
    }

    /**
     * This is the factory that will provide data to the collection widget. Behaves pretty much like
     * an {@link android.widget.Adapter}.
     */
    private static class WidgetRemoveViewsFactory implements RemoteViewsService.RemoteViewsFactory,
            ExtensionManager.OnChangeListener {
        private Context mContext;
        private ExtensionManager mExtensionManager;
        private List<ExtensionWithData> mVisibleExtensions = new ArrayList<ExtensionWithData>();

        public WidgetRemoveViewsFactory(Context context) {
            mContext = context;
            mExtensionManager = ExtensionManager.getInstance(context);
            mExtensionManager.addOnChangeListener(this);
            onExtensionsChanged();
        }

        @Override
        public void onExtensionsChanged() {
            List<ExtensionWithData> ewds = mExtensionManager.getActiveExtensionsWithData();
            mVisibleExtensions.clear();
            for (ExtensionWithData ewd : ewds) {
                if (ewd.latestData.visible()) {
                    mVisibleExtensions.add(ewd);
                }
            }

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(mContext, WidgetProvider.class));
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.expanded_extensions);
        }

        public void onCreate() {
            // Since we reload the cursor in onDataSetChanged() which gets called immediately after
            // onCreate(), we do nothing here.
        }

        public void onDestroy() {
            mExtensionManager.removeOnChangeListener(this);
        }

        public void onDataSetChanged() {
        }

        public int getViewTypeCount() {
            return 1;
        }

        public long getItemId(int position) {
            return mExtensionManager.getActiveExtensionsWithData().get(position)
                    .listing.componentName.hashCode();
        }

        public boolean hasStableIds() {
            return true;
        }

        public int getCount() {
            return mVisibleExtensions.size();
        }

        public RemoteViews getViewAt(int position) {
            if (position >= getCount()) {
                // TODO: trap this better
                return null;
            }

            RemoteViews rv;

            ExtensionWithData ewd = mVisibleExtensions.get(position);
            rv = new RemoteViews(mContext.getPackageName(),
                    R.layout.widget_list_item_expanded_extension);

            rv.setTextViewText(R.id.text1, expandedTitleOrStatus(ewd.latestData));

            String expandedBody = ewd.latestData.expandedBody();
            rv.setViewVisibility(R.id.text2, TextUtils.isEmpty(expandedBody)
                    ? View.GONE : View.VISIBLE);
            rv.setTextViewText(R.id.text2, ewd.latestData.expandedBody());

            rv.setImageViewBitmap(R.id.icon,
                    loadExtensionIcon(mContext, ewd.listing.componentName, ewd.latestData.icon()));
            rv.setContentDescription(R.id.icon, ewd.listing.title);

            Intent clickIntent = ewd.latestData.clickIntent();
            if (clickIntent != null) {
                rv.setOnClickFillInIntent(R.id.list_item,
                        WidgetClickProxyActivity.getFillIntent(clickIntent,
                                ewd.listing.componentName));
            }

            return rv;
        }

        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(),
                    R.layout.widget_list_item_expanded_extension_loading);
        }
    }

    private static Bitmap loadExtensionIcon(Context context, ComponentName extension, int icon) {
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
                    0xffffffff);

        } catch (PackageManager.NameNotFoundException e) {
            LOGE(TAG, "Couldn't access extension's package while loading icon data.");
        }

        return null;
    }

    private static String expandedTitleOrStatus(ExtensionData data) {
        String expandedTitle = data.expandedTitle();
        if (TextUtils.isEmpty(expandedTitle)) {
            expandedTitle = data.status();
            if (!TextUtils.isEmpty(expandedTitle)) {
                expandedTitle = expandedTitle.replace("\n", " ");
            }
        }
        return expandedTitle;
    }
}
