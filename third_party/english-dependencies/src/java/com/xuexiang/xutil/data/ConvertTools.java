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

package com.xuexiang.xutil.data;

import com.xuexiang.xutil.common.StringUtils;

import java.util.Locale;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class ConvertTools {

    private ConvertTools() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    private static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String byte2HexString(byte byte1) {
        return bytes2HexString(new byte[]{byte1});
    }

    
    public static String bytes2HexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int len = bytes.length;
        if (len <= 0) {
            return null;
        }
        char[] ret = new char[len << 1];
        for (int i = 0, j = 0; i < len; i++) {
            ret[j++] = hexDigits[bytes[i] >>> 4 & 0x0f];
            ret[j++] = hexDigits[bytes[i] & 0x0f];
        }
        return new String(ret);
    }

    
    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    
    public static final String byteArrayToHexString(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            int v = b & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase(Locale.getDefault());
    }

    
    public static byte[] hexStringToByteArray(String hexString) {
        if (StringUtils.isEmpty(hexString)) {
            return null;
        }
        int len = hexString.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Length is not even");
        }
        byte[] d = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            
            d[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return d;
    }

    
    public static byte[] hexStringToBytes(String hexString) {
        if (StringUtils.isEmpty(hexString)) {
            return null;
        }
        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("Length is not even");
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    public static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    

    
    public static int byteToIntUnSigned(byte b) {
        return b & 0xFF;
    }

    
    public static int byteToIntSigned(byte b) {
        return b;
    }

    
    public static byte intToByte(int value) {
        return (byte) value;
    }

    

    
    public static byte[] intToBytesLittleEndian(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) (value & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[3] = (byte) ((value >> 24) & 0xFF);
        return src;
    }

    
    public static void fillIntToBytesLittleEndian(int value, byte[] src, int offset) {
        src[offset] = (byte) (value & 0xFF);
        src[offset + 1] = (byte) ((value >> 8) & 0xFF);
        src[offset + 2] = (byte) ((value >> 16) & 0xFF);
        src[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    
    public static byte[] intToBytesBigEndian(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }

    
    public static void fillIntToBytesBigEndian(int value, byte[] src, int offset) {
        src[offset] = (byte) ((value >> 24) & 0xFF);
        src[offset + 1] = (byte) ((value >> 16) & 0xFF);
        src[offset + 2] = (byte) ((value >> 8) & 0xFF);
        src[offset + 3] = (byte) (value & 0xFF);
    }

    
    public static int bytesToIntLittleEndian(byte[] src, int offset) {
        int value;
        value = (int) ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8) | ((src[offset + 2] & 0xFF) << 16) | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    
    public static int bytesToIntBigEndian(byte[] src, int offset) {
        int value;
        value = (int) (((src[offset] & 0xFF) << 24) | ((src[offset + 1] & 0xFF) << 16) | ((src[offset + 2] & 0xFF) << 8) | (src[offset + 3] & 0xFF));
        return value;
    }

    

    
    public static byte[] shortToBytesLittleEndian(short value) {
        byte[] src = new byte[2];
        src[0] = (byte) (value & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        return src;
    }

    
    public static void fillShortToBytesLittleEndian(short value, byte[] src, int offset) {
        src[offset] = (byte) (value & 0xFF);
        src[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    
    public static void fillUnsignedShortToBytesLittleEndian(int value, byte[] src, int offset) {
        src[offset] = (byte) (value & 0xFF);
        src[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    
    public static byte[] shortToBytesBigEndian(short value) {
        byte[] src = new byte[2];
        src[0] = (byte) ((value >> 8) & 0xFF);
        src[1] = (byte) (value & 0xFF);
        return src;
    }

    
    public static void fillShortToBytesBigEndian(short value, byte[] src, int offset) {
        src[offset] = (byte) ((value >> 8) & 0xFF);
        src[offset + 1] = (byte) (value & 0xFF);
    }

    
    public static void fillUnsignedShortToBytesBigEndian(int value, byte[] src, int offset) {
        src[offset] = (byte) ((value >> 8) & 0xFF);
        src[offset + 1] = (byte) (value & 0xFF);
    }

    
    public static short bytesToShortLittleEndian(byte[] src, int offset) {
        short value;
        value = (short) ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8));
        return value;
    }

    
    public static short bytesToShortBigEndian(byte[] src, int offset) {
        short value;
        value = (short) (((src[offset] & 0xFF) << 8) | (src[offset + 1] & 0xFF));
        return value;
    }


}
