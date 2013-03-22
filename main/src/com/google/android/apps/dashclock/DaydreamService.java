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

import com.google.android.apps.dashclock.render.DashClockRenderer;
import com.google.android.apps.dashclock.render.SimpleRenderer;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Daydream for DashClock.
 */
public class DaydreamService extends DreamService implements
        ExtensionManager.OnChangeListener,
        DashClockRenderer.OnClickListener {
    private static final int CYCLE_INTERVAL_MILLIS = 20000;
    private static final long FADE_MILLIS = 5000;

    private Handler mHandler = new Handler();
    private ExtensionManager mExtensionManager;
    private ViewGroup mDaydreamContainer;

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        mExtensionManager = ExtensionManager.getInstance(this);
        mExtensionManager.addOnChangeListener(this);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        mExtensionManager.removeOnChangeListener(this);
        mExtensionManager = null;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(false);
        setScreenBright(false);
        layoutDream();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mHandler.removeCallbacks(mCycleRunnable);
        layoutDream();
    }

    @Override
    public void onExtensionsChanged() {
        mHandler.removeCallbacks(mHandleExtensionsChanged);
        mHandler.postDelayed(mHandleExtensionsChanged,
                ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS);
    }

    private Runnable mHandleExtensionsChanged = new Runnable() {
        @Override
        public void run() {
            renderDaydream();
        }
    };

    private void layoutDream() {
        setContentView(R.layout.daydream);
        renderDaydream();

        mHandler.removeCallbacks(mCycleRunnable);
        mHandler.postDelayed(mCycleRunnable, CYCLE_INTERVAL_MILLIS);
    }

    public class TouchInterFrameLayout extends FrameLayout {
        public TouchInterFrameLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mHandler.removeCallbacks(mCycleRunnable);
                    mHandler.postDelayed(mCycleRunnable, CYCLE_INTERVAL_MILLIS);
                    mDaydreamContainer.animate().alpha(1f).setDuration(250);
                    break;

                // ACTION_UP doesn't seem to reliably get called. Otherwise
                // should postDelayed on ACTION_UP instead of ACTION_DOWN.
            }
            return false;
        }
    }

    private void renderDaydream() {
        mDaydreamContainer = (ViewGroup) findViewById(R.id.daydream_container);
        mDaydreamContainer.removeAllViews();

        Resources res = getResources();

        float dipFactor = getResources().getDisplayMetrics().density;
        int screenHeightDp = (int) (res.getDisplayMetrics().heightPixels * 1f / dipFactor);

        // Render the widget
        SimpleRenderer renderer = new SimpleRenderer(this);
        DashClockRenderer.Options options = new DashClockRenderer.Options();
        options.target = DashClockRenderer.Options.TARGET_DAYDREAM;
        options.minHeightDp = screenHeightDp;
        options.newTaskOnClick = true;
        options.onClickListener = this;

        View dayDreamView = (View) renderer.renderDashClock(mDaydreamContainer, options);
        Utils.traverseAndRecolor(dayDreamView, res.getColor(R.color.daydream_fore_color));

        ViewGroup container = new TouchInterFrameLayout(this);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(dayDreamView);

        mDaydreamContainer.addView(container);
    }

    public Runnable mCycleRunnable = new Runnable() {
        @Override
        public void run() {
            mDaydreamContainer.animate().alpha(0f).setDuration(FADE_MILLIS)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            renderDaydream();

                            mDaydreamContainer.animate().alpha(1f).setDuration(FADE_MILLIS)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            mHandler.removeCallbacks(mCycleRunnable);
                                            mHandler.postDelayed(mCycleRunnable,
                                                    CYCLE_INTERVAL_MILLIS);
                                        }
                                    });
                        }
                    });
        }
    };

    @Override
    public void onClick() {
        // Any time anything in DashClock is clicked
        finish();
    }
}
