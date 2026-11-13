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

import com.xuexiang.xutil.data.DateUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class IDCardUtils {

    
    public static final int CHINA_ID_MIN_LENGTH = 15;

    
    public static final int CHINA_ID_MAX_LENGTH = 18;

    
    public static final String[] cityCode = {"11", "12", "13", "14", "15", "21", "22", "23", "31", "32", "33", "34", "35", "36", "37", "41", "42", "43", "44", "45", "46", "50", "51", "52", "53",
            "54", "61", "62", "63", "64", "65", "71", "81", "82", "91"};

    
    public static final int[] power = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    
    public static final String[] verifyCode = {"1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"};
    
    public static final int MIN = 1930;
    public static final Map<String, String> cityCodes = new HashMap<>();
    
    public static final Map<String, Integer> twFirstCode = new HashMap<>();
    
    public static final Map<String, Integer> hkFirstCode = new HashMap<>();

    static {
        cityCodes.put("11", "Beijing");
        cityCodes.put("12", "Tianjin");
        cityCodes.put("13", "Hebei");
        cityCodes.put("14", "Shanxi");
        cityCodes.put("15", "Inner Mongolia");
        cityCodes.put("21", "Liaoning");
        cityCodes.put("22", "Jilin");
        cityCodes.put("23", "Heilongjiang");
        cityCodes.put("31", "Shanghai");
        cityCodes.put("32", "Jiangsu");
        cityCodes.put("33", "Zhejiang");
        cityCodes.put("34", "Anhui");
        cityCodes.put("35", "Fujian");
        cityCodes.put("36", "Jiangxi");
        cityCodes.put("37", "Shandong");
        cityCodes.put("41", "Henan");
        cityCodes.put("42", "Hubei");
        cityCodes.put("43", "Hunan");
        cityCodes.put("44", "Guangdong");
        cityCodes.put("45", "Guangxi");
        cityCodes.put("46", "Hainan");
        cityCodes.put("50", "Chongqing");
        cityCodes.put("51", "Sichuan");
        cityCodes.put("52", "Guizhou");
        cityCodes.put("53", "Yunnan");
        cityCodes.put("54", "Tibet");
        cityCodes.put("61", "Shaanxi");
        cityCodes.put("62", "Gansu");
        cityCodes.put("63", "Qinghai");
        cityCodes.put("64", "Ningxia");
        cityCodes.put("65", "Xinjiang");
        cityCodes.put("71", "Taiwan");
        cityCodes.put("81", "Hong Kong");
        cityCodes.put("82", "Macao");
        cityCodes.put("91", "Overseas");
        twFirstCode.put("A", 10);
        twFirstCode.put("B", 11);
        twFirstCode.put("C", 12);
        twFirstCode.put("D", 13);
        twFirstCode.put("E", 14);
        twFirstCode.put("F", 15);
        twFirstCode.put("G", 16);
        twFirstCode.put("H", 17);
        twFirstCode.put("J", 18);
        twFirstCode.put("K", 19);
        twFirstCode.put("L", 20);
        twFirstCode.put("M", 21);
        twFirstCode.put("N", 22);
        twFirstCode.put("P", 23);
        twFirstCode.put("Q", 24);
        twFirstCode.put("R", 25);
        twFirstCode.put("S", 26);
        twFirstCode.put("T", 27);
        twFirstCode.put("U", 28);
        twFirstCode.put("V", 29);
        twFirstCode.put("X", 30);
        twFirstCode.put("Y", 31);
        twFirstCode.put("W", 32);
        twFirstCode.put("Z", 33);
        twFirstCode.put("I", 34);
        twFirstCode.put("O", 35);
        hkFirstCode.put("A", 1);
        hkFirstCode.put("B", 2);
        hkFirstCode.put("C", 3);
        hkFirstCode.put("R", 18);
        hkFirstCode.put("U", 21);
        hkFirstCode.put("Z", 26);
        hkFirstCode.put("X", 24);
        hkFirstCode.put("W", 23);
        hkFirstCode.put("O", 15);
        hkFirstCode.put("N", 14);
    }

    private IDCardUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    private static String convert15CardTo18(String idCard) {
        String idCard18 = "";
        if (idCard.length() != CHINA_ID_MIN_LENGTH) {
            return null;
        }
        if (isNum(idCard)) {
            
            String birthday = idCard.substring(6, 12);
            Date birthDate = null;
            try {
                birthDate = new SimpleDateFormat("yyMMdd").parse(birthday);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Calendar cal = Calendar.getInstance();
            if (birthDate != null) {
                cal.setTime(birthDate);
            }
            
            String sYear = String.valueOf(cal.get(Calendar.YEAR));
            idCard18 = idCard.substring(0, 6) + sYear + idCard.substring(8);
            
            char[] cArr = idCard18.toCharArray();
            int[] iCard = convertCharToInt(cArr);
            int iSum17 = getPowerSum(iCard);
            
            String sVal = getCheckCode18(iSum17);
            if (sVal.length() > 0) {
                idCard18 += sVal;
            } else {
                return null;
            }
        } else {
            return null;
        }
        return idCard18;
    }

    
    private static boolean validateIdCard18(String idCard) {
        boolean bTrue = false;
        if (idCard.length() == CHINA_ID_MAX_LENGTH) {
            
            String code17 = idCard.substring(0, 17);
            
            String code18 = idCard.substring(17, CHINA_ID_MAX_LENGTH);
            if (isNum(code17)) {
                char[] cArr = code17.toCharArray();
                int[] iCard = convertCharToInt(cArr);
                int iSum17 = getPowerSum(iCard);
                
                String val = getCheckCode18(iSum17);
                if (val.length() > 0) {
                    if (val.equalsIgnoreCase(code18)) {
                        bTrue = true;
                    }
                }
            }
        }
        return bTrue;
    }

    
    private static boolean validateIdCard15(String idCard) {
        if (idCard.length() != CHINA_ID_MIN_LENGTH) {
            return false;
        }
        if (isNum(idCard)) {
            String proCode = idCard.substring(0, 2);
            if (cityCodes.get(proCode) == null) {
                return false;
            }
            String birthCode = idCard.substring(6, 12);
            Date birthDate = null;
            try {
                birthDate = new SimpleDateFormat("yy").parse(birthCode.substring(0, 2));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Calendar cal = Calendar.getInstance();
            if (birthDate != null) {
                cal.setTime(birthDate);
            }
            return validate(cal.get(Calendar.YEAR), Integer.parseInt(birthCode.substring(2, 4)), Integer.parseInt(birthCode.substring(4, 6)));
        } else {
            return false;
        }
    }

    
    private static String[] validateIdCard10(String idCard) {
        String[] info = new String[3];
        String card = idCard.replaceAll("[\\(|\\)]", "");
        if (card.length() != 8 && card.length() != 9 && idCard.length() != 10) {
            return null;
        }
        if (idCard.matches("^[a-zA-Z][0-9]{9}$")) { 
            info[0] = "Taiwan";
            System.out.println("11111");
            String char2 = idCard.substring(1, 2);
            if ("1".equals(char2)) {
                info[1] = "M";
                System.out.println("MMMMMMM");
            } else if ("2".equals(char2)) {
                info[1] = "F";
                System.out.println("FFFFFFF");
            } else {
                info[1] = "N";
                info[2] = "false";
                System.out.println("NNNN");
                return info;
            }
            info[2] = validateTWCard(idCard) ? "true" : "false";
        } else if (idCard.matches("^[1|5|7][0-9]{6}\\(?[0-9A-Z]\\)?$")) { 
            info[0] = "Macao";
            info[1] = "N";
        } else if (idCard.matches("^[A-Z]{1,2}[0-9]{6}\\(?[0-9A]\\)?$")) { 
            info[0] = "Hong Kong";
            info[1] = "N";
            info[2] = validateHKCard(idCard) ? "true" : "false";
        } else {
            return null;
        }
        return info;
    }

    
    public static boolean validateTWCard(String idCard) {
        String start = idCard.substring(0, 1);
        String mid = idCard.substring(1, 9);
        String end = idCard.substring(9, 10);
        Integer iStart = twFirstCode.get(start);
        int sum = iStart / 10 + (iStart % 10) * 9;
        char[] chars = mid.toCharArray();
        int iflag = 8;
        for (char c : chars) {
            sum = sum + Integer.parseInt(c + "") * iflag;
            iflag--;
        }
        return (sum % 10 == 0 ? 0 : (10 - sum % 10)) == Integer.parseInt(end);
    }

    
    public static boolean validateHKCard(String idCard) {
        String card = idCard.replaceAll("[\\(|\\)]", "");
        int sum = 0;
        if (card.length() == 9) {
            sum = ((int) card.substring(0, 1).toUpperCase().toCharArray()[0] - 55) * 9 + ((int) card.substring(1, 2).toUpperCase().toCharArray()[0] - 55) * 8;
            card = card.substring(1, 9);
        } else {
            sum = 522 + ((int) card.substring(0, 1).toUpperCase().toCharArray()[0] - 55) * 8;
        }
        String mid = card.substring(1, 7);
        String end = card.substring(7, 8);
        char[] chars = mid.toCharArray();
        int iflag = 7;
        for (char c : chars) {
            sum = sum + Integer.parseInt(c + "") * iflag;
            iflag--;
        }
        if ("A".equals(end.toUpperCase())) {
            sum = sum + 10;
        } else {
            sum = sum + Integer.parseInt(end);
        }
        return sum % 11 == 0;
    }

    
    private static int[] convertCharToInt(char[] ca) {
        int len = ca.length;
        int[] iArr = new int[len];
        try {
            for (int i = 0; i < len; i++) {
                iArr[i] = Integer.parseInt(String.valueOf(ca[i]));
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return iArr;
    }

    
    private static int getPowerSum(int[] iArr) {
        int iSum = 0;
        if (power.length == iArr.length) {
            for (int i = 0; i < iArr.length; i++) {
                for (int j = 0; j < power.length; j++) {
                    if (i == j) {
                        iSum = iSum + iArr[i] * power[j];
                    }
                }
            }
        }
        return iSum;
    }

    
    private static String getCheckCode18(int iSum) {
        String sCode = "";
        switch (iSum % 11) {
            case 10:
                sCode = "2";
                break;
            case 9:
                sCode = "3";
                break;
            case 8:
                sCode = "4";
                break;
            case 7:
                sCode = "5";
                break;
            case 6:
                sCode = "6";
                break;
            case 5:
                sCode = "7";
                break;
            case 4:
                sCode = "8";
                break;
            case 3:
                sCode = "9";
                break;
            case 2:
                sCode = "x";
                break;
            case 1:
                sCode = "0";
                break;
            case 0:
                sCode = "1";
                break;
            default:
                break;
        }
        return sCode;
    }

    
    public static String getAgeDescriptionByBirthday(final String birthday, final DateFormat format) {
        return getAgeDescriptionByBirthday(DateUtils.string2Date(birthday, format));
    }

    
    public static String getAgeDescriptionByBirthday(final Date birthday) {
        String ageDescription = "";
        Calendar cal = Calendar.getInstance();

        int yearNow = cal.get(Calendar.YEAR);
        int monthNow = cal.get(Calendar.MONTH) + 1;
        int dayNow = cal.get(Calendar.DAY_OF_MONTH);
        long millisNow = cal.getTimeInMillis();

        cal.setTime(birthday);

        int yearBirth = cal.get(Calendar.YEAR);
        int monthBirth = cal.get(Calendar.MONTH) + 1;
        int dayBirth = cal.get(Calendar.DAY_OF_MONTH);
        long millisBirth = cal.getTimeInMillis();

        int ages = yearNow - yearBirth;
        long time = millisNow - millisBirth;
        int days = (int) (time / (24 * 60 * 60 * 1000));
        int hours = (int) (time / (60 * 60 * 1000));
        if (ages > 2 && ages <= 150) {
            if (monthNow <= monthBirth) {
                if (monthNow == monthBirth) {
                    if (dayNow < dayBirth) {
                        ages--;
                    }
                } else {
                    ages--;
                }
            }
            ageDescription = ages + " years ";
        } else if (ages <= 2) {
            if (days > 100) {
                ageDescription = ages * 12 + (monthNow - monthBirth) + " month ";
            } else {
                if (hours > 72) {
                    ageDescription = days + " days ";
                } else {
                    ageDescription = hours + " hours ";
                }
            }
        }
        return ageDescription;
    }

    
    public static String getAgeDescriptionByIdCard(String idCard) {
        return getAgeDescriptionByBirthday(getBirthByIdCard(idCard), DateUtils.yyyyMMddNoSep.get());
    }

    
    public static String getBirthByIdCard(String idCard) {
        int len = idCard.length();
        if (len < CHINA_ID_MIN_LENGTH) {
            return null;
        } else if (len == CHINA_ID_MIN_LENGTH) {
            idCard = convert15CardTo18(idCard);
        }
        return idCard.substring(6, 14);
    }

    
    public static Short getYearByIdCard(String idCard) {
        int len = idCard.length();
        if (len < CHINA_ID_MIN_LENGTH) {
            return null;
        } else if (len == CHINA_ID_MIN_LENGTH) {
            idCard = convert15CardTo18(idCard);
        }
        return Short.valueOf(idCard.substring(6, 10));
    }

    
    public static Short getMonthByIdCard(String idCard) {
        int len = idCard.length();
        if (len < CHINA_ID_MIN_LENGTH) {
            return null;
        } else if (len == CHINA_ID_MIN_LENGTH) {
            idCard = convert15CardTo18(idCard);
        }
        return Short.valueOf(idCard.substring(10, 12));
    }

    
    public static Short getDayByIdCard(String idCard) {
        int len = idCard.length();
        if (len < CHINA_ID_MIN_LENGTH) {
            return null;
        } else if (len == CHINA_ID_MIN_LENGTH) {
            idCard = convert15CardTo18(idCard);
        }
        return Short.valueOf(idCard.substring(12, 14));
    }

    
    public static String getGenderByIdCard(String idCard) {
        String sGender = "N";
        if (idCard.length() == CHINA_ID_MIN_LENGTH) {
            idCard = convert15CardTo18(idCard);
        }
        String sCardNum = idCard.substring(16, 17);
        if (Integer.parseInt(sCardNum) % 2 != 0) {
            sGender = "M";
        } else {
            sGender = "F";
        }
        return sGender;
    }

    
    public static String getProvinceByIdCard(String idCard) {
        int len = idCard.length();
        String sProvince = null;
        String sProvinNum = "";
        if (len == CHINA_ID_MIN_LENGTH || len == CHINA_ID_MAX_LENGTH) {
            sProvinNum = idCard.substring(0, 2);
        }
        sProvince = cityCodes.get(sProvinNum);
        return sProvince;
    }

    
    private static boolean isNum(String val) {
        return !(val == null || "".equals(val)) && val.matches("^[0-9]*$");
    }

    
    private static boolean validate(int iYear, int iMonth, int iDate) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int datePerMonth;
        if (iYear < MIN || iYear >= year) {
            return false;
        }
        if (iMonth < 1 || iMonth > 12) {
            return false;
        }
        switch (iMonth) {
            case 4:
            case 6:
            case 9:
            case 11:
                datePerMonth = 30;
                break;
            case 2:
                boolean dm = ((iYear % 4 == 0 && iYear % 100 != 0) || (iYear % 400 == 0)) && (iYear > MIN && iYear < year);
                datePerMonth = dm ? 29 : 28;
                break;
            default:
                datePerMonth = 31;
        }
        return (iDate >= 1) && (iDate <= datePerMonth);
    }

    
    public static boolean validateCard(String idCard) {
        String card = idCard.trim();
        if (validateIdCard18(card)) {
            return true;
        }
        if (validateIdCard15(card)) {
            return true;
        }
        String[] cardval = validateIdCard10(card);
        return cardval != null && "true".equals(cardval[2]);
    }

    
    public static String hideIdCard(String idCardNo) {
        if (StringUtils.isEmpty(idCardNo)) {
            return StringUtils.EMPTY;
        }
        if (idCardNo.length() >= 7) {
            String tempStr = idCardNo.substring(3, idCardNo.length() - 4);
            idCardNo = idCardNo.replaceAll(tempStr, "*****");
        }
        return idCardNo;
    }

}