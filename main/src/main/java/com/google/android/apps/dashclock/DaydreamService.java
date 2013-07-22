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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
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

import com.google.android.apps.dashclock.configuration.AppearanceConfig;
import com.google.android.apps.dashclock.render.DashClockRenderer;
import com.google.android.apps.dashclock.render.SimpleRenderer;
import com.google.android.apps.dashclock.render.SimpleViewBuilder;

import net.nurik.roman.dashclock.R;

import java.util.List;

import static com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData;

/**
 * Daydream for DashClock.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class DaydreamService extends DreamService implements
        ExtensionManager.OnChangeListener,
        DashClockRenderer.OnClickListener {
    public static final String PREF_DAYDREAM_COLOR = "pref_daydream_color";
    public static final String PREF_DAYDREAM_NIGHT_MODE = "pref_daydream_night_mode";
    public static final String PREF_DAYDREAM_ANIMATION = "pref_daydream_animation";

    private static final int ANIMATION_HAS_ROTATE = 0x1;
    private static final int ANIMATION_HAS_SLIDE = 0x2;
    private static final int ANIMATION_HAS_FADE = 0x4;

    private static final int ANIMATION_NONE = 0;
    private static final int ANIMATION_FADE = ANIMATION_HAS_FADE;
    private static final int ANIMATION_SLIDE = ANIMATION_FADE | ANIMATION_HAS_SLIDE;
    private static final int ANIMATION_PENDULUM = ANIMATION_SLIDE | ANIMATION_HAS_ROTATE;

    private static final int CYCLE_INTERVAL_MILLIS = 20000;
    private static final int FADE_MILLIS = 5000;
    private static final int TRAVEL_ROTATE_DEGREES = 3;
    private static final float SCALE_WHEN_MOVING = 0.85f;

    private Handler mHandler = new Handler();
    private ExtensionManager mExtensionManager;
    private int mTravelDistance;
    private int mForegroundColor;
    private int mAnimation;

    private ViewGroup mDaydreamContainer;
    private ViewGroup mExtensionsContainer;
    private AnimatorSet mSingleCycleAnimator;

    private boolean mAttached;
    private boolean mNeedsRelayout;
    private boolean mMovingLeft;
    private boolean mManuallyAwoken;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mExtensionManager = ExtensionManager.getInstance(this);
        mExtensionManager.addOnChangeListener(this);

        mAttached = true;
        setInteractive(true);
        setFullscreen(true);

        // Read preferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mForegroundColor = sp.getInt(PREF_DAYDREAM_COLOR,
                AppearanceConfig.DEFAULT_WIDGET_FOREGROUND_COLOR);

        String animation = sp.getString(PREF_DAYDREAM_ANIMATION, "");
        if ("none".equals(animation)) {
            mAnimation = ANIMATION_NONE;
        } else if ("slide".equals(animation)) {
            mAnimation = ANIMATION_SLIDE;
        } else if ("fade".equals(animation)) {
            mAnimation = ANIMATION_FADE;
        } else {
            mAnimation = ANIMATION_PENDULUM;
        }

        setScreenBright(!sp.getBoolean(PREF_DAYDREAM_NIGHT_MODE, true));

        // Begin daydream
        layoutDream();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mExtensionManager.removeOnChangeListener(this);
        mExtensionManager = null;
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
        if (!mAttached || mExtensionManager == null) {
            return;
        }

        if (restartAnimation) {
            // Only modify fullscreen state if this render will restart an animation (enter a new
            // cycle)
            setFullscreen(true);
        }

        final Resources res = getResources();

        mDaydreamContainer = (ViewGroup) findViewById(R.id.daydream_container);
        RootLayout rootContainer = (RootLayout)
                findViewById(R.id.daydream_root);
        if (mTravelDistance == 0) {
            mTravelDistance = rootContainer.getWidth() / 4;
        }
        rootContainer.setRootLayoutListener(new RootLayout.RootLayoutListener() {
            @Override
            public void onAwake() {
                mManuallyAwoken = true;
                setFullscreen(false);
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
                if (mSingleCycleAnimator != null) {
                    mSingleCycleAnimator.cancel();
                }
            }

            @Override
            public boolean isAwake() {
                return mManuallyAwoken;
            }

            @Override
            public void onSizeChanged(int width, int height) {
                mTravelDistance = width / 4;
            }
        });

        DisplayMetrics displayMetrics = res.getDisplayMetrics();

        int screenWidthDp = (int) (displayMetrics.widthPixels * 1f / displayMetrics.density);
        int screenHeightDp = (int) (displayMetrics.heightPixels * 1f / displayMetrics.density);

        // Set up rendering
        SimpleRenderer renderer = new SimpleRenderer(this);
        DashClockRenderer.Options options = new DashClockRenderer.Options();
        options.target = DashClockRenderer.Options.TARGET_DAYDREAM;
        options.foregroundColor = Color.WHITE;
        options.minWidthDp = screenWidthDp;
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
                = mExtensionManager.getVisibleExtensionsWithData();
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
            int x = 0;
            int deg = 0;
            if ((mAnimation & ANIMATION_HAS_SLIDE) != 0) {
                x = (mMovingLeft ? 1 : -1) * mTravelDistance;
            }
            if ((mAnimation & ANIMATION_HAS_ROTATE) != 0) {
                deg = (mMovingLeft ? 1 : -1) * TRAVEL_ROTATE_DEGREES;
            }
            mMovingLeft = !mMovingLeft;
            mDaydreamContainer.animate().cancel();
            if ((mAnimation & ANIMATION_HAS_SLIDE) != 0) {
                // Only use small size when moving
                mDaydreamContainer.setScaleX(SCALE_WHEN_MOVING);
                mDaydreamContainer.setScaleY(SCALE_WHEN_MOVING);
            }
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
            mManuallyAwoken = false;
            float outAlpha = 1f;
            if ((mAnimation & ANIMATION_HAS_FADE) != 0) {
                outAlpha = 0f;
            }
            mDaydreamContainer.animate().alpha(outAlpha).setDuration(FADE_MILLIS)
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
    public static class RootLayout extends FrameLayout {
        private RootLayoutListener mRootLayoutListener;
        private boolean mCancelCurrentEvent;

        public RootLayout(Context context) {
            super(context);
        }

        public RootLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public RootLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                // ACTION_UP doesn't seem to reliably get called. Otherwise
                // should postDelayed on ACTION_UP instead of ACTION_DOWN.
                case MotionEvent.ACTION_DOWN:
                    if (mRootLayoutListener != null && !mRootLayoutListener.isAwake()) {
                        mCancelCurrentEvent = true;
                        mRootLayoutListener.onAwake();
                    } else {
                        mCancelCurrentEvent = false;
                    }
                    break;
            }
            return mCancelCurrentEvent;
        }

        public void setRootLayoutListener(RootLayoutListener rootLayoutListener) {
            mRootLayoutListener = rootLayoutListener;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (mRootLayoutListener != null) {
                mRootLayoutListener.onSizeChanged(w, h);
            }
        }

        public static interface RootLayoutListener {
            void onAwake();
            void onSizeChanged(int width, int height);
            boolean isAwake();
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
