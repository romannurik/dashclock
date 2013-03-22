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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.view.View;
import android.widget.AbsListView;

/**
 * Class in charge of rendering DashClock to a normal view hierarchy (i.e. not RemoteViews), along
 * with {@link SimpleExpandedExtensionsAdapter}.
 */
public class SimpleRenderer extends DashClockRenderer implements SimpleViewBuilder.Callbacks {
    public SimpleRenderer(Context context) {
        super(context);
    }

    @Override
    protected ViewBuilder onCreateViewBuilder() {
        return new SimpleViewBuilder(mContext, this);
    }

    @Override
    protected void builderSetExpandedExtensionsAdapter(ViewBuilder builder,
            int viewId, Intent clickTemplateIntent) {
        View root = (View) builder.getRoot();

        // TODO: create a copy of the options object with the clickIntentTemplate set.
        mCurrentOptions.clickIntentTemplate = clickTemplateIntent;

        AbsListView listView = (AbsListView) root.findViewById(viewId);
        listView.setAdapter(new SimpleExpandedExtensionsAdapter(mContext, this, mCurrentOptions));
    }

    @Override
    public Intent onGetClickIntentTemplate() {
        return mCurrentOptions.clickIntentTemplate;
    }

    @Override
    public void onModifyClickIntent(Intent clickIntent) {
        if (mCurrentOptions.newTaskOnClick) {
            clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    @Override
    public void onClickIntentCalled(int viewId) {
        mCurrentOptions.onClickListener.onClick();
    }
}
