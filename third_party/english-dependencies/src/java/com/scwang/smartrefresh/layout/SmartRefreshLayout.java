// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.scwang.smartrefresh.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import android.widget.TextView;

import com.scwang.smartrefresh.layout.api.DefaultRefreshFooterCreator;
import com.scwang.smartrefresh.layout.api.DefaultRefreshHeaderCreator;
import com.scwang.smartrefresh.layout.api.DefaultRefreshInitializer;
import com.scwang.smartrefresh.layout.api.RefreshContent;
import com.scwang.smartrefresh.layout.api.RefreshFooter;
import com.scwang.smartrefresh.layout.api.RefreshHeader;
import com.scwang.smartrefresh.layout.api.RefreshInternal;
import com.scwang.smartrefresh.layout.api.RefreshKernel;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.api.ScrollBoundaryDecider;
import com.scwang.smartrefresh.layout.constant.DimensionStatus;
import com.scwang.smartrefresh.layout.constant.RefreshState;
import com.scwang.smartrefresh.layout.constant.SpinnerStyle;
import com.scwang.smartrefresh.layout.footer.BallPulseFooter;
import com.scwang.smartrefresh.layout.header.BezierRadarHeader;
import com.scwang.smartrefresh.layout.impl.RefreshContentWrapper;
import com.scwang.smartrefresh.layout.impl.RefreshFooterWrapper;
import com.scwang.smartrefresh.layout.impl.RefreshHeaderWrapper;
import com.scwang.smartrefresh.layout.listener.OnLoadMoreListener;
import com.scwang.smartrefresh.layout.listener.OnMultiPurposeListener;
import com.scwang.smartrefresh.layout.listener.OnRefreshListener;
import com.scwang.smartrefresh.layout.listener.OnRefreshLoadMoreListener;
import com.scwang.smartrefresh.layout.listener.OnStateChangedListener;
import com.scwang.smartrefresh.layout.util.DelayedRunnable;
import com.scwang.smartrefresh.layout.util.DensityUtil;
import com.scwang.smartrefresh.layout.util.ViscousFluidInterpolator;

import java.util.ArrayList;
import java.util.List;

import static android.view.MotionEvent.obtain;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.scwang.smartrefresh.layout.util.DensityUtil.dp2px;
import static com.scwang.smartrefresh.layout.util.SmartUtil.fling;
import static com.scwang.smartrefresh.layout.util.SmartUtil.getColor;
import static com.scwang.smartrefresh.layout.util.SmartUtil.isContentView;
import static java.lang.System.currentTimeMillis;


@SuppressLint("RestrictedApi")
@SuppressWarnings({"unused", "WeakerAccess"})
public class SmartRefreshLayout extends ViewGroup implements RefreshLayout, NestedScrollingParent/*, NestedScrollingChild*/ {

    

    
    protected int mTouchSlop;
    protected int mSpinner;
    protected int mLastSpinner;
    protected int mTouchSpinner;
    protected int mFloorDuration = 250;
    protected int mReboundDuration = 250;
    protected int mScreenHeightPixels;
    protected float mTouchX;
    protected float mTouchY;
    protected float mLastTouchX;
    protected float mLastTouchY;
    protected float mDragRate = .5f;
    protected char mDragDirection = 'n';
    protected boolean mIsBeingDragged;
    protected boolean mSuperDispatchTouchEvent;
    protected int mFixedHeaderViewId = View.NO_ID;
    protected int mFixedFooterViewId = View.NO_ID;
    protected int mHeaderTranslationViewId = View.NO_ID;
    protected int mFooterTranslationViewId = View.NO_ID;

    protected int mMinimumVelocity;
    protected int mMaximumVelocity;
    protected int mCurrentVelocity;
    protected Scroller mScroller;
    protected VelocityTracker mVelocityTracker;
    protected Interpolator mReboundInterpolator;

    //</editor-fold>

    
    protected int[] mPrimaryColors;
    protected boolean mEnableRefresh = true;
    protected boolean mEnableLoadMore = false;
    protected boolean mEnableClipHeaderWhenFixedBehind = true;
    protected boolean mEnableClipFooterWhenFixedBehind = true;
    protected boolean mEnableHeaderTranslationContent = true;
    protected boolean mEnableFooterTranslationContent = true;
    protected boolean mEnableFooterFollowWhenNoMoreData = false;
    protected boolean mEnablePreviewInEditMode = true;
    protected boolean mEnableOverScrollBounce = true;
    protected boolean mEnableOverScrollDrag = false;
    protected boolean mEnableAutoLoadMore = true;
    protected boolean mEnablePureScrollMode = false;
    protected boolean mEnableScrollContentWhenLoaded = true;
    protected boolean mEnableScrollContentWhenRefreshed = true;
    protected boolean mEnableLoadMoreWhenContentNotFull = true;
    protected boolean mDisableContentWhenRefresh = false;
    protected boolean mDisableContentWhenLoading = false;
    protected boolean mFooterNoMoreData = false;

    protected boolean mManualLoadMore = false;
    protected boolean mManualNestedScrolling = false;
    protected boolean mManualHeaderTranslationContent = false;
    protected boolean mManualFooterTranslationContent = false;
    //</editor-fold>

    
    protected OnRefreshListener mRefreshListener;
    protected OnLoadMoreListener mLoadMoreListener;
    protected OnMultiPurposeListener mOnMultiPurposeListener;
    protected ScrollBoundaryDecider mScrollBoundaryDecider;
    //</editor-fold>

    
    protected int mTotalUnconsumed;
    protected boolean mNestedInProgress;
    protected int[] mParentOffsetInWindow = new int[2];
    protected NestedScrollingChildHelper mNestedChild = new NestedScrollingChildHelper(this);
    protected NestedScrollingParentHelper mNestedParent = new NestedScrollingParentHelper(this);
    //</editor-fold>

    

    protected int mHeaderHeight;        
    protected DimensionStatus mHeaderHeightStatus = DimensionStatus.DefaultUnNotify;
    protected int mFooterHeight;        
    protected DimensionStatus mFooterHeightStatus = DimensionStatus.DefaultUnNotify;

    protected int mHeaderInsetStart;    
    protected int mFooterInsetStart;    

    protected float mHeaderMaxDragRate = 2.5f;  
    protected float mFooterMaxDragRate = 2.5f;  
    protected float mHeaderTriggerRate = 1.0f;  
    protected float mFooterTriggerRate = 1.0f;  

    protected RefreshInternal mRefreshHeader;     
    protected RefreshInternal mRefreshFooter;     
    protected RefreshContent mRefreshContent;   
    //</editor-fold>

    protected Paint mPaint;
    protected Handler mHandler;
    protected RefreshKernel mKernel = new RefreshKernelImpl();
    protected List<DelayedRunnable> mListDelayedRunnable;

    protected RefreshState mState = RefreshState.None;          
    protected RefreshState mViceState = RefreshState.None;      

    protected long mLastOpenTime = 0;                           

    protected int mHeaderBackgroundColor = 0;                   
    protected int mFooterBackgroundColor = 0;

    protected boolean mHeaderNeedTouchEventWhenRefreshing;      
    protected boolean mFooterNeedTouchEventWhenLoading;

    protected boolean mFooterLocked = false;


    protected static DefaultRefreshFooterCreator sFooterCreator = null;
    protected static DefaultRefreshHeaderCreator sHeaderCreator = null;
    protected static DefaultRefreshInitializer sRefreshInitializer = null;

    //</editor-fold>

    
    public SmartRefreshLayout(Context context) {
        this(context, null);
    }

    public SmartRefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmartRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        super.setClipToPadding(false);

        DensityUtil density = new DensityUtil();
        ViewConfiguration configuration = ViewConfiguration.get(context);

        mScroller = new Scroller(context);
        mVelocityTracker = VelocityTracker.obtain();
        mScreenHeightPixels = context.getResources().getDisplayMetrics().heightPixels;
        mReboundInterpolator = new ViscousFluidInterpolator();
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();


        mFooterHeight = density.dip2px(60);
        mHeaderHeight = density.dip2px(100);

        if (sRefreshInitializer != null) {
            sRefreshInitializer.initialize(context, this);
        }

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SmartRefreshLayout);

        mNestedChild.setNestedScrollingEnabled(ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableNestedScrolling, mNestedChild.isNestedScrollingEnabled()));
        mDragRate = ta.getFloat(R.styleable.SmartRefreshLayout_srlDragRate, mDragRate);
        mHeaderMaxDragRate = ta.getFloat(R.styleable.SmartRefreshLayout_srlHeaderMaxDragRate, mHeaderMaxDragRate);
        mFooterMaxDragRate = ta.getFloat(R.styleable.SmartRefreshLayout_srlFooterMaxDragRate, mFooterMaxDragRate);
        mHeaderTriggerRate = ta.getFloat(R.styleable.SmartRefreshLayout_srlHeaderTriggerRate, mHeaderTriggerRate);
        mFooterTriggerRate = ta.getFloat(R.styleable.SmartRefreshLayout_srlFooterTriggerRate, mFooterTriggerRate);
        mEnableRefresh = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableRefresh, mEnableRefresh);
        mReboundDuration = ta.getInt(R.styleable.SmartRefreshLayout_srlReboundDuration, mReboundDuration);
        mEnableLoadMore = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableLoadMore, mEnableLoadMore);
        mHeaderHeight = ta.getDimensionPixelOffset(R.styleable.SmartRefreshLayout_srlHeaderHeight, mHeaderHeight);
        mFooterHeight = ta.getDimensionPixelOffset(R.styleable.SmartRefreshLayout_srlFooterHeight, mFooterHeight);
        mHeaderInsetStart = ta.getDimensionPixelOffset(R.styleable.SmartRefreshLayout_srlHeaderInsetStart, mHeaderInsetStart);
        mFooterInsetStart = ta.getDimensionPixelOffset(R.styleable.SmartRefreshLayout_srlFooterInsetStart, mFooterInsetStart);
        mDisableContentWhenRefresh = ta.getBoolean(R.styleable.SmartRefreshLayout_srlDisableContentWhenRefresh, mDisableContentWhenRefresh);
        mDisableContentWhenLoading = ta.getBoolean(R.styleable.SmartRefreshLayout_srlDisableContentWhenLoading, mDisableContentWhenLoading);
        mEnableHeaderTranslationContent = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableHeaderTranslationContent, mEnableHeaderTranslationContent);
        mEnableFooterTranslationContent = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableFooterTranslationContent, mEnableFooterTranslationContent);
        mEnablePreviewInEditMode = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnablePreviewInEditMode, mEnablePreviewInEditMode);
        mEnableAutoLoadMore = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableAutoLoadMore, mEnableAutoLoadMore);
        mEnableOverScrollBounce = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableOverScrollBounce, mEnableOverScrollBounce);
        mEnablePureScrollMode = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnablePureScrollMode, mEnablePureScrollMode);
        mEnableScrollContentWhenLoaded = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableScrollContentWhenLoaded, mEnableScrollContentWhenLoaded);
        mEnableScrollContentWhenRefreshed = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableScrollContentWhenRefreshed, mEnableScrollContentWhenRefreshed);
        mEnableLoadMoreWhenContentNotFull = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableLoadMoreWhenContentNotFull, mEnableLoadMoreWhenContentNotFull);
        mEnableFooterFollowWhenNoMoreData = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableFooterFollowWhenLoadFinished, mEnableFooterFollowWhenNoMoreData);
        mEnableFooterFollowWhenNoMoreData = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableFooterFollowWhenNoMoreData, mEnableFooterFollowWhenNoMoreData);
        mEnableClipHeaderWhenFixedBehind = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableClipHeaderWhenFixedBehind, mEnableClipHeaderWhenFixedBehind);
        mEnableClipFooterWhenFixedBehind = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableClipFooterWhenFixedBehind, mEnableClipFooterWhenFixedBehind);
        mEnableOverScrollDrag = ta.getBoolean(R.styleable.SmartRefreshLayout_srlEnableOverScrollDrag, mEnableOverScrollDrag);
        mFixedHeaderViewId = ta.getResourceId(R.styleable.SmartRefreshLayout_srlFixedHeaderViewId, mFixedHeaderViewId);
        mFixedFooterViewId = ta.getResourceId(R.styleable.SmartRefreshLayout_srlFixedFooterViewId, mFixedFooterViewId);
        mHeaderTranslationViewId = ta.getResourceId(R.styleable.SmartRefreshLayout_srlHeaderTranslationViewId, mHeaderTranslationViewId);
        mFooterTranslationViewId = ta.getResourceId(R.styleable.SmartRefreshLayout_srlFooterTranslationViewId, mFooterTranslationViewId);

        if (mEnablePureScrollMode && !ta.hasValue(R.styleable.SmartRefreshLayout_srlEnableOverScrollDrag)) {
            mEnableOverScrollDrag = true;
        }

        mManualLoadMore = mManualLoadMore || ta.hasValue(R.styleable.SmartRefreshLayout_srlEnableLoadMore);
        mManualHeaderTranslationContent = mManualHeaderTranslationContent || ta.hasValue(R.styleable.SmartRefreshLayout_srlEnableHeaderTranslationContent);
        mManualFooterTranslationContent = mManualFooterTranslationContent || ta.hasValue(R.styleable.SmartRefreshLayout_srlEnableFooterTranslationContent);
        mManualNestedScrolling = mManualNestedScrolling || ta.hasValue(R.styleable.SmartRefreshLayout_srlEnableNestedScrolling);
        mHeaderHeightStatus = ta.hasValue(R.styleable.SmartRefreshLayout_srlHeaderHeight) ? DimensionStatus.XmlLayoutUnNotify : mHeaderHeightStatus;
        mFooterHeightStatus = ta.hasValue(R.styleable.SmartRefreshLayout_srlFooterHeight) ? DimensionStatus.XmlLayoutUnNotify : mFooterHeightStatus;

        int accentColor = ta.getColor(R.styleable.SmartRefreshLayout_srlAccentColor, 0);
        int primaryColor = ta.getColor(R.styleable.SmartRefreshLayout_srlPrimaryColor, 0);
        if (primaryColor != 0) {
            if (accentColor != 0) {
                mPrimaryColors = new int[]{primaryColor, accentColor};
            } else {
                mPrimaryColors = new int[]{primaryColor};
            }
        } else if (accentColor != 0) {
            mPrimaryColors = new int[]{0, accentColor};
        }

        ta.recycle();

    }
    //</editor-fold>

    

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final int count = super.getChildCount();
        if (count > 3) {
            throw new RuntimeException("At most three child views are supported");
        }

        int contentLevel = 0;
        int indexContent = -1;
        for (int i = 0; i < count; i++) {
            View view = super.getChildAt(i);
            if (isContentView(view) && (contentLevel < 2 || i == 1)) {
                indexContent = i;
                contentLevel = 2;
            } else if (!(view instanceof RefreshInternal) && contentLevel < 1) {
                indexContent = i;
                contentLevel = i > 0 ? 1 : 0;
            }
        }

        int indexHeader = -1;
        int indexFooter = -1;
        if (indexContent >= 0) {
            mRefreshContent = new RefreshContentWrapper(super.getChildAt(indexContent));
            if (indexContent == 1) {
                indexHeader = 0;
                if (count == 3) {
                    indexFooter = 2;
                }
            } else if (count == 2) {
                indexFooter = 1;
            }
        }

        for (int i = 0; i < count; i++) {
            View view = super.getChildAt(i);
            if (i == indexHeader || (i != indexFooter && indexHeader == -1 && mRefreshHeader == null && view instanceof RefreshHeader)) {
                mRefreshHeader = (view instanceof RefreshHeader) ? (RefreshHeader) view : new RefreshHeaderWrapper(view);
            } else if (i == indexFooter || (indexFooter == -1 && view instanceof RefreshFooter)) {
                mEnableLoadMore = (mEnableLoadMore || !mManualLoadMore);
                mRefreshFooter = (view instanceof RefreshFooter) ? (RefreshFooter) view : new RefreshFooterWrapper(view);
//            } else if (mRefreshContent == null) {
//                mRefreshContent = new RefreshContentWrapper(view);
            }
        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final View thisView = this;
        if (!thisView.isInEditMode()) {

            if (mHandler == null) {
                mHandler = new Handler();
            }

            if (mListDelayedRunnable != null) {
                for (DelayedRunnable runnable : mListDelayedRunnable) {
                    mHandler.postDelayed(runnable, runnable.delayMillis);
                }
                mListDelayedRunnable.clear();
                mListDelayedRunnable = null;
            }

            if (mRefreshHeader == null) {
                if (sHeaderCreator != null) {
                    setRefreshHeader(sHeaderCreator.createRefreshHeader(thisView.getContext(), this));
                } else {
                    setRefreshHeader(new BezierRadarHeader(thisView.getContext()));
                }
            }
            if (mRefreshFooter == null) {
                if (sFooterCreator != null) {
                    setRefreshFooter(sFooterCreator.createRefreshFooter(thisView.getContext(), this));
                } else {
                    boolean old = mEnableLoadMore;
                    setRefreshFooter(new BallPulseFooter(thisView.getContext()));
                    mEnableLoadMore = old;
                }
            } else {
                mEnableLoadMore = mEnableLoadMore || !mManualLoadMore;
            }

            if (mRefreshContent == null) {
                for (int i = 0, len = getChildCount(); i < len; i++) {
                    View view = getChildAt(i);
                    if ((mRefreshHeader == null || view != mRefreshHeader.getView())&&
                            (mRefreshFooter == null || view != mRefreshFooter.getView())) {
                        mRefreshContent = new RefreshContentWrapper(view);
                    }
                }
            }
            if (mRefreshContent == null) {
                final int padding = DensityUtil.dp2px(20);
                final TextView errorView = new TextView(thisView.getContext());
                errorView.setTextColor(0xffff6600);
                errorView.setGravity(Gravity.CENTER);
                errorView.setTextSize(20);
                errorView.setText(R.string.srl_content_empty);
                super.addView(errorView, MATCH_PARENT, MATCH_PARENT);
                mRefreshContent = new RefreshContentWrapper(errorView);
                mRefreshContent.getView().setPadding(padding, padding, padding, padding);
            }

            View fixedHeaderView = mFixedHeaderViewId > 0 ? thisView.findViewById(mFixedHeaderViewId) : null;
            View fixedFooterView = mFixedFooterViewId > 0 ? thisView.findViewById(mFixedFooterViewId) : null;

            mRefreshContent.setScrollBoundaryDecider(mScrollBoundaryDecider);
            mRefreshContent.setEnableLoadMoreWhenContentNotFull(mEnableLoadMoreWhenContentNotFull);
            mRefreshContent.setUpComponent(mKernel, fixedHeaderView, fixedFooterView);

            if (mSpinner != 0) {
                notifyStateChanged(RefreshState.None);
                mRefreshContent.moveSpinner(mSpinner = 0, mHeaderTranslationViewId, mFooterTranslationViewId);
            }

            if (!mManualNestedScrolling && !isNestedScrollingEnabled()) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        final View thisView = SmartRefreshLayout.this;
                        for (ViewParent parent = thisView.getParent() ; parent != null ; ) {
                            if (parent instanceof NestedScrollingParent) {
                                View target = SmartRefreshLayout.this;
                                //noinspection RedundantCast
                                if (((NestedScrollingParent)parent).onStartNestedScroll(target,target,ViewCompat.SCROLL_AXIS_VERTICAL)) {
                                    setNestedScrollingEnabled(true);
                                    mManualNestedScrolling = false;
                                    break;
                                }
                            }
                            if (parent instanceof View) {
                                View thisParent = (View) parent;
                                parent = thisParent.getParent();
                            } else {
                                break;
                            }
                        }
                    }
                });
            }
        }

        if (mPrimaryColors != null) {
            if (mRefreshHeader != null) {
                mRefreshHeader.setPrimaryColors(mPrimaryColors);
            }
            if (mRefreshFooter != null) {
                mRefreshFooter.setPrimaryColors(mPrimaryColors);
            }
        }

        
        if (mRefreshContent != null) {
            super.bringChildToFront(mRefreshContent.getView());
        }
        if (mRefreshHeader != null && mRefreshHeader.getSpinnerStyle() != SpinnerStyle.FixedBehind) {
            super.bringChildToFront(mRefreshHeader.getView());
        }
        if (mRefreshFooter != null && mRefreshFooter.getSpinnerStyle() != SpinnerStyle.FixedBehind) {
            super.bringChildToFront(mRefreshFooter.getView());
        }

    }

    @Override
    protected void onMeasure(final int widthMeasureSpec,final int heightMeasureSpec) {
        int minimumHeight = 0;
        final View thisView = this;
        final boolean needPreview = thisView.isInEditMode() && mEnablePreviewInEditMode;

        for (int i = 0, len = super.getChildCount(); i < len; i++) {
            View child = super.getChildAt(i);

            if (mRefreshHeader != null && mRefreshHeader.getView() == child) {
                final View headerView = mRefreshHeader.getView();
                final LayoutParams lp = (LayoutParams) headerView.getLayoutParams();
                final int widthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec, lp.leftMargin + lp.rightMargin, lp.width);
                int height = mHeaderHeight;

                if (mHeaderHeightStatus.ordinal() < DimensionStatus.XmlLayoutUnNotify.ordinal()) {
                    if (lp.height > 0) {
                        height =  lp.height + lp.bottomMargin + lp.topMargin;
                        if (mHeaderHeightStatus.canReplaceWith(DimensionStatus.XmlExactUnNotify)) {
                            mHeaderHeight = lp.height + lp.bottomMargin + lp.topMargin;
                            mHeaderHeightStatus = DimensionStatus.XmlExactUnNotify;
                        }
                    } else if (lp.height == WRAP_CONTENT && (mRefreshHeader.getSpinnerStyle() != SpinnerStyle.MatchLayout || !mHeaderHeightStatus.notified)) {
                        final int maxHeight = Math.max(getSize(heightMeasureSpec) - lp.bottomMargin - lp.topMargin, 0);
                        headerView.measure(widthSpec, makeMeasureSpec(maxHeight, AT_MOST));
                        final int measuredHeight = headerView.getMeasuredHeight();
                        if (measuredHeight > 0) {
                            height = -1;
                            if (measuredHeight != (maxHeight) && mHeaderHeightStatus.canReplaceWith(DimensionStatus.XmlWrapUnNotify)) {
                                mHeaderHeight = measuredHeight + lp.bottomMargin + lp.topMargin;
                                mHeaderHeightStatus = DimensionStatus.XmlWrapUnNotify;
                            }
                        }
                    }
                }

                if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.MatchLayout) {
                    height = getSize(heightMeasureSpec);
                } else if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Scale && !needPreview) {
                    height = Math.max(0, isEnableRefreshOrLoadMore(mEnableRefresh) ? mSpinner : 0);
                }

                if (height != -1) {
                    headerView.measure(widthSpec, makeMeasureSpec(Math.max(height - lp.bottomMargin - lp.topMargin, 0), EXACTLY));
                }

                if (!mHeaderHeightStatus.notified) {
                    mHeaderHeightStatus = mHeaderHeightStatus.notified();
                    mRefreshHeader.onInitialized(mKernel, mHeaderHeight, (int) (mHeaderMaxDragRate * mHeaderHeight));
                }

                if (needPreview && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                    minimumHeight += headerView.getMeasuredHeight();
                }
            }

            if (mRefreshFooter != null && mRefreshFooter.getView() == child) {
                final View footerView = mRefreshFooter.getView();
                final LayoutParams lp = (LayoutParams) footerView.getLayoutParams();
                final int widthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec, lp.leftMargin + lp.rightMargin, lp.width);
                int height = mFooterHeight;

                if (mFooterHeightStatus.ordinal() < DimensionStatus.XmlLayoutUnNotify.ordinal()) {
                    if (lp.height > 0) {
                        height = lp.height + lp.topMargin + lp.bottomMargin;
                        if (mFooterHeightStatus.canReplaceWith(DimensionStatus.XmlExactUnNotify)) {
                            mFooterHeight = lp.height + lp.topMargin + lp.bottomMargin;
                            mFooterHeightStatus = DimensionStatus.XmlExactUnNotify;
                        }
                    } else if (lp.height == WRAP_CONTENT && (mRefreshFooter.getSpinnerStyle() != SpinnerStyle.MatchLayout || !mFooterHeightStatus.notified)) {
                        int maxHeight = Math.max(getSize(heightMeasureSpec) - lp.bottomMargin - lp.topMargin, 0);
                        footerView.measure(widthSpec, makeMeasureSpec(maxHeight, AT_MOST));
                        int measuredHeight = footerView.getMeasuredHeight();
                        if (measuredHeight > 0) {
                            height = -1;
                            if (measuredHeight != (maxHeight) && mFooterHeightStatus.canReplaceWith(DimensionStatus.XmlWrapUnNotify)) {
                                mFooterHeight = measuredHeight + lp.topMargin + lp.bottomMargin;
                                mFooterHeightStatus = DimensionStatus.XmlWrapUnNotify;
                            }
                        }
                    }
                }

                if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.MatchLayout) {
                    height = getSize(heightMeasureSpec);
                } else if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.Scale && !needPreview) {
                    height = Math.max(0, isEnableRefreshOrLoadMore(mEnableLoadMore) ? -mSpinner : 0);
                }

                if (height != -1) {
                    footerView.measure(widthSpec, makeMeasureSpec(Math.max(height - lp.bottomMargin - lp.topMargin, 0), EXACTLY));
                }

                if (!mFooterHeightStatus.notified) {
                    mFooterHeightStatus = mFooterHeightStatus.notified();
                    mRefreshFooter.onInitialized(mKernel, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
                }

                if (needPreview && isEnableRefreshOrLoadMore(mEnableLoadMore)) {
                    minimumHeight += footerView.getMeasuredHeight();
                }
            }

            if (mRefreshContent != null && mRefreshContent.getView() == child) {
                final View contentView = mRefreshContent.getView();
                final LayoutParams lp = (LayoutParams) contentView.getLayoutParams();
                final boolean showHeader = (mRefreshHeader != null && isEnableRefreshOrLoadMore(mEnableRefresh) && isEnableTranslationContent(mEnableHeaderTranslationContent, mRefreshHeader));
                final boolean showFooter = (mRefreshFooter != null && isEnableRefreshOrLoadMore(mEnableLoadMore) && isEnableTranslationContent(mEnableFooterTranslationContent, mRefreshFooter));
                final int widthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
                        thisView.getPaddingLeft() + thisView.getPaddingRight() +  lp.leftMargin + lp.rightMargin, lp.width);
                final int heightSpec = ViewGroup.getChildMeasureSpec(heightMeasureSpec,
                        thisView.getPaddingTop() + thisView.getPaddingBottom() + lp.topMargin + lp.bottomMargin +
                                ((needPreview && showHeader) ? mHeaderHeight : 0) +
                                ((needPreview && showFooter) ? mFooterHeight : 0), lp.height);
                contentView.measure(widthSpec, heightSpec);
                minimumHeight += contentView.getMeasuredHeight();
            }
        }

        super.setMeasuredDimension(
                View.resolveSize(super.getSuggestedMinimumWidth(), widthMeasureSpec),
                View.resolveSize(minimumHeight, heightMeasureSpec));

        mLastTouchX = thisView.getMeasuredWidth() / 2;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final View thisView = this;
        final int paddingLeft = thisView.getPaddingLeft();
        final int paddingTop = thisView.getPaddingTop();
        final int paddingBottom = thisView.getPaddingBottom();

        for (int i = 0, len = super.getChildCount(); i < len; i++) {
            View child = super.getChildAt(i);

            if (mRefreshContent != null && mRefreshContent.getView() == child) {
                boolean isPreviewMode = thisView.isInEditMode() && mEnablePreviewInEditMode && isEnableRefreshOrLoadMore(mEnableRefresh) && mRefreshHeader != null;
                final View contentView = mRefreshContent.getView();
                final LayoutParams lp = (LayoutParams) contentView.getLayoutParams();
                int left = paddingLeft + lp.leftMargin;
                int top = paddingTop + lp.topMargin;
                int right = left + contentView.getMeasuredWidth();
                int bottom = top + contentView.getMeasuredHeight();
                if (isPreviewMode && (isEnableTranslationContent(mEnableHeaderTranslationContent, mRefreshHeader))) {
                    top = top + mHeaderHeight;
                    bottom = bottom + mHeaderHeight;
                }

                contentView.layout(left, top, right, bottom);
            }
            if (mRefreshHeader != null && mRefreshHeader.getView() == child) {
                boolean isPreviewMode = thisView.isInEditMode() && mEnablePreviewInEditMode && isEnableRefreshOrLoadMore(mEnableRefresh);
                final View headerView = mRefreshHeader.getView();
                final LayoutParams lp = (LayoutParams) headerView.getLayoutParams();
                int left = lp.leftMargin;
                int top = lp.topMargin + mHeaderInsetStart;
                int right = left + headerView.getMeasuredWidth();
                int bottom = top + headerView.getMeasuredHeight();
                if (!isPreviewMode) {
                    if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Translate) {
                        top = top - mHeaderHeight;
                        bottom = bottom - mHeaderHeight;
                        
//                    } else if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Scale && mSpinner > 0) {
//                        bottom = top + Math.max(Math.max(0, isEnableRefreshOrLoadMore(mEnableRefresh) ? mSpinner : 0) - lp.bottomMargin - lp.topMargin, 0);
                    }
                }
                headerView.layout(left, top, right, bottom);
            }
            if (mRefreshFooter != null && mRefreshFooter.getView() == child) {
                final boolean isPreviewMode = thisView.isInEditMode() && mEnablePreviewInEditMode && isEnableRefreshOrLoadMore(mEnableLoadMore);
                final View footerView = mRefreshFooter.getView();
                final LayoutParams lp = (LayoutParams) footerView.getLayoutParams();
                final SpinnerStyle style = mRefreshFooter.getSpinnerStyle();
                int left = lp.leftMargin;
                int top = lp.topMargin + thisView.getMeasuredHeight() - mFooterInsetStart;

                if (style == SpinnerStyle.MatchLayout) {
                    top = lp.topMargin - mFooterInsetStart;
                } else if (isPreviewMode
                        || style == SpinnerStyle.FixedFront
                        || style == SpinnerStyle.FixedBehind) {
                    top = top - mFooterHeight;
                } else if (style == SpinnerStyle.Scale && mSpinner < 0) {
                    top = top - Math.max(isEnableRefreshOrLoadMore(mEnableLoadMore) ? -mSpinner : 0, 0);
                }

                int right = left + footerView.getMeasuredWidth();
                int bottom = top + footerView.getMeasuredHeight();
                footerView.layout(left, top, right, bottom);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mKernel.moveSpinner(0, true);
        notifyStateChanged(RefreshState.None);
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        if (mListDelayedRunnable != null) {
            mListDelayedRunnable.clear();
            mListDelayedRunnable = null;
        }
        mManualLoadMore = true;
        mManualNestedScrolling = true;
        animationRunnable = null;
        if (reboundAnimator != null) {
            reboundAnimator.removeAllListeners();
            reboundAnimator.removeAllUpdateListeners();
            reboundAnimator.cancel();
            reboundAnimator = null;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final View thisView = this;
        final View contentView = mRefreshContent != null ? mRefreshContent.getView() : null;
        if (mRefreshHeader != null && mRefreshHeader.getView() == child) {
            if (!isEnableRefreshOrLoadMore(mEnableRefresh) || (!mEnablePreviewInEditMode && thisView.isInEditMode())) {
                return true;
            }
            if (contentView != null) {
                int bottom = Math.max(contentView.getTop() + contentView.getPaddingTop() + mSpinner, child.getTop());
                if (mHeaderBackgroundColor != 0 && mPaint != null) {
                    mPaint.setColor(mHeaderBackgroundColor);
                    if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Scale) {
                        bottom = child.getBottom();
                    } else if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Translate) {
                        bottom = child.getBottom() + mSpinner;
                    }
                    canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(), bottom, mPaint);
                }
                if (mEnableClipHeaderWhenFixedBehind && mRefreshHeader.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
                    canvas.save();
                    canvas.clipRect(child.getLeft(), child.getTop(), child.getRight(), bottom);
                    boolean ret = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return ret;
                }
            }
        }
        if (mRefreshFooter != null && mRefreshFooter.getView() == child) {
            if (!isEnableRefreshOrLoadMore(mEnableLoadMore) || (!mEnablePreviewInEditMode && thisView.isInEditMode())) {
                return true;
            }
            if (contentView != null) {
                int top = Math.min(contentView.getBottom() - contentView.getPaddingBottom() + mSpinner, child.getBottom());
                if (mFooterBackgroundColor != 0 && mPaint != null) {
                    mPaint.setColor(mFooterBackgroundColor);
                    if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.Scale) {
                        top = child.getTop();
                    } else if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.Translate) {
                        top = child.getTop() + mSpinner;
                    }
                    canvas.drawRect(child.getLeft(), top, child.getRight(), child.getBottom(), mPaint);
                }
                if (mEnableClipFooterWhenFixedBehind && mRefreshFooter.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
                    canvas.save();
                    canvas.clipRect(child.getLeft(), top, child.getRight(), child.getBottom());
                    boolean ret = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return ret;
                }
            }

        }
        return super.drawChild(canvas, child, drawingTime);
    }


    
    protected boolean mVerticalPermit = false;                  
    @Override
    public void computeScroll() {
        int lastCurY = mScroller.getCurrY();
        if (mScroller.computeScrollOffset()) {
            int finalY = mScroller.getFinalY();
            if ((finalY < 0 && (mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh)) && mRefreshContent.canRefresh())
                    || (finalY > 0 && (mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableLoadMore)) && mRefreshContent.canLoadMore())) {
                if(mVerticalPermit) {
                    float velocity;
                    if (Build.VERSION.SDK_INT >= 14) {
                        velocity = finalY > 0 ? -mScroller.getCurrVelocity() : mScroller.getCurrVelocity();
                    } else {
                        velocity = 1f * (mScroller.getCurrY() - finalY) / Math.max((mScroller.getDuration() - mScroller.timePassed()), 1);
                    }
                    animSpinnerBounce(velocity);
                }
                mScroller.forceFinished(true);
            } else {
                mVerticalPermit = true;
                final View thisView = this;
                thisView.invalidate();
            }
        }
    }
    //</editor-fold>

    //</editor-fold>

    
    protected MotionEvent mFalsifyEvent = null;

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {

        
        //---------------------------------------------------------------------------
        
        //---------------------------------------------------------------------------
        final int action = e.getActionMasked();
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? e.getActionIndex() : -1;

        // Determine focal point
        float sumX = 0, sumY = 0;
        final int count = e.getPointerCount();
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            sumX += e.getX(i);
            sumY += e.getY(i);
        }
        final int div = pointerUp ? count - 1 : count;
        final float touchX = sumX / div;
        final float touchY = sumY / div;
        if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_POINTER_DOWN)
                && mIsBeingDragged) {
            mTouchY += touchY - mLastTouchY;
        }
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        //---------------------------------------------------------------------------
        //</editor-fold>

        final View thisView = this;
        if (mNestedInProgress) {
            int totalUnconsumed = mTotalUnconsumed;
            boolean ret = super.dispatchTouchEvent(e);
            //noinspection ConstantConditions
            if (action == MotionEvent.ACTION_MOVE) {
                if (totalUnconsumed == mTotalUnconsumed) {
                    final int offsetX = (int) mLastTouchX;
                    final int offsetMax = thisView.getWidth();
                    final float percentX = mLastTouchX / (offsetMax == 0 ? 1 : offsetMax);
                    if (isEnableRefreshOrLoadMore(mEnableRefresh) && mSpinner > 0 && mRefreshHeader != null && mRefreshHeader.isSupportHorizontalDrag()) {
                        mRefreshHeader.onHorizontalDrag(percentX, offsetX, offsetMax);
                    } else if (isEnableRefreshOrLoadMore(mEnableLoadMore) && mSpinner < 0 && mRefreshFooter != null && mRefreshFooter.isSupportHorizontalDrag()) {
                        mRefreshFooter.onHorizontalDrag(percentX, offsetX, offsetMax);
                    }
                }
            }
            return ret;
        } else if (!thisView.isEnabled()
                || (!isEnableRefreshOrLoadMore(mEnableRefresh) && !isEnableRefreshOrLoadMore(mEnableLoadMore) && !mEnableOverScrollDrag)
                || (mHeaderNeedTouchEventWhenRefreshing && ((mState.isOpening || mState.isFinishing) && mState.isHeader))
                || (mFooterNeedTouchEventWhenLoading && ((mState.isOpening || mState.isFinishing) && mState.isFooter))) {
            return super.dispatchTouchEvent(e);
        }

        if (interceptAnimatorByAction(action) || mState.isFinishing
                || (mState == RefreshState.Loading && mDisableContentWhenLoading)
                || (mState == RefreshState.Refreshing && mDisableContentWhenRefresh)) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                /*----------------------------------------------------*/
                
                /*----------------------------------------------------*/
                mCurrentVelocity = 0;
                mVelocityTracker.addMovement(e);
                mScroller.forceFinished(true);
                /*----------------------------------------------------*/
                
                /*----------------------------------------------------*/
                mTouchX = touchX;
                mTouchY = touchY;
                mLastSpinner = 0;
                mTouchSpinner = mSpinner;
                mIsBeingDragged = false;
                /*----------------------------------------------------*/
                mSuperDispatchTouchEvent = super.dispatchTouchEvent(e);
                if (mState == RefreshState.TwoLevel && mTouchY < 5 * thisView.getMeasuredHeight() / 6) {
                    mDragDirection = 'h';
                    return mSuperDispatchTouchEvent;
                }
                if (mRefreshContent != null) {
                    
                    mRefreshContent.onActionDown(e);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = touchX - mTouchX;
                float dy = touchY - mTouchY;
                mVelocityTracker.addMovement(e);
                if (!mIsBeingDragged && mDragDirection != 'h' && mRefreshContent != null) {
                    if (mDragDirection == 'v' || (Math.abs(dy) >= mTouchSlop && Math.abs(dx) < Math.abs(dy))) {
                        mDragDirection = 'v';
                        if (dy > 0 && (mSpinner < 0 || ((mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh)) && mRefreshContent.canRefresh()))) {
                            mIsBeingDragged = true;
                            mTouchY = touchY - mTouchSlop;
                        } else if (dy < 0 && (mSpinner > 0 || ((mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableLoadMore)) && ((mState==RefreshState.Loading&&mFooterLocked)||mRefreshContent.canLoadMore())))) {
                            mIsBeingDragged = true;
                            mTouchY = touchY + mTouchSlop;
                        }
                        if (mIsBeingDragged) {
                            dy = touchY - mTouchY;
                            if (mSuperDispatchTouchEvent) {
                                e.setAction(MotionEvent.ACTION_CANCEL);
                                super.dispatchTouchEvent(e);
                            }
                            if (mSpinner > 0 || (mSpinner == 0 && dy > 0)) {
                                mKernel.setState(RefreshState.PullDownToRefresh);
                            } else {
                                mKernel.setState(RefreshState.PullUpToLoad);
                            }
                            ViewParent parent = thisView.getParent();
                            if (parent != null) {
                                
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    } else if (Math.abs(dx) >= mTouchSlop && Math.abs(dx) > Math.abs(dy) && mDragDirection != 'v') {
                        mDragDirection = 'h';
                    }
                }
                if (mIsBeingDragged) {
                    int spinner = (int) dy + mTouchSpinner;
                    if ((mViceState.isHeader && (spinner < 0 || mLastSpinner < 0)) || (mViceState.isFooter && (spinner > 0 || mLastSpinner > 0))) {
                        mLastSpinner = spinner;
                        long time = e.getEventTime();
                        if (mFalsifyEvent == null) {
                            mFalsifyEvent = obtain(time, time, MotionEvent.ACTION_DOWN, mTouchX + dx, mTouchY, 0);
                            super.dispatchTouchEvent(mFalsifyEvent);
                        }
                        MotionEvent em = obtain(time, time, MotionEvent.ACTION_MOVE, mTouchX + dx, mTouchY + spinner, 0);
                        super.dispatchTouchEvent(em);
                        if (mFooterLocked && dy > mTouchSlop && mSpinner < 0) {
                            mFooterLocked = false;
                        }
                        if (spinner > 0 && ((mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh)) && mRefreshContent.canRefresh())) {
                            mTouchY = mLastTouchY = touchY;
                            mTouchSpinner = spinner = 0;
                            mKernel.setState(RefreshState.PullDownToRefresh);
                        } else if (spinner < 0 && ((mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableLoadMore)) && mRefreshContent.canLoadMore())) {
                            mTouchY = mLastTouchY = touchY;
                            mTouchSpinner = spinner = 0;
                            mKernel.setState(RefreshState.PullUpToLoad);
                        }
                        if ((mViceState.isHeader && spinner < 0) || (mViceState.isFooter && spinner > 0)) {
                            if (mSpinner != 0) {
                                moveSpinnerInfinitely(0);
                            }
                            return true;
                        } else if (mFalsifyEvent != null) {
                            mFalsifyEvent = null;
                            em.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(em);
                        }
                        em.recycle();
                    }
                    moveSpinnerInfinitely(spinner);
                    return true;
                } else if (mFooterLocked && dy > mTouchSlop && mSpinner < 0) {
                    mFooterLocked = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(e);
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                mCurrentVelocity = (int) mVelocityTracker.getYVelocity();
                startFlingIfNeed(null);
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.clear();
                mDragDirection = 'n';
                if (mFalsifyEvent != null) {
                    mFalsifyEvent.recycle();
                    mFalsifyEvent = null;
                    long time = e.getEventTime();
                    MotionEvent ec = obtain(time, time, action, mTouchX, touchY, 0);
                    super.dispatchTouchEvent(ec);
                    ec.recycle();
                }
                overSpinner();
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    
    protected boolean startFlingIfNeed(Float flingVelocity) {
        final float velocity = flingVelocity == null ? mCurrentVelocity : flingVelocity;
        if (Math.abs(velocity) > mMinimumVelocity) {
            if (velocity * mSpinner < 0) {
                if (mState.isOpening) {
                    if (mState != RefreshState.TwoLevel && mState != mViceState) {
                        
                        animationRunnable = new FlingRunnable(velocity).start();
                        return true;
                    }
                } else if (mSpinner > mHeaderHeight * mHeaderTriggerRate || -mSpinner > mFooterHeight * mFooterTriggerRate) {
                    return true;
                }
            }
            if ((velocity < 0 && ((mEnableOverScrollBounce && (mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableLoadMore))) || (mState == RefreshState.Loading && mSpinner >= 0) || (mEnableAutoLoadMore&&isEnableRefreshOrLoadMore(mEnableLoadMore))))
                    || (velocity > 0 && ((mEnableOverScrollBounce && (mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh))) || (mState == RefreshState.Refreshing && mSpinner <= 0)))) {
                mVerticalPermit = false;
                mScroller.fling(0, 0, 0, (int) -velocity, 0, 0, -Integer.MAX_VALUE, Integer.MAX_VALUE);
                mScroller.computeScrollOffset();
                final View thisView = this;
                thisView.invalidate();
            }
        }
        return false;
    }

    
    protected boolean interceptAnimatorByAction(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (reboundAnimator != null) {
                if (mState.isFinishing || mState == RefreshState.TwoLevelReleased) {
                    return true;
                }
                if (mState == RefreshState.PullDownCanceled) {
                    mKernel.setState(RefreshState.PullDownToRefresh);
                } else if (mState == RefreshState.PullUpCanceled) {
                    mKernel.setState(RefreshState.PullUpToLoad);
                }
                reboundAnimator.cancel();
                reboundAnimator = null;
            }
            animationRunnable = null;
        }
        return reboundAnimator != null;
    }

//    /*



//     */
//    @Override
//    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
//        // if this is a List < L or another view that doesn't support nested
//        // scrolling, ignore this request so that the vertical scroll event
//        // isn't stolen
//        View target = mRefreshContent.getScrollableView();
//        if ((android.os.Build.VERSION.SDK_INT >= 21 || !(target instanceof AbsListView))
//                && (target == null || ViewCompat.isNestedScrollingEnabled(target))) {
//            super.requestDisallowInterceptTouchEvent(disallowIntercept);
//            //} else {
//            // Nope.
//        }
//    }

    //</editor-fold>

    

    protected void notifyStateChanged(RefreshState state) {
        final RefreshState oldState = mState;
        if (oldState != state) {
            mState = state;
            mViceState = state;
            final OnStateChangedListener refreshHeader = mRefreshHeader;
            final OnStateChangedListener refreshFooter = mRefreshFooter;
            final OnStateChangedListener refreshListener = mOnMultiPurposeListener;
            if (refreshHeader != null) {
                refreshHeader.onStateChanged(this, oldState, state);
            }
            if (refreshFooter != null) {
                refreshFooter.onStateChanged(this, oldState, state);
            }
            if (refreshListener != null) {
                refreshListener.onStateChanged(this, oldState, state);
            }
        }
    }

    protected void setStateDirectLoading() {
        if (mState != RefreshState.Loading) {
            mLastOpenTime = currentTimeMillis();
//            if (mState != RefreshState.LoadReleased) {
//                if (mState != RefreshState.ReleaseToLoad) {
//                    if (mState != RefreshState.PullUpToLoad) {
//                        mKernel.setState(RefreshState.PullUpToLoad);
//                    }
//                    mKernel.setState(RefreshState.ReleaseToLoad);
//                }
//                notifyStateChanged(RefreshState.LoadReleased);
//                if (mRefreshFooter != null) {
//                    mRefreshFooter.onReleased(this, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
//                }
//            }
            mFooterLocked = true;
            notifyStateChanged(RefreshState.Loading);
            if (mLoadMoreListener != null) {
                mLoadMoreListener.onLoadMore(this);
            } else if (mOnMultiPurposeListener == null) {
                finishLoadMore(2000);
            }
            if (mRefreshFooter != null) {
                mRefreshFooter.onStartAnimator(this, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
            }
            if (mOnMultiPurposeListener != null && mRefreshFooter instanceof RefreshFooter) {
                mOnMultiPurposeListener.onLoadMore(this);
                mOnMultiPurposeListener.onFooterStartAnimator((RefreshFooter) mRefreshFooter, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
            }
        }
    }

    protected void setStateLoading() {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setStateDirectLoading();
            }
        };
        notifyStateChanged(RefreshState.LoadReleased);
        ValueAnimator animator = mKernel.animSpinner(-mFooterHeight);
        if (animator != null) {
            animator.addListener(listener);
        }
        if (mRefreshFooter != null) {
            
            
            mRefreshFooter.onReleased(this, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
        }
        if (mOnMultiPurposeListener != null && mRefreshFooter instanceof RefreshFooter) {
            
            mOnMultiPurposeListener.onFooterReleased((RefreshFooter) mRefreshFooter, mFooterHeight, (int) (mFooterMaxDragRate * mFooterHeight));
        }
        if (animator == null) {
            
            listener.onAnimationEnd(null);
        }
    }

    protected void setStateRefreshing() {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLastOpenTime = currentTimeMillis();
                notifyStateChanged(RefreshState.Refreshing);
                if (mRefreshListener != null) {
                    mRefreshListener.onRefresh(SmartRefreshLayout.this);
                } else if (mOnMultiPurposeListener == null) {
                    finishRefresh(3000);
                }
                if (mRefreshHeader != null) {
                    mRefreshHeader.onStartAnimator(SmartRefreshLayout.this, mHeaderHeight,  (int) (mHeaderMaxDragRate * mHeaderHeight));
                }
                if (mOnMultiPurposeListener != null && mRefreshHeader instanceof RefreshHeader) {
                    mOnMultiPurposeListener.onRefresh(SmartRefreshLayout.this);
                    mOnMultiPurposeListener.onHeaderStartAnimator((RefreshHeader) mRefreshHeader, mHeaderHeight,  (int) (mHeaderMaxDragRate * mHeaderHeight));
                }
            }
        };
        notifyStateChanged(RefreshState.RefreshReleased);
        ValueAnimator animator = mKernel.animSpinner(mHeaderHeight);
        if (animator != null) {
            animator.addListener(listener);
        }
        if (mRefreshHeader != null) {
            
            
            mRefreshHeader.onReleased(this, mHeaderHeight,  (int) (mHeaderMaxDragRate * mHeaderHeight));
        }
        if (mOnMultiPurposeListener != null && mRefreshHeader instanceof RefreshHeader) {
            
            mOnMultiPurposeListener.onHeaderReleased((RefreshHeader)mRefreshHeader, mHeaderHeight,  (int) (mHeaderMaxDragRate * mHeaderHeight));
        }
        if (animator == null) {
            
            listener.onAnimationEnd(null);
        }
    }

    
    protected void resetStatus() {
        if (mState != RefreshState.None) {
            if (mSpinner == 0) {
                notifyStateChanged(RefreshState.None);
            }
        }
        if (mSpinner != 0) {
            mKernel.animSpinner(0);
        }
    }

    protected void setViceState(RefreshState state) {
        if (mState.isDragging && mState.isHeader != state.isHeader) {
            notifyStateChanged(RefreshState.None);
        }
        if (mViceState != state) {
            mViceState = state;
        }
    }

    protected boolean isEnableTranslationContent(boolean enable, RefreshInternal internal) {
        return enable || mEnablePureScrollMode || internal == null || internal.getSpinnerStyle() == SpinnerStyle.FixedBehind;
    }

    protected boolean isEnableRefreshOrLoadMore(boolean enable) {
        return enable && !mEnablePureScrollMode;
    }

    //</editor-fold>

    

    
    protected Runnable animationRunnable;
    protected ValueAnimator reboundAnimator;
    protected class FlingRunnable implements Runnable {
        int mOffset;
        int mFrame = 0;
        int mFrameDelay = 10;
        float mVelocity;
        float mDamping = 0.98f;
        long mStartTime = 0;
        long mLastTime = AnimationUtils.currentAnimationTimeMillis();

        FlingRunnable(float velocity) {
            mVelocity = velocity;
            mOffset = mSpinner;
        }

        public Runnable start() {
            if (mState.isFinishing) {
                return null;
            }
            if (mSpinner != 0 && (!(mState.isOpening || (mFooterNoMoreData && mEnableFooterFollowWhenNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore)))
                    || ((mState == RefreshState.Loading || (mFooterNoMoreData && mEnableFooterFollowWhenNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore))) && mSpinner < -mFooterHeight)
                    || (mState == RefreshState.Refreshing && mSpinner > mHeaderHeight))) {
                int frame = 0;
                int offset = mSpinner;
                int spinner = mSpinner;
                float velocity = mVelocity;
                while (spinner * offset > 0) {
                    velocity *= Math.pow(mDamping, (++frame) * mFrameDelay / 10);
                    float velocityFrame = (velocity * (1f * mFrameDelay / 1000));
                    if (Math.abs(velocityFrame) < 1) {
                        if (!mState.isOpening
                                || (mState == RefreshState.Refreshing && offset > mHeaderHeight)
                                || (mState != RefreshState.Refreshing && offset < -mFooterHeight)) {
                            return null;
                        }
                        break;
                    }
                    offset += velocityFrame;
                }
            }
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            postDelayed(this, mFrameDelay);
            return this;
        }

        @Override
        public void run() {
            if (animationRunnable == this && !mState.isFinishing) {
//                mVelocity *= Math.pow(mDamping, ++mFrame);
                long now = AnimationUtils.currentAnimationTimeMillis();
                long span = now - mLastTime;
                mVelocity *= Math.pow(mDamping, (now - mStartTime) / (1000 / mFrameDelay));
                float velocity = (mVelocity * (1f * span / 1000));
                if (Math.abs(velocity) > 1) {
                    mLastTime = now;
                    mOffset += velocity;
                    if (mSpinner * mOffset > 0) {
                        mKernel.moveSpinner(mOffset, true);
                        postDelayed(this, mFrameDelay);
                    } else {
                        animationRunnable = null;
                        mKernel.moveSpinner(0, true);
                        fling(mRefreshContent.getScrollableView(), (int) -mVelocity);
                        if (mFooterLocked && velocity > 0) {
                            mFooterLocked = false;
                        }
                    }
                } else {
                    animationRunnable = null;
                }
            }
        }
    }
    protected class BounceRunnable implements Runnable {
        int mFrame = 0;
        int mFrameDelay = 10;
        int mSmoothDistance;
        long mLastTime;
        float mOffset = 0;
        float mVelocity;
        BounceRunnable(float velocity, int smoothDistance){
            mVelocity = velocity;
            mSmoothDistance = smoothDistance;
            mLastTime = AnimationUtils.currentAnimationTimeMillis();
            postDelayed(this, mFrameDelay);
        }
        @Override
        public void run() {
            if (animationRunnable == this && !mState.isFinishing) {
                if (Math.abs(mSpinner) >= Math.abs(mSmoothDistance)) {
                    if (mSmoothDistance != 0) {
                        mVelocity *= Math.pow(0.45f, ++mFrame * 2);
                    } else {
                        mVelocity *= Math.pow(0.85f, ++mFrame * 2);
                    }
                } else {
                    mVelocity *= Math.pow(0.95f, ++mFrame * 2);
                }
                long now = AnimationUtils.currentAnimationTimeMillis();
                float t = 1f * (now - mLastTime) / 1000;
                float velocity = mVelocity * t;
                if (Math.abs(velocity) >= 1) {
                    mLastTime = now;
                    mOffset += velocity;
                    moveSpinnerInfinitely(mOffset);
                    postDelayed(this, mFrameDelay);
                } else {
                    animationRunnable = null;
                    if (Math.abs(mSpinner) >= Math.abs(mSmoothDistance)) {
                        int duration = 10 * Math.min(Math.max((int) DensityUtil.px2dp(Math.abs(mSpinner-mSmoothDistance)), 30), 100);
                        animSpinner(mSmoothDistance, 0, mReboundInterpolator, duration);
                    }
                }
            }
        }
    }
    //</editor-fold>

    
    protected ValueAnimator animSpinner(int endSpinner, int startDelay, Interpolator interpolator, int duration) {
        if (mSpinner != endSpinner) {
            if (reboundAnimator != null) {
                reboundAnimator.cancel();
            }
            animationRunnable = null;
            reboundAnimator = ValueAnimator.ofInt(mSpinner, endSpinner);
            reboundAnimator.setDuration(duration);
            reboundAnimator.setInterpolator(interpolator);
            reboundAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationEnd(animation);
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    reboundAnimator = null;
                    if (mSpinner == 0) {
                        if (mState != RefreshState.None && !mState.isOpening) {
                            notifyStateChanged(RefreshState.None);
                        }
                    } else if (mState != mViceState) {
                        setViceState(mState);
                    }
                }
            });
            reboundAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mKernel.moveSpinner((int) animation.getAnimatedValue(), false);
                }
            });
            reboundAnimator.setStartDelay(startDelay);
//            reboundAnimator.setDuration(20000);
            reboundAnimator.start();
            return reboundAnimator;
        }
        return null;
    }

    
    protected void animSpinnerBounce(final float velocity) {
        if (reboundAnimator == null) {
            if (velocity > 0 && (mState == RefreshState.Refreshing || mState == RefreshState.TwoLevel)) {
                animationRunnable = new BounceRunnable(velocity, mHeaderHeight);
            } else if (velocity < 0 && (mState == RefreshState.Loading
                    || (mEnableFooterFollowWhenNoMoreData && mFooterNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore))
                    || (mEnableAutoLoadMore && !mFooterNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore) && mState != RefreshState.Refreshing))) {
                animationRunnable = new BounceRunnable(velocity, -mFooterHeight);
            } else if (mSpinner == 0 && mEnableOverScrollBounce) {
                animationRunnable = new BounceRunnable(velocity, 0);
            }
        }
    }

    
    protected void overSpinner() {
        if (mState == RefreshState.TwoLevel) {
            final View thisView = this;
            if (mCurrentVelocity > -1000 && mSpinner > thisView.getMeasuredHeight() / 2) {
                ValueAnimator animator = mKernel.animSpinner(thisView.getMeasuredHeight());
                if (animator != null) {
                    animator.setDuration(mFloorDuration);
                }
            } else if (mIsBeingDragged) {
                mKernel.finishTwoLevel();
            }
        } else if (mState == RefreshState.Loading
                || (mEnableFooterFollowWhenNoMoreData && mFooterNoMoreData && mSpinner < 0 && isEnableRefreshOrLoadMore(mEnableLoadMore))) {
            if (mSpinner < -mFooterHeight) {
                mKernel.animSpinner(-mFooterHeight);
            } else if (mSpinner > 0) {
                mKernel.animSpinner(0);
            }
        } else if (mState == RefreshState.Refreshing) {
            if (mSpinner > mHeaderHeight) {
                mKernel.animSpinner(mHeaderHeight);
            } else if (mSpinner < 0) {
                mKernel.animSpinner(0);
            }
        } else if (mState == RefreshState.PullDownToRefresh) {
            mKernel.setState(RefreshState.PullDownCanceled);
        } else if (mState == RefreshState.PullUpToLoad) {
            mKernel.setState(RefreshState.PullUpCanceled);
        } else if (mState == RefreshState.ReleaseToRefresh) {
            mKernel.setState(RefreshState.Refreshing);
        } else if (mState == RefreshState.ReleaseToLoad) {
            mKernel.setState(RefreshState.Loading);
        } else if (mState == RefreshState.ReleaseToTwoLevel) {
            mKernel.setState(RefreshState.TwoLevelReleased);
        } else if (mState == RefreshState.RefreshReleased) {
            if (reboundAnimator == null) {
                mKernel.animSpinner(mHeaderHeight);
            }
        } else if (mState == RefreshState.LoadReleased) {
            if (reboundAnimator == null) {
                mKernel.animSpinner(-mFooterHeight);
            }
        } else if (mSpinner != 0) {
            mKernel.animSpinner(0);
        }
    }

    protected void moveSpinnerInfinitely(float spinner) {
        final View thisView = this;
        if (mState == RefreshState.TwoLevel && spinner > 0) {
            mKernel.moveSpinner(Math.min((int) spinner, thisView.getMeasuredHeight()), true);
        } else if (mState == RefreshState.Refreshing && spinner >= 0) {
            if (spinner < mHeaderHeight) {
                mKernel.moveSpinner((int) spinner, true);
            } else {
                final double M = (mHeaderMaxDragRate - 1) * mHeaderHeight;
                final double H = Math.max(mScreenHeightPixels * 4 / 3, thisView.getHeight()) - mHeaderHeight;
                final double x = Math.max(0, (spinner - mHeaderHeight) * mDragRate);
                final double y = Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);
                mKernel.moveSpinner((int) y + mHeaderHeight, true);
            }
        } else if (spinner < 0 && (mState == RefreshState.Loading
                || (mEnableFooterFollowWhenNoMoreData && mFooterNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore))
                || (mEnableAutoLoadMore && !mFooterNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore)))) {
            if (spinner > -mFooterHeight) {
                mKernel.moveSpinner((int) spinner, true);
            } else {
                final double M = (mFooterMaxDragRate - 1) * mFooterHeight;
                final double H = Math.max(mScreenHeightPixels * 4 / 3, thisView.getHeight()) - mFooterHeight;
                final double x = -Math.min(0, (spinner + mFooterHeight) * mDragRate);
                final double y = -Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);
                mKernel.moveSpinner((int) y - mFooterHeight, true);
            }
        } else if (spinner >= 0) {
            final double M = mHeaderMaxDragRate * mHeaderHeight;
            final double H = Math.max(mScreenHeightPixels / 2, thisView.getHeight());
            final double x = Math.max(0, spinner * mDragRate);
            final double y = Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);
            mKernel.moveSpinner((int) y, true);
        } else {
            final double M = mFooterMaxDragRate * mFooterHeight;
            final double H = Math.max(mScreenHeightPixels / 2, thisView.getHeight());
            final double x = -Math.min(0, spinner * mDragRate);
            final double y = -Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);
            mKernel.moveSpinner((int) y, true);
        }
        if (mEnableAutoLoadMore && !mFooterNoMoreData && isEnableRefreshOrLoadMore(mEnableLoadMore) && spinner < 0
                && mState != RefreshState.Refreshing
                && mState != RefreshState.Loading
                && mState != RefreshState.LoadFinish) {
            setStateDirectLoading();
            if (mDisableContentWhenLoading) {
                animationRunnable = null;
                mKernel.animSpinner(-mFooterHeight);
            }
        }
    }

    //</editor-fold>

    
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(MATCH_PARENT, MATCH_PARENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        final View thisView = this;
        return new LayoutParams(thisView.getContext(), attrs);
    }

    public static class LayoutParams extends MarginLayoutParams {

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SmartRefreshLayout_Layout);
            backgroundColor = ta.getColor(R.styleable.SmartRefreshLayout_Layout_layout_srlBackgroundColor, backgroundColor);
            if (ta.hasValue(R.styleable.SmartRefreshLayout_Layout_layout_srlSpinnerStyle)) {
                spinnerStyle = SpinnerStyle.values()[ta.getInt(R.styleable.SmartRefreshLayout_Layout_layout_srlSpinnerStyle, SpinnerStyle.Translate.ordinal())];
            }
            ta.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public int backgroundColor = 0;
        public SpinnerStyle spinnerStyle = null;
    }
    //</editor-fold>

    

    //<editor-fold desc="NestedScrollingParent">

    @Override
    public int getNestedScrollAxes() {
        return mNestedParent.getNestedScrollAxes();
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
        final View thisView = this;
        boolean accepted = thisView.isEnabled() && isNestedScrollingEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        accepted = accepted && (mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh) || isEnableRefreshOrLoadMore(mEnableLoadMore));
        return accepted;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedParent.onNestedScrollAccepted(child, target, axes);
        // Dispatch up to the nested parent
        mNestedChild.startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mTotalUnconsumed = mSpinner;//0;
        mNestedInProgress = true;
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        int consumedY = 0;

        if (dy * mTotalUnconsumed > 0) {
            if (Math.abs(dy) > Math.abs(mTotalUnconsumed)) {
                consumedY = mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                consumedY = dy;
                mTotalUnconsumed -= dy;
            }
            moveSpinnerInfinitely(mTotalUnconsumed);
            if (mViceState.isOpening || mViceState == RefreshState.None) {
                if (mSpinner > 0) {
                    mKernel.setState(RefreshState.PullDownToRefresh);
                } else {
                    mKernel.setState(RefreshState.PullUpToLoad);
                }
            }
        } else if (dy > 0 && mFooterLocked) {
            consumedY = dy;
            mTotalUnconsumed -= dy;
            moveSpinnerInfinitely(mTotalUnconsumed);
        }

        // Now let our nested parent consume the leftovers
        mNestedChild.dispatchNestedPreScroll(dx, dy - consumedY, consumed, null);
        consumed[1] += consumedY;

    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        // Dispatch up to the nested parent first
        mNestedChild.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        final int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy != 0 && (mEnableOverScrollDrag || (dy < 0 && isEnableRefreshOrLoadMore(mEnableRefresh)) || (dy > 0 && isEnableRefreshOrLoadMore(mEnableLoadMore)))) {
            if (mViceState == RefreshState.None) {
                mKernel.setState(dy > 0 ? RefreshState.PullUpToLoad : RefreshState.PullDownToRefresh);
            }
            moveSpinnerInfinitely(mTotalUnconsumed -= dy);
        }

        if (mFooterLocked && dyConsumed < 0) {
            mFooterLocked = false;
        }

    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return mFooterLocked && velocityY > 0 || startFlingIfNeed(-velocityY) || mNestedChild.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return mNestedChild.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target) {
        mNestedParent.onStopNestedScroll(target);
        mNestedInProgress = false;
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        mTotalUnconsumed = 0;
        overSpinner();
        // Dispatch up our nested parent
        mNestedChild.stopNestedScroll();
    }
    //</editor-fold>

    //<editor-fold desc="NestedScrollingChild">
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mManualNestedScrolling = true;
        mNestedChild.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedChild.isNestedScrollingEnabled();
    }

//    @Override
//    public boolean canScrollVertically(int direction) {
//        View target = mRefreshContent.getScrollableView();
//        if (direction < 0) {
//            return mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableRefresh) || ScrollBoundaryUtil.canScrollUp(target);
//        } else if (direction > 0) {
//            return mEnableOverScrollDrag || isEnableRefreshOrLoadMore(mEnableLoadMore) || ScrollBoundaryUtil.canScrollDown(target);
//        }
//        return true;
//    }

    //    @Override
//    @Deprecated
//    public boolean startNestedScroll(int axes) {
//        return mNestedChild.startNestedScroll(axes);
//    }
//
//    @Override
//    @Deprecated
//    public void stopNestedScroll() {
//        mNestedChild.stopNestedScroll();
//    }
//
//    @Override
//    @Deprecated
//    public boolean hasNestedScrollingParent() {
//        return mNestedChild.hasNestedScrollingParent();
//    }
//
//    @Override
//    @Deprecated
//    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
//                                        int dyUnconsumed, int[] offsetInWindow) {
//        return mNestedChild.dispatchNestedScroll(dxConsumed, dyConsumed,
//                dxUnconsumed, dyUnconsumed, offsetInWindow);
//    }
//
//    @Override
//    @Deprecated
//    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
//        return mNestedChild.dispatchNestedPreScroll(
//                dx, dy, consumed, offsetInWindow);
//    }
//
//    @Override
//    @Deprecated
//    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
//        return mNestedChild.dispatchNestedFling(velocityX, velocityY, consumed);
//    }
//
//    @Override
//    @Deprecated
//    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
//        return mNestedChild.dispatchNestedPreFling(velocityX, velocityY);
//    }
    //</editor-fold>

    //</editor-fold>

    

    
    @Override
    public SmartRefreshLayout setFooterHeight(float heightDp) {
        if (mFooterHeightStatus.canReplaceWith(DimensionStatus.CodeExact)) {
            mFooterHeight = dp2px(heightDp);
            mFooterHeightStatus = DimensionStatus.CodeExactUnNotify;
            if (mRefreshFooter != null) {
                mRefreshFooter.getView().requestLayout();
            }
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setHeaderHeight(float heightDp) {
        if (mHeaderHeightStatus.canReplaceWith(DimensionStatus.CodeExact)) {
            mHeaderHeight = dp2px(heightDp);
            mHeaderHeightStatus = DimensionStatus.CodeExactUnNotify;
            if (mRefreshHeader != null) {
                mRefreshHeader.getView().requestLayout();
            }
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setHeaderInsetStart(float insetDp) {
        mHeaderInsetStart = dp2px(insetDp);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setFooterInsetStart(float insetDp) {
        mFooterInsetStart = dp2px(insetDp);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setDragRate(float rate) {
        this.mDragRate = rate;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setHeaderMaxDragRate(float rate) {
        this.mHeaderMaxDragRate = rate;
        if (mRefreshHeader != null && mHandler != null) {
            mRefreshHeader.onInitialized(mKernel, mHeaderHeight,  (int) (mHeaderMaxDragRate * mHeaderHeight));
        } else {
            mHeaderHeightStatus = mHeaderHeightStatus.unNotify();
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setFooterMaxDragRate(float rate) {
        this.mFooterMaxDragRate = rate;
        if (mRefreshFooter != null && mHandler != null) {
            mRefreshFooter.onInitialized(mKernel, mFooterHeight, (int)(mFooterHeight * mFooterMaxDragRate));
        } else {
            mFooterHeightStatus = mFooterHeightStatus.unNotify();
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setHeaderTriggerRate(float rate) {
        this.mHeaderTriggerRate = rate;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setFooterTriggerRate(float rate) {
        this.mFooterTriggerRate = rate;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setReboundInterpolator(@NonNull Interpolator interpolator) {
        this.mReboundInterpolator = interpolator;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setReboundDuration(int duration) {
        this.mReboundDuration = duration;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableLoadMore(boolean enabled) {
        this.mManualLoadMore = true;
        this.mEnableLoadMore = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableRefresh(boolean enabled) {
        this.mEnableRefresh = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableHeaderTranslationContent(boolean enabled) {
        this.mEnableHeaderTranslationContent = enabled;
        this.mManualHeaderTranslationContent = true;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableFooterTranslationContent(boolean enabled) {
        this.mEnableFooterTranslationContent = enabled;
        this.mManualFooterTranslationContent = true;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableAutoLoadMore(boolean enabled) {
        this.mEnableAutoLoadMore = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableOverScrollBounce(boolean enabled) {
        this.mEnableOverScrollBounce = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnablePureScrollMode(boolean enabled) {
        this.mEnablePureScrollMode = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableScrollContentWhenLoaded(boolean enabled) {
        this.mEnableScrollContentWhenLoaded = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableScrollContentWhenRefreshed(boolean enabled) {
        this.mEnableScrollContentWhenRefreshed = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableLoadMoreWhenContentNotFull(boolean enabled) {
        this.mEnableLoadMoreWhenContentNotFull = enabled;
        if (mRefreshContent != null) {
            mRefreshContent.setEnableLoadMoreWhenContentNotFull(enabled);
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableOverScrollDrag(boolean enabled) {
        this.mEnableOverScrollDrag = enabled;
        return this;
    }

    
    @Override
    @Deprecated
    public SmartRefreshLayout setEnableFooterFollowWhenLoadFinished(boolean enabled) {
        this.mEnableFooterFollowWhenNoMoreData = enabled;
        return this;
    }

    
    @Override
    public RefreshLayout setEnableFooterFollowWhenNoMoreData(boolean enabled) {
        this.mEnableFooterFollowWhenNoMoreData = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableClipHeaderWhenFixedBehind(boolean enabled) {
        this.mEnableClipHeaderWhenFixedBehind = enabled;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setEnableClipFooterWhenFixedBehind(boolean enabled) {
        this.mEnableClipFooterWhenFixedBehind = enabled;
        return this;
    }

    
    @Override
    public RefreshLayout setEnableNestedScroll(boolean enabled) {
        setNestedScrollingEnabled(enabled);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setDisableContentWhenRefresh(boolean disable) {
        this.mDisableContentWhenRefresh = disable;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setDisableContentWhenLoading(boolean disable) {
        this.mDisableContentWhenLoading = disable;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setRefreshHeader(@NonNull RefreshHeader header) {
        return setRefreshHeader(header, MATCH_PARENT, WRAP_CONTENT);
    }

    
    @Override
    public SmartRefreshLayout setRefreshHeader(@NonNull RefreshHeader header, int width, int height) {
        if (mRefreshHeader != null) {
            super.removeView(mRefreshHeader.getView());
        }
        this.mRefreshHeader = header;
        this.mHeaderBackgroundColor = 0;
        this.mHeaderNeedTouchEventWhenRefreshing = false;
        this.mHeaderHeightStatus = mHeaderHeightStatus.unNotify();
        if (header.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
            super.addView(mRefreshHeader.getView(), 0, new LayoutParams(width, height));
        } else {
            super.addView(mRefreshHeader.getView(), width, height);
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setRefreshFooter(@NonNull RefreshFooter footer) {
        return setRefreshFooter(footer, MATCH_PARENT, WRAP_CONTENT);
    }

    
    @Override
    public SmartRefreshLayout setRefreshFooter(@NonNull RefreshFooter footer, int width, int height) {
        if (mRefreshFooter != null) {
            super.removeView(mRefreshFooter.getView());
        }
        this.mRefreshFooter = footer;
        this.mFooterBackgroundColor = 0;
        this.mFooterNeedTouchEventWhenLoading = false;
        this.mFooterHeightStatus = mFooterHeightStatus.unNotify();
        this.mEnableLoadMore = !mManualLoadMore || mEnableLoadMore;
        if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
            super.addView(mRefreshFooter.getView(), 0, new LayoutParams(width, height));
        } else {
            super.addView(mRefreshFooter.getView(), width, height);
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout setRefreshContent(@NonNull View content) {
        return setRefreshContent(content, MATCH_PARENT, MATCH_PARENT);
    }

    
    @Override
    public SmartRefreshLayout setRefreshContent(@NonNull View content, int width, int height) {
        final View thisView = this;
        if (mRefreshContent != null) {
            super.removeView(mRefreshContent.getView());
        }
        super.addView(content, 0, new LayoutParams(width, height));
        if (mRefreshHeader != null && mRefreshHeader.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
            super.bringChildToFront(content);
            if (mRefreshFooter != null && mRefreshFooter.getSpinnerStyle() != SpinnerStyle.FixedBehind) {
                super.bringChildToFront(mRefreshFooter.getView());
            }
        } else if (mRefreshFooter != null && mRefreshFooter.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
            super.bringChildToFront(content);
            if (mRefreshHeader != null && mRefreshHeader.getSpinnerStyle() == SpinnerStyle.FixedBehind) {
                super.bringChildToFront(mRefreshHeader.getView());
            }
        }
        mRefreshContent = new RefreshContentWrapper(content);
        if (mHandler != null) {
            View fixedHeaderView = mFixedHeaderViewId > 0 ? thisView.findViewById(mFixedHeaderViewId) : null;
            View fixedFooterView = mFixedFooterViewId > 0 ? thisView.findViewById(mFixedFooterViewId) : null;

            mRefreshContent.setScrollBoundaryDecider(mScrollBoundaryDecider);
            mRefreshContent.setEnableLoadMoreWhenContentNotFull(mEnableLoadMoreWhenContentNotFull);
            mRefreshContent.setUpComponent(mKernel, fixedHeaderView, fixedFooterView);
        }
        return this;
    }

    
    @Nullable
    @Override
    public RefreshFooter getRefreshFooter() {
        return mRefreshFooter instanceof RefreshFooter ? (RefreshFooter) mRefreshFooter : null;
    }

    
    @Nullable
    @Override
    public RefreshHeader getRefreshHeader() {
        return mRefreshHeader instanceof RefreshHeader ? (RefreshHeader) mRefreshHeader : null;
    }

    
    @Override
    public RefreshState getState() {
        return mState;
    }

    
    @Override
    public SmartRefreshLayout getLayout() {
        return this;
    }

    
    @Override
    public SmartRefreshLayout setOnRefreshListener(OnRefreshListener listener) {
        this.mRefreshListener = listener;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setOnLoadMoreListener(OnLoadMoreListener listener) {
        this.mLoadMoreListener = listener;
        this.mEnableLoadMore = mEnableLoadMore || (!mManualLoadMore && listener != null);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setOnRefreshLoadMoreListener(OnRefreshLoadMoreListener listener) {
        this.mRefreshListener = listener;
        this.mLoadMoreListener = listener;
        this.mEnableLoadMore = mEnableLoadMore || (!mManualLoadMore && listener != null);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setOnMultiPurposeListener(OnMultiPurposeListener listener) {
        this.mOnMultiPurposeListener = listener;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setPrimaryColors(@ColorInt int... primaryColors) {
        if (mRefreshHeader != null) {
            mRefreshHeader.setPrimaryColors(primaryColors);
        }
        if (mRefreshFooter != null) {
            mRefreshFooter.setPrimaryColors(primaryColors);
        }
        mPrimaryColors = primaryColors;
        return this;
    }

    
    @Override
    public SmartRefreshLayout setPrimaryColorsId(@ColorRes int... primaryColorId) {
        final View thisView = this;
        final int[] colors = new int[primaryColorId.length];
        for (int i = 0; i < primaryColorId.length; i++) {
            colors[i] = getColor(thisView.getContext(), primaryColorId[i]);
        }
        setPrimaryColors(colors);
        return this;
    }

    
    @Override
    public SmartRefreshLayout setScrollBoundaryDecider(ScrollBoundaryDecider boundary) {
        mScrollBoundaryDecider = boundary;
        if (mRefreshContent != null) {
            mRefreshContent.setScrollBoundaryDecider(boundary);
        }
        return this;
    }

    
    @Override
    @Deprecated
    public SmartRefreshLayout setNoMoreData(boolean noMoreData) {
        if (mState == RefreshState.Loading) {
            finishLoadMore();
        }
        mFooterNoMoreData = noMoreData;
        if (mRefreshFooter instanceof RefreshFooter && !((RefreshFooter)mRefreshFooter).setNoMoreData(noMoreData)) {
            System.out.println("Footer:" + mRefreshFooter + " NoMoreData is not supported.");
        }
        return this;
    }

    
    @Override
    public RefreshLayout resetNoMoreData() {
        mFooterNoMoreData = false;
        if (mRefreshFooter instanceof RefreshFooter && !((RefreshFooter)mRefreshFooter).setNoMoreData(false)) {
            System.out.println("Footer:" + mRefreshFooter + " NoMoreData is not supported.");
        }
        return this;
    }

    
    @Override
    public SmartRefreshLayout finishRefresh() {
        long passTime = System.currentTimeMillis() - mLastOpenTime;
        return finishRefresh(Math.min(Math.max(0, 300 - (int) passTime), 300));
    }

    
    @Override
    public SmartRefreshLayout finishLoadMore() {
        long passTime = System.currentTimeMillis() - mLastOpenTime;
        return finishLoadMore(Math.min(Math.max(0, 300 - (int) passTime), 300));
    }

    
    @Override
    public SmartRefreshLayout finishRefresh(int delayed) {
        return finishRefresh(delayed, true);
    }

    
    @Override
    public SmartRefreshLayout finishRefresh(boolean success) {
        long passTime = System.currentTimeMillis() - mLastOpenTime;
        return finishRefresh(success ? Math.min(Math.max(0, 300 - (int) passTime), 300) : 0, success);
    }

    
    @Override
    public SmartRefreshLayout finishRefresh(int delayed, final boolean success) {
        if (mState == RefreshState.Refreshing && success) {
            resetNoMoreData();
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mState == RefreshState.Refreshing && mRefreshHeader != null && mRefreshContent != null) {
                    notifyStateChanged(RefreshState.RefreshFinish);
                    int startDelay = mRefreshHeader.onFinish(SmartRefreshLayout.this, success);
                    if (mOnMultiPurposeListener != null && mRefreshHeader instanceof RefreshHeader) {
                        mOnMultiPurposeListener.onHeaderFinish((RefreshHeader) mRefreshHeader, success);
                    }
                    if (startDelay < Integer.MAX_VALUE) {
                        if (mIsBeingDragged || mNestedInProgress) {
                            if (mIsBeingDragged) {
                                mTouchY = mLastTouchY;
                                mTouchSpinner = 0;
                                mIsBeingDragged = false;
                            }
                            long time = System.currentTimeMillis();
                            SmartRefreshLayout.super.dispatchTouchEvent(obtain(time, time, MotionEvent.ACTION_DOWN, mLastTouchX, mLastTouchY + mSpinner - mTouchSlop*2, 0));
                            SmartRefreshLayout.super.dispatchTouchEvent(obtain(time, time, MotionEvent.ACTION_MOVE, mLastTouchX, mLastTouchY + mSpinner, 0));
                            if (mNestedInProgress) {
                                mTotalUnconsumed = 0;
                            }
                        }
                        if (mSpinner > 0) {
                            AnimatorUpdateListener updateListener = null;
                            ValueAnimator valueAnimator = animSpinner(0, startDelay, mReboundInterpolator, mReboundDuration);
                            if (mEnableScrollContentWhenRefreshed) {
                                updateListener = mRefreshContent.scrollContentWhenFinished(mSpinner);
                            }
                            if (valueAnimator != null && updateListener != null) {
                                valueAnimator.addUpdateListener(updateListener);
                            }
                        } else if (mSpinner < 0) {
                            animSpinner(0, startDelay, mReboundInterpolator, mReboundDuration);
                        } else {
                            mKernel.moveSpinner(0, false);
                            resetStatus();
                        }
                    }
                }
            }
        }, delayed <= 0 ? 1 : delayed);
        return this;
    }

    
    @Override
    public SmartRefreshLayout finishLoadMore(int delayed) {
        return finishLoadMore(delayed, true, false);
    }

    
    @Override
    public SmartRefreshLayout finishLoadMore(boolean success) {
        long passTime = System.currentTimeMillis() - mLastOpenTime;
        return finishLoadMore(success ? Math.min(Math.max(0, 300 - (int) passTime), 300) : 0, success, false);
    }

    
    @Override
    public SmartRefreshLayout finishLoadMore(int delayed, final boolean success, final boolean noMoreData) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mState == RefreshState.Loading && mRefreshFooter != null && mRefreshContent != null) {
                    notifyStateChanged(RefreshState.LoadFinish);
                    final int startDelay = mRefreshFooter.onFinish(SmartRefreshLayout.this, success);
                    if (mOnMultiPurposeListener != null && mRefreshFooter instanceof RefreshFooter) {
                        mOnMultiPurposeListener.onFooterFinish((RefreshFooter) mRefreshFooter, success);
                    }
                    if (startDelay < Integer.MAX_VALUE) {
                        
                        final boolean needHoldFooter = noMoreData && mEnableFooterFollowWhenNoMoreData && mSpinner < 0 && mRefreshContent.canLoadMore();
                        final int offset = mSpinner - (needHoldFooter ? Math.max(mSpinner,-mFooterHeight) : 0);
                        
                        if (mIsBeingDragged || mNestedInProgress) {
                            if (mIsBeingDragged) {
                                mTouchY = mLastTouchY;
                                mIsBeingDragged = false;
                                mTouchSpinner = mSpinner - offset;
                            }
                            final long time = System.currentTimeMillis();
                            SmartRefreshLayout.super.dispatchTouchEvent(obtain(time, time, MotionEvent.ACTION_DOWN, mLastTouchX, mLastTouchY + offset + mTouchSlop * 2, 0));
                            SmartRefreshLayout.super.dispatchTouchEvent(obtain(time, time, MotionEvent.ACTION_MOVE, mLastTouchX, mLastTouchY + offset, 0));
                            if (mNestedInProgress) {
                                mTotalUnconsumed = 0;
                            }
                        }
                        
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                AnimatorUpdateListener updateListener = null;
                                if (mEnableScrollContentWhenLoaded && offset < 0) {
                                    updateListener = mRefreshContent.scrollContentWhenFinished(mSpinner);
                                }
                                if (updateListener != null) {
                                    updateListener.onAnimationUpdate(ValueAnimator.ofInt(0, 0));
                                }
                                ValueAnimator animator = null;
                                AnimatorListenerAdapter listenerAdapter = new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationCancel(Animator animation) {
                                        super.onAnimationEnd(animation);
                                    }
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mFooterLocked = false;
                                        if (noMoreData) {
                                            setNoMoreData(true);
                                        }
                                        if (mState == RefreshState.LoadFinish) {
                                            notifyStateChanged(RefreshState.None);
                                        }
                                    }
                                };
                                if (mSpinner > 0) {
                                    animator = mKernel.animSpinner(0);
                                } else if (updateListener != null || mSpinner == 0) {
                                    if (reboundAnimator != null) {
                                        reboundAnimator.cancel();
                                        reboundAnimator = null;
                                    }
                                    mKernel.moveSpinner(0, false);
                                    resetStatus();
                                } else {
                                    if (noMoreData && mEnableFooterFollowWhenNoMoreData) {
                                        if (mSpinner >= -mFooterHeight) {
                                            notifyStateChanged(RefreshState.None);
                                        } else {
                                            animator = mKernel.animSpinner(-mFooterHeight);
                                        }
                                    } else {
                                        animator = mKernel.animSpinner(0);
                                    }
                                }
                                if (animator != null) {
                                    animator.addListener(listenerAdapter);
                                } else {
                                    listenerAdapter.onAnimationEnd(null);
                                }
                            }
                        }, mSpinner < 0 ? startDelay : 0);
                    }
                } else {
                    if (noMoreData) {
                        setNoMoreData(true);
                    }
                }
            }
        }, delayed <= 0 ? 1 : delayed);
        return this;
    }

    
    @Override
    public SmartRefreshLayout finishLoadMoreWithNoMoreData() {
        long passTime = System.currentTimeMillis() - mLastOpenTime;
        return finishLoadMore(Math.min(Math.max(0, 300 - (int) passTime), 300), true, true);
    }

    
    @Override
    public RefreshLayout closeHeaderOrFooter() {
        if (mState == RefreshState.Refreshing) {
            finishRefresh();
        } else if (mState == RefreshState.Loading) {
            finishLoadMore();
        } else if (mSpinner != 0) {
            animSpinner(0, 0, mReboundInterpolator, mReboundDuration);
        }
        return this;
    }

    
    @Override
    public boolean autoRefresh() {
        return autoRefresh(mHandler == null ? 400 : 0, mReboundDuration, 1f * ((mHeaderMaxDragRate/2 + 0.5f) * mHeaderHeight) / (mHeaderHeight == 0 ? 1 : mHeaderHeight), false);
    }

    
    @Override
    @Deprecated
    public boolean autoRefresh(int delayed) {
        return autoRefresh(delayed, mReboundDuration, 1f * ((mHeaderMaxDragRate/2 + 0.5f) * mHeaderHeight) / (mHeaderHeight == 0 ? 1 : mHeaderHeight), false);
    }


    
    @Override
    public boolean autoRefreshAnimationOnly() {
        return autoRefresh(mHandler == null ? 400 : 0, mReboundDuration, 1f * ((mHeaderMaxDragRate/2 + 0.5f) * mHeaderHeight) / (mHeaderHeight == 0 ? 1 : mHeaderHeight), true);
    }

    
    @Override
    public boolean autoRefresh(int delayed, final int duration, final float dragRate,final boolean animationOnly) {
        if (mState == RefreshState.None && isEnableRefreshOrLoadMore(mEnableRefresh)) {
            if (reboundAnimator != null) {
                reboundAnimator.cancel();
            }
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    reboundAnimator = ValueAnimator.ofInt(mSpinner, (int) (mHeaderHeight * dragRate));
                    reboundAnimator.setDuration(duration);
                    reboundAnimator.setInterpolator(new DecelerateInterpolator());
                    reboundAnimator.addUpdateListener(new AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mKernel.moveSpinner((int) animation.getAnimatedValue(), true);
                        }
                    });
                    reboundAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            final View thisView = SmartRefreshLayout.this;
                            mLastTouchX = thisView.getMeasuredWidth() / 2;
                            mKernel.setState(RefreshState.PullDownToRefresh);
                        }
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            reboundAnimator = null;
                            if (animationOnly) {
                                if (mState == RefreshState.ReleaseToRefresh) {
                                    mKernel.setState(RefreshState.PullDownToRefresh);
                                }
                            } else {
                                if (mState != RefreshState.ReleaseToRefresh) {
                                    mKernel.setState(RefreshState.ReleaseToRefresh);
                                }
                            }
                            overSpinner();
                        }
                    });
                    reboundAnimator.start();
                }
            };
            if (delayed > 0) {
                reboundAnimator = new ValueAnimator();
                postDelayed(runnable, delayed);
            } else {
                runnable.run();
            }
            return true;
        } else {
            return false;
        }
    }

    
    @Override
    public boolean autoLoadMore() {
        return autoLoadMore(0, mReboundDuration, 1f * (mFooterHeight * (mFooterMaxDragRate / 2 + 0.5f)) / (mFooterHeight == 0 ? 1 : mFooterHeight), false);
    }

    
    @Override
    @Deprecated
    public boolean autoLoadMore(int delayed) {
        return autoLoadMore(delayed, mReboundDuration, 1f * (mFooterHeight * (mFooterMaxDragRate / 2 + 0.5f)) / (mFooterHeight == 0 ? 1 : mFooterHeight), false);
    }

    
    @Override
    public boolean autoLoadMoreAnimationOnly() {
        return autoLoadMore(0, mReboundDuration, 1f * (mFooterHeight * (mFooterMaxDragRate / 2 + 0.5f)) / (mFooterHeight == 0 ? 1 : mFooterHeight), true);
    }

    
    @Override
    public boolean autoLoadMore(int delayed, final int duration, final float dragRate, final boolean animationOnly) {
        if (mState == RefreshState.None && (isEnableRefreshOrLoadMore(mEnableLoadMore) && !mFooterNoMoreData)) {
            if (reboundAnimator != null) {
                reboundAnimator.cancel();
            }
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    reboundAnimator = ValueAnimator.ofInt(mSpinner, -(int) (mFooterHeight * dragRate));
                    reboundAnimator.setDuration(duration);
                    reboundAnimator.setInterpolator(new DecelerateInterpolator());
                    reboundAnimator.addUpdateListener(new AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mKernel.moveSpinner((int) animation.getAnimatedValue(), true);
                        }
                    });
                    reboundAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            final View thisView = SmartRefreshLayout.this;
                            mLastTouchX = thisView.getMeasuredWidth() / 2;
                            mKernel.setState(RefreshState.PullUpToLoad);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            reboundAnimator = null;
                            if (animationOnly) {
                                if (mState == RefreshState.ReleaseToLoad) {
                                    mKernel.setState(RefreshState.PullUpToLoad);
                                }
                            } else {
                                if (mState != RefreshState.ReleaseToLoad) {
                                    mKernel.setState(RefreshState.ReleaseToLoad);
                                }
                            }
                            if (mEnableAutoLoadMore) {
                                mEnableAutoLoadMore = false;
                                overSpinner();
                                mEnableAutoLoadMore = true;
                            } else {
                                overSpinner();
                            }
                        }
                    });
                    reboundAnimator.start();
                }
            };
            if (delayed > 0) {
                reboundAnimator = new ValueAnimator();
                postDelayed(runnable, delayed);
            } else {
                runnable.run();
            }
            return true;
        } else {
            return false;
        }
    }

    
    public static void setDefaultRefreshHeaderCreator(@NonNull DefaultRefreshHeaderCreator creator) {
        sHeaderCreator = creator;
    }

    
    public static void setDefaultRefreshFooterCreator(@NonNull DefaultRefreshFooterCreator creator) {
        sFooterCreator = creator;
    }

    
    public static void setDefaultRefreshInitializer(@NonNull DefaultRefreshInitializer initializer) {
        sRefreshInitializer = initializer;
    }

    

//    /**


//     */
//    @Override
//    public boolean isRefreshing() {
//        return mState == RefreshState.Refreshing;
//    }
//
//    /**


//     */
//    @Override
//    public boolean isLoading() {
//        return mState == RefreshState.Loading;
//    }
//    /**


//     * @return SmartRefreshLayout
//     */
//    @Override
//    @Deprecated
//    public SmartRefreshLayout resetNoMoreData() {
//        return setNoMoreData(false);
//    }

    //</editor-fold>

    //</editor-fold>

    
    public class RefreshKernelImpl implements RefreshKernel {

        @NonNull
        @Override
        public RefreshLayout getRefreshLayout() {
            return SmartRefreshLayout.this;
        }

        @NonNull
        @Override
        public RefreshContent getRefreshContent() {
            return mRefreshContent;
        }

        @Override
        public RefreshKernel setState(@NonNull RefreshState state) {
            switch (state) {
                case None:
                    resetStatus();
                    break;
                case PullDownToRefresh:
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                        notifyStateChanged(RefreshState.PullDownToRefresh);
                    } else {
                        setViceState(RefreshState.PullDownToRefresh);
                    }
                    break;
                case PullUpToLoad:
                    if (isEnableRefreshOrLoadMore(mEnableLoadMore) && !mState.isOpening && !mState.isFinishing && !(mFooterNoMoreData && mEnableFooterFollowWhenNoMoreData)) {
                        notifyStateChanged(RefreshState.PullUpToLoad);
                    } else {
                        setViceState(RefreshState.PullUpToLoad);
                    }
                    break;
                case PullDownCanceled:
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                        notifyStateChanged(RefreshState.PullDownCanceled);
                        resetStatus();
                    } else {
                        setViceState(RefreshState.PullDownCanceled);
                    }
                    break;
                case PullUpCanceled:
                    if (isEnableRefreshOrLoadMore(mEnableLoadMore) && !mState.isOpening && !(mFooterNoMoreData && mEnableFooterFollowWhenNoMoreData)) {
                        notifyStateChanged(RefreshState.PullUpCanceled);
                        resetStatus();
                    } else {
                        setViceState(RefreshState.PullUpCanceled);
                    }
                    break;
                case ReleaseToRefresh:
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                        notifyStateChanged(RefreshState.ReleaseToRefresh);
                    } else {
                        setViceState(RefreshState.ReleaseToRefresh);
                    }
                    break;
                case ReleaseToLoad:
                    if (isEnableRefreshOrLoadMore(mEnableLoadMore) && !mState.isOpening && !mState.isFinishing && !(mFooterNoMoreData && mEnableFooterFollowWhenNoMoreData)) {
                        notifyStateChanged(RefreshState.ReleaseToLoad);
                    } else {
                        setViceState(RefreshState.ReleaseToLoad);
                    }
                    break;
                case ReleaseToTwoLevel: {
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                        notifyStateChanged(RefreshState.ReleaseToTwoLevel);
                    } else {
                        setViceState(RefreshState.ReleaseToTwoLevel);
                    }
                    break;
                }
                case RefreshReleased: {
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableRefresh)) {
                        notifyStateChanged(RefreshState.RefreshReleased);
                    } else {
                        setViceState(RefreshState.RefreshReleased);
                    }
                    break;
                }
                case LoadReleased: {
                    if (!mState.isOpening && isEnableRefreshOrLoadMore(mEnableLoadMore)) {
                        notifyStateChanged(RefreshState.LoadReleased);
                    } else {
                        setViceState(RefreshState.LoadReleased);
                    }
                    break;
                }
                case Refreshing:
                    setStateRefreshing();
                    break;
                case Loading:
                    setStateLoading();
                    break;
                case RefreshFinish: {
                    if (mState == RefreshState.Refreshing) {
                        notifyStateChanged(RefreshState.RefreshFinish);
                    }
                    break;
                }
                case LoadFinish:{
                    if (mState == RefreshState.Loading) {
                        notifyStateChanged(RefreshState.LoadFinish);
                    }
                    break;
                }
                case TwoLevelReleased:
                    notifyStateChanged(RefreshState.TwoLevelReleased);
                    break;
                case TwoLevelFinish:
                    notifyStateChanged(RefreshState.TwoLevelFinish);
                    break;
                case TwoLevel:
                    notifyStateChanged(RefreshState.TwoLevel);
                    break;
            }
            return null;
        }

        @Override
        public RefreshKernel startTwoLevel(boolean open) {
            if (open) {
                AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mKernel.setState(RefreshState.TwoLevel);
                    }
                };
                final View thisView = SmartRefreshLayout.this;
                ValueAnimator animator = animSpinner(thisView.getMeasuredHeight());
                if (animator != null && animator == reboundAnimator) {
                    animator.setDuration(mFloorDuration);
                    animator.addListener(listener);
                } else {
                    listener.onAnimationEnd(null);
                }
            } else {
                if (animSpinner(0) == null) {
                    notifyStateChanged(RefreshState.None);
                }
            }
            return this;
        }

        @Override
        public RefreshKernel finishTwoLevel() {
            if (mState == RefreshState.TwoLevel) {
                mKernel.setState(RefreshState.TwoLevelFinish);
                if (mSpinner == 0) {
                    moveSpinner(0, false);
                    notifyStateChanged(RefreshState.None);
                } else {
                    animSpinner(0).setDuration(mFloorDuration);
                }
            }
            return this;
        }
        

        //</editor-fold>

        

        
        public RefreshKernel moveSpinner(int spinner, boolean isDragging) {
            if (mSpinner == spinner
                    && (mRefreshHeader == null || !mRefreshHeader.isSupportHorizontalDrag())
                    && (mRefreshFooter == null || !mRefreshFooter.isSupportHorizontalDrag())) {
                return this;
            }
            final View thisView = SmartRefreshLayout.this;
            final int oldSpinner = mSpinner;
            mSpinner = spinner;
            if (isDragging && mViceState.isDragging) {
                if (mSpinner > mHeaderHeight * mHeaderTriggerRate) {
                    if (mState != RefreshState.ReleaseToTwoLevel) {
                        mKernel.setState(RefreshState.ReleaseToRefresh);
                    }
                } else if (-mSpinner > mFooterHeight * mFooterTriggerRate && !mFooterNoMoreData) {
                    mKernel.setState(RefreshState.ReleaseToLoad);
                } else if (mSpinner < 0 && !mFooterNoMoreData) {
                    mKernel.setState(RefreshState.PullUpToLoad);
                } else if (mSpinner > 0) {
                    mKernel.setState(RefreshState.PullDownToRefresh);
                }
            }
            if (mRefreshContent != null) {
                Integer tSpinner = null;
                if (spinner >= 0 && mRefreshHeader != null) {
                    if (isEnableTranslationContent(mEnableHeaderTranslationContent, mRefreshHeader)) {
                        tSpinner = spinner;
                    } else if (oldSpinner < 0) {
                        tSpinner = 0;
                    }
                }
                if (spinner <= 0 && mRefreshFooter != null) {
                    if (isEnableTranslationContent(mEnableFooterTranslationContent, mRefreshFooter)) {
                        tSpinner = spinner;
                    } else if (oldSpinner > 0) {
                        tSpinner = 0;
                    }
                }
                if (tSpinner != null) {
                    mRefreshContent.moveSpinner(tSpinner, mHeaderTranslationViewId, mFooterTranslationViewId);
                    boolean header = mEnableClipHeaderWhenFixedBehind && mRefreshHeader != null && mRefreshHeader.getSpinnerStyle() == SpinnerStyle.FixedBehind;
                    header = header || mHeaderBackgroundColor != 0;
                    boolean footer = mEnableClipFooterWhenFixedBehind && mRefreshFooter != null && mRefreshFooter.getSpinnerStyle() == SpinnerStyle.FixedBehind;
                    footer = footer || mFooterBackgroundColor != 0;
                    if ((header && (tSpinner >= 0 || oldSpinner > 0)) || (footer && (tSpinner <= 0 || oldSpinner < 0))) {
                        thisView.invalidate();
                    }
                }
            }
            if ((spinner >= 0 || oldSpinner > 0) && mRefreshHeader != null) {

                final int offset = Math.max(spinner, 0);
                final int headerHeight = mHeaderHeight;
                final int maxDragHeight = (int) (mHeaderHeight * mHeaderMaxDragRate);
                final float percent = 1f * offset / (mHeaderHeight == 0 ? 1 : mHeaderHeight);

                if (isEnableRefreshOrLoadMore(mEnableRefresh) || (mState == RefreshState.RefreshFinish && !isDragging)) {
                    if (oldSpinner != mSpinner) {
                        if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Translate) {
                            mRefreshHeader.getView().setTranslationY(mSpinner);
                            if (mHeaderBackgroundColor != 0 && mPaint != null && !isEnableTranslationContent(mEnableHeaderTranslationContent,mRefreshHeader)) {
                                thisView.invalidate();
                            }
                        } else if (mRefreshHeader.getSpinnerStyle() == SpinnerStyle.Scale){
                            mRefreshHeader.getView().requestLayout();
                        }
                        mRefreshHeader.onMoving(isDragging, percent, offset, headerHeight, maxDragHeight);
                    }
                    if (isDragging && mRefreshHeader.isSupportHorizontalDrag()) {
                        final int offsetX = (int) mLastTouchX;
                        final int offsetMax = thisView.getWidth();
                        final float percentX = mLastTouchX / (offsetMax == 0 ? 1 : offsetMax);
                        mRefreshHeader.onHorizontalDrag(percentX, offsetX, offsetMax);
                    }
                }

                if (oldSpinner != mSpinner && mOnMultiPurposeListener != null && mRefreshHeader instanceof RefreshHeader) {
                    mOnMultiPurposeListener.onHeaderMoving((RefreshHeader) mRefreshHeader, isDragging, percent, offset, headerHeight, maxDragHeight);
                }

            }
            if ((spinner <= 0 || oldSpinner < 0) && mRefreshFooter != null) {

                final int offset = -Math.min(spinner, 0);
                final int footerHeight = mFooterHeight;
                final int maxDragHeight = (int) (mFooterHeight * mFooterMaxDragRate);
                final float percent = offset * 1f / (mFooterHeight == 0 ? 1 : mFooterHeight);

                if (isEnableRefreshOrLoadMore(mEnableLoadMore) || (mState == RefreshState.LoadFinish && !isDragging)) {
                    if (oldSpinner != mSpinner) {
                        if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.Translate) {
                            mRefreshFooter.getView().setTranslationY(mSpinner);
                            if (mFooterBackgroundColor != 0 && mPaint != null && !isEnableTranslationContent(mEnableFooterTranslationContent, mRefreshFooter)) {
                                thisView.invalidate();
                            }
                        } else if (mRefreshFooter.getSpinnerStyle() == SpinnerStyle.Scale){
                            mRefreshFooter.getView().requestLayout();
                        }
                        mRefreshFooter.onMoving(isDragging, percent, offset, footerHeight, maxDragHeight);
                    }
                    if (isDragging && mRefreshFooter.isSupportHorizontalDrag()) {
                        final int offsetX = (int) mLastTouchX;
                        final int offsetMax = thisView.getWidth();
                        final float percentX = mLastTouchX / (offsetMax == 0 ? 1 : offsetMax);
                        mRefreshFooter.onHorizontalDrag(percentX, offsetX, offsetMax);
                    }
                }

                if (oldSpinner != mSpinner && mOnMultiPurposeListener != null && mRefreshFooter instanceof RefreshFooter) {
                    mOnMultiPurposeListener.onFooterMoving((RefreshFooter)mRefreshFooter, isDragging, percent, offset, footerHeight, maxDragHeight);
                }
            }
            return this;
        }

        public ValueAnimator animSpinner(int endSpinner) {
            return SmartRefreshLayout.this.animSpinner(endSpinner, 0, mReboundInterpolator, mReboundDuration);
        }

        //</editor-fold>

        

        @Override
        public RefreshKernel requestDrawBackgroundFor(@NonNull RefreshInternal internal, int backgroundColor) {
            if (mPaint == null && backgroundColor != 0) {
                mPaint = new Paint();
            }
            if (internal.equals(mRefreshHeader)) {
                mHeaderBackgroundColor = backgroundColor;
            } else if (internal.equals(mRefreshFooter)) {
                mFooterBackgroundColor = backgroundColor;
            }
            return this;
        }

        @Override
        public RefreshKernel requestNeedTouchEventFor(@NonNull RefreshInternal internal, boolean request) {
            if (internal.equals(mRefreshHeader)) {
                mHeaderNeedTouchEventWhenRefreshing = request;
            } else if (internal.equals(mRefreshFooter)) {
                mFooterNeedTouchEventWhenLoading = request;
            }
            return this;
        }

        @Override
        public RefreshKernel requestDefaultTranslationContentFor(@NonNull RefreshInternal internal, boolean translation) {
            if (internal.equals(mRefreshHeader)) {
                if (!mManualHeaderTranslationContent) {
                    mManualHeaderTranslationContent = true;
                    mEnableHeaderTranslationContent = translation;
                }
            } else if (internal.equals(mRefreshFooter)) {
                if (!mManualFooterTranslationContent) {
                    mManualFooterTranslationContent = true;
                    mEnableFooterTranslationContent = translation;
                }
            }
            return this;
        }
        @Override
        public RefreshKernel requestRemeasureHeightFor(@NonNull RefreshInternal internal) {
            if (internal.equals(mRefreshHeader)) {
                if (mHeaderHeightStatus.notified) {
                    mHeaderHeightStatus = mHeaderHeightStatus.unNotify();
                }
            } else if (internal.equals(mRefreshFooter)) {
                if (mFooterHeightStatus.notified) {
                    mFooterHeightStatus = mFooterHeightStatus.unNotify();
                }
            }
            return this;
        }
        @Override
        public RefreshKernel requestFloorDuration(int duration) {
            mFloorDuration = duration;
            return this;
        }
        //</editor-fold>
    }
    //</editor-fold>

    

    @Override
    public boolean post(@NonNull Runnable action) {
        if (mHandler == null) {
            mListDelayedRunnable = mListDelayedRunnable == null ? new ArrayList<DelayedRunnable>() : mListDelayedRunnable;
            mListDelayedRunnable.add(new DelayedRunnable(action,0));
            return false;
        }
        return mHandler.post(new DelayedRunnable(action,0));
    }

    @Override
    public boolean postDelayed(@NonNull Runnable action, long delayMillis) {
        if (delayMillis == 0) {
            new DelayedRunnable(action,0).run();
            return true;
        }
        if (mHandler == null) {
            mListDelayedRunnable = mListDelayedRunnable == null ? new ArrayList<DelayedRunnable>() : mListDelayedRunnable;
            mListDelayedRunnable.add(new DelayedRunnable(action, delayMillis));
            return false;
        }
        return mHandler.postDelayed(new DelayedRunnable(action, 0), delayMillis);
    }

    //</editor-fold>
}
