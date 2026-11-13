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
package com.xuexiang.xutil.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.RequiresPermission;
import androidx.core.content.FileProvider;

import com.xuexiang.xutil.common.ShellUtils;
import com.xuexiang.xutil.common.ShellUtils.CommandResult;
import com.xuexiang.xutil.common.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static android.Manifest.permission.INSTALL_PACKAGES;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class PackageUtils {

    /**
     * App installation location settings values
     */
    private static final int APP_INSTALL_AUTO = 0;
    private static final int APP_INSTALL_INTERNAL = 1;
    private static final int APP_INSTALL_EXTERNAL = 2;

    /**
     * Don't let anyone instantiate this class.
     */
    private PackageUtils() {
        throw new Error("Do not need instantiate!");
    }

    
    public static final int REQUEST_CODE_INSTALL_APP = 999;

    
    public static boolean install(Context context, File apkFile) throws IOException {
        return install(context, apkFile.getCanonicalPath());
    }

    
    public static boolean install(Context context, String filePath) {
        if (PackageUtils.isSystemApplication(context)
                || ShellUtils.checkRootPermission()) {
            return installAppSilent(context, filePath);
        }
        return installNormal(context, filePath);
    }

    
    @RequiresPermission(INSTALL_PACKAGES)
    public static boolean installAppSilent(Context context, String filePath) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return installAppSilentBelow24(context, filePath);
        } else {
            return installAppSilentAbove24(context.getPackageName(), filePath);
        }
    }

    
    @RequiresPermission(INSTALL_PACKAGES)
    private static boolean installAppSilentBelow24(Context context, String filePath) {
        File file = getFileByPath(filePath);
        if (!isFileExists(file)) {
            return false;
        }

        String pmParams = " -r " + getInstallLocationParams();

        StringBuilder command = new StringBuilder()
                .append("LD_LIBRARY_PATH=/vendor/lib:/system/lib pm install ")
                .append(pmParams).append(" ")
                .append(filePath.replace(" ", "\\ "));
        CommandResult commandResult = ShellUtils.execCommand(
                command.toString(), !isSystemApplication(context), true);
        return commandResult.successMsg != null
                && (commandResult.successMsg.contains("Success") || commandResult.successMsg
                .contains("success"));
    }

    //===============================//
    
    @RequiresPermission(INSTALL_PACKAGES)
    private static boolean installAppSilentAbove24(String packageName, String filePath) {
        File file = getFileByPath(filePath);
        if (!isFileExists(file)) {
            return false;
        }
        boolean isRoot = isDeviceRooted();
        String command = "pm install -i " + packageName + " --user 0 " + filePath;
        CommandResult commandResult = ShellUtils.execCommand(command, isRoot);
        return (commandResult.successMsg != null
                && commandResult.successMsg.toLowerCase().contains("success"));
    }

    /**
     * install package normal by system intent
     *
     * @param context
     * @param filePath file path of package
     * @return whether apk exist
     */
    private static boolean installNormal(Context context, String filePath) {
        File file = getFileByPath(filePath);
        return isFileExists(file) && installNormal(context, file);
    }

    
    private static boolean installNormal(Context context, File appFile) {
        try {
            Intent intent = getInstallAppIntent(context, appFile);
            if (context.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                if (context instanceof Activity) {
                    ((Activity) context).startActivityForResult(intent, REQUEST_CODE_INSTALL_APP);
                } else {
                    context.startActivity(intent);
                }
                return true;
            }
        } catch (Exception e) {
            Logger.e("APK installation using system intent failed!", e);
        }
        return false;
    }

    
    public static Intent getInstallAppIntent(Context context, File appFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                Uri fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".updateFileProvider", appFile);
                intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(appFile), "application/vnd.android.package-archive");
            }
            return intent;
        } catch (Exception e) {
            Logger.e("Failed to get installation intent!", e);
        }
        return null;
    }

    
    private static File getFileByPath(final String filePath) {
        return isSpace(filePath) ? null : new File(filePath);
    }

    
    private static boolean isSpace(final String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    
    private static boolean isFileExists(final File file) {
        return file != null && file.exists();
    }

    
    private static boolean isDeviceRooted() {
        String su = "su";
        String[] locations = {"/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
                "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/", "/data/local/"};
        for (String location : locations) {
            if (new File(location + su).exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * uninstall according conditions
     * <ul>
     * <li>if system application or rooted, see
     * {@link #uninstallSilent(Context, String)}</li>
     * <li>else see {@link #uninstallNormal(Context, String)}</li>
     * </ul>
     *
     * @param context
     * @param packageName package name of app
     * @return
     */
    public static int uninstall(Context context, String packageName) {
        if (PackageUtils.isSystemApplication(context) || ShellUtils.checkRootPermission()) {
            return uninstallSilent(context, packageName);
        }
        return uninstallNormal(context, packageName) ? DELETE_SUCCEEDED : DELETE_FAILED_INVALID_PACKAGE;
    }

    /**
     * uninstall package normal by system intent
     *
     * @param context
     * @param packageName package name of app
     * @return whether package name is empty
     */
    public static boolean uninstallNormal(Context context, String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }

        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse(new StringBuilder(32).append("package:").append(packageName).toString()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
        return true;
    }

    /**
     * uninstall package and clear data of app silent by root
     *
     * @param context
     * @param packageName package name of app
     * @return
     * @see #uninstallSilent(Context, String, boolean)
     */
    public static int uninstallSilent(Context context, String packageName) {
        return uninstallSilent(context, packageName, true);
    }

    /**
     * uninstall package silent by root
     * <ul>
     * <strong>Attentions:</strong>
     * <li>Don't call this on the ui thread, it may costs some times.</li>
     * <li>You should add <strong>android.permission.DELETE_PACKAGES</strong> in
     * manifest, so no need to request root permission, if you are system app.</li>
     * </ul>
     *
     * @param context     file path of package
     * @param packageName package name of app
     * @param isKeepData  whether keep the data and cache directories around after
     *                    package removal
     * @return <ul>
     * <li>{@link #DELETE_SUCCEEDED} means uninstall success</li>
     * <li>{@link #DELETE_FAILED_INTERNAL_ERROR} means internal error</li>
     * <li>{@link #DELETE_FAILED_INVALID_PACKAGE} means package name
     * error</li>
     * <li>{@link #DELETE_FAILED_PERMISSION_DENIED} means permission
     * denied</li>
     */
    public static int uninstallSilent(Context context, String packageName, boolean isKeepData) {
        if (packageName == null || packageName.length() == 0) {
            return DELETE_FAILED_INVALID_PACKAGE;
        }

        /**
         * if context is system app, don't need root permission, but should add
         * <uses-permission android:name="android.permission.DELETE_PACKAGES" />
         * in mainfest
         **/
        StringBuilder command = new StringBuilder().append("LD_LIBRARY_PATH=/vendor/lib:/system/lib pm uninstall").append(isKeepData ? " -k " : " ").append(packageName.replace(" ", "\\ "));
        CommandResult commandResult = ShellUtils.execCommand(command.toString(), !isSystemApplication(context), true);
        if (commandResult.successMsg != null && (commandResult.successMsg.contains("Success") || commandResult.successMsg.contains("success"))) {
            return DELETE_SUCCEEDED;
        }
        Logger.e(new StringBuilder().append("uninstallSilent successMsg:").append(commandResult.successMsg).append(", ErrorMsg:").append(commandResult.errorMsg).toString());
        if (commandResult.errorMsg == null) {
            return DELETE_FAILED_INTERNAL_ERROR;
        }
        if (commandResult.errorMsg.contains("Permission denied")) {
            return DELETE_FAILED_PERMISSION_DENIED;
        }
        return DELETE_FAILED_INTERNAL_ERROR;
    }

    /**
     * whether context is system application
     *
     * @param context
     * @return
     */
    public static boolean isSystemApplication(Context context) {
        return context != null && isSystemApplication(context, context.getPackageName());

    }

    /**
     * whether packageName is system application
     *
     * @param context
     * @param packageName
     * @return
     */
    public static boolean isSystemApplication(Context context, String packageName) {
        return context != null && isSystemApplication(context.getPackageManager(), packageName);

    }

    /**
     * whether packageName is system application
     *
     * @param packageManager
     * @param packageName
     * @return <ul>
     * <li>if packageManager is null, return false</li>
     * <li>if package name is null or is empty, return false</li>
     * <li>if package name not exit, return false</li>
     * <li>if package name exit, but not system app, return false</li>
     * <li>else return true</li>
     * </ul>
     */
    public static boolean isSystemApplication(PackageManager packageManager, String packageName) {
        if (packageManager == null || packageName == null || packageName.length() == 0) {
            return false;
        }

        try {
            ApplicationInfo app = packageManager.getApplicationInfo(packageName, 0);
            return (app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) > 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * get app version code
     *
     * @param context
     * @return
     */
    public static int getAppVersionCode(Context context) {
        if (context != null) {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                PackageInfo pi;
                try {
                    pi = pm.getPackageInfo(context.getPackageName(), 0);
                    if (pi != null) {
                        return pi.versionCode;
                    }
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return -1;
    }

    /**
     * get system install location<br/>
     * can be set by System Menu Setting->Storage->Prefered install location
     *
     * @return
     */
    private static int getInstallLocation() {
        CommandResult commandResult = ShellUtils.execCommand("LD_LIBRARY_PATH=/vendor/lib:/system/lib pm get-install-location", false, true);
        if (commandResult.result == 0 && commandResult.successMsg != null && commandResult.successMsg.length() > 0) {
            try {
                int location = Integer.parseInt(commandResult.successMsg.substring(0, 1));
                switch (location) {
                    case APP_INSTALL_INTERNAL:
                        return APP_INSTALL_INTERNAL;
                    case APP_INSTALL_EXTERNAL:
                        return APP_INSTALL_EXTERNAL;
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
                Logger.e("pm get-install-location error");
            }
        }
        return APP_INSTALL_AUTO;
    }

    /**
     * get params for pm install location
     *
     * @return
     */
    private static String getInstallLocationParams() {
        int location = getInstallLocation();
        switch (location) {
            case APP_INSTALL_INTERNAL:
                return "-f";
            case APP_INSTALL_EXTERNAL:
                return "-s";
        }
        return "";
    }

    /**
     * start InstalledAppDetails Activity
     *
     * @param context
     * @param packageName
     * @since android.provider.Settings#ACTION_APPLICATION_DETAILS_SETTINGS
     * requires API level 9 (current min is 8):
     */
    @SuppressLint("InlinedApi")
    public static void startInstalledAppDetails(Context context, String packageName) {
        Intent intent = new Intent();
        int sdkVersion = Build.VERSION.SDK_INT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
        } else {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
            intent.putExtra((sdkVersion == Build.VERSION_CODES.FROYO ? "pkg" : "com.android.settings.ApplicationPkgName"), packageName);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    
    public static boolean checkAPP(Context context, String packageName) {
        if (packageName == null || "".equals(packageName)) {
            return false;
        }
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    
    public static void installApk(Context context, String filePath) {
        File apkFile = new File(filePath);
        if (!apkFile.exists()) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse("file://" + apkFile.toString()), "application/vnd.android.package-archive");
        context.startActivity(i);
    }

    
    public static void openApp(Context context) {
        context.startActivity(IntentUtils.getLaunchAppIntent(context.getPackageName()));
    }

    
    public static void openApp(Context context, ApplicationInfo appInfo) {
        openApp(context, appInfo.packageName);
    }

    
    public static boolean openApp(Context context, String packageName) {
        Intent intent = getAppOpenIntentByPackageName(context, packageName, true);
        if (intent != null) {
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    
    public static boolean switchApp(Context context, String packageName) {
        Context pkgContext = getPackageContext(context, packageName);
        Intent intent = getAppOpenIntentByPackageName(context, packageName, false);
        if (pkgContext != null && intent != null) {
            pkgContext.startActivity(intent);
            return true;
        }
        return false;
    }

    private static Intent getAppOpenIntentByPackageName(Context context, String packageName, boolean isReopen) {
        
        String mainAct = null;
        
        PackageManager pkgMag = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (!isReopen) { 
            intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        List<ResolveInfo> list = pkgMag.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES);
        for (int i = 0; i < list.size(); i++) {
            ResolveInfo info = list.get(i);
            if (info.activityInfo.packageName.equals(packageName)) {
                mainAct = info.activityInfo.name;
                break;
            }
        }
        if (TextUtils.isEmpty(mainAct)) {
            return null;
        }
        intent.setComponent(new ComponentName(packageName, mainAct));
        return intent;
    }

    private static Context getPackageContext(Context context, String packageName) {
        Context pkgContext = null;
        if (context.getPackageName().equals(packageName)) {
            pkgContext = context;
        } else {
            
            try {
                pkgContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY
                        | Context.CONTEXT_INCLUDE_CODE);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return pkgContext;
    }

    /**
     * Uninstall return code<br/>
     * uninstall success.
     */
    public static final int DELETE_SUCCEEDED = 1;

    /**
     * Uninstall return code<br/>
     * uninstall fail if the system failed to delete the package for an
     * unspecified reason.
     */
    public static final int DELETE_FAILED_INTERNAL_ERROR = -1;

    /**
     * Uninstall return code<br/>
     * uninstall fail if the system failed to delete the package because it is
     * the active DevicePolicy manager.
     */
    public static final int DELETE_FAILED_DEVICE_POLICY_MANAGER = -2;

    /**
     * Uninstall return code<br/>
     * uninstall fail if pcakge name is invalid
     */
    public static final int DELETE_FAILED_INVALID_PACKAGE = -3;

    /**
     * Uninstall return code<br/>
     * uninstall fail if permission denied
     */
    public static final int DELETE_FAILED_PERMISSION_DENIED = -4;
}
