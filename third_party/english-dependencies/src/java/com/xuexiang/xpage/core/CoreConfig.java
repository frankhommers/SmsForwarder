// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.xuexiang.xpage.core;

import android.content.Context;

import com.xuexiang.xpage.model.PageInfo;
import com.xuexiang.xpage.utils.GsonUtils;

import java.util.ArrayList;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class CoreConfig {
    
    private static boolean isOpenAtlas = false;
    private static ClassLoader mBundleClassLoader = null;

    public static boolean isOpenAtlas() {
        return isOpenAtlas;
    }

    public static void setIsOpenAtlas(boolean isOpenAtlasFlag) {
        isOpenAtlas = isOpenAtlasFlag;
    }

    public static ClassLoader getBundleClassLoader() {
        return mBundleClassLoader;
    }

    public static void setBundleClassLoader(ClassLoader classLoader) {
        mBundleClassLoader = classLoader;
    }

    private static Context sContext;

    
    public static void init(Context context) {
        sContext = context.getApplicationContext();
        CorePageManager.getInstance().init(sContext);
    }

    
    public static void init(Context context, String pageJson) {
        sContext = context.getApplicationContext();
        CorePageManager.getInstance().init(sContext, pageJson);
    }

    
    public static void init(Context context, List<PageInfo> pageInfoList) {
        CoreConfig.init(context, GsonUtils.toJson(pageInfoList));
    }

    
    public static void init(Context context, PageInfo pageInfo) {
        List<PageInfo> list = new ArrayList<PageInfo>();
        list.add(pageInfo);
        CoreConfig.init(context, list);
    }

    public static void readConfig(String pageJson) {
        CorePageManager.getInstance().readConfig(pageJson);
    }


    public static Context getContext() {
        testInitialize();
        return sContext;
    }

    private static void testInitialize() {
        if (sContext == null) {
            throw new ExceptionInInitializerError("Call PageConfig.init() in Application before using this library!");
        }
    }
}
