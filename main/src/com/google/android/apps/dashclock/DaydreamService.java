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
import com.google.android.apps.dashclock.render.SimpleViewBuilder;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

import static android.view.View.MeasureSpec;
import static com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData;

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
    private boolean mAttached;

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
        mAttached = true;
        setInteractive(true);
        setFullscreen(false);
        setScreenBright(false);
        layoutDream();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacksAndMessages(null);
        mAttached = true;
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

    public class TouchToAwakeFrameLayout extends FrameLayout {
        public TouchToAwakeFrameLayout(Context context) {
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
        if (!mAttached) {
            return;
        }

        mDaydreamContainer = (ViewGroup) findViewById(R.id.daydream_container);

        Resources res = getResources();
        DisplayMetrics displayMetrics = res.getDisplayMetrics();

        int screenHeightDp = (int) (displayMetrics.heightPixels * 1f / displayMetrics.density);

//        int maxWidth = res.getDimensionPixelSize(R.dimen.daydream_max_width);
//
//        ViewGroup.LayoutParams lp = mDaydreamContainer.getLayoutParams();
//        lp.width = Math.min(maxWidth, displayMetrics.widthPixels);
//        mDaydreamContainer.setLayoutParams(lp);

        // Set up rendering
        SimpleRenderer renderer = new SimpleRenderer(this);
        DashClockRenderer.Options options = new DashClockRenderer.Options();
        options.target = DashClockRenderer.Options.TARGET_DAYDREAM;
        options.minHeightDp = screenHeightDp;
        options.newTaskOnClick = true;
        options.onClickListener = this;
        options.clickIntentTemplate = WidgetClickProxyActivity.getTemplate(this);
        renderer.setOptions(options);

        // Render the clock face
        SimpleViewBuilder vb = renderer.createSimpleViewBuilder();
        vb.useRoot(mDaydreamContainer);
        renderer.renderClockFace(vb);
        vb.setLinearLayoutGravity(R.id.clock_target, Gravity.CENTER_HORIZONTAL);

        // Render extensions
        ViewGroup extensionsContainer = (ViewGroup) findViewById(R.id.extensions_container);
        extensionsContainer.removeAllViews();
        List<ExtensionWithData> visibleExtensions
                = ExtensionManager.getInstance(this).getVisibleExtensionsWithData();
        for (ExtensionWithData ewd : visibleExtensions) {
            extensionsContainer.addView(
                    (View) renderer.renderExpandedExtension(extensionsContainer, null, ewd));
        }

        // Adjust the ScrollView
        View scrollView = findViewById(R.id.extensions_scroller);
        mDaydreamContainer.measure(
                MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels, MeasureSpec.AT_MOST));
        int contentHeight = extensionsContainer.getMeasuredHeight();
        int maxAvailableHeight = (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                ? displayMetrics.heightPixels
                : scrollView.getMeasuredHeight();

        if (contentHeight < maxAvailableHeight) {
            ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
            lp.height = contentHeight + 2;
            scrollView.setLayoutParams(lp);
            mDaydreamContainer.requestLayout();
        }

        // Recolor widget
        Utils.traverseAndRecolor(mDaydreamContainer, res.getColor(R.color.daydream_fore_color));

//        ViewGroup container = new TouchToAwakeFrameLayout(this);
//        container.setLayoutParams(new ViewGroup.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//        container.addView(dayDreamView);
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
