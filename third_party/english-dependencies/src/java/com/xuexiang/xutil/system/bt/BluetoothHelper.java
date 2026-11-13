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

package com.xuexiang.xutil.system.bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.common.StringUtils;
import com.xuexiang.xutil.system.ThreadPoolManager;
import com.xuexiang.xutil.tip.ToastUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public class BluetoothHelper {

    private static volatile BluetoothHelper sInstance;
    
    private volatile BlueToothReceiver mReceiver;
    
    private boolean mNeed2unRegister = false;

    
    private final BluetoothAdapter mBluetoothAdapter;
    
    private List<BluetoothDevice> mBondedList;
    
    private List<BluetoothDevice> mNewList;

    
    private OnBluetoothDeviceListener mOnBluetoothDeviceListener;
    
    private IBluetoothDeviceFilter mBluetoothDeviceFilter;

    

    public static BluetoothHelper get() {
        if (sInstance == null) {
            synchronized (BluetoothHelper.class) {
                if (sInstance == null) {
                    sInstance = new BluetoothHelper();
                }
            }
        }
        return sInstance;
    }

    
    private BluetoothHelper() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /* Upstream explanatory comments omitted; see pinned source receipt.
     * @author xx
 */
    private class BlueToothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (isCorrectDevice(device)) {
                    if (mOnBluetoothDeviceListener != null) {
                        mOnBluetoothDeviceListener.onNewDeviceFound(device);
                    }
                    if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                        mNewList.add(device);
                    } else if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        mBondedList.add(device);
                    }
                }

            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mOnBluetoothDeviceListener != null) {
                    mOnBluetoothDeviceListener.onBondStateChanged(device, intent);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (mOnBluetoothDeviceListener != null) {
                    mOnBluetoothDeviceListener.onSearchCompleted(mBondedList, mNewList);
                }
            }
        }
    }

    

    
    private void registerBTReceiver() {
        if (mReceiver == null) {
            mReceiver = new BlueToothReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        XUtil.getContext().getApplicationContext().registerReceiver(mReceiver, intentFilter);
        mNeed2unRegister = true;
    }

    
    public void openBluetooth(final boolean isReOpen) {
        ThreadPoolManager.get().addTask(new Runnable() {
            boolean openResult;

            @Override
            public void run() {
                if (isReOpen) {
                    openResult = reOpenBluetooth();
                } else {
                    openResult = openBluetooth();
                }
                if (openResult) {
                    if (mOnBluetoothDeviceListener != null) {
                        if (isReOpen) {
                            mOnBluetoothDeviceListener.onBluetoothReOpened();
                        } else {
                            mOnBluetoothDeviceListener.onBluetoothOpened();
                        }
                    }
                }
            }
        });
    }

    
    private boolean openBluetooth() {
        boolean result = false;
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                return true;
            } else {
                result = enableBluetooth();
            }
        }
        return result;
    }

    
    private boolean reOpenBluetooth() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable(); 
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return enableBluetooth();
    }

    
    private boolean enableBluetooth() {
        boolean result = false;
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.enable(); 
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mBluetoothAdapter.isEnabled()) {
                result = true;
            }
        }
        return result;
    }

    

    
    public boolean startSearch() {
        stopSearch();
        return mBluetoothAdapter.startDiscovery();
    }

    
    public void stopSearch() {
        if (mBluetoothAdapter == null) {
            return;
        }
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    
    public void searchDevices(OnBluetoothDeviceListener listener) {
        mOnBluetoothDeviceListener = listener;
        if (mBluetoothAdapter == null) {
            return;
        }
        registerBTReceiver();

        if (mBondedList == null) {
            mBondedList = new ArrayList<>();
        }
        if (mNewList == null) {
            mNewList = new ArrayList<>();
        }
        mBondedList.clear();
        mNewList.clear();

        if (startSearch()) {
            if (mOnBluetoothDeviceListener != null) {
                mOnBluetoothDeviceListener.onStartDiscovery();
            }
        } else {
            ToastUtils.toast("Bluetooth scan failed; retrying");
            openBluetooth(true);
        }

    }

    

    
    public boolean createBind(BluetoothDevice btDevice) throws Exception {
        Method createBondMethod = btDevice.getClass().getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    
    @SuppressLint("NewApi")
    public boolean createBind(BluetoothDevice btDevice, String pingCode) {
        btDevice.setPin(pingCode.getBytes());
        return btDevice.createBond();
    }

    
    @SuppressLint("NewApi")
    public boolean pairBtDevice(String address) {
        boolean result = false;
        stopSearch();
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        if (BluetoothAdapter.checkBluetoothAddress(address)) { 
            BluetoothDevice device = getBluetoothDevice(address);
            if (isCorrectDevice(device)) {
                result = device.getBondState() == BluetoothDevice.BOND_BONDED || device.createBond();
            }
        }
        return result;
    }


    

    
    public boolean isOpenBluetooth() {
        return mBluetoothAdapter.isEnabled();
    }

    
    public boolean isBtAddressValid(String address) {
        return !StringUtils.isEmpty(address) && BluetoothAdapter.checkBluetoothAddress(address.toUpperCase());
    }

    
    public boolean isCorrectDevice(String address) {
        return isCorrectDevice(getBluetoothDevice(address));
    }

    
    public boolean isCorrectDevice(BluetoothDevice device) {
        boolean isCorrectDevice = true;
        if (mBluetoothDeviceFilter != null) {
            isCorrectDevice = mBluetoothDeviceFilter.isCorrect(device);
        }
        return isCorrectDevice;
    }

    
    public boolean isBluetoothBond(String address) {
        BluetoothDevice device = getBluetoothDevice(address);
        return device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    

    
    public void release() {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // unregister
        if (mNeed2unRegister) {
            XUtil.getContext().getApplicationContext().unregisterReceiver(mReceiver);
            mNeed2unRegister = false;
        }

        mNewList = null;
        mBondedList = null;
        mOnBluetoothDeviceListener = null;
        mReceiver = null;
        mBluetoothDeviceFilter = null;
    }

    // ================================================set/get================================================//

    
    public BluetoothHelper setOnBluetoothDeviceListener(OnBluetoothDeviceListener listener) {
        mOnBluetoothDeviceListener = listener;
        return this;
    }

    
    public BluetoothHelper setBluetoothDeviceFilter(IBluetoothDeviceFilter deviceFilter) {
        mBluetoothDeviceFilter = deviceFilter;
        return this;
    }

    public IBluetoothDeviceFilter getBluetoothDeviceFilter() {
        return mBluetoothDeviceFilter;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    
    public BluetoothDevice getBluetoothDevice(String address) {
        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.getRemoteDevice(address);
        } else {
            return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        }
    }

    
    /* Upstream explanatory comments omitted; see pinned source receipt.
     * @author xx
 */
    public interface OnBluetoothDeviceListener extends OnSearchDeviceListener {
        
        void onBondStateChanged(BluetoothDevice device, Intent intent);

        
        void onBluetoothOpened();

        
        void onBluetoothReOpened();
    }

    /* Upstream explanatory comments omitted; see pinned source receipt.
     * @author xx
 */
    public interface OnSearchDeviceListener {

        
        void onStartDiscovery();

        
        void onNewDeviceFound(BluetoothDevice device);

        
        void onSearchCompleted(List<BluetoothDevice> bondedList, List<BluetoothDevice> newList);

    }

}
