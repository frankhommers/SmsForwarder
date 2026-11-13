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

package com.xuexiang.xui.widget.slideback;

import static com.xuexiang.xui.widget.slideback.SlideBack.EDGE_BOTH;
import static com.xuexiang.xui.widget.slideback.SlideBack.EDGE_LEFT;
import static com.xuexiang.xui.widget.slideback.SlideBack.EDGE_RIGHT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.xuexiang.xui.widget.slideback.callback.SlideBackCallBack;
import com.xuexiang.xui.widget.slideback.callback.SlideCallBack;
import com.xuexiang.xui.widget.slideback.dispatcher.ISlideTouchEventDispatcher;
import com.xuexiang.xui.widget.slideback.dispatcher.OnSlideUpdateListener;
import com.xuexiang.xui.widget.slideback.dispatcher.impl.DefaultSlideTouchDispatcher;
import com.xuexiang.xui.widget.slideback.widget.SlideBackIconView;
import com.xuexiang.xui.widget.slideback.widget.SlideBackInterceptLayout;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class SlideBackManager implements OnSlideUpdateListener {
    private SlideBackIconView mSlideBackIconViewLeft;
    private SlideBackIconView mSlideBackIconViewRight;

    private Activity mActivity;
    private boolean mHaveScroll;
    
    private SlideCallBack mCallBack;
    
    private SlideInfo mSlideInfo;

    SlideBackManager(Activity activity, boolean isFixedSize) {
        mActivity = activity;
        mHaveScroll = false;

        
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        mSlideInfo = new SlideInfo()
                .setArrowSize(dp2px(5))
                .setScreenWidth(dm.widthPixels)
                .setDragRate(3)
                .setAllowEdgeLeft(true)
                .setAllowEdgeRight(false);
        if (isFixedSize) {
            mSlideInfo.setBackViewHeight(dm.heightPixels / 5f)
                    .setMaxSlideLength(dp2px(24))
                    .setSideSlideLength(dp2px(12));
        } else {
            mSlideInfo.setBackViewHeight(dm.heightPixels / 4f)
                    .setMaxSlideLength((float) dm.widthPixels / 12)
                    .setSideSlideLength((float) dm.widthPixels / 24);
        }
    }

    SlideBackManager(Activity activity, SlideInfo slideInfo) {
        mActivity = activity;
        mHaveScroll = false;
        mSlideInfo = slideInfo;
    }

    
    public SlideBackManager haveScroll(boolean haveScroll) {
        mHaveScroll = haveScroll;
        return this;
    }

    
    public SlideBackManager callBack(SlideBackCallBack callBack) {
        mCallBack = new SlideCallBack(callBack) {
            @Override
            public void onSlide(int edgeFrom) {
                onSlideBack();
            }
        };
        return this;
    }

    
    public SlideBackManager callBack(SlideCallBack callBack) {
        mCallBack = callBack;
        return this;
    }

    
    public SlideBackManager setSlideInfo(SlideInfo slideInfo) {
        mSlideInfo = slideInfo;
        return this;
    }

    
    public SlideBackManager viewHeight(float backViewHeightDp) {
        mSlideInfo.setBackViewHeight(dp2px(backViewHeightDp));
        return this;
    }

    
    public SlideBackManager arrowSize(float arrowSizeDp) {
        mSlideInfo.setArrowSize(dp2px(arrowSizeDp));
        return this;
    }

    
    public SlideBackManager maxSlideLength(float maxSlideLengthDp) {
        mSlideInfo.setMaxSlideLength(dp2px(maxSlideLengthDp));
        return this;
    }

    
    public SlideBackManager sideSlideLength(float sideSlideLengthDp) {
        mSlideInfo.setSideSlideLength(dp2px(sideSlideLengthDp));
        return this;
    }

    
    public SlideBackManager dragRate(float dragRate) {
        mSlideInfo.setDragRate(dragRate);
        return this;
    }

    
    // SOURCE-retention annotation is absent from the published binary; runtime is unchanged.
    public SlideBackManager edgeMode(int edgeMode) {
        switch (edgeMode) {
            case EDGE_LEFT:
                mSlideInfo.setEdgeMode(true, false);
                break;
            case EDGE_RIGHT:
                mSlideInfo.setEdgeMode(false, true);
                break;
            case EDGE_BOTH:
                mSlideInfo.setEdgeMode(true, true);
                break;
            default:
                throw new RuntimeException("Undefined edge swipe mode: EdgeMode = " + edgeMode);
        }
        return this;
    }

    
    public void register() {
        register(new DefaultSlideTouchDispatcher().init(mSlideInfo, mCallBack, this));
    }

    
    @SuppressLint("ClickableViewAccessibility")
    public void register(ISlideTouchEventDispatcher dispatcher) {
        if (mSlideInfo.isAllowEdgeLeft()) {
            
            mSlideBackIconViewLeft = new SlideBackIconView(mActivity);
            mSlideBackIconViewLeft.setBackViewHeight(mSlideInfo.getBackViewHeight());
            mSlideBackIconViewLeft.setArrowSize(mSlideInfo.getArrowSize());
            mSlideBackIconViewLeft.setMaxSlideLength(mSlideInfo.getMaxSlideLength());
        }
        if (mSlideInfo.isAllowEdgeRight()) {
            
            mSlideBackIconViewRight = new SlideBackIconView(mActivity);
            mSlideBackIconViewRight.setBackViewHeight(mSlideInfo.getBackViewHeight());
            mSlideBackIconViewRight.setArrowSize(mSlideInfo.getArrowSize());
            mSlideBackIconViewRight.setMaxSlideLength(mSlideInfo.getMaxSlideLength());
            
            mSlideBackIconViewRight.setRotationY(180);
        }

        
        FrameLayout container = (FrameLayout) mActivity.getWindow().getDecorView();
        if (mHaveScroll) {
            SlideBackInterceptLayout interceptLayout = new SlideBackInterceptLayout(mActivity);
            interceptLayout.setSideSlideLength(mSlideInfo.getSideSlideLength());
            addInterceptLayout(container, interceptLayout);
        }
        if (mSlideInfo.isAllowEdgeLeft()) {
            container.addView(mSlideBackIconViewLeft);
        }
        if (mSlideInfo.isAllowEdgeRight()) {
            container.addView(mSlideBackIconViewRight);
        }
        container.setOnTouchListener(dispatcher);
    }

    
    @SuppressLint("ClickableViewAccessibility")
    void unregister() {
        mActivity = null;
        mCallBack = null;
        mSlideBackIconViewLeft = null;
        mSlideBackIconViewRight = null;
    }

    
    private void addInterceptLayout(ViewGroup decorView, SlideBackInterceptLayout interceptLayout) {
        View rootLayout = decorView.getChildAt(0); 
        decorView.removeView(rootLayout); 
        
        interceptLayout.addView(rootLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        decorView.addView(interceptLayout);
    }

    
    private void removeInterceptLayout(ViewGroup decorView) {
        FrameLayout rootLayout = (FrameLayout) decorView.getChildAt(0); 
        decorView.removeView(rootLayout); 
        
        View oriLayout = rootLayout.getChildAt(0);
        rootLayout.removeView(oriLayout);
        decorView.addView(oriLayout);
    }

    
    private void setSlideBackPosition(SlideBackIconView view, int position) {
        
        int topMargin = (int) (position - (view.getBackViewHeight() / 2));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(view.getLayoutParams());
        layoutParams.topMargin = topMargin;
        view.setLayoutParams(layoutParams);
    }

    private float dp2px(float dpValue) {
        return dpValue * mActivity.getResources().getDisplayMetrics().density + 0.5f;
    }

    
    @Override
    public void updateSlideLength(boolean isLeft, float length) {
        if (isLeft) {
            mSlideBackIconViewLeft.updateSlideLength(length);
        } else {
            mSlideBackIconViewRight.updateSlideLength(length);
        }
    }

    
    @Override
    public void updateSlidePosition(boolean isLeft, int position) {
        if (isLeft) {
            setSlideBackPosition(mSlideBackIconViewLeft, position);
        } else {
            setSlideBackPosition(mSlideBackIconViewRight, position);
        }
    }
}