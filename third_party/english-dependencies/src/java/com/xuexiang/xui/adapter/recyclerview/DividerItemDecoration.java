// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Copyright (C) 2019 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.xuexiang.xui.adapter.recyclerview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xuexiang.xui.utils.DensityUtils;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class DividerItemDecoration extends RecyclerView.ItemDecoration {
    
    private Paint mPaint;
    private Drawable mDivider;
    
    private int mDividerHeight;
    
    private int mOrientation;
    private static final int[] ATTRS = new int[]{android.R.attr.listDivider};

    
    private int mExtraPadding;

    
    public DividerItemDecoration(Context context, int orientation) {
        if (orientation != LinearLayoutManager.VERTICAL && orientation != LinearLayoutManager.HORIZONTAL) {
            throw new IllegalArgumentException("Please supply valid parameters!");
        }
        mOrientation = orientation;
        final TypedArray a = context.obtainStyledAttributes(ATTRS);
        mDivider = a.getDrawable(0);
        a.recycle();
        if (mDivider != null) {
            mDividerHeight = mDivider.getIntrinsicHeight();
        } else {
            mDividerHeight = DensityUtils.dp2px(context, 1);
        }
    }

    
    public DividerItemDecoration(Context context, int orientation, int dividerHeight) {
        this(context, orientation);
        mDividerHeight = dividerHeight;

    }

    
    public DividerItemDecoration(Context context, int orientation, int dividerHeight, int dividerColor) {
        this(context, orientation);
        mDividerHeight = dividerHeight;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(dividerColor);
        mPaint.setStyle(Paint.Style.FILL);
    }

    
    public DividerItemDecoration setDivider(@NonNull Drawable divider) {
        mDivider = divider;
        mDividerHeight = mDivider.getIntrinsicHeight();
        return this;
    }

    
    public DividerItemDecoration(Context context, int orientation, int dividerHeight, int dividerColor, int extraPadding) {
        this(context, orientation);
        mDividerHeight = dividerHeight;
        mExtraPadding = extraPadding;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(dividerColor);
        mPaint.setStyle(Paint.Style.FILL);
    }

    
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        if (mOrientation == LinearLayoutManager.VERTICAL) {
            outRect.set(0, 0, 0, mDividerHeight);
        } else {
            outRect.set(0, 0, mDividerHeight, 0);
        }
    }

    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        if (mOrientation == LinearLayoutManager.VERTICAL) {
            drawVertical(canvas, parent);
        } else {
            drawHorizontal(canvas, parent);
        }
    }

    
    private void drawVertical(Canvas canvas, RecyclerView parent) {
        final int left = parent.getPaddingLeft() + mExtraPadding;
        final int right = parent.getMeasuredWidth() - parent.getPaddingRight() - mExtraPadding;
        final int childSize = parent.getChildCount();
        for (int i = 0; i < childSize - 1; i++) {
            final View child = parent.getChildAt(i);
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int top = child.getBottom() + layoutParams.bottomMargin;
            final int bottom = top + mDividerHeight;
            if (mPaint != null) {
                canvas.drawRect(left, top, right, bottom, mPaint);
            } else {
                if (mDivider != null) {
                    mDivider.setBounds(left, top, right, bottom);
                    mDivider.draw(canvas);
                }
            }
        }
    }

    
    private void drawHorizontal(Canvas canvas, RecyclerView parent) {
        final int top = parent.getPaddingTop() + mExtraPadding;
        final int bottom = parent.getMeasuredHeight() - parent.getPaddingBottom() - mExtraPadding;
        final int childSize = parent.getChildCount();
        for (int i = 0; i < childSize - 1; i++) {
            final View child = parent.getChildAt(i);
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int left = child.getRight() + layoutParams.rightMargin;
            final int right = left + mDividerHeight;
            if (mPaint != null) {
                canvas.drawRect(left, top, right, bottom, mPaint);
            } else {
                if (mDivider != null) {
                    mDivider.setBounds(left, top, right, bottom);
                    mDivider.draw(canvas);
                }
            }
        }
    }
}
