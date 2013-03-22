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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * {@link ViewBuilder} implementation for standard framework views.
 */
public class SimpleViewBuilder implements ViewBuilder {
    private Context mContext;
    private View mRootView;
    private Callbacks mCallbacks;

    public SimpleViewBuilder(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
    }

    @Override
    public void reuseRootLayout(Object root) {
        mRootView = (View) root;
    }

    @Override
    public void loadRootLayout(Object container, int layoutResId) {
        mRootView = LayoutInflater.from(mContext).inflate(layoutResId,
                (ViewGroup) container, false);
    }

    @Override
    public Object inflateChildLayout(int layoutResId, int containerId) {
        return LayoutInflater.from(mContext).inflate(layoutResId,
                (containerId != 0) ? (ViewGroup) mRootView.findViewById(containerId) : null, false);
    }

    @Override
    public void setImageViewBitmap(int viewId, Bitmap bitmap) {
        ((ImageView) mRootView.findViewById(viewId)).setImageBitmap(bitmap);
    }

    @Override
    public void setViewContentDescription(int viewId, String contentDescription) {
        mRootView.findViewById(viewId).setContentDescription(contentDescription);
    }

    @Override
    public void setTextViewText(int viewId, CharSequence text) {
        ((TextView) mRootView.findViewById(viewId)).setText(text);
    }

    @Override
    public void setTextViewMaxLines(int viewId, int maxLines) {
        ((TextView) mRootView.findViewById(viewId)).setMaxLines(maxLines);
    }

    @Override
    public void setTextViewSingleLine(int viewId, boolean singleLine) {
        ((TextView) mRootView.findViewById(viewId)).setSingleLine(singleLine);
    }

    @Override
    public void setTextViewTextSize(int viewId, int unit, float size) {
        ((TextView) mRootView.findViewById(viewId)).setTextSize(unit, size);
    }

    @Override
    public void setLinearLayoutGravity(int viewId, int gravity) {
        ((LinearLayout) mRootView.findViewById(viewId)).setGravity(gravity);
    }

    @Override
    public void setViewPadding(int viewId, int left, int top, int right, int bottom) {
        mRootView.findViewById(viewId).setPadding(left, top, right, bottom);
    }

    @Override
    public void addView(int viewId, Object child) {
        ((ViewGroup) mRootView.findViewById(viewId)).addView((View) child);
    }

    @Override
    public void removeAllViews(int viewId) {
        ((ViewGroup) mRootView.findViewById(viewId)).removeAllViews();
    }

    @Override
    public void setViewVisibility(int viewId, int visibility) {
        View v = mRootView.findViewById(viewId);
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    @Override
    public void setViewBackgroundColor(int viewId, int color) {
        mRootView.findViewById(viewId).setBackgroundColor(color);
    }

    @Override
    public void setViewClickIntent(final int viewId, final Intent clickIntent) {
        if (mCallbacks != null) {
            mCallbacks.onModifyClickIntent(clickIntent);
        }

        mRootView.findViewById(viewId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mContext.startActivity(clickIntent);
                mCallbacks.onClickIntentCalled(viewId);
            }
        });
    }

    @Override
    public void setViewClickFillInIntent(final int viewId, final Intent fillIntent) {
        View targetView = mRootView.findViewById(viewId);
        targetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallbacks == null) {
                    return;
                }

                Intent intent = mCallbacks.onGetClickIntentTemplate();
                intent.fillIn(fillIntent, 0);
                mContext.startActivity(intent);
                mCallbacks.onClickIntentCalled(viewId);
            }
        });
    }

    @Override
    public Object getRoot() {
        return mRootView;
    }

    public static interface Callbacks {
        Intent onGetClickIntentTemplate();
        void onModifyClickIntent(Intent clickIntent);
        void onClickIntentCalled(int viewId);
    }
}
