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

package com.xuexiang.xutil.common;

import android.os.SystemClock;
import android.view.View;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.tip.ToastUtils;

import java.util.Timer;
import java.util.TimerTask;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public final class ClickUtils {

    
    private final static long DEFAULT_INTERVAL_MILLIS = 1000;
    
    private static long sLastClickTime;
    
    private static int sLastClickViewId;

    private ClickUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    public static boolean isFastDoubleClick(View v) {
        return isFastDoubleClick(v, DEFAULT_INTERVAL_MILLIS);
    }

    
    public static boolean isFastDoubleClick(View v, long intervalMillis) {
        long time = System.currentTimeMillis();
        int viewId = v.getId();
        long timeD = time - sLastClickTime;
        if (0 < timeD && timeD < intervalMillis && viewId == sLastClickViewId) {
            return true;
        } else {
            sLastClickTime = time;
            sLastClickViewId = viewId;
            return false;
        }
    }

    

    
    private final static int COUNTS = 5;
    private static long[] sHits = new long[COUNTS];
    
    private final static long DEFAULT_DURATION = 1000;

    
    public static void doClick(OnContinuousClickListener listener) {
        doClick(DEFAULT_DURATION, listener);
    }

    
    public static void doClick(long duration, OnContinuousClickListener listener) {
        
        System.arraycopy(sHits, 1, sHits, 0, sHits.length - 1);
        
        sHits[sHits.length - 1] = SystemClock.uptimeMillis();
        if (sHits[0] >= (SystemClock.uptimeMillis() - duration)) {
            
            sHits = new long[COUNTS];
            if (listener != null) {
                listener.onContinuousClick();
            }
        }
    }

    
    public interface OnContinuousClickListener {
        
        void onContinuousClick();
    }

    

    
    private static boolean sIsExit = false;

    
    public static void exitBy2Click() {
        exitBy2Click(2000, null);
    }

    
    public static void exitBy2Click(long intervalMillis, OnClick2ExitListener listener) {
        if (!sIsExit) {
            sIsExit = true;
            
            if (listener != null) {
                listener.onRetry();
            } else {
                ToastUtils.toast("Press again to exit");
            }
            Timer tExit = new Timer();
            tExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    
                    sIsExit = false;
                }
                
            }, intervalMillis);
        } else {
            if (listener != null) {
                listener.onExit();
            } else {
                XUtil.exitApp();
            }
        }
    }

    
    public interface OnClick2ExitListener {
        
        void onRetry();

        
        void onExit();
    }
}
