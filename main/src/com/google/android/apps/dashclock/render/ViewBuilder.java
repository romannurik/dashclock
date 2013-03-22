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

import android.content.Intent;
import android.graphics.Bitmap;

/**
 * Abstraction for building up view hierarchies, using either standard framework views or
 * RemoteViews.
 */
public interface ViewBuilder {
    void loadRootLayout(Object container, int layoutResId);
    void reuseRootLayout(Object root);
    Object inflateChildLayout(int layoutResId, int containerId);
    void setViewClickIntent(int viewId, Intent clickIntent);
    void setViewClickFillInIntent(int viewId, Intent fillIntent);
    void setViewVisibility(int viewId, int visibility);
    void setViewPadding(int viewId, int left, int top, int right, int bottom);
    void setViewContentDescription(int viewId, String contentDescription);
    void setViewBackgroundColor(int viewId, int color);
    void setTextViewText(int viewId, CharSequence text);
    void setTextViewTextSize(int viewId, int unit, float size);
    void setTextViewSingleLine(int viewId, boolean singleLine);
    void setTextViewMaxLines(int viewId, int maxLines);
    void setImageViewBitmap(int viewId, Bitmap bitmap);
    void setLinearLayoutGravity(int viewId, int gravity);
    void addView(int viewId, Object child);
    void removeAllViews(int viewId);
    Object getRoot();
}
