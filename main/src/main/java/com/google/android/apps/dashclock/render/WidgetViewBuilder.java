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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

/**
 * {@link ViewBuilder} implementation for {@link RemoteViews}.
 */
public class WidgetViewBuilder implements ViewBuilder {
    private Context mContext;
    private RemoteViews mRemoteViews;

    public WidgetViewBuilder(Context context) {
        mContext = context;
    }

    @Override
    public void loadRootLayout(Object container, int layoutResId) {
        mRemoteViews = new RemoteViews(mContext.getPackageName(), layoutResId);
    }

    @Override
    public void useRoot(Object root) {
        throw new UnsupportedOperationException("Can't reuse RemoteViews for WidgetViewBuilder.");
    }

    @Override
    public Object inflateChildLayout(int layoutResId, int containerId) {
        return new RemoteViews(mContext.getPackageName(), layoutResId);
    }

    @Override
    public void setLinearLayoutGravity(int viewId, int gravity) {
        mRemoteViews.setInt(viewId, "setGravity", gravity);
    }

    @Override
    public void setViewPadding(int viewId, int left, int top, int right, int bottom) {
        mRemoteViews.setViewPadding(viewId, left, top, right, bottom);
    }

    @Override
    public void addView(int viewId, Object child) {
        mRemoteViews.addView(viewId, (RemoteViews) child);
    }

    @Override
    public void removeAllViews(int viewId) {
        mRemoteViews.removeAllViews(viewId);
    }

    @Override
    public void setViewVisibility(int viewId, int visibility) {
        mRemoteViews.setViewVisibility(viewId, visibility);
    }

    @Override
    public void setViewBackgroundColor(int viewId, int color) {
        mRemoteViews.setInt(viewId, "setBackgroundColor", color);
    }

    @Override
    public void setImageViewBitmap(int viewId, Bitmap bitmap) {
        mRemoteViews.setImageViewBitmap(viewId, bitmap);
    }

    @Override
    public void setViewContentDescription(int viewId, String contentDescription) {
        mRemoteViews.setContentDescription(viewId, contentDescription);
    }

    @Override
    public void setTextViewText(int viewId, CharSequence text) {
        mRemoteViews.setTextViewText(viewId, text);
    }

    @Override
    public void setTextViewColor(int viewId, int color) {
        mRemoteViews.setTextColor(viewId, color);
    }

    @Override
    public void setTextViewMaxLines(int viewId, int maxLines) {
        mRemoteViews.setInt(viewId, "setMaxLines", maxLines);
    }

    @Override
    public void setTextViewSingleLine(int viewId, boolean singleLine) {
        mRemoteViews.setBoolean(viewId, "setSingleLine", singleLine);
    }

    @Override
    public void setTextViewTextSize(int viewId, int unit, float size) {
        mRemoteViews.setTextViewTextSize(viewId, unit, size);
    }

    @Override
    public void setViewClickIntent(int viewId, Intent clickIntent) {
        mRemoteViews.setOnClickPendingIntent(viewId,
                PendingIntent.getActivity(mContext, 0,
                        clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Override
    public void setViewClickFillInIntent(int viewId, Intent fillIntent) {
        mRemoteViews.setOnClickFillInIntent(viewId, fillIntent);
    }

    @Override
    public Object getRoot() {
        return mRemoteViews;
    }
}
