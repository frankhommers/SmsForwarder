// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xui.widget.imageview.preview.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.xuexiang.xui.R;


/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class BezierBannerView extends View implements ViewPager.OnPageChangeListener {
    
    private Paint mCirclePaint;
    
    private Paint mCirclePaint2;
    
    private Path mPath = new Path();
    
    private Path mPath2 = new Path();
    
    private int mSelectedColor;
    
    private int mUnSelectedColor;
    
    private float distance = 80;
    
    private float mRadius = 30;
    
    private float mChangeRadius;
    
    private float mNomarlRadius = 20;
    
    private float mChangeBgRadius;
    
    private float mSupportChangeRadius;
    
    private float mSupport_Next_ChangeRadius;
    
    float mCenterPointX;
    float mCenterPointY;
    
    float mSupportCircleX;
    float mSupportCircleY;
    
    float mSupport_next_centerX;
    float mSupport_next_centerY;
    
    float mbgNextPointX;
    float mbgNextPointY;

    
    private boolean autoMove = false;
    
    private float mProgress = 0;
    
    private float mProgress2 = 0;
    
    private float mOriginProgress;

    
    private int mSelectedIndex = 0;
    private int count;

    
    private int MOVE_STEP_ONE = 1;
    
    private int MOVE_STEP_TWO = 2;
    
    float controlPointX;
    float controlPointY;
    
    float mStartX;
    float mStartY;
    
    float endPointX;
    float endPointY;
    private int mDrection;
    
    public static int DIRECTION_LEFT = 1;
    
    public static int DIRECTION_RIGHT = 2;
    private static final String TAG = BezierBannerView.class.getName();

    Interpolator accelerateinterpolator = new AccelerateDecelerateInterpolator();


    public BezierBannerView(Context context) {
        this(context, null);
    }

    public BezierBannerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BezierBannerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttrs(attrs);
        initPaint();
    }


    private void initPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(mSelectedColor);
        p.setStyle(Paint.Style.FILL);
        p.setAntiAlias(true);
        p.setDither(true);
        mCirclePaint = p;

        Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p1.setColor(mUnSelectedColor);
        p1.setStyle(Paint.Style.FILL);
        p1.setAntiAlias(true);
        p1.setDither(true);
        mCirclePaint2 = p1;

    }

    private void initAttrs(AttributeSet attrs) {
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.BezierBannerView);
        mSelectedColor = typedArray.getColor(R.styleable.BezierBannerView_bbv_selectedColor, 0xFFFFFFFF);
        mUnSelectedColor = typedArray.getColor(R.styleable.BezierBannerView_bbv_unSelectedColor, 0xFFAAAAAA);
        mRadius = typedArray.getDimension(R.styleable.BezierBannerView_bbv_selectedRadius, mRadius);
        mNomarlRadius = typedArray.getDimension(R.styleable.BezierBannerView_bbv_unSelectedRadius, mNomarlRadius);
        distance = typedArray.getDimension(R.styleable.BezierBannerView_bbv_spacing, distance);
        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        
        int width = (int) (mNomarlRadius * 2 * count + (mRadius - mNomarlRadius) * 2 + (count - 1) * distance + getPaddingLeft() + getPaddingRight());
        int height = (int) (2 * mRadius + getPaddingTop() + getPaddingBottom());

        int mHeight, mWidth;

        if (widthMode == MeasureSpec.EXACTLY) {
            mWidth = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            mWidth = Math.min(widthSize, width);
        } else {
            mWidth = widthSize;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            mHeight = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            mHeight = Math.min(heightSize, height);
        } else {
            mHeight = heightSize;
        }

        setMeasuredDimension(mWidth, mHeight);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());

        
        for (int i = 0; i < count; i++) {
            if (mDrection == DIRECTION_RIGHT) {
                if (i == mSelectedIndex || i == mSelectedIndex + 1) {
                    
                } else {
                    canvas.drawCircle(getCenterPointAt(i), mRadius, mNomarlRadius, mCirclePaint2);
                }

            } else if (mDrection == DIRECTION_LEFT) {
                if (i == mSelectedIndex || i == mSelectedIndex - 1) {
                    
                } else {
                    canvas.drawCircle(getCenterPointAt(i), mRadius, mNomarlRadius, mCirclePaint2);
                }
            }
        }

        
        canvas.drawCircle(mSupport_next_centerX, mSupport_next_centerY, mSupport_Next_ChangeRadius, mCirclePaint2);
        canvas.drawCircle(mbgNextPointX, mbgNextPointY, mChangeBgRadius, mCirclePaint2);
        canvas.drawPath(mPath2, mCirclePaint2);
        
        canvas.drawCircle(mSupportCircleX, mSupportCircleY, mSupportChangeRadius, mCirclePaint);
        canvas.drawCircle(mCenterPointX, mCenterPointY, mChangeRadius, mCirclePaint);
        canvas.drawPath(mPath, mCirclePaint);

        canvas.restore();

    }

    
    public void setProgress(float progress) {
        
        if (progress == 0) {
            return;
        }
        mOriginProgress = progress;
        if (progress <= 0.5) {
            mProgress = progress / 0.5f;
            mProgress2 = 0;
        } else {
            mProgress2 = (progress - 0.5f) / 0.5f;
            mProgress = 1;

        }
        if (mDrection == DIRECTION_RIGHT) {
            moveToNext();
        } else {
            moveToPrivous();
        }
        invalidate();
    }

    
    private void moveToNext() {
        
        mPath.reset();
        mPath2.reset();
        
        float mRadiusProgress = accelerateinterpolator.getInterpolation(mOriginProgress);
        
        
        mCenterPointX = getValue(getCenterPointAt(mSelectedIndex), getCenterPointAt(mSelectedIndex + 1) - mRadius, MOVE_STEP_TWO);
        mCenterPointY = mRadius;
        
        mChangeRadius = getValue(mRadius, 0, mRadiusProgress);

        
        double radian = Math.toRadians(getValue(45, 0, MOVE_STEP_ONE));
        
        float mX = (float) (Math.sin(radian) * mChangeRadius);
        
        float mY = (float) (Math.cos(radian) * mChangeRadius);

        
        mSupportCircleX = getValue(getCenterPointAt(mSelectedIndex) + mRadius, getCenterPointAt(mSelectedIndex + 1), MOVE_STEP_ONE);
        mSupportCircleY = mRadius;
        mSupportChangeRadius = getValue(0, mRadius, mRadiusProgress);

        
        double supportradian = Math.toRadians(getValue(0, 45, MOVE_STEP_TWO));
        
        float msupportradianX = (float) (Math.sin(supportradian) * mSupportChangeRadius);
        
        float msupportradianY = (float) (Math.cos(supportradian) * mSupportChangeRadius);

        
        mStartX = mCenterPointX + mX;
        mStartY = mCenterPointY - mY;

        
        endPointX = mSupportCircleX - msupportradianX;
        endPointY = mRadius - msupportradianY;

        
        controlPointX = getValueForAll(getCenterPointAt(mSelectedIndex) + mRadius, getCenterPointAt(mSelectedIndex + 1) - mRadius);
        controlPointY = mRadius;

        
        mPath.moveTo(mStartX, mStartY);

        
        mPath.quadTo(controlPointX, controlPointY, endPointX, endPointY);
        mPath.lineTo(endPointX, mRadius + msupportradianY);
        mPath.quadTo(controlPointX, mRadius, mStartX, mStartY + 2 * mY);
        mPath.lineTo(mStartX, mStartY);

        
        
        mbgNextPointX = getValue(getCenterPointAt(mSelectedIndex + 1), getCenterPointAt(mSelectedIndex) + mNomarlRadius, MOVE_STEP_TWO);
        mbgNextPointY = mRadius;
        
        mChangeBgRadius = getValue(mNomarlRadius, 0, mRadiusProgress);

        
        double m_Next_radian = Math.toRadians(getValue(45, 0, MOVE_STEP_ONE));
        float mX_next = (float) (Math.sin(m_Next_radian) * mChangeBgRadius);
        float mY_next = (float) (Math.cos(m_Next_radian) * mChangeBgRadius);
        
        mSupport_next_centerX = getValue(getCenterPointAt(mSelectedIndex + 1) - mNomarlRadius, getCenterPointAt(mSelectedIndex), MOVE_STEP_ONE);
        mSupport_next_centerY = mRadius;
        
        mSupport_Next_ChangeRadius = getValue(0, mNomarlRadius, mRadiusProgress);

        
        double mSupport_Next_radian = Math.toRadians(getValue(0, 45, MOVE_STEP_TWO));
        float mSupport_Next_radianX = (float) (Math.sin(mSupport_Next_radian) * mSupport_Next_ChangeRadius);
        float mSupport_Next_radianY = (float) (Math.cos(mSupport_Next_radian) * mSupport_Next_ChangeRadius);

        
        float startPoint_support_nextX = mbgNextPointX - mX_next;
        float startPoint_support_nextY = mbgNextPointY - mY_next;

        
        float endPoint_support_nextX = mSupport_next_centerX + mSupport_Next_radianX;
        float endPoint_support_nextY = mSupport_next_centerY - mSupport_Next_radianY;

        
        float controlPointX_Next = getValueForAll(getCenterPointAt(mSelectedIndex + 1) - mNomarlRadius, getCenterPointAt(mSelectedIndex) + mNomarlRadius);
        float controlPointY_Next = mRadius;

        
        mPath2.moveTo(startPoint_support_nextX, startPoint_support_nextY);
        
        mPath2.quadTo(controlPointX_Next, controlPointY_Next, endPoint_support_nextX, endPoint_support_nextY);
        mPath2.lineTo(endPoint_support_nextX, mRadius + mSupport_Next_radianY);
        mPath2.quadTo(controlPointX_Next, controlPointY_Next, startPoint_support_nextX, startPoint_support_nextY + 2 * mY_next);
        mPath2.lineTo(startPoint_support_nextX, startPoint_support_nextY);

    }


    
    private void moveToPrivous() {
        mPath.reset();
        mPath2.reset();

        float mRadiusProgress = accelerateinterpolator.getInterpolation(mOriginProgress);

        
        mCenterPointX = getValue(getCenterPointAt(mSelectedIndex), getCenterPointAt(mSelectedIndex - 1) + mRadius, MOVE_STEP_TWO);
        mCenterPointY = mRadius;
        mChangeRadius = getValue(mRadius, 0, mRadiusProgress);
        
        double radian = Math.toRadians(getValue(45, 0, MOVE_STEP_ONE));
        
        float mX = (float) (Math.sin(radian) * mChangeRadius);
        
        float mY = (float) (Math.cos(radian) * mChangeRadius);

        
        mSupportCircleX = getValue(getCenterPointAt(mSelectedIndex) - mRadius, getCenterPointAt(mSelectedIndex - 1), MOVE_STEP_ONE);
        mSupportCircleY = mRadius;
        mSupportChangeRadius = getValue(0, mRadius, mRadiusProgress);


        
        double supportradian = Math.toRadians(getValue(0, 45, MOVE_STEP_TWO));
        
        float msupportradianX = (float) (Math.sin(supportradian) * mSupportChangeRadius);
        
        float msupportradianY = (float) (Math.cos(supportradian) * mSupportChangeRadius);

        mStartX = mCenterPointX - mX;
        mStartY = mCenterPointY - mY;

        endPointX = mSupportCircleX + msupportradianX;
        endPointY = mRadius - msupportradianY;

        controlPointX = getValueForAll(getCenterPointAt(mSelectedIndex) - mRadius, getCenterPointAt(mSelectedIndex - 1) + mRadius);
        controlPointY = mRadius;

        mPath.moveTo(mStartX, mStartY);
        mPath.quadTo(controlPointX, controlPointY, endPointX, endPointY);
        mPath.lineTo(endPointX, mRadius + msupportradianY);
        mPath.quadTo(controlPointX, mRadius, mStartX, mStartY + 2 * mY);
        mPath.lineTo(mStartX, mStartY);


        
        mbgNextPointX = getValue(getCenterPointAt(mSelectedIndex - 1), getCenterPointAt(mSelectedIndex) - mNomarlRadius, MOVE_STEP_TWO);
        mbgNextPointY = mRadius;
        mChangeBgRadius = getValue(mNomarlRadius, 0, mRadiusProgress);
        
        double m_Next_radian = Math.toRadians(getValue(45, 0, MOVE_STEP_ONE));
        
        float mX_next = (float) (Math.sin(m_Next_radian) * mChangeBgRadius);
        
        float mY_next = (float) (Math.cos(m_Next_radian) * mChangeBgRadius);

        mSupport_next_centerX = getValue(getCenterPointAt(mSelectedIndex - 1) + mNomarlRadius, getCenterPointAt(mSelectedIndex), MOVE_STEP_ONE);
        mSupport_next_centerY = mRadius;
        mSupport_Next_ChangeRadius = getValue(0, mNomarlRadius, mRadiusProgress);

        
        double mSupport_Next_radian = Math.toRadians(getValue(0, 45, MOVE_STEP_TWO));
        
        float mSupport_Next_radianX = (float) (Math.sin(mSupport_Next_radian) * mSupport_Next_ChangeRadius);
        
        float mSupport_Next_radianY = (float) (Math.cos(mSupport_Next_radian) * mSupport_Next_ChangeRadius);

        float startPoint_support_nextX = mbgNextPointX + mX_next;
        float startPoint_support_nextY = mbgNextPointY - mY_next;

        float endPoint_support_nextX = mSupport_next_centerX - mSupport_Next_radianX;
        float endPoint_support_nextY = mSupport_next_centerY - mSupport_Next_radianY;

        float controlPointX_Next = getValueForAll(getCenterPointAt(mSelectedIndex - 1) + mNomarlRadius, getCenterPointAt(mSelectedIndex) - mNomarlRadius);
        float controlPointY_Next = mRadius;

        mPath2.moveTo(startPoint_support_nextX, startPoint_support_nextY);
        mPath2.quadTo(controlPointX_Next, controlPointY_Next, endPoint_support_nextX, endPoint_support_nextY);
        mPath2.lineTo(endPoint_support_nextX, mRadius + mSupport_Next_radianY);
        mPath2.quadTo(controlPointX_Next, controlPointY_Next, startPoint_support_nextX, startPoint_support_nextY + 2 * mY_next);
        mPath2.lineTo(startPoint_support_nextX, startPoint_support_nextY);

    }


    
    public float getValue(float start, float end, int step) {
        if (step == MOVE_STEP_ONE) {
            return start + (end - start) * mProgress;
        } else {
            return start + (end - start) * mProgress2;
        }
    }

    
    public float getValueForAll(float start, float end) {
        return start + (end - start) * mOriginProgress;
    }

    
    public float getValue(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    
    private float getCenterPointAt(int index) {
        if (index == 0) {
            return mRadius;
        }
        return index * (distance + 2 * mNomarlRadius) + mNomarlRadius + (mRadius - mNomarlRadius);
    }

    public void setDirection(int direction) {
        mDrection = direction;
    }

    
    public void resetProgress() {
        mProgress = 0;
        mProgress2 = 0;
        mOriginProgress = 0;
    }

    
    public void attachToViewpager(ViewPager viewPager) {
        viewPager.addOnPageChangeListener(this);
        if (viewPager.getAdapter() != null) {
            count = viewPager.getAdapter().getCount();
        }
        mSelectedIndex = viewPager.getCurrentItem();
        moveToNext();
        mDrection = DIRECTION_RIGHT;
        invalidate();
    }


    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        
        if (positionOffset == 0) {
            mSelectedIndex = position;
            Log.d(TAG, "Reached ");
            resetProgress();
        }
        
        if (position + positionOffset - mSelectedIndex > 0) {
            mDrection = DIRECTION_RIGHT;
            
            if (position + positionOffset > mSelectedIndex + 1) {
                mSelectedIndex = position;
                Log.d(TAG, "Swipe quickly left");
            } else {
                setProgress(positionOffset);
            }
        } else if (position + positionOffset - mSelectedIndex < 0) { 
            mDrection = DIRECTION_LEFT;
            
            if (position + positionOffset < mSelectedIndex - 1) {
                mSelectedIndex = position;
                Log.d(TAG, "Swipe quickly right");
            } else {
                setProgress(1 - positionOffset);
            }
        }

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

}