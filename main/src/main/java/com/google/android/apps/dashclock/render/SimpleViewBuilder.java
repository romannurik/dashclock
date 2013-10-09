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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.apps.dashclock.LogUtils;

import static com.google.android.apps.dashclock.LogUtils.LOGE;

/**
 * {@link ViewBuilder} implementation for standard framework views.
 */
public class SimpleViewBuilder implements ViewBuilder {
    private static final String TAG = LogUtils.makeLogTag(SimpleViewBuilder.class);

    private Context mContext;
    private View mRootView;
    private Callbacks mCallbacks;

    public SimpleViewBuilder(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
    }

    @Override
    public void useRoot(Object root) {
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
        try {
            ((ImageView) mRootView.findViewById(viewId)).setImageBitmap(bitmap);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setViewContentDescription(int viewId, String contentDescription) {
        try {
            mRootView.findViewById(viewId).setContentDescription(contentDescription);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setTextViewText(int viewId, CharSequence text) {
        try {
            ((TextView) mRootView.findViewById(viewId)).setText(text);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setTextViewColor(int viewId, int color) {
        try {
            ((TextView) mRootView.findViewById(viewId)).setTextColor(color);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setTextViewMaxLines(int viewId, int maxLines) {
        try {
            ((TextView) mRootView.findViewById(viewId)).setMaxLines(maxLines);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setTextViewSingleLine(int viewId, boolean singleLine) {
        try {
            ((TextView) mRootView.findViewById(viewId)).setSingleLine(singleLine);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setTextViewTextSize(int viewId, int unit, float size) {
        try {
            ((TextView) mRootView.findViewById(viewId)).setTextSize(unit, size);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setLinearLayoutGravity(int viewId, int gravity) {
        try {
            ((LinearLayout) mRootView.findViewById(viewId)).setGravity(gravity);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setViewPadding(int viewId, int left, int top, int right, int bottom) {
        try {
            mRootView.findViewById(viewId).setPadding(left, top, right, bottom);
        } catch (NullPointerException ignored) {
        }
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
        try {
            mRootView.findViewById(viewId).setVisibility(visibility);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setViewBackgroundColor(int viewId, int color) {
        try {
            mRootView.findViewById(viewId).setBackgroundColor(color);
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setViewClickIntent(final int viewId, final Intent clickIntent) {
        try {
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
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void setViewClickFillInIntent(final int viewId, final Intent fillIntent) {
        try {
            View targetView = mRootView.findViewById(viewId);
            targetView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mCallbacks == null) {
                        return;
                    }

                    try {
                        Intent intent = mCallbacks.onGetClickIntentTemplate();
                        intent.fillIn(fillIntent, 0);
                        mContext.startActivity(intent);
                        mCallbacks.onClickIntentCalled(viewId);
                    } catch (SecurityException e) {
                        LOGE(TAG, "Can't click extension.", e);
                    } catch (ActivityNotFoundException e) {
                        LOGE(TAG, "Can't click extension.", e);
                    }
                }
            });
        } catch (NullPointerException ignored) {
        }
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
