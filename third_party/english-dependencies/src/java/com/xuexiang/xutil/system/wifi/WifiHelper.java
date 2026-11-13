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

package com.xuexiang.xutil.system.wifi;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;

import com.xuexiang.xutil.common.StringUtils;
import com.xuexiang.xutil.common.logger.Logger;
import com.xuexiang.xutil.net.NetworkUtils;
import com.xuexiang.xutil.system.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class WifiHelper {
    
    private final static int DEFAULT_CONNECT_TIME_OUT = 25;

    private static volatile WifiHelper sInstance;
    
    private final WifiManager mWifiManager;
    
    private WifiInfo mWifiInfo;
    
    private int mTimeOut;
    
    private OnWifiConnectStateListener mOnWifiConnectStateListener;

    
    private List<ScanResult> mWifiList;
    
    private List<WifiConfiguration> mWifiConfiguration;
    
    private WifiLock mWifiLock;

    
    public WifiHelper setOnConnectWifiStateListener(OnWifiConnectStateListener listener) {
        mOnWifiConnectStateListener = listener;
        return this;
    }

    
    public WifiHelper setConnectTimeOut(int timeOut) {
        mTimeOut = timeOut;
        return this;
    }

    
    private WifiHelper() {
        
        mWifiManager = NetworkUtils.getWifiManager();
        
        mWifiInfo = mWifiManager.getConnectionInfo();
        mTimeOut = DEFAULT_CONNECT_TIME_OUT;
    }

    
    public static WifiHelper get() {
        if (sInstance == null) {
            synchronized (WifiHelper.class) {
                if (sInstance == null) {
                    sInstance = new WifiHelper();
                }
            }
        }
        return sInstance;
    }


    
    public void connectWifi(String wifiSsid, String wifiPassword) {
        ThreadPoolManager.get().addTask(new WifiConnectRunnable(wifiSsid, wifiPassword));
    }

    /* Upstream explanatory comments omitted; see pinned source receipt.
     * @author xx
 */
    private class WifiConnectRunnable implements Runnable {
        
        private final String mWifiSsid;
        
        private final String mWifiPassword;

        public WifiConnectRunnable(String wifiSsid, String wifiPassword) {
            mWifiSsid = wifiSsid;
            mWifiPassword = wifiPassword;
        }

        @Override
        public void run() {
            if (!checkState()) {
                openWifi();
                checkWifiEnable();

                try {
                    Thread.sleep(2000); 
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    onCheckWifiStateFinished(false);
                    return;
                }
            }
            
            if (checkSSIDState(mWifiSsid)) {
                checkConnectWifi(mWifiSsid);
            } else {
                mWifiConfiguration = startScan();
                WifiConfiguration wcg = isExistSSID(mWifiConfiguration, mWifiSsid);
                if (wcg != null) {
                    connectConfigurationWifi(wcg);
                } else {
                    int encryptionType = 1;
                    if (!StringUtils.isEmpty(mWifiPassword)) {
                        encryptionType = 3;
                    }
                    addNetwork(createWifiInfo(mWifiSsid, mWifiPassword, encryptionType));
                }
                checkConnectWifi(mWifiSsid);
            }
        }
    }

    
    private void checkWifiEnable() {
        int timeCount = 0;
        while (!checkState()) {
            if (timeCount >= mTimeOut) {
                onCheckWifiStateFinished(false);
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                onCheckWifiStateFinished(false);
                return;
            }
            timeCount++;
        }
    }

    
    private void checkConnectWifi(String wifiSsid) {
        int timeCount = 0;
        while (!isConnectSuccess(wifiSsid)) {
            if (timeCount >= mTimeOut) {
                onCheckWifiStateFinished(false);
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                onCheckWifiStateFinished(false);
                return;
            }
            timeCount++;
        }
        Logger.d("Router connection time: " + timeCount + " seconds ");
        onCheckWifiStateFinished(true);
    }

    private void onCheckWifiStateFinished(boolean isConnectSuccess) {
        if (mOnWifiConnectStateListener != null) {
            if (isConnectSuccess) {
                mOnWifiConnectStateListener.onWifiConnectSuccess();
            } else {
                mOnWifiConnectStateListener.onWifiConnectFailed();
            }
        }
    }

    
    public void release() {
        if (mWifiList != null && mWifiList.size() > 0) {
            mWifiList.clear();
        }
        if (mWifiConfiguration != null && mWifiConfiguration.size() > 0) {
            mWifiConfiguration.clear();
        }
        mOnWifiConnectStateListener = null;
    }

    

    
    private WifiConfiguration createWifiInfo(String SSID, String Password, int Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\""; //

        if (Type == 1) {  // WIFICIPHER_NOPASS
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if (Type == 2) { // WIFICIPHER_WEP
            config.hiddenSSID = true;
            config.wepKeys[0] = "\"" + Password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        } else if (Type == 3) { // WIFICIPHER_WPA
            config.preSharedKey = "\"" + Password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        return config;
    }

    
    public void connectConfiguration(int index) {
        
        if (index > mWifiConfiguration.size()) {
            return;
        }
        
        mWifiManager.enableNetwork(mWifiConfiguration.get(index).networkId, true);
    }

    
    public boolean addNetwork(WifiConfiguration wcg) {
        int wcgID = mWifiManager.addNetwork(wcg);
        boolean result = mWifiManager.enableNetwork(wcgID, true);
        return result;
    }

    
    public boolean connectConfigurationWifi(WifiConfiguration wcg) {
        return mWifiManager.enableNetwork(wcg.networkId, true);
    }

    
    public void disconnectWifi(int netId) {
        mWifiManager.disableNetwork(netId);
        mWifiManager.disconnect();
    }


    

    
    public void openWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
        }
    }

    
    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    
    public boolean checkState() {
        return mWifiManager.isWifiEnabled();
    }

    //=====================WifiLock========================//

    
    public void acquireWifiLock(String tag) {
        if (mWifiLock == null) {
            mWifiLock = mWifiManager.createWifiLock(tag);
        }
        mWifiLock.acquire();
    }

    
    public void releaseWifiLock() {
        
        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    

    
    public WifiConfiguration isExistSSID(List<WifiConfiguration> wifiConfigurations, String SSID) {
        for (WifiConfiguration existingConfig : wifiConfigurations) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"") || existingConfig.SSID.equals(SSID)) {
                return existingConfig;
            }
        }
        return null;
    }

    
    public boolean checkSSIDState(String ssid) {
        mWifiInfo = mWifiManager.getConnectionInfo();
        if (mWifiInfo != null && getSSID() != null) {
            return getSSID().equals("\"" + ssid + "\"") || getSSID().equals(ssid);
        }
        return false;
    }

    
    public boolean isConnectSuccess(String wifiSsid) {
        return checkState() && checkSSIDState(wifiSsid) && NetworkUtils.isHaveInternet();
    }

    

    
    public List<WifiConfiguration> startScan() {
        mWifiManager.startScan();
        
        mWifiList = mWifiManager.getScanResults();
        
        return mWifiManager.getConfiguredNetworks();
    }

    
    public List<ScanResult> getWifiList() {
        return mWifiList;
    }

    
    public StringBuilder lookUpScan() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < mWifiList.size(); i++) {
            stringBuilder.append("Index_" + Integer.valueOf(i + 1).toString() + ":");
            
            
            stringBuilder.append((mWifiList.get(i).SSID).toString());
            stringBuilder.append("\n");
        }
        return stringBuilder;
    }

    
    public List<String> getScanResultList() {
        List<String> list = new ArrayList<>();
        if (mWifiList != null && mWifiList.size() > 0) {
            for (int i = 0; i < mWifiList.size(); i++) {
                list.add(i + "--  " + mWifiList.get(i).SSID);
            }
        }
        return list;
    }

    
    public List<String> getConfigWifiList() {
        List<String> list = new ArrayList<>();
        if (mWifiConfiguration != null && mWifiConfiguration.size() > 0) {
            for (int i = 0; i < mWifiConfiguration.size(); i++) {
                list.add(i + "--  " + mWifiConfiguration.get(i).SSID);
            }
        }
        return list;
    }

    
    public boolean checkScanResult(String SSID) {
        if (mWifiList != null && mWifiList.size() > 0) {
            for (int i = 0; i < mWifiList.size(); i++) {
                if (mWifiList.get(i).SSID.equals(SSID) || mWifiList.get(i).SSID.equals("\"" + SSID + "\"")) {
                    return true;
                }
            }
        }
        return false;
    }

    

    
    private String getMacAddress() {
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getMacAddress();
    }

    
    private String getBSSID() {
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getBSSID();
    }

    
    private int getIpAddress() {
        return (mWifiInfo == null) ? 0 : mWifiInfo.getIpAddress();
    }

    
    private int getNetworkId() {
        return (mWifiInfo == null) ? 0 : mWifiInfo.getNetworkId();
    }

    
    private String getWifiInfo() {
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.toString();
    }

    
    private String getSSID() {
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getSSID();
    }

    
    public List<WifiConfiguration> getConfiguration() {
        return mWifiConfiguration;
    }

    
    public interface OnWifiConnectStateListener {
        
        void onWifiConnectSuccess();

        
        void onWifiConnectFailed();
    }
}
