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

package com.xuexiang.xutil.system;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresPermission;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.app.IntentUtils;
import com.xuexiang.xutil.common.StringUtils;

import java.lang.reflect.Method;
import java.util.List;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.SEND_SMS;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class PhoneUtils {

    private PhoneUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    public static boolean isPhone() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    
    private static TelephonyManager getTelephonyManager() {
        return (TelephonyManager) XUtil.getContext().getSystemService(Context.TELEPHONY_SERVICE);
    }

    
    @SuppressLint("MissingPermission")
    @RequiresPermission(READ_PHONE_STATE)
    public static String getIMEI() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null ? tm.getDeviceId() : null;
    }

    
    @SuppressLint("MissingPermission")
    @RequiresPermission(READ_PHONE_STATE)
    public static String getIMSI() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null ? tm.getSubscriberId() : null;
    }

    
    public static int getPhoneType() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null ? tm.getPhoneType() : -1;
    }

    
    public static boolean isSimCardReady() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    
    public static String getSimOperatorName() {
        TelephonyManager tm = getTelephonyManager();
        return tm != null ? tm.getSimOperatorName() : null;
    }

    
    public static String getSimOperatorByMnc() {
        TelephonyManager tm = getTelephonyManager();
        String operator = tm != null ? tm.getSimOperator() : null;
        if (operator == null) {
            return null;
        }
        switch (operator) {
            case "46000":
            case "46002":
            case "46007":
                return "China Mobile";
            case "46001":
            case "46006":
                return "China Unicom";
            case "46003":
            case "46005":
            case "46011":
                return "China Telecom";
            default:
                return operator;
        }
    }

    
    @SuppressLint("MissingPermission")
    @RequiresPermission(READ_PHONE_STATE)
    public static String getPhoneInfo() {
        TelephonyManager tm = getTelephonyManager();
        if (tm == null) {
            return "";
        }
        String str = "";
        str += "DeviceId(IMEI) = " + tm.getDeviceId() + "\n";
        str += "DeviceSoftwareVersion = " + tm.getDeviceSoftwareVersion() + "\n";
        str += "Line1Number = " + tm.getLine1Number() + "\n";
        str += "NetworkCountryIso = " + tm.getNetworkCountryIso() + "\n";
        str += "NetworkOperator = " + tm.getNetworkOperator() + "\n";
        str += "NetworkOperatorName = " + tm.getNetworkOperatorName() + "\n";
        str += "NetworkType = " + tm.getNetworkType() + "\n";
        str += "PhoneType = " + tm.getPhoneType() + "\n";
        str += "SimCountryIso = " + tm.getSimCountryIso() + "\n";
        str += "SimOperator = " + tm.getSimOperator() + "\n";
        str += "SimOperatorName = " + tm.getSimOperatorName() + "\n";
        str += "SimSerialNumber = " + tm.getSimSerialNumber() + "\n";
        str += "SimState = " + tm.getSimState() + "\n";
        str += "SubscriberId(IMSI) = " + tm.getSubscriberId() + "\n";
        str += "VoiceMailNumber = " + tm.getVoiceMailNumber() + "\n";
        return str;
    }

    
    public static void dial(final String phoneNumber) {
        XUtil.getContext().startActivity(IntentUtils.getDialIntent(phoneNumber, true));
    }

    
    @RequiresPermission(CALL_PHONE)
    public static void call(final String phoneNumber) {
        XUtil.getContext().startActivity(IntentUtils.getCallIntent(phoneNumber, true));
    }

    
    public static void sendSms(final String phoneNumber, final String content) {
        XUtil.getContext().startActivity(IntentUtils.getSendSmsIntent(phoneNumber, content, true));
    }

    
    @RequiresPermission(SEND_SMS)
    public static void sendSmsSilent(final String phoneNumber, final String content) {
        if (StringUtils.isEmpty(content)) {
            return;
        }
        PendingIntent sentIntent = PendingIntent.getBroadcast(XUtil.getContext(), 0, new Intent(), 0);
        SmsManager smsManager = SmsManager.getDefault();
        if (content.length() >= 70) {
            List<String> ms = smsManager.divideMessage(content);
            for (String str : ms) {
                smsManager.sendTextMessage(phoneNumber, null, str, sentIntent, null);
            }
        } else {
            smsManager.sendTextMessage(phoneNumber, null, content, sentIntent, null);
        }
    }


    
    public static void setMobileDataEnabled(final boolean enabled) {
        try {
            TelephonyManager tm =
                    (TelephonyManager) XUtil.getContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return;
            }
            Method setMobileDataEnabledMethod =
                    tm.getClass().getDeclaredMethod("setDataEnabled", boolean.class);
            setMobileDataEnabledMethod.invoke(tm, enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // notification bar
    ///////////////////////////////////////////////////////////////////////////

    
    public static void setNotificationBarVisibility(final boolean isVisible) {
        String methodName;
        if (isVisible) {
            methodName = (Build.VERSION.SDK_INT <= 16) ? "expand" : "expandNotificationsPanel";
        } else {
            methodName = (Build.VERSION.SDK_INT <= 16) ? "collapse" : "collapsePanels";
        }
        invokePanels(methodName);
    }

    private static void invokePanels(final String methodName) {
        try {
            @SuppressLint("WrongConstant")
            Object service = XUtil.getContext().getSystemService("statusbar");
            @SuppressLint("PrivateApi")
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method expand = statusBarManager.getMethod(methodName);
            expand.invoke(service);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


//    /**


//     * {@code <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />}</p>

//     * {@code <uses-permission android:name="android.permission.READ_CONTACTS" />}</p>
//     */
//    public static void getAllContactInfo() {
//        Log.i("PhoneUtils", "Please refer to the following code.");
//        ArrayList<HashMap<String, String>> list = new ArrayList<>();

//        ContentResolver resolver = Utils.getApp().getContentResolver();




//        Uri raw_uri = Uri.parse("content://com.android.contacts/raw_contacts");
//        Uri date_uri = Uri.parse("content://com.android.contacts/data");


//        Cursor cursor = resolver.query(raw_uri, new String[]{"contact_id"}, null, null, null);
//        try {

//            if (cursor != null) {
//                while (cursor.moveToNext()) {

//                    String contact_id = cursor.getString(0);
//                    // cursor.getString(cursor.getColumnIndex("contact_id"));//getColumnIndex


//                    if (!StringUtils.isEmpty(contact_id)) {//null   ""





//                        Cursor c = resolver.query(date_uri, new String[]{"data1",
//                                        "mimetype"}, "raw_contact_id=?",
//                                new String[]{contact_id}, null);
//                        HashMap<String, String> map = new HashMap<String, String>();

//                        if (c != null) {
//                            while (c.moveToNext()) {

//                                String data1 = c.getString(0);
//                                String mimetype = c.getString(1);

//                                if (mimetype.equals("vnd.android.cursor.item/phone_v2")) {

//                                    map.put("phone", data1);
//                                } else if (mimetype.equals("vnd.android.cursor.item/name")) {

//                                    map.put("name", data1);
//                                }
//                            }
//                        }

//                        list.add(map);

//                        if (c != null) {
//                            c.close();
//                        }
//                    }
//                }
//            }
//        } finally {

//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        return list;
//    }

//    /**


//     */
//    public static void getContactNum() {
//        Log.i("PhoneUtils", "Please refer to the following code.");
//        Intent intent = new Intent();
//        intent.setAction("android.intent.action.PICK");
//        intent.setType("vnd.android.cursor.dir/phone_v2");
//        startActivityForResult(intent, 0);
//
//        @Override
//        protected void onActivityResult ( int requestCode, int resultCode, Intent data){
//            super.onActivityResult(requestCode, resultCode, data);
//            if (data != null) {
//                Uri uri = data.getData();
//                String num = null;

//                ContentResolver contentResolver = getContentResolver();
//                Cursor cursor = contentResolver.query(uri,
//                        null, null, null, null);
//                while (cursor.moveToNext()) {
//                    num = cursor.getString(cursor.getColumnIndex("data1"));
//                }
//                cursor.close();

//            }
//        }
//    }

//    /**


//     * {@code <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />}</p>

//     * {@code <uses-permission android:name="android.permission.READ_SMS" />}</p>
//     */
//    public static void getAllSMS() {
//        Log.i("PhoneUtils", "Please refer to the following code.");


//        ContentResolver resolver = Utils.getApp().getContentResolver();


//        Uri uri = Uri.parse("content://sms");





//        Cursor cursor = resolver.query(uri,
//                new String[]{"address", "date", "type", "body"},
//                null,
//                null,
//                null
//        );




//        XmlSerializer xmlSerializer = Xml.newSerializer();
//        try {



//            xmlSerializer.setOutput(new FileOutputStream(new File("/mnt/sdcard/backupsms.xml")),
//                    "utf-8"
//            );


//            xmlSerializer.startDocument("utf-8", true);

//            xmlSerializer.startTag(null, "smss");

//            while (cursor.moveToNext()) {
//                SystemClock.sleep(1000);

//                xmlSerializer.startTag(null, "sms");

//                xmlSerializer.startTag(null, "address");
//                String address = cursor.getString(0);

//                xmlSerializer.text(address);
//                xmlSerializer.endTag(null, "address");
//                xmlSerializer.startTag(null, "date");
//                String date = cursor.getString(1);
//                xmlSerializer.text(date);
//                xmlSerializer.endTag(null, "date");
//                xmlSerializer.startTag(null, "type");
//                String type = cursor.getString(2);
//                xmlSerializer.text(type);
//                xmlSerializer.endTag(null, "type");
//                xmlSerializer.startTag(null, "body");
//                String body = cursor.getString(3);
//                xmlSerializer.text(body);
//                xmlSerializer.endTag(null, "body");
//                xmlSerializer.endTag(null, "sms");
//                Log.i("PhoneUtils", "address: " + address
//                        + ", date: " + date
//                        + ", type: " + type
//                        + ", body: " + body
//                );
//            }
//            xmlSerializer.endTag(null, "smss");
//            xmlSerializer.endDocument();

//            xmlSerializer.flush();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
