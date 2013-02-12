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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class PagerPositionStrip extends View {
    private static final int[] ATTRS = new int[]{
            android.R.attr.color,
    };

    private int mPageCount;
    private float mPosition;

    private int mColor = 0xff000000;

    private float mIndicatorWidth;
    private Paint mIndicatorPaint;

    private RectF mTempRectF = new RectF();

    private int mWidth;
    private int mHeight;

    public PagerPositionStrip(Context context) {
        this(context, null, 0);
    }

    public PagerPositionStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagerPositionStrip(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);
        mColor = a.getColor(0, mColor);
        a.recycle();

        mIndicatorPaint = new Paint();
        mIndicatorPaint.setColor(mColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mPageCount == 0) {
            return;
        }

        mTempRectF.top = 0;
        mTempRectF.bottom = mHeight;
        mTempRectF.left = mPosition / mPageCount * (mWidth);
        mTempRectF.right = mTempRectF.left + mIndicatorWidth;

        canvas.drawRect(mTempRectF, mIndicatorPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                View.resolveSize(0, widthMeasureSpec),
                View.resolveSize(0, heightMeasureSpec));
    }

    public void setPosition(float currentPosition) {
        mPosition = currentPosition;
        postInvalidateOnAnimation();
    }

    public void setPageCount(int count) {
        mPageCount = count;
        recalculateIndicatorWidth();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mHeight = h;
        mWidth = w;
        recalculateIndicatorWidth();
    }

    private void recalculateIndicatorWidth() {
        mIndicatorWidth = (mPageCount > 0) ? (mWidth * 1f / mPageCount) : 0;
    }
}
