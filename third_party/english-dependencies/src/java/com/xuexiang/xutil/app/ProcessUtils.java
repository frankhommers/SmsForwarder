// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Copyright (C) 2020 xuexiangjys(xuexiangjys@163.com)
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

package com.xuexiang.xutil.app;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.common.logger.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.Manifest.permission.KILL_BACKGROUND_PROCESSES;
import static android.Manifest.permission.PACKAGE_USAGE_STATS;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class ProcessUtils {

    private ProcessUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    @RequiresApi(api = Build.VERSION_CODES.M)
    @RequiresPermission(PACKAGE_USAGE_STATS)
    public static String getForegroundProcessName() {
        ActivityManager manager =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return null;
        }
        List<ActivityManager.RunningAppProcessInfo> pInfo = manager.getRunningAppProcesses();
        if (pInfo != null && pInfo.size() > 0) {
            for (ActivityManager.RunningAppProcessInfo aInfo : pInfo) {
                if (aInfo.importance
                        == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return aInfo.processName;
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
            PackageManager packageManager = XUtil.getContext().getPackageManager();
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            List<ResolveInfo> list =
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            Logger.iTag("ProcessUtils", list.toString());
            if (list.size() <= 0) {
                Logger.iTag("ProcessUtils",
                        "getForegroundProcessName() called" + ": No usage-access permission option");
                return null;
            }
            try {
                ApplicationInfo info =
                        packageManager.getApplicationInfo(XUtil.getContext().getPackageName(), 0);
                AppOpsManager aom =
                        (AppOpsManager) XUtil.getContext().getSystemService(Context.APP_OPS_SERVICE);
                if (aom != null) {
                    if (aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            info.uid,
                            info.packageName) != AppOpsManager.MODE_ALLOWED) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        XUtil.getContext().startActivity(intent);
                    }
                    if (aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            info.uid,
                            info.packageName) != AppOpsManager.MODE_ALLOWED) {
                        Logger.iTag("ProcessUtils", "Usage-access permission is not enabled");
                        return null;
                    }
                }
                UsageStatsManager usageStatsManager = (UsageStatsManager) XUtil.getContext()
                        .getSystemService(Context.USAGE_STATS_SERVICE);
                List<UsageStats> usageStatsList = null;
                if (usageStatsManager != null) {
                    long endTime = System.currentTimeMillis();
                    long beginTime = endTime - 86400000 * 7;
                    usageStatsList = usageStatsManager
                            .queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                                    beginTime, endTime);
                }
                if (usageStatsList == null || usageStatsList.isEmpty()) {
                    return null;
                }
                UsageStats recentStats = null;
                for (UsageStats usageStats : usageStatsList) {
                    if (recentStats == null
                            || usageStats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                        recentStats = usageStats;
                    }
                }
                return recentStats == null ? null : recentStats.getPackageName();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    
    public static Set<String> getAllBackgroundProcesses() {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return Collections.emptySet();
        }
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        Set<String> set = new HashSet<>();
        if (info != null) {
            for (ActivityManager.RunningAppProcessInfo aInfo : info) {
                Collections.addAll(set, aInfo.pkgList);
            }
        }
        return set;
    }

    
    public static boolean isProcessRunning(@NonNull String processName) {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        if (info != null) {
            for (ActivityManager.RunningAppProcessInfo aInfo : info) {
                if (processName.equals(aInfo.processName)) {
                    return  true;
                }
            }
        }
        return false;
    }

    
    @RequiresPermission(KILL_BACKGROUND_PROCESSES)
    public static Set<String> killAllBackgroundProcesses() {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return Collections.emptySet();
        }
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        Set<String> set = new HashSet<>();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            for (String pkg : aInfo.pkgList) {
                am.killBackgroundProcesses(pkg);
                set.add(pkg);
            }
        }
        info = am.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            for (String pkg : aInfo.pkgList) {
                set.remove(pkg);
            }
        }
        return set;
    }

    
    @RequiresPermission(KILL_BACKGROUND_PROCESSES)
    public static boolean killBackgroundProcesses(@NonNull final String packageName) {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        if (info == null || info.size() == 0) {
            return true;
        }
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (Arrays.asList(aInfo.pkgList).contains(packageName)) {
                Logger.d("[killBackgroundProcesses]：" + aInfo.processName);
                am.killBackgroundProcesses(packageName);
            }
        }
        info = am.getRunningAppProcesses();
        if (info == null || info.size() == 0) {
            return true;
        }
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (Arrays.asList(aInfo.pkgList).contains(packageName)) {
                return false;
            }
        }
        return true;
    }


    
    @RequiresPermission(Manifest.permission.KILL_BACKGROUND_PROCESSES)
    public static int gc() {
        long beforeGCDeviceUsableMemory = getDeviceUsableMemory();
        int count = 0; 
        ActivityManager am = (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        
        List<ActivityManager.RunningServiceInfo> serviceList = am.getRunningServices(100);
        if (serviceList != null) {
            for (ActivityManager.RunningServiceInfo service : serviceList) {
                if (service.pid == Process.myPid()) {
                    continue;
                }
                try {
                    Process.killProcess(service.pid);
                    count++;
                } catch (Exception e) {
                    e.getStackTrace();
                }
            }
        }

        
        List<ActivityManager.RunningAppProcessInfo> processList = am.getRunningAppProcesses();
        if (processList != null) {
            for (ActivityManager.RunningAppProcessInfo process : processList) {
                
                
                if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    
                    String[] pkgList = process.pkgList;
                    for (String pkgName : pkgList) {
                        Logger.d("======Stopping package: " + pkgName);
                        try {
                            am.killBackgroundProcesses(pkgName);
                            count++;
                        } catch (Exception e) { 
                            e.getStackTrace();
                        }
                    }
                }
            }
        }
        Logger.d("Freed " + (getDeviceUsableMemory() - beforeGCDeviceUsableMemory) + " MB of memory");
        return count;
    }

    
    public static int getDeviceUsableMemory() {
        ActivityManager am = (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        
        return (int) (mi.availMem / (1024 * 1024));
    }

    
    public static boolean isMainProcess() {
        return XUtil.getContext().getPackageName().equals(getCurrentProcessName());
    }

    
    public static String getCurrentProcessName() {
        ActivityManager am = (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return null;
        }
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        if (info == null || info.size() == 0) {
            return null;
        }
        int pid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (aInfo.pid == pid) {
                if (aInfo.processName != null) {
                    return aInfo.processName;
                }
            }
        }
        return "";
    }
}
