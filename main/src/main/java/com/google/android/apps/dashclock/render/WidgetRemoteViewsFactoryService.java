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

import com.google.android.apps.dashclock.*;
import com.google.android.apps.dashclock.configuration.AppearanceConfig;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.apps.dashclock.LogUtils.LOGD;

/**
 * This is the service that provides the factory to be bound to the collection. Basically the
 * {@link android.widget.Adapter} for expanded DashClock extensions.
 */
public class WidgetRemoteViewsFactoryService extends RemoteViewsService {
    private static final String TAG = LogUtils.makeLogTag(WidgetRemoteViewsFactoryService.class);

    public static String EXTRA_TARGET = "com.google.android.apps.dashclock.extra.TARGET";

    public static String EXTRA_IS_MINI = "com.google.android.apps.dashclock.extra.IS_MINI";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        LOGD(TAG, "Instantiating a remote views factory.");
        int target = intent.getIntExtra(EXTRA_TARGET, DashClockRenderer.Options.TARGET_HOME_SCREEN);
        boolean isMini = intent.getBooleanExtra(EXTRA_IS_MINI, false);
        return new WidgetRemoveViewsFactory(this, target, isMini);
    }

    /**
     * This is the factory that will provide data to the collection widget. Behaves pretty much like
     * an {@link android.widget.Adapter}.
     */
    class WidgetRemoveViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private Context mContext;
        private ExtensionManager mExtensionManager;
        private List<ExtensionManager.ExtensionWithData>
                mVisibleExtensions = new ArrayList<ExtensionManager.ExtensionWithData>();
        private int mTarget;
        private boolean mIsMini;

        public WidgetRemoveViewsFactory(Context context, int target, boolean isMini) {
            mContext = context;
            mTarget = target;
            mIsMini = isMini;
            mExtensionManager = ExtensionManager.getInstance(context);
        }

        public void onCreate() {
        }

        public void onDestroy() {
        }

        public void onDataSetChanged() {
            mVisibleExtensions = mExtensionManager.getVisibleExtensionsWithData();
        }

        public int getViewTypeCount() {
            return 1;
        }

        public long getItemId(int position) {
            ExtensionManager.ExtensionWithData ewd = getItemAtProtected(position);
            return (ewd != null) ? ewd.listing.componentName.hashCode() : 0;
        }

        public boolean hasStableIds() {
            return true;
        }

        public int getCount() {
            return mVisibleExtensions.size();
        }

        private ExtensionManager.ExtensionWithData getItemAtProtected(int position) {
            return position < mVisibleExtensions.size() ? mVisibleExtensions.get(position) : null;
        }

        public RemoteViews getViewAt(int position) {
            if (position >= mVisibleExtensions.size()) {
                // TODO: trap this better
                // See note on synchronization below.
                return null;
            }

            WidgetRenderer renderer = new WidgetRenderer(mContext);
            DashClockRenderer.Options options = new DashClockRenderer.Options();
            options.target = mTarget;
            options.foregroundColor = AppearanceConfig.getForegroundColor(mContext, mTarget);
            renderer.setOptions(options);
            ExtensionManager.ExtensionWithData ewd = getItemAtProtected(position);
            return (RemoteViews) (mIsMini
                    ? renderer.renderCollapsedExtension(null, null, true, ewd)
                    : renderer.renderExpandedExtension(null, null, true, ewd));
        }

        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(),
                    R.layout.widget_list_item_expanded_extension_loading);
        }
    }

}
