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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.List;

import static com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData;

/**
 * Daydream for DashClock.
 */
public class DaydreamService extends DreamService implements
        ExtensionManager.OnChangeListener,
        DashClockRenderer.OnClickListener {
    private static final String PREF_DAYDREAM_COLOR = "pref_daydream_color";
    private static final int DEFAULT_FOREGROUND_COLOR = 0xffffffff;

    private static final int CYCLE_INTERVAL_MILLIS = 20000;
    private static final int FADE_MILLIS = 5000;
    private static final int TRAVEL_ROTATE_DEGREES = 3;

    private Handler mHandler = new Handler();
    private ExtensionManager mExtensionManager;
    private int mTravelDistance;
    private int mForegroundColor;

    private ViewGroup mDaydreamContainer;
    private ViewGroup mExtensionsContainer;
    private AnimatorSet mSingleCycleAnimator;

    private boolean mAttached;
    private boolean mNeedsRelayout;
    private boolean mMovingLeft;

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

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mForegroundColor = sp.getInt(PREF_DAYDREAM_COLOR, DEFAULT_FOREGROUND_COLOR);

        Resources res = getResources();
        mTravelDistance = res.getDimensionPixelSize(R.dimen.daydream_travel_distance);

        layoutDream();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacksAndMessages(null);
        mAttached = false;
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
            renderDaydream(false);
        }
    };

    private void layoutDream() {
        setContentView(R.layout.daydream);
        mNeedsRelayout = true;
        renderDaydream(true);

        mHandler.removeCallbacks(mCycleRunnable);
        mHandler.postDelayed(mCycleRunnable, CYCLE_INTERVAL_MILLIS - FADE_MILLIS);
    }

    private void renderDaydream(final boolean restartAnimation) {
        if (!mAttached) {
            return;
        }

        final Resources res = getResources();

        mDaydreamContainer = (ViewGroup) findViewById(R.id.daydream_container);
        TouchToAwakeFrameLayout awakeContainer = (TouchToAwakeFrameLayout)
                findViewById(R.id.touch_awake_container);
        awakeContainer.setOnAwakeListener(new TouchToAwakeFrameLayout.OnAwakeListener() {
            @Override
            public void onAwake() {
                mHandler.removeCallbacks(mCycleRunnable);
                mHandler.postDelayed(mCycleRunnable, CYCLE_INTERVAL_MILLIS);
                mDaydreamContainer.animate()
                        .alpha(1f)
                        .rotation(0)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(res.getInteger(android.R.integer.config_shortAnimTime));
                mSingleCycleAnimator.cancel();
            }
        });

        DisplayMetrics displayMetrics = res.getDisplayMetrics();

        int screenHeightDp = (int) (displayMetrics.heightPixels * 1f / displayMetrics.density);

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
        mExtensionsContainer = (ViewGroup) findViewById(R.id.extensions_container);
        mExtensionsContainer.removeAllViews();
        List<ExtensionWithData> visibleExtensions
                = ExtensionManager.getInstance(this).getVisibleExtensionsWithData();
        for (ExtensionWithData ewd : visibleExtensions) {
            mExtensionsContainer.addView(
                    (View) renderer.renderExpandedExtension(mExtensionsContainer, null, ewd));
        }

        if (mDaydreamContainer.getHeight() == 0 || mNeedsRelayout) {
            ViewTreeObserver vto = mDaydreamContainer.getViewTreeObserver();
            if (vto.isAlive()) {
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        ViewTreeObserver vto = mDaydreamContainer.getViewTreeObserver();
                        if (vto.isAlive()) {
                            vto.removeOnGlobalLayoutListener(this);
                        }

                        postLayoutRender(restartAnimation);
                    }
                });
            }
            mDaydreamContainer.requestLayout();
            mNeedsRelayout = false;
        } else {
            postLayoutRender(restartAnimation);
        }
    }

    /**
     * Post-layout render code.
     */
    public void postLayoutRender(boolean restartAnimation) {
        Resources res = getResources();

        // Adjust the ScrollView
        ExposedScrollView scrollView = (ExposedScrollView) findViewById(R.id.extensions_scroller);
        int maxScroll = scrollView.computeVerticalScrollRange() - scrollView.getHeight();
        if (maxScroll < 0) {
            ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
            lp.height = mExtensionsContainer.getHeight();
            scrollView.setLayoutParams(lp);
            mDaydreamContainer.requestLayout();
        }

        // Recolor widget
        Utils.traverseAndRecolor(mDaydreamContainer, mForegroundColor, true);

        if (restartAnimation) {
            int x = (mMovingLeft ? 1 : -1) * mTravelDistance;
            int deg = (mMovingLeft ? 1 : -1) * TRAVEL_ROTATE_DEGREES;
            mMovingLeft = !mMovingLeft;
            mDaydreamContainer.animate().cancel();
            mDaydreamContainer.setScaleX(0.85f);
            mDaydreamContainer.setScaleY(0.85f);
            if (mSingleCycleAnimator != null) {
                mSingleCycleAnimator.cancel();
            }

            Animator scrollDownAnimator = ObjectAnimator.ofInt(scrollView,
                    ExposedScrollView.SCROLL_POS, 0, maxScroll);
            scrollDownAnimator.setDuration(CYCLE_INTERVAL_MILLIS / 5);
            scrollDownAnimator.setStartDelay(CYCLE_INTERVAL_MILLIS / 5);

            Animator scrollUpAnimator = ObjectAnimator.ofInt(scrollView,
                    ExposedScrollView.SCROLL_POS, 0);
            scrollUpAnimator.setDuration(CYCLE_INTERVAL_MILLIS / 5);
            scrollUpAnimator.setStartDelay(CYCLE_INTERVAL_MILLIS / 5);

            AnimatorSet scrollAnimator = new AnimatorSet();
            scrollAnimator.playSequentially(scrollDownAnimator, scrollUpAnimator);

            Animator moveAnimator = ObjectAnimator.ofFloat(mDaydreamContainer,
                    View.TRANSLATION_X, x, -x).setDuration(CYCLE_INTERVAL_MILLIS);
            moveAnimator.setInterpolator(new LinearInterpolator());

            Animator rotateAnimator = ObjectAnimator.ofFloat(mDaydreamContainer,
                    View.ROTATION, deg, -deg).setDuration(CYCLE_INTERVAL_MILLIS);
            moveAnimator.setInterpolator(new LinearInterpolator());

            mSingleCycleAnimator = new AnimatorSet();
            mSingleCycleAnimator.playTogether(scrollAnimator, moveAnimator, rotateAnimator);
            mSingleCycleAnimator.start();
        }
    }

    public Runnable mCycleRunnable = new Runnable() {
        @Override
        public void run() {
            mDaydreamContainer.animate().alpha(0f).setDuration(FADE_MILLIS)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            renderDaydream(true);
                            mHandler.removeCallbacks(mCycleRunnable);
                            mHandler.postDelayed(mCycleRunnable,
                                    CYCLE_INTERVAL_MILLIS - FADE_MILLIS);
                            mDaydreamContainer.animate().alpha(1f).setDuration(FADE_MILLIS);
                        }
                    });
        }
    };

    @Override
    public void onClick() {
        // Any time anything in DashClock is clicked
        finish();
    }

    /**
     * FrameLayout that can notify listeners of ACTION_DOWN events.
     */
    public static class TouchToAwakeFrameLayout extends FrameLayout {
        private OnAwakeListener mOnAwakeListener;

        public TouchToAwakeFrameLayout(Context context) {
            super(context);
        }

        public TouchToAwakeFrameLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TouchToAwakeFrameLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (mOnAwakeListener != null) {
                        mOnAwakeListener.onAwake();
                    }
                    break;

                // ACTION_UP doesn't seem to reliably get called. Otherwise
                // should postDelayed on ACTION_UP instead of ACTION_DOWN.
            }
            return false;
        }

        public void setOnAwakeListener(OnAwakeListener onAwakeListener) {
            mOnAwakeListener = onAwakeListener;
        }

        public static interface OnAwakeListener {
            void onAwake();
        }
    }

    /**
     * ScrollView that exposes its scroll range.
     */
    public static class ExposedScrollView extends ScrollView {
        public ExposedScrollView(Context context) {
            super(context);
        }

        public ExposedScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public ExposedScrollView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public int computeVerticalScrollRange() {
            return super.computeVerticalScrollRange();
        }

        public static final Property<ScrollView, Integer> SCROLL_POS
                = new Property<ScrollView, Integer>(Integer.class, "scrollPos") {
            @Override
            public void set(ScrollView object, Integer value) {
                object.scrollTo(0, value);
            }

            @Override
            public Integer get(ScrollView object) {
                return object.getScrollY();
            }
        };
    }
}
