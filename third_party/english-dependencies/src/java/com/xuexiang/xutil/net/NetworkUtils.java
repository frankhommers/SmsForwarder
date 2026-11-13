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

package com.xuexiang.xutil.net;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.common.ShellUtils;
import com.xuexiang.xutil.common.logger.Logger;
import com.xuexiang.xutil.file.CloseUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.INTERNET;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class NetworkUtils {

    /**
     * Don't let anyone instantiate this class.
     */
    private NetworkUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }


    
    public static void openWirelessSettings() {
        XUtil.getContext().startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    private static NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager manager = getConnectivityManager();
        if (manager == null) {
            return null;
        }
        return manager.getActiveNetworkInfo();
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static boolean isNetworkAvailable() {
        boolean netState = false;
        ConnectivityManager manager = getConnectivityManager();
        if (manager != null) {
            NetworkInfo[] info = manager.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == State.CONNECTED) {
                        netState = true;
                        break;
                    }
                }
            }
        }
        return netState;
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static boolean isHaveInternet() {
        try {
            ConnectivityManager connectivity = getConnectivityManager();
            if (connectivity != null) {
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    if (info.getState() == State.CONNECTED) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    
    @SuppressLint("MissingPermission")
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public static boolean isGpsEnabled() {
        LocationManager lm = (LocationManager) XUtil.getContext().getSystemService(Context.LOCATION_SERVICE);
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static boolean isMobile() {
        NetworkInfo activeNetInfo = getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.isAvailable() && activeNetInfo.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static boolean isWifi() {
        NetworkInfo activeNetInfo = getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.isAvailable() && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    
    public static boolean is3G() {
        NetworkInfo activeNetInfo = getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.isAvailable() && activeNetInfo.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    
    public static boolean is4G() {
        NetworkInfo activeNetInfo = getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.isConnectedOrConnecting() && activeNetInfo.getType() == TelephonyManager.NETWORK_TYPE_LTE;
    }

    
    public static boolean isWifiEnabled() {
        @SuppressLint("WifiManagerLeak")
        WifiManager manager = getWifiManager();
        return manager != null && manager.isWifiEnabled();
    }

    
    @SuppressLint("MissingPermission")
    public static void setWifiEnabled(final boolean enabled) {
        @SuppressLint("WifiManagerLeak")
        WifiManager manager = getWifiManager();
        if (manager == null) {
            return;
        }
        if (enabled) {
            if (!manager.isWifiEnabled()) {
                manager.setWifiEnabled(true);
            }
        } else {
            if (manager.isWifiEnabled()) {
                manager.setWifiEnabled(false);
            }
        }
    }

    
    public static boolean isAvailableByPing() {
        return isAvailableByPing(null);
    }

    
    public static boolean isAvailableByPing(String ip) {
        if (ip == null || ip.length() <= 0) {
            
            ip = "223.5.5.5";
        }
        ShellUtils.CommandResult result = ShellUtils.execCommand(String.format("ping -c 1 %s", ip), false);
        boolean ret = result.result == 0;
        if (result.errorMsg != null) {
            Logger.dTag("NetworkUtils", "isAvailableByPing() called" + result.errorMsg);
        }
        if (result.successMsg != null) {
            Logger.dTag("NetworkUtils", "isAvailableByPing() called" + result.successMsg);
        }
        return ret;
    }

    
    public static boolean getMobileDataEnabled() {
        try {
            TelephonyManager tm =
                    (TelephonyManager) XUtil.getContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return false;
            }
            @SuppressLint("PrivateApi")
            Method getMobileDataEnabledMethod = tm.getClass().getDeclaredMethod("getDataEnabled");
            if (null != getMobileDataEnabledMethod) {
                return (boolean) getMobileDataEnabledMethod.invoke(tm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    
    public static WifiManager getWifiManager() {
        return (WifiManager) XUtil.getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    
    public static ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) XUtil.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    

    
    @RequiresPermission(ACCESS_WIFI_STATE)
    public static InetAddress getLocalInetAddress() {
        WifiManager wifiManager = getWifiManager();
        if (wifiManager != null && isWifi()) {
            return getWifiInetAddress(wifiManager);
        } else {
            return getInetAddressByEnumerate();
        }
    }

    
    @Nullable
    private static InetAddress getInetAddressByEnumerate() {
        Enumeration<NetworkInterface> networkInterfaces = null;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        if (networkInterfaces != null) {
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        return address;
                    }
                }
            }
        }
        return null;
    }

    
    @Nullable
    private static InetAddress getWifiInetAddress(WifiManager wifiManager) {
        int ipAsInt = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAsInt == 0) {
            return null;
        } else {
            return intToInet(ipAsInt);
        }
    }

    private static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }

    
    public static int ipToInt(String address) {
        String[] addressArray = address.split("\\.");
        int num = 0;
        for (int i = 0; i < addressArray.length; i++) {
            int power = 3 - i;
            num += ((Integer.parseInt(addressArray[i]) % 256 * Math.pow(256, power)));
        }
        return num;
    }

    
    public static String ipToString(int address) {
        if (address == 0) {
            return null;
        }
        return ipToString(address, ".");
    }

    private static String ipToString(int address, String sep) {
        if (address > 0) {
            StringBuffer buf = new StringBuffer();
            buf.
                    append(byteOfInt(address, 0)).append(sep).
                    append(byteOfInt(address, 1)).append(sep).
                    append(byteOfInt(address, 2)).append(sep).
                    append(byteOfInt(address, 3));
            return buf.toString();
        } else {
            return null;
        }
    }

    private static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte) (value >> shift);
    }

    /**
     * Return the ip address.
     * <p>Must hold {@code <uses-permission android:name="android.permission.INTERNET" />}</p>
     *
     * @param useIPv4 True to use ipv4, false otherwise.
     * @return the ip address
     */
    @RequiresPermission(INTERNET)
    public static String getIPAddress(final boolean useIPv4) {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            LinkedList<InetAddress> adds = new LinkedList<>();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                // To prevent phone of xiaomi return "10.0.2.15"
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    adds.addFirst(addresses.nextElement());
                }
            }
            for (InetAddress add : adds) {
                if (!add.isLoopbackAddress()) {
                    String hostAddress = add.getHostAddress();
                    boolean isIPv4 = hostAddress.indexOf(':') < 0;
                    if (useIPv4) {
                        if (isIPv4) {
                            return hostAddress;
                        }
                    } else {
                        if (!isIPv4) {
                            int index = hostAddress.indexOf('%');
                            return index < 0
                                    ? hostAddress.toUpperCase()
                                    : hostAddress.substring(0, index).toUpperCase();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Return the ip address of broadcast.
     *
     * @return the ip address of broadcast
     */
    public static String getBroadcastIpAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            LinkedList<InetAddress> adds = new LinkedList<>();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                List<InterfaceAddress> ias = ni.getInterfaceAddresses();
                for (int i = 0; i < ias.size(); i++) {
                    InterfaceAddress ia = ias.get(i);
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null) {
                        return broadcast.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "";
    }

    
    @RequiresPermission(INTERNET)
    public static String getDomainAddress(final String domain) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(domain);
            return inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "";
        }
    }

    

    
    public enum NetState {
        
        NET_NO,
        
        NET_2G,
        
        NET_3G,
        
        NET_4G,
        
        NET_5G,
        /**
         * wifi
         */
        NET_WIFI,
        
        NET_ETHERNET,
        
        NET_UNKNOWN
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static NetState getNetStateType() {
        if (isEthernet()) {
            return NetState.NET_ETHERNET;
        }
        NetworkInfo info = getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                return NetState.NET_WIFI;
            } else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                switch (info.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_GSM:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return NetState.NET_2G;

                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        return NetState.NET_3G;

                    case TelephonyManager.NETWORK_TYPE_IWLAN:
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        return NetState.NET_4G;

                    case TelephonyManager.NETWORK_TYPE_NR:
                        return NetState.NET_5G;
                    default:
                        String subtypeName = info.getSubtypeName();
                        if (subtypeName.equalsIgnoreCase("TD-SCDMA")
                                || subtypeName.equalsIgnoreCase("WCDMA")
                                || subtypeName.equalsIgnoreCase("CDMA2000")) {
                            return NetState.NET_3G;
                        } else {
                            return NetState.NET_UNKNOWN;
                        }
                }
            } else {
                return NetState.NET_UNKNOWN;
            }
        }
        return NetState.NET_NO;
    }

    
    @RequiresPermission(ACCESS_NETWORK_STATE)
    private static boolean isEthernet() {
        final ConnectivityManager cm = getConnectivityManager();
        if (cm == null) {
            return false;
        }
        final NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET);
        if (info == null) {
            return false;
        }
        NetworkInfo.State state = info.getState();
        if (null == state) {
            return false;
        }
        return state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING;
    }

    
    public static String getNetworkOperatorName(Context context) {
        String networkOperatorName = "Unknown";
        TelephonyManager telManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String operator = telManager.getSimOperator();
        if (operator != null) {
            switch (operator) {
                case "46000":
                case "46002":
                case "46007":
                    networkOperatorName = "China Mobile";
                    break;
                case "46001":
                case "46006":
                    networkOperatorName = "China Unicom";
                    break;
                case "46003":
                case "46005":
                case "46011":
                    networkOperatorName = "China Telecom";
                    break;
                default:
                    break;
            }
        }
        return networkOperatorName;
    }

    
    public static String getNetworkOperatorName() {
        TelephonyManager tm =
                (TelephonyManager) XUtil.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        return tm != null ? tm.getNetworkOperatorName() : null;
    }

    
    public static Map<String, String> getUrlParams(String url) {
        Map<String, String> params = null;
        try {
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1) {
                params = new HashMap<>();
                String query = urlParts[1];
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8");
                    }
                    params.put(key, value);
                }
            }
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        return params;
    }

    
    public static String parseUrl(String url) {
        if (!"".equals(url) && url.contains("?")) {
            url = url.substring(0, url.indexOf('?'));
        }
        return url;
    }


    
    public static boolean isUrlValid(String url) {
        String expr = "^(http|https|ftp)\\://(((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])|([a-zA-Z0-9_\\-\\.])+\\.(com|cn|net|org|edu|int|mil|gov|arpa|biz|aero|name|coop|info|pro|museum|uk|me))((:[a-zA-Z0-9]*)?/?([a-zA-Z0-9\\-\\._\\?\\,\\'/\\\\\\+&%\\$#\\=~])*)$";
        return url.matches(expr);
    }

    
    public static boolean isIP(String ip) {
        Pattern pattern = Pattern.compile("\\b((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\b");
        Matcher matcher = pattern.matcher(ip);
        return matcher.matches();
    }

    
    @RequiresPermission(INTERNET)
    public static void downLoadFileByUrl(String urlStr, String fileName, String savePath) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setConnectTimeout(3 * 1000);
        
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        
        InputStream inputStream = conn.getInputStream();
        
        byte[] getData = readInputStream(inputStream);
        
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        File file = new File(saveDir + File.separator + fileName);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(getData);
        CloseUtils.closeIO(fos, inputStream);
    }

    
    private static byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

}
