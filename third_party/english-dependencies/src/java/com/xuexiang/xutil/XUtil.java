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

package com.xuexiang.xutil;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.RequiresPermission;

import com.xuexiang.xutil.app.ActivityLifecycleHelper;
import com.xuexiang.xutil.app.AppUtils;
import com.xuexiang.xutil.app.ProcessUtils;
import com.xuexiang.xutil.app.ServiceUtils;
import com.xuexiang.xutil.common.logger.Logger;

import static android.Manifest.permission.KILL_BACKGROUND_PROCESSES;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class XUtil {
    
    private static Application sContext;
    
    private static ActivityLifecycleHelper sLifecycleHelper = new ActivityLifecycleHelper();
    
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    

    
    private static boolean sAutoInit = true;

    
    public static void disableAutoInit() {
        XUtil.sAutoInit = false;
    }

    
    public static boolean isAutoInit() {
        return sAutoInit;
    }

    
    public static void init(Application application) {
        sContext = application;
        application.registerActivityLifecycleCallbacks(sLifecycleHelper);
    }

    
    public static void init(Context context) {
        sContext = (Application) context.getApplicationContext();
    }

    
    public static void registerLifecycleCallbacks(Application application, ActivityLifecycleHelper lifecycleHelper) {
        sLifecycleHelper = lifecycleHelper;
        application.registerActivityLifecycleCallbacks(sLifecycleHelper);
    }

    

    
    public static Context getContext() {
        testInitialize();
        return sContext;
    }

    
    public static ContentResolver getContentResolver() {
        return getContext().getContentResolver();
    }

    
    public static Resources getResources() {
        return getContext().getResources();
    }

    
    public static AssetManager getAssetManager() {
        return getContext().getAssets();
    }

    
    public static PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    
    public static <T> T getSystemService(String name, Class<T> clazz) {
        return getSystemService(getContext(), name, clazz);
    }

    
    public static <T> T getSystemService(Context context, String name, Class<T> clazz) {
        if (!TextUtils.isEmpty(name) && clazz != null && context != null) {
            Object obj = context.getSystemService(name);
            return clazz.isInstance(obj) ? (T) obj : null;
        } else {
            return null;
        }
    }

    
    public static ActivityLifecycleHelper getActivityLifecycleHelper() {
        return sLifecycleHelper;
    }

    private static void testInitialize() {
        if (sContext == null) {
            throw new ExceptionInInitializerError("Call XUtil.init() in Application before using this library!");
        }
    }

    

    
    public static void debug(boolean isDebug) {
        if (isDebug) {
            debug(Logger.DEFAULT_LOG_TAG);
        } else {
            debug("");
        }
    }

    
    public static void debug(String tag) {
        Logger.debug(tag);
    }

    

    
    public static Handler getMainHandler() {
        return MAIN_HANDLER;
    }

    
    public static boolean runOnUiThread(Runnable runnable) {
        return getMainHandler().post(runnable);
    }

    

    
    @RequiresPermission(KILL_BACKGROUND_PROCESSES)
    public static void exitApp() {
        if (sLifecycleHelper != null) {
            sLifecycleHelper.exit();
        }
        ServiceUtils.stopAllRunningService(getContext());
        ProcessUtils.killBackgroundProcesses(XUtil.getContext().getPackageName());
        System.exit(0);
    }

    
    public static void rebootApp() {
        AppUtils.rebootApp();
    }


}
