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

package com.google.android.apps.dashclock.render;

import com.google.android.apps.dashclock.ExtensionManager;
import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.Utils;
import com.google.android.apps.dashclock.WidgetClickProxyActivity;
import com.google.android.apps.dashclock.configuration.AppChooserPreference;
import com.google.android.apps.dashclock.configuration.AppearanceConfig;
import com.google.android.apps.dashclock.configuration.ConfigurationActivity;

import net.nurik.roman.dashclock.R;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import java.util.List;
import java.util.Locale;

import static com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData;

/**
 * Abstract helper class in charge of core rendering logic for DashClock widgets.
 */
public abstract class DashClockRenderer {
    private static final String TAG = LogUtils.makeLogTag(DashClockRenderer.class);

    private static final int MAX_COLLAPSED_EXTENSIONS = 3;

    public static final String PREF_CLOCK_SHORTCUT = "pref_clock_shortcut";
    public static final String PREF_HIDE_SETTINGS = "pref_hide_settings";

    protected Context mContext;

    protected Options mOptions;
    protected ExtensionManager mExtensionManager;

    protected DashClockRenderer(Context context) {
        mContext = context;
        mExtensionManager = ExtensionManager.getInstance(context);
    }

    public void setOptions(Options options) {
        mOptions = options;
    }

    public Object renderWidget(Object container) {
        ViewBuilder vb = onCreateViewBuilder();
        Resources res = mContext.getResources();

        // Load data from extensions
        List<ExtensionWithData> extensions = mExtensionManager.getActiveExtensionsWithData();
        int activeExtensions = extensions.size();
        int visibleExtensions = 0;
        for (ExtensionWithData ewd : extensions) {
            if (!ewd.latestData.visible()) {
                continue;
            }
            ++visibleExtensions;
        }

        // Load some settings
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean hideSettings = sp.getBoolean(PREF_HIDE_SETTINGS, false);

        // Determine if we're on a tablet or not (lock screen widgets can't be collapsed on
        // tablets).
        boolean isTablet = res.getConfiguration().smallestScreenWidthDp >= 600;

        // Pull high-level user-defined appearance options.
        int shadeColor = AppearanceConfig.getHomescreenBackgroundColor(mContext);
        boolean aggressiveCentering = AppearanceConfig.isAggressiveCenteringEnabled(mContext);

        int minExpandedHeight = res.getDimensionPixelSize(
                mOptions.target == Options.TARGET_LOCK_SCREEN
                        ? R.dimen.min_expanded_height_lock_screen
                        : R.dimen.min_expanded_height);
        boolean isExpanded = (mOptions.minHeightDp
                >= minExpandedHeight / res.getDisplayMetrics().density);

        // Step 1. Load the root layout
        vb.loadRootLayout(container, isExpanded
                ? (aggressiveCentering
                        ? R.layout.widget_main_expanded_forced_center
                        : R.layout.widget_main_expanded)
                : (aggressiveCentering
                        ? R.layout.widget_main_collapsed_forced_center
                        : R.layout.widget_main_collapsed));

        // Step 2. Configure the shade, if it should exist
        vb.setViewBackgroundColor(R.id.shade, shadeColor);
        vb.setViewVisibility(R.id.shade,
                (mOptions.target != Options.TARGET_HOME_SCREEN || shadeColor == 0)
                        ? View.GONE : View.VISIBLE);

        // Step 3. Draw the basic clock face
        renderClockFace(vb);

        // Step 4. Align the clock face and settings button (if shown)
        boolean isPortrait = res.getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;

        if (aggressiveCentering) {
            // Forced/aggressive centering rules
            vb.setViewVisibility(R.id.settings_button_center_displacement,
                    hideSettings ? View.GONE : View.VISIBLE);
            vb.setViewPadding(R.id.clock_row, 0, 0, 0, 0);
            vb.setLinearLayoutGravity(R.id.clock_target, Gravity.CENTER_HORIZONTAL);

        } else {
            // Normal centering rules
            boolean forceCentered = isTablet && isPortrait
                    && mOptions.target != Options.TARGET_HOME_SCREEN;

            int clockInnerGravity = Gravity.CENTER_HORIZONTAL;
            if (activeExtensions > 0 && !forceCentered) {
                // Extensions are visible, don't center clock
                if (mOptions.target == Options.TARGET_LOCK_SCREEN) {
                    // lock screen doesn't look at expanded state; the UI should
                    // not jitter across expanded/collapsed states for lock screen
                    clockInnerGravity = isTablet ? Gravity.LEFT : Gravity.RIGHT;
                } else {
                    // home screen
                    clockInnerGravity = (isExpanded && isTablet) ? Gravity.LEFT : Gravity.RIGHT;
                }
            }
            vb.setLinearLayoutGravity(R.id.clock_target, clockInnerGravity);

            boolean clockCentered = activeExtensions == 0 || forceCentered; // left otherwise
            vb.setLinearLayoutGravity(R.id.clock_row,
                    clockCentered ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT);
            vb.setViewVisibility(R.id.settings_button_center_displacement,
                    hideSettings
                            ? View.GONE
                            : (clockCentered ? View.INVISIBLE : View.GONE));

            int clockLeftMargin = res.getDimensionPixelSize(R.dimen.clock_left_margin);
            if (!isExpanded && mOptions.target == Options.TARGET_HOME_SCREEN) {
                clockLeftMargin = 0;
            }
            vb.setViewPadding(R.id.clock_row, clockCentered ? 0 : clockLeftMargin,
                    0, 0, 0);
        }

        // Settings button
        if (isExpanded) {
            vb.setViewVisibility(R.id.settings_button, hideSettings ? View.GONE : View.VISIBLE);
            vb.setViewClickIntent(R.id.settings_button,
                    new Intent(mContext, ConfigurationActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
        }

        // Step 6. Render the extensions (collapsed or expanded)

        vb.setViewVisibility(R.id.widget_divider,
                (visibleExtensions > 0) ? View.VISIBLE : View.GONE);

        if (isExpanded) {
            // Expanded style
            final Intent onClickTemplateIntent = WidgetClickProxyActivity.getTemplate(mContext);
            builderSetExpandedExtensionsAdapter(vb, R.id.expanded_extensions,
                    onClickTemplateIntent);

        } else {
            // Collapsed style
            vb.setViewVisibility(R.id.collapsed_extensions_container,
                    activeExtensions > 0 ? View.VISIBLE : View.GONE);

            boolean ellipsisVisible = false;
            vb.removeAllViews(R.id.collapsed_extensions_container);
            int slotIndex = 0;
            for (ExtensionWithData ewd : extensions) {
                if (!ewd.latestData.visible()) {
                    continue;
                }

                if (slotIndex >= MAX_COLLAPSED_EXTENSIONS) {
                    ellipsisVisible = true;
                    break;
                }

                vb.addView(R.id.collapsed_extensions_container,
                        renderCollapsedExtension(null, ewd));

                ++slotIndex;
            }

            if (ellipsisVisible) {
                vb.addView(R.id.collapsed_extensions_container,
                        vb.inflateChildLayout(
                                R.layout.widget_include_collapsed_ellipsis,
                                R.id.collapsed_extensions_container));
            }
        }

        return vb.getRoot();
    }

    public void renderClockFace(ViewBuilder vb) {
        vb.removeAllViews(R.id.time_container);
        vb.addView(R.id.time_container,
                vb.inflateChildLayout(
                        AppearanceConfig.getCurrentTimeLayout(mContext),
                        R.id.time_container));
        vb.removeAllViews(R.id.date_container);
        vb.addView(R.id.date_container,
                vb.inflateChildLayout(
                        AppearanceConfig.getCurrentDateLayout(mContext),
                        R.id.date_container));

        if (mOptions.minWidthDp < 300) {
            Resources res = mContext.getResources();
            int miniTextSizeLargePx = res.getDimensionPixelSize(R.dimen.mini_clock_text_size_large);
            int miniTextSizeSmallPx = res.getDimensionPixelSize(R.dimen.mini_clock_text_size_small);
            for (int id : LARGE_TIME_COMPONENT_IDS) {
                vb.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, miniTextSizeLargePx);
            }
            for (int id : SMALL_TIME_COMPONENT_IDS) {
                vb.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, miniTextSizeSmallPx);
            }
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        Intent clockIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_CLOCK_SHORTCUT, null),
                Utils.getDefaultClockIntent(mContext));
        if (clockIntent != null) {
            vb.setViewClickIntent(R.id.clock_target, clockIntent);
        }
    }

    public Object renderCollapsedExtension(Object container, ExtensionWithData ewd) {
        ViewBuilder vb = onCreateViewBuilder();
        vb.loadRootLayout(container, R.layout.widget_include_collapsed_extension);

        Resources res = mContext.getResources();
        int extensionCollapsedTextSizeSingleLine = res
                .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_single_line);
        int extensionCollapsedTextSizeTwoLine = res
                .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_two_line);

        String status = ewd.latestData.status();
        if (TextUtils.isEmpty(status)) {
            status = "";
        }

        if (status.indexOf("\n") > 0) {
            vb.setTextViewSingleLine(R.id.collapsed_extension_text, false);
            vb.setTextViewMaxLines(R.id.collapsed_extension_text, 2);
            vb.setTextViewTextSize(R.id.collapsed_extension_text,
                    TypedValue.COMPLEX_UNIT_PX,
                    extensionCollapsedTextSizeTwoLine);
        } else {
            vb.setTextViewSingleLine(R.id.collapsed_extension_text, true);
            vb.setTextViewMaxLines(R.id.collapsed_extension_text, 1);
            vb.setTextViewTextSize(R.id.collapsed_extension_text,
                    TypedValue.COMPLEX_UNIT_PX,
                    extensionCollapsedTextSizeSingleLine);
        }

        vb.setTextViewText(R.id.collapsed_extension_text,
                status.toUpperCase(Locale.getDefault()));

        String statusContentDescription = ewd.latestData.contentDescription();
        if (TextUtils.isEmpty(statusContentDescription)) {
            StringBuilder builder = new StringBuilder();
            String expandedTitle = Utils.expandedTitleOrStatus(ewd.latestData);
            if (!TextUtils.isEmpty(expandedTitle)) {
                builder.append(expandedTitle);
            }
            String expandedBody = ewd.latestData.expandedBody();
            if (!TextUtils.isEmpty(expandedBody)) {
                builder.append(" ").append(expandedBody);
            }
            statusContentDescription = builder.toString();
        }
        vb.setViewContentDescription(R.id.collapsed_extension_text, statusContentDescription);

        vb.setImageViewBitmap(R.id.collapsed_extension_icon,
                Utils.loadExtensionIcon(mContext, ewd.listing.componentName,
                        ewd.latestData.icon(), ewd.latestData.iconUri()));
        vb.setViewContentDescription(R.id.collapsed_extension_icon, ewd.listing.title);

        Intent clickIntent = ewd.latestData.clickIntent();
        if (clickIntent != null) {
            vb.setViewClickIntent(R.id.collapsed_extension_target,
                    WidgetClickProxyActivity.wrap(mContext, clickIntent,
                            ewd.listing.componentName));
        }

        return vb.getRoot();
    }

    public Object renderExpandedExtension(Object container, Object convertRoot,
            ExtensionWithData ewd) {
        ViewBuilder vb = onCreateViewBuilder();

        if (convertRoot != null) {
            vb.useRoot(convertRoot);
        } else {
            vb.loadRootLayout(container, R.layout.widget_list_item_expanded_extension);
        }

        if (ewd == null || ewd.latestData == null) {
            vb.setTextViewText(R.id.text1, mContext.getResources()
                    .getText(R.string.status_none));
            vb.setViewVisibility(R.id.text2, View.GONE);
            return vb.getRoot();
        }

        vb.setTextViewText(R.id.text1, Utils.expandedTitleOrStatus(ewd.latestData));

        String expandedBody = ewd.latestData.expandedBody();
        vb.setViewVisibility(R.id.text2, TextUtils.isEmpty(expandedBody)
                ? View.GONE : View.VISIBLE);
        vb.setTextViewText(R.id.text2, ewd.latestData.expandedBody());

        vb.setImageViewBitmap(R.id.icon,
                Utils.loadExtensionIcon(mContext, ewd.listing.componentName,
                        ewd.latestData.icon(), ewd.latestData.iconUri()));
        String contentDescription = ewd.latestData.contentDescription();
        if (TextUtils.isEmpty(contentDescription)) {
            // No specific content description provided. Just set the minimal extra content
            // description for the icon.
            vb.setViewContentDescription(R.id.icon, ewd.listing.title);
        } else {
            // Content description for the entire row provided. Use it!
            vb.setViewContentDescription(R.id.list_item,
                    ewd.listing.title + ". " + contentDescription);
            vb.setViewContentDescription(R.id.text1, "."); // silence title
            vb.setViewContentDescription(R.id.text2, "."); // silence body
        }

        Intent clickIntent = ewd.latestData.clickIntent();
        if (clickIntent != null) {
            vb.setViewClickFillInIntent(R.id.list_item,
                    WidgetClickProxyActivity.getFillIntent(clickIntent,
                            ewd.listing.componentName));
        }

        return vb.getRoot();
    }

    protected abstract ViewBuilder onCreateViewBuilder();

    protected abstract void builderSetExpandedExtensionsAdapter(ViewBuilder builder,
            int viewId, Intent onClickTemplateIntent);

    public static class Options {
        public static final int TARGET_HOME_SCREEN = 0;
        public static final int TARGET_LOCK_SCREEN = 1;
        public static final int TARGET_DAYDREAM = 2;

        public int target;
        public int minWidthDp;
        public int minHeightDp;

        // Only used by WidgetRenderer
        public int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID; // optional

        // Only used by SimpleRenderer
        public boolean newTaskOnClick;
        public OnClickListener onClickListener;
        public Intent clickIntentTemplate;
    }

    public static interface OnClickListener {
        void onClick();
    }

    private static int[] LARGE_TIME_COMPONENT_IDS = {
            R.id.large_time_component_1,
            R.id.large_time_component_2,
            R.id.large_time_component_3,
    };

    private static int[] SMALL_TIME_COMPONENT_IDS = {
            R.id.small_time_component_1,
            R.id.small_time_component_2,
            R.id.small_time_component_3,
    };

    private static int[] DATE_COMPONENT_IDS = {
            R.id.date_component_1,
            R.id.date_component_2,
            R.id.date_component_3,
    };
}
