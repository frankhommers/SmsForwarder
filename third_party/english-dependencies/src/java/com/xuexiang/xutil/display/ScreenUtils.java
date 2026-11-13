// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
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
 */

package com.xuexiang.xutil.display;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.common.logger.Logger;
import com.xuexiang.xutil.resource.ResUtils;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class ScreenUtils {

    private ScreenUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    public static int getScreenWidth() {
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        if (wm == null) {
            return ResUtils.getResources().getDisplayMetrics().widthPixels;
        }
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            wm.getDefaultDisplay().getRealSize(point);
        } else {
            wm.getDefaultDisplay().getSize(point);
        }
        return point.x;
    }

    
    public static int getScreenHeight() {
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        if (wm == null) {
            return ResUtils.getResources().getDisplayMetrics().heightPixels;
        }
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            wm.getDefaultDisplay().getRealSize(point);
        } else {
            wm.getDefaultDisplay().getSize(point);
        }
        return point.y;
    }

    
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static int getDisplayMetrics(boolean isReal) {
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        DisplayMetrics dm = new DisplayMetrics();
        if (wm != null) {
            if (isReal) {
                wm.getDefaultDisplay().getRealMetrics(dm);
            } else {
                wm.getDefaultDisplay().getMetrics(dm);
            }
        }
        return dm.heightPixels;
    }

    
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static DisplayMetrics getRealDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        }
        return dm;
    }

    
    private static Point getDisplaySize() {
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        Point size = new Point();
        if (wm != null) {
            wm.getDefaultDisplay().getSize(size);
        }
        return size;
    }

    
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static Point getDisplayRealSize() {
        WindowManager wm = XUtil.getSystemService(Context.WINDOW_SERVICE, WindowManager.class);
        Point size = new Point();
        if (wm != null) {
            wm.getDefaultDisplay().getRealSize(size);
        }
        return size;
    }

    
    public static double getDevicePhysicalSize(Context context) {
        int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        float xdpi = context.getResources().getDisplayMetrics().xdpi;
        float ydpi = context.getResources().getDisplayMetrics().ydpi;
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        double size = Math.sqrt(Math.pow(width / xdpi, 2) + Math.pow(height / ydpi, 2));
        Logger.d("Calculated physical size:" + size + " , [width:" + width + ", height:" + height + ", xdpi:" + xdpi + ", ydpi:" + ydpi + ", densityDpi:" + densityDpi + "]");
        return size;
    }

    
    public static double getDensityDpi(Context context, float inch) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        double densityDpi = Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2)) / inch;
        Logger.d("Calculated screen density:" + densityDpi + " , [width:" + width + ", height:" + height + "]");
        return densityDpi;
    }


    
    public static float getScreenDensity() {
        return ResUtils.getResources().getDisplayMetrics().density;
    }

    
    public static int getScreenDensityDpi() {
        return ResUtils.getResources().getDisplayMetrics().densityDpi;
    }

    
    public static void setFullScreen(@NonNull final Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    /**
     * Set non full screen.
     *
     * @param activity The activity.
     */
    public static void setNonFullScreen(@NonNull final Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    /**
     * Toggle full screen.
     *
     * @param activity The activity.
     */
    public static void toggleFullScreen(@NonNull final Activity activity) {
        int fullScreenFlag = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        Window window = activity.getWindow();
        if ((window.getAttributes().flags & fullScreenFlag) == fullScreenFlag) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    }

    /**
     * Return whether screen is full.
     *
     * @param activity The activity.
     * @return {@code true}: yes<br>{@code false}: no
     */
    public static boolean isFullScreen(@NonNull final Activity activity) {
        int fullScreenFlag = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        return (activity.getWindow().getAttributes().flags & fullScreenFlag) == fullScreenFlag;
    }

    
    public static void setLandscape(@NonNull final Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    
    public static void setPortrait(@NonNull final Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    
    public static boolean isLandscape() {
        return ResUtils.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    
    public static boolean isPortrait() {
        return ResUtils.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    
    public static int getScreenRotation(@NonNull final Activity activity) {
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    
    public static Bitmap screenShot(@NonNull final Activity activity) {
        return screenShot(activity, false);
    }

    
    public static Bitmap screenShot(@NonNull final Activity activity, boolean isDeleteStatusBar) {
        View decorView = activity.getWindow().getDecorView();
        decorView.setDrawingCacheEnabled(true);
        decorView.buildDrawingCache();
        Bitmap bmp = decorView.getDrawingCache();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        Bitmap ret;
        if (isDeleteStatusBar) {
            Resources resources = activity.getResources();
            int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
            int statusBarHeight = resources.getDimensionPixelSize(resourceId);
            ret = Bitmap.createBitmap(
                    bmp,
                    0,
                    statusBarHeight,
                    dm.widthPixels,
                    dm.heightPixels - statusBarHeight
            );
        } else {
            ret = Bitmap.createBitmap(bmp, 0, 0, dm.widthPixels, dm.heightPixels);
        }
        decorView.destroyDrawingCache();
        return ret;
    }

    
    public static boolean isScreenLock() {
        KeyguardManager km =
                (KeyguardManager) XUtil.getContext().getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.inKeyguardRestrictedInputMode();
    }

    
    public static void setSleepDuration(final int duration) {
        Settings.System.putInt(
                XUtil.getContext().getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT,
                duration
        );
    }

    
    public static int getSleepDuration() {
        try {
            return Settings.System.getInt(
                    XUtil.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT
            );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    
    public static boolean isTablet() {
        return (ResUtils.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    

    
    public static void adaptScreen(final Activity activity,
                                   final int designDp) {
        if (ScreenUtils.isPortrait()) { 
            ScreenUtils.adaptScreen4VerticalSlide(activity, designDp);
        } else {
            ScreenUtils.adaptScreen4HorizontalSlide(activity, designDp);
        }
    }


    /**
     * Adapt the screen for vertical slide.
     *
     * @param designWidthInDp The size of design diagram's width, in dp,
     *                        e.g. the design diagram width is 720px, in XHDPI device,
     *                        the designWidthInDp = 720 / 2.
     */
    public static void adaptScreen4VerticalSlide(final Activity activity,
                                                 final int designWidthInDp) {
        adaptScreen(activity, designWidthInDp, true);
    }

    /**
     * Adapt the screen for horizontal slide.
     *
     * @param designHeightInDp The size of design diagram's height, in dp,
     *                         e.g. the design diagram height is 1080px, in XXHDPI device,
     *                         the designHeightInDp = 1080 / 3.
     */
    public static void adaptScreen4HorizontalSlide(final Activity activity,
                                                   final int designHeightInDp) {
        adaptScreen(activity, designHeightInDp, false);
    }

    /**
     * Cancel adapt the screen.
     *
     * @param activity The activity.
     */
    public static void cancelAdaptScreen(final Activity activity) {
        final DisplayMetrics appDm = XUtil.getContext().getResources().getDisplayMetrics();
        final DisplayMetrics activityDm = activity.getResources().getDisplayMetrics();
        activityDm.density = appDm.density;
        activityDm.scaledDensity = appDm.scaledDensity;
        activityDm.densityDpi = appDm.densityDpi;
    }

    /**
     * Reference from: https://mp.weixin.qq.com/s/d9QCoBP6kV9VSWvVldVVwA
     */
    private static void adaptScreen(final Activity activity,
                                    final float sizeInDp,
                                    final boolean isVerticalSlide) {
        final DisplayMetrics appDm = ResUtils.getResources().getDisplayMetrics();
        final DisplayMetrics activityDm = activity.getResources().getDisplayMetrics();
        if (isVerticalSlide) {
            activityDm.density = activityDm.widthPixels / sizeInDp;
        } else {
            activityDm.density = activityDm.heightPixels / sizeInDp;
        }
        activityDm.scaledDensity = activityDm.density * (appDm.scaledDensity / appDm.density);
        activityDm.densityDpi = (int) (160 * activityDm.density);
    }
}
