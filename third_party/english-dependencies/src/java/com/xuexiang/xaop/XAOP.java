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

package com.xuexiang.xaop;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.xuexiang.xaop.cache.XCache;
import com.xuexiang.xaop.cache.XDiskCache;
import com.xuexiang.xaop.cache.XMemoryCache;
import com.xuexiang.xaop.cache.converter.IDiskConverter;
import com.xuexiang.xaop.cache.converter.SerializableDiskConverter;
import com.xuexiang.xaop.cache.key.DefaultCacheKeyCreator;
import com.xuexiang.xaop.cache.key.ICacheKeyCreator;
import com.xuexiang.xaop.checker.IThrowableHandler;
import com.xuexiang.xaop.checker.Interceptor;
import com.xuexiang.xaop.logger.ILogger;
import com.xuexiang.xaop.logger.XLogger;
import com.xuexiang.xaop.util.PermissionUtils.OnPermissionDeniedListener;
import com.xuexiang.xaop.util.Strings;

/**
 * XAOP
 *
 * @author xuexiang
 * @since 2019-08-14 00:06
 */
public final class XAOP {

    private static Context sContext;

    
    private static OnPermissionDeniedListener sOnPermissionDeniedListener;
    
    private static IDiskConverter sIDiskConverter = new SerializableDiskConverter();
    
    private static ICacheKeyCreator sICacheKeyCreator = new DefaultCacheKeyCreator();
    
    private static Interceptor sInterceptor;

    
    private static IThrowableHandler sIThrowableHandler;

    
    public static void init(Application application) {
        sContext = application.getApplicationContext();
    }

    
    public static Context getContext() {
        testInitialize();
        return sContext;
    }

    private static void testInitialize() {
        if (sContext == null) {
            throw new ExceptionInInitializerError("Call XAOP.init() in Application before using this library!");
        }
    }

    

    
    public static void setOnPermissionDeniedListener(@NonNull OnPermissionDeniedListener listener) {
        XAOP.sOnPermissionDeniedListener = listener;
    }

    public static OnPermissionDeniedListener getOnPermissionDeniedListener() {
        return sOnPermissionDeniedListener;
    }

    

    
    public static void setIDiskConverter(@NonNull IDiskConverter sIDiskConverter) {
        XAOP.sIDiskConverter = sIDiskConverter;
    }

    public static IDiskConverter getIDiskConverter() {
        return sIDiskConverter;
    }

    

    /**
     * @param sICacheKeyCreator
     */
    public static void setICacheKeyCreator(@NonNull ICacheKeyCreator sICacheKeyCreator) {
        XAOP.sICacheKeyCreator = sICacheKeyCreator;
    }

    public static ICacheKeyCreator getICacheKeyCreator() {
        return sICacheKeyCreator;
    }

    

    
    public static void setInterceptor(@NonNull Interceptor sInterceptor) {
        XAOP.sInterceptor = sInterceptor;
    }

    public static Interceptor getInterceptor() {
        return sInterceptor;
    }

    

    
    public static void setIThrowableHandler(@NonNull IThrowableHandler sIThrowableHandler) {
        XAOP.sIThrowableHandler = sIThrowableHandler;
    }

    public static IThrowableHandler getIThrowableHandler() {
        return sIThrowableHandler;
    }

    

    
    public static void debug(boolean isDebug) {
        XLogger.debug(isDebug);
    }

    
    public static void debug(String tag) {
        XLogger.debug(tag);
    }

    
    public static void setPriority(int priority) {
        XLogger.setPriority(priority);
    }

    
    public static void setISerializer(@NonNull Strings.ISerializer sISerializer) {
        XLogger.setISerializer(sISerializer);
    }

    
    public static void setLogger(@NonNull ILogger logger) {
        XLogger.setLogger(logger);
    }

    

    
    public static void initMemoryCache(int memoryMaxSize) {
        XMemoryCache.getInstance().init(memoryMaxSize);
    }

    
    public static void initDiskCache(XCache.Builder builder) {
        XDiskCache.getInstance().init(builder.isDiskCache(true));
    }


}
