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

import com.google.android.apps.dashclock.ExtensionManager;
import com.google.android.apps.dashclock.Utils;

import net.nurik.roman.dashclock.R;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
* This adapter renders 'expanded'-style DashClock extensions.
*/
public class SimpleExpandedExtensionsAdapter extends BaseAdapter {
    private Context mContext;
    private SimpleRenderer mRenderer;
    private DashClockRenderer.Options mOptions;

    private List<ExtensionManager.ExtensionWithData>
            mVisibleExtensions = new ArrayList<ExtensionManager.ExtensionWithData>();

    public SimpleExpandedExtensionsAdapter(Context context, SimpleRenderer renderer,
            DashClockRenderer.Options options) {
        mContext = context;
        mRenderer = renderer;
        mRenderer.setOptions(options);

        mVisibleExtensions = ExtensionManager.getInstance(context).getVisibleExtensionsWithData();
    }

    @Override
    public int getCount() {
        return mVisibleExtensions.size();
    }

    @Override
    public Object getItem(int position) {
        return mVisibleExtensions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mVisibleExtensions.get(position).listing.componentName.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {
        convertView = (View) mRenderer.renderExpandedExtension(container, convertView,
                mVisibleExtensions.get(position));
        Utils.traverseAndRecolor(convertView,
                mContext.getResources().getColor(R.color.daydream_fore_color));
        return convertView;
    }
}
