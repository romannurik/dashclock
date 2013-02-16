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

package com.google.android.apps.dashclock.ui;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper class for creating swipeable tabs without the use of {@link android.app.ActionBar} APIs.
 */
public class SimplePagedTabsHelper {
    private Context mContext;
    private ViewGroup mTabContainer;
    private ViewPager mPager;
    private Map<View, Integer> mTabPositions = new HashMap<View, Integer>();
    private List<Integer> mTabContentIds = new ArrayList<Integer>();

    public SimplePagedTabsHelper(Context context, ViewGroup tabContainer, ViewPager pager) {
        mContext = context;
        mTabContainer = tabContainer;
        mPager = pager;

        pager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return mTabContentIds.size();
            }

            @Override
            public boolean isViewFromObject(View view, Object o) {
                return view == o;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                return mPager.findViewById(mTabContentIds.get(position));
            }
        });
        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < mTabContainer.getChildCount(); i++) {
                    mTabContainer.getChildAt(i).setSelected(i == position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });
    }

    public void addTab(int labelResId, int contentViewId) {
        addTab(mContext.getString(labelResId), contentViewId);
    }

    public void addTab(CharSequence label, int contentViewId) {
        View tabView = LayoutInflater.from(mContext).inflate(R.layout.tab, mTabContainer, false);
        ((TextView) tabView.findViewById(R.id.tab)).setText(label);
        tabView.setOnClickListener(mTabClickListener);
        int position = mTabContentIds.size();
        tabView.setSelected(mPager.getCurrentItem() == position);
        mTabPositions.put(tabView, position);
        mTabContainer.addView(tabView);
        mTabContentIds.add(contentViewId);
    }

    private View.OnClickListener mTabClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mPager.setCurrentItem(mTabPositions.get(view));
        }
    };
}
