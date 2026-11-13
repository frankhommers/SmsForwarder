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

package com.xuexiang.xui.utils;

import android.annotation.TargetApi;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.SnackbarContentLayout;
import com.xuexiang.xui.R;
import com.xuexiang.xui.logs.UILog;

import java.lang.ref.WeakReference;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class SnackbarUtils {

    private static final String TAG = "SnackbarUtils";

    
    private static int sColorInfo = 0xFF299EE3;
    private static int sColorConfirm = 0xFF4CB04E;
    private static int sColorWarning = 0xFFFEC005;
    private static int sColorDanger = 0xFFF44336;

    
    private static WeakReference<Snackbar> mSnackbarWeakRef;

    private SnackbarUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    private SnackbarUtils(@Nullable WeakReference<Snackbar> snackbarWeakReference) {
        mSnackbarWeakRef = snackbarWeakReference;
    }

    
    public static void setColorInfo(int colorInfo) {
        SnackbarUtils.sColorInfo = colorInfo;
    }

    
    public static void setColorConfirm(int colorConfirm) {
        SnackbarUtils.sColorConfirm = colorConfirm;
    }

    
    public static void setColorWarning(int colorWarning) {
        SnackbarUtils.sColorWarning = colorWarning;
    }

    
    public static void setColorDanger(int colorDanger) {
        SnackbarUtils.sColorDanger = colorDanger;
    }

    
    public Snackbar getSnackbar() {
        if (mSnackbarWeakRef != null && mSnackbarWeakRef.get() != null) {
            return mSnackbarWeakRef.get();
        } else {
            return null;
        }
    }

    
    public static SnackbarUtils Short(View view, String message) {
        /*
        <view xmlns:android="http://schemas.android.com/apk/res/android"
          class="android.support.design.widget.Snackbar$SnackbarLayout"
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_gravity="bottom"
          android:theme="@style/ThemeOverlay.AppCompat.Dark"
          style="@style/Widget.Design.Snackbar" />
        <style name="Widget.Design.Snackbar" parent="android:Widget">
            <item name="android:minWidth">@dimen/design_snackbar_min_width</item>
            <item name="android:maxWidth">@dimen/design_snackbar_max_width</item>
            <item name="android:background">@drawable/design_snackbar_background</item>
            <item name="android:paddingLeft">@dimen/design_snackbar_padding_horizontal</item>
            <item name="android:paddingRight">@dimen/design_snackbar_padding_horizontal</item>
            <item name="elevation">@dimen/design_snackbar_elevation</item>
            <item name="maxActionInlineWidth">@dimen/design_snackbar_action_inline_max_width</item>
        </style>
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
            android:shape="rectangle">
            <corners android:radius="@dimen/design_snackbar_background_corner_radius"/>
            <solid android:color="@color/design_snackbar_background_color"/>
        </shape>
        <color name="design_snackbar_background_color">#323232</color>
        */
        return new SnackbarUtils(new WeakReference<>(Snackbar.make(view, message, Snackbar.LENGTH_SHORT))).backColor(0XFF323232);
    }

    
    public static SnackbarUtils Long(View view, String message) {
        return new SnackbarUtils(new WeakReference<>(Snackbar.make(view, message, Snackbar.LENGTH_LONG))).backColor(0XFF323232);
    }

    
    public static SnackbarUtils Indefinite(View view, String message) {
        return new SnackbarUtils(new WeakReference<>(Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE))).backColor(0XFF323232);
    }

    
    public static SnackbarUtils Custom(View view, String message, int duration) {
        return new SnackbarUtils(new WeakReference<>(Snackbar.make(view, message, Snackbar.LENGTH_SHORT).setDuration(duration))).backColor(0XFF323232);
    }

    
    public SnackbarUtils info() {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(sColorInfo);
        }
        return this;
    }

    
    public SnackbarUtils confirm() {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(sColorConfirm);
        }
        return this;
    }

    
    public SnackbarUtils warning() {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(sColorWarning);
        }
        return this;
    }

    
    public SnackbarUtils danger() {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(sColorDanger);
        }
        return this;
    }

    
    public SnackbarUtils backColor(@ColorInt int backgroundColor) {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(backgroundColor);
        }
        return this;
    }

    
    public SnackbarUtils messageColor(@ColorInt int messageColor) {
        if (getSnackbar() != null) {
            ((TextView) getSnackbar().getView().findViewById(R.id.snackbar_text)).setTextColor(messageColor);
        }
        return this;
    }

    
    public SnackbarUtils actionColor(@ColorInt int actionTextColor) {
        if (getSnackbar() != null) {
            ((Button) getSnackbar().getView().findViewById(R.id.snackbar_action)).setTextColor(actionTextColor);
        }
        return this;
    }

    
    public SnackbarUtils colors(@ColorInt int backgroundColor, @ColorInt int messageColor, @ColorInt int actionTextColor) {
        if (getSnackbar() != null) {
            getSnackbar().getView().setBackgroundColor(backgroundColor);
            ((TextView) getSnackbar().getView().findViewById(R.id.snackbar_text)).setTextColor(messageColor);
            ((Button) getSnackbar().getView().findViewById(R.id.snackbar_action)).setTextColor(actionTextColor);
        }
        return this;
    }

    
    public SnackbarUtils alpha(float alpha) {
        if (getSnackbar() != null) {
            alpha = alpha >= 1.0f ? 1.0f : (Math.max(alpha, 0.0f));
            getSnackbar().getView().setAlpha(alpha);
        }
        return this;
    }

    
    public SnackbarUtils gravityFrameLayout(int gravity) {
        if (getSnackbar() != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getSnackbar().getView().getLayoutParams().width, getSnackbar().getView().getLayoutParams().height);
            params.gravity = gravity;
            getSnackbar().getView().setLayoutParams(params);
        }
        return this;
    }

    
    public SnackbarUtils gravityCoordinatorLayout(int gravity) {
        if (getSnackbar() != null) {
            CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(getSnackbar().getView().getLayoutParams().width, getSnackbar().getView().getLayoutParams().height);
            params.gravity = gravity;
            getSnackbar().getView().setLayoutParams(params);
        }
        return this;
    }

    
    public SnackbarUtils setAction(@StringRes int resId, View.OnClickListener listener) {
        if (getSnackbar() != null) {
            return setAction(getSnackbar().getView().getResources().getText(resId), listener);
        } else {
            return this;
        }
    }

    
    public SnackbarUtils setAction(CharSequence text, View.OnClickListener listener) {
        if (getSnackbar() != null) {
            getSnackbar().setAction(text, listener);
        }
        return this;
    }

    
    public SnackbarUtils setCallback(Snackbar.Callback setCallback) {
        if (getSnackbar() != null) {
            getSnackbar().setCallback(setCallback);
        }
        return this;
    }

    
    public SnackbarUtils leftAndRightDrawable(@Nullable @DrawableRes Integer leftDrawable, @Nullable @DrawableRes Integer rightDrawable) {
        if (getSnackbar() != null) {
            Drawable drawableLeft = null;
            Drawable drawableRight = null;
            if (leftDrawable != null) {
                try {
                    drawableLeft = getSnackbar().getView().getResources().getDrawable(leftDrawable);
                } catch (Exception e) {
                }
            }
            if (rightDrawable != null) {
                try {
                    drawableRight = getSnackbar().getView().getResources().getDrawable(rightDrawable);
                } catch (Exception e) {
                }
            }
            return leftAndRightDrawable(drawableLeft, drawableRight);
        } else {
            return this;
        }
    }

    
    public SnackbarUtils leftAndRightDrawable(@Nullable Drawable leftDrawable, @Nullable Drawable rightDrawable) {
        if (getSnackbar() != null) {
            TextView message = getSnackbar().getView().findViewById(R.id.snackbar_text);
            LinearLayout.LayoutParams paramsMessage = (LinearLayout.LayoutParams) message.getLayoutParams();
            paramsMessage = new LinearLayout.LayoutParams(paramsMessage.width, paramsMessage.height, 0.0f);
            message.setLayoutParams(paramsMessage);
            message.setCompoundDrawablePadding(message.getPaddingLeft());
            int textSize = (int) message.getTextSize();
            UILog.dTag(TAG, "textSize:" + textSize);
            if (leftDrawable != null) {
                leftDrawable.setBounds(0, 0, textSize, textSize);
            }
            if (rightDrawable != null) {
                rightDrawable.setBounds(0, 0, textSize, textSize);
            }
            message.setCompoundDrawables(leftDrawable, null, rightDrawable, null);
            LinearLayout.LayoutParams paramsSpace = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            ((Snackbar.SnackbarLayout) getSnackbar().getView()).addView(new Space(getSnackbar().getView().getContext()), 1, paramsSpace);
        }
        return this;
    }

    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public SnackbarUtils messageCenter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (getSnackbar() != null) {
                TextView message = getSnackbar().getView().findViewById(R.id.snackbar_text);
                
                message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                message.setGravity(Gravity.CENTER);
            }
        }
        return this;
    }

    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public SnackbarUtils messageRight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (getSnackbar() != null) {
                TextView message = getSnackbar().getView().findViewById(R.id.snackbar_text);
                
                message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                message.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            }
        }
        return this;
    }

    
    public SnackbarUtils addView(int layoutId, int index) {
        if (getSnackbar() != null) {
            
            View addView = LayoutInflater.from(getSnackbar().getView().getContext()).inflate(layoutId, null);
            return addView(addView, index);
        } else {
            return this;
        }
    }

    
    public SnackbarUtils addView(View addView, int index) {
        if (getSnackbar() != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            
            params.gravity = Gravity.CENTER_VERTICAL;
            addView.setLayoutParams(params);
            
            final SnackbarContentLayout contentLayout = (SnackbarContentLayout) ((Snackbar.SnackbarLayout) getSnackbar().getView())
                    .getChildAt(0);
            contentLayout.addView(addView, index);
        }
        return this;
    }

    
    public SnackbarUtils margins(int margin) {
        if (getSnackbar() != null) {
            return margins(margin, margin, margin, margin);
        } else {
            return this;
        }
    }

    
    public SnackbarUtils margins(int left, int top, int right, int bottom) {
        if (getSnackbar() != null) {
            ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
            ((ViewGroup.MarginLayoutParams) params).setMargins(left, top, right, bottom);
            getSnackbar().getView().setLayoutParams(params);
        }
        return this;
    }

    
    /*
    public SnackbarUtils radius(){
        Drawable background = mSnackbarWeakRef.get().getView().getBackground();
        if(background instanceof GradientDrawable){
            Log.e("Jet","radius():GradientDrawable");
        }
        if(background instanceof ColorDrawable){
            Log.e("Jet","radius():ColorDrawable");
        }
        if(background instanceof StateListDrawable){
            Log.e("Jet","radius():StateListDrawable");
        }
        Log.e("Jet","radius()background:"+background.getClass().getSimpleName());
        return new SnackbarUtils(mSnackbar);
    }
    */

    
    private GradientDrawable getRadiusDrawable(Drawable backgroundOri) {
        GradientDrawable background = null;
        if (backgroundOri instanceof GradientDrawable) {
            background = (GradientDrawable) backgroundOri;
        } else if (backgroundOri instanceof ColorDrawable) {
            int backgroundColor = ((ColorDrawable) backgroundOri).getColor();
            background = new GradientDrawable();
            background.setColor(backgroundColor);
        } else {
        }
        return background;
    }

    
    public SnackbarUtils radius(float radius) {
        if (getSnackbar() != null) {
            
            GradientDrawable background = getRadiusDrawable(getSnackbar().getView().getBackground());
            if (background != null) {
                radius = radius <= 0 ? 12 : radius;
                background.setCornerRadius(radius);
                getSnackbar().getView().setBackgroundDrawable(background);
            }
        }
        return this;
    }

    
    public SnackbarUtils radius(int radius, int strokeWidth, @ColorInt int strokeColor) {
        if (getSnackbar() != null) {
            
            GradientDrawable background = getRadiusDrawable(getSnackbar().getView().getBackground());
            if (background != null) {
                radius = radius <= 0 ? 12 : radius;
                strokeWidth = strokeWidth <= 0 ? 1 : (strokeWidth >= getSnackbar().getView().findViewById(R.id.snackbar_text).getPaddingTop() ? 2 : strokeWidth);
                background.setCornerRadius(radius);
                background.setStroke(strokeWidth, strokeColor);
                getSnackbar().getView().setBackgroundDrawable(background);
            }
        }
        return this;
    }

    
    private int calculateSnackBarHeight() {
        /*
        <TextView
                android:id="@+id/snackbar_text"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:paddingTop="@dimen/design_snackbar_padding_vertical"
                android:paddingBottom="@dimen/design_snackbar_padding_vertical"
                android:paddingLeft="@dimen/design_snackbar_padding_horizontal"
                android:paddingRight="@dimen/design_snackbar_padding_horizontal"
                android:textAppearance="@style/TextAppearance.Design.Snackbar.Message"
                android:maxLines="@integer/design_snackbar_text_max_lines"
                android:layout_gravity="center_vertical|left|start"
                android:ellipsize="end"
                android:textAlignment="viewStart"/>
        */
        
        int SnackbarHeight = DensityUtils.dp2px(getSnackbar().getView().getContext(), 28) + DensityUtils.sp2px(getSnackbar().getView().getContext(), 14);
        UILog.dTag(TAG, "MessageView height:" + getSnackbar().getView().findViewById(R.id.snackbar_text).getHeight());
        return SnackbarHeight;
    }

    
    public SnackbarUtils above(View targetView, int contentViewTop, int marginLeft, int marginRight) {
        if (getSnackbar() != null) {
            marginLeft = Math.max(marginLeft, 0);
            marginRight = Math.max(marginRight, 0);
            int[] locations = new int[2];
            targetView.getLocationOnScreen(locations);
            UILog.dTag(TAG, "Distance from left edge:" + locations[0] + "==Distance from top edge:" + locations[1]);
            int snackbarHeight = calculateSnackBarHeight();
            UILog.dTag(TAG, "Snackbar height:" + snackbarHeight);
            
            if (locations[1] >= contentViewTop + snackbarHeight) {
                gravityFrameLayout(Gravity.BOTTOM);
                ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, getSnackbar().getView().getResources().getDisplayMetrics().heightPixels - locations[1]);
                getSnackbar().getView().setLayoutParams(params);
            }
        }
        return this;
    }

    //CoordinatorLayout
    public SnackbarUtils aboveCoordinatorLayout(View targetView, int contentViewTop, int marginLeft, int marginRight) {
        if (getSnackbar() != null) {
            marginLeft = Math.max(marginLeft, 0);
            marginRight = Math.max(marginRight, 0);
            int[] locations = new int[2];
            targetView.getLocationOnScreen(locations);
            UILog.dTag(TAG, "Distance from left edge:" + locations[0] + "==Distance from top edge:" + locations[1]);
            int snackbarHeight = calculateSnackBarHeight();
            UILog.dTag(TAG, "Snackbar height:" + snackbarHeight);
            
            if (locations[1] >= contentViewTop + snackbarHeight) {
                gravityCoordinatorLayout(Gravity.BOTTOM);
                ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, getSnackbar().getView().getResources().getDisplayMetrics().heightPixels - locations[1]);
                getSnackbar().getView().setLayoutParams(params);
            }
        }
        return this;
    }

    
    public SnackbarUtils bellow(View targetView, int contentViewTop, int marginLeft, int marginRight) {
        if (getSnackbar() != null) {
            marginLeft = Math.max(marginLeft, 0);
            marginRight = Math.max(marginRight, 0);
            int[] locations = new int[2];
            targetView.getLocationOnScreen(locations);
            int snackbarHeight = calculateSnackBarHeight();
            int screenHeight = Utils.getScreenHeight(getSnackbar().getView().getContext());
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                
                
                if (locations[1] + targetView.getHeight() >= contentViewTop && locations[1] + targetView.getHeight() + snackbarHeight + 2 <= screenHeight) {
                    gravityFrameLayout(Gravity.BOTTOM);
                    ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                    ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, screenHeight - (locations[1] + targetView.getHeight() + snackbarHeight + 2));
                    getSnackbar().getView().setLayoutParams(params);
                }
            } else {
                if (locations[1] + targetView.getHeight() >= contentViewTop && locations[1] + targetView.getHeight() + snackbarHeight <= screenHeight) {
                    gravityFrameLayout(Gravity.BOTTOM);
                    ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                    ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, screenHeight - (locations[1] + targetView.getHeight() + snackbarHeight));
                    getSnackbar().getView().setLayoutParams(params);
                }
            }
        }
        return this;
    }

    public SnackbarUtils bellowCoordinatorLayout(View targetView, int contentViewTop, int marginLeft, int marginRight) {
        if (getSnackbar() != null) {
            marginLeft = Math.max(marginLeft, 0);
            marginRight = Math.max(marginRight, 0);
            int[] locations = new int[2];
            targetView.getLocationOnScreen(locations);
            int snackbarHeight = calculateSnackBarHeight();
            int screenHeight = Utils.getScreenHeight(getSnackbar().getView().getContext());
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                
                
                if (locations[1] + targetView.getHeight() >= contentViewTop && locations[1] + targetView.getHeight() + snackbarHeight + 2 <= screenHeight) {
                    gravityCoordinatorLayout(Gravity.BOTTOM);
                    ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                    ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, screenHeight - (locations[1] + targetView.getHeight() + snackbarHeight + 2));
                    getSnackbar().getView().setLayoutParams(params);
                }
            } else {
                if (locations[1] + targetView.getHeight() >= contentViewTop && locations[1] + targetView.getHeight() + snackbarHeight <= screenHeight) {
                    gravityCoordinatorLayout(Gravity.BOTTOM);
                    ViewGroup.LayoutParams params = getSnackbar().getView().getLayoutParams();
                    ((ViewGroup.MarginLayoutParams) params).setMargins(marginLeft, 0, marginRight, screenHeight - (locations[1] + targetView.getHeight() + snackbarHeight));
                    getSnackbar().getView().setLayoutParams(params);
                }
            }
        }
        return this;
    }

    
    public void show() {
        if (getSnackbar() != null) {
            getSnackbar().show();
        } else {
            UILog.dTag(TAG, "Already recycled");
        }
    }
}
