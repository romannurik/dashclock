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

package com.google.android.apps.dashclock.configuration;

import com.google.android.apps.dashclock.ui.PagerPositionStrip;

import net.nurik.roman.dashclock.R;

import android.app.Fragment;
import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment for allowing the user to configure widget appearance, shown within a {@link
 * ConfigurationActivity}.
 */
public class ConfigureAppearanceFragment extends Fragment {
    private static final int AUTO_HIDE_DELAY_MILLIS = 1000;

    private Handler mHandler = new Handler();

    private Map<View, Runnable> mHidePositionStripRunnables = new HashMap<View, Runnable>();

    private Map<String, String> mCurrentStyleNames = new HashMap<String, String>();
    private int mAnimationDuration;
    private View[] mPositionStrips;

    public ConfigureAppearanceFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_configure_appearance, container, false);

        rootView.findViewById(R.id.container).setBackgroundColor(
                AppearanceConfig.getHomescreenBackgroundColor(getActivity()));

        mCurrentStyleNames.put(
                AppearanceConfig.PREF_STYLE_TIME,
                sp.getString(AppearanceConfig.PREF_STYLE_TIME,
                        AppearanceConfig.TIME_STYLE_NAMES[0]));
        configureStylePager(
                (ViewPager) rootView.findViewById(R.id.pager_time_style),
                (PagerPositionStrip) rootView.findViewById(R.id.pager_time_position_strip),
                AppearanceConfig.TIME_STYLE_NAMES, AppearanceConfig.COMPONENT_TIME,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, AppearanceConfig.PREF_STYLE_TIME);

        mCurrentStyleNames.put(
                AppearanceConfig.PREF_STYLE_DATE,
                sp.getString(AppearanceConfig.PREF_STYLE_DATE,
                        AppearanceConfig.DATE_STYLE_NAMES[0]));
        configureStylePager(
                (ViewPager) rootView.findViewById(R.id.pager_date_style),
                (PagerPositionStrip) rootView.findViewById(R.id.pager_date_position_strip),
                AppearanceConfig.DATE_STYLE_NAMES, AppearanceConfig.COMPONENT_DATE,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, AppearanceConfig.PREF_STYLE_DATE);
        ((ConfigurationActivity) getActivity()).setTranslucentActionBar(true);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        View rootView = getView();
        if (rootView != null) {
            mPositionStrips = new View[] {
                    rootView.findViewById(R.id.pager_time_position_strip),
                    rootView.findViewById(R.id.pager_date_position_strip)
            };
            for (View strip : mPositionStrips) {
                strip.setAlpha(0f);
            }
            showPositionStrips(true, AUTO_HIDE_DELAY_MILLIS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        final SharedPreferences.Editor sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity()).edit();
        for (String key : mCurrentStyleNames.keySet()) {
            sp.putString(key, mCurrentStyleNames.get(key));
        }
        sp.commit();

        new BackupManager(getActivity()).dataChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // remove all scheduled runnables
        mHidePositionStripRunnables.clear();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void configureStylePager(final ViewPager pager, final PagerPositionStrip positionStrip,
            final String[] styleNames, final String styleComponent,
            final int gravity, final String preference) {
        String currentStyleName = mCurrentStyleNames.get(preference);

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        pager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return styleNames.length;
            }

            @Override
            public boolean isViewFromObject(View view, Object o) {
                return view == o;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                FrameLayout wrapper = new FrameLayout(getActivity());
                ViewPager.LayoutParams wrapperLp = new ViewPager.LayoutParams();
                wrapper.setLayoutParams(wrapperLp);
                View v = inflater.inflate(
                        AppearanceConfig.getLayoutByStyleName(
                                getActivity(), styleComponent, styleNames[position]),
                        container, false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = gravity;
                v.setLayoutParams(lp);
                wrapper.addView(v);
                container.addView(wrapper);
                return wrapper;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        });

        pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mCurrentStyleNames.put(preference, styleNames[position]);
            }

            @Override
            public void onPageScrolled(int position, float positionOffset,
                    int positionOffsetPixels) {
                positionStrip.setPosition(position + positionOffset);
            }
        });

        positionStrip.setPageCount(styleNames.length);

        pager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        showPositionStrips(true, -1);
                        break;

                    case MotionEvent.ACTION_UP:
                        showPositionStrips(false, AUTO_HIDE_DELAY_MILLIS);
                        break;
                }
                return false;
            }
        });

        for (int i = 0; i < styleNames.length; i++) {
            if (currentStyleName.equals(styleNames[i])) {
                pager.setCurrentItem(i);
                positionStrip.setPosition(i);
                break;
            }
        }
    }

    private void showPositionStrips(final boolean show, final int hideDelay) {
        // remove any currently scheduled runnables
        mHandler.removeCallbacks(mHidePositionStripsRunnable);

        // if show or hide immediately, take action now
        if (show || hideDelay <= 0) {
            for (View strip : mPositionStrips) {
                strip.animate().cancel();
                strip.animate().alpha(show ? 1f : 0f).setDuration(mAnimationDuration);
            }
        }

        // schedule a hide if hideDelay > 0
        if (hideDelay > 0) {
            mHandler.postDelayed(mHidePositionStripsRunnable, hideDelay);
        }
    }

    private Runnable mHidePositionStripsRunnable = new Runnable() {
        @Override
        public void run() {
            showPositionStrips(false, 0);
        }
    };

    @Override
    public void onDetach() {
        super.onDetach();
        ((ConfigurationActivity) getActivity()).setTranslucentActionBar(false);
    }
}
