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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.EdgeEffect;
import android.widget.ScrollView;

import net.nurik.roman.dashclock.R;

import java.lang.reflect.Field;

public class EdgeEffectUtil {
    public static void tryChangeEdgeEffects(ScrollView view, int color) {
        final Resources res = view.getResources();
        tryChangeEdgeEffects(view,
                recolor(res, res.getDrawable(R.drawable.overscroll_edge), color),
                recolor(res, res.getDrawable(R.drawable.overscroll_glow), color));
    }

    public static void tryChangeEdgeEffects(ScrollView view, Drawable edgeDrawable,
            Drawable glowDrawable) {
        try {
            Field edgeGlowTopField = ScrollView.class.getDeclaredField("mEdgeGlowTop");
            edgeGlowTopField.setAccessible(true);
            Field edgeGlowBottomField = ScrollView.class.getDeclaredField("mEdgeGlowBottom");
            edgeGlowBottomField.setAccessible(true);
            EdgeEffect edgeGlowTop = (EdgeEffect) edgeGlowTopField.get(view);
            EdgeEffect edgeGlowBottom = (EdgeEffect) edgeGlowBottomField.get(view);
            tryChangeEdgeEffects(edgeGlowTop, edgeDrawable, glowDrawable);
            tryChangeEdgeEffects(edgeGlowBottom, edgeDrawable, glowDrawable);
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (ClassCastException ignored) {
        } // TODO: catch all exceptions?
    }

    public static void tryChangeEdgeEffects(EdgeEffect edgeEffect, Drawable edgeDrawable,
            Drawable glowDrawable) {
        try {
            Field edgeField = EdgeEffect.class.getDeclaredField("mEdge");
            edgeField.setAccessible(true);
            Field glowField = EdgeEffect.class.getDeclaredField("mGlow");
            glowField.setAccessible(true);
            Drawable oldEdge = (Drawable) edgeField.get(edgeEffect);
            Drawable oldGlow = (Drawable) glowField.get(edgeEffect);
            edgeField.set(edgeEffect, edgeDrawable);
            glowField.set(edgeEffect, glowDrawable);
            if (oldEdge != null) {
                oldEdge.setCallback(null);
            }
            if (oldGlow != null) {
                oldGlow.setCallback(null);
            }
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (ClassCastException ignored) {
        } // TODO: catch all exceptions?
    }

    private static Drawable recolor(Resources res, Drawable drawable, int color) {
        if (drawable == null) {
            return null;
        }

        Bitmap outBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outBitmap);
        drawable.setBounds(0, 0, outBitmap.getWidth(), outBitmap.getHeight());
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        drawable.draw(canvas);
        drawable.setColorFilter(null);
        drawable.setCallback(null); // free up any references
        return new BitmapDrawable(res, outBitmap);
    }
}
