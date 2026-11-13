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

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.xuexiang.constant.DateFormatConstants;
import com.xuexiang.constant.TimeConstants;
import com.xuexiang.xutil.common.StringUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xuexiang.constant.TimeConstants.DAY;
import static com.xuexiang.constant.TimeConstants.HOUR;
import static com.xuexiang.constant.TimeConstants.MIN;
import static com.xuexiang.constant.TimeConstants.SEC;
import static com.xuexiang.xutil.common.StringUtils.EMPTY;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public final class DateUtils {

    /**
     * yyyy-MM-dd
     */
    public static final ThreadLocal<DateFormat> yyyyMMdd = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMdd);
        }
    };
    /**
     * yyyyMMdd
     */
    public static final ThreadLocal<DateFormat> yyyyMMddNoSep = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMddNoSep);
        }
    };
    /**
     * HH:mm:ss
     */
    public static final ThreadLocal<DateFormat> HHmmss = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.HHmmss);
        }
    };
    /**
     * HH:mm
     */
    public static final ThreadLocal<DateFormat> HHmm = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.HHmm);
        }
    };
    /**
     * yyyy-MM-dd HH:mm:ss
     */
    public static final ThreadLocal<DateFormat> yyyyMMddHHmmss = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMddHHmmss);
        }
    };
    /**
     * yyyyMMddHHmmss
     */
    public static final ThreadLocal<DateFormat> yyyyMMddHHmmssNoSep = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMddHHmmssNoSep);
        }
    };
    /**
     * yyyy-MM-dd HH:mm
     */
    public static final ThreadLocal<DateFormat> yyyyMMddHHmm = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMddHHmm);
        }
    };
    /**
     * yyyy-MM-dd HH:mm:ss:SSS
     */
    public static final ThreadLocal<DateFormat> yyyyMMddHHmmssSSS = new ThreadLocal<DateFormat>() {
        @SuppressLint("SimpleDateFormat")
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat(DateFormatConstants.yyyyMMddHHmmssSSS);
        }
    };
    

    /**
     * Don't let anyone instantiate this class.
     */
    private DateUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }


    
    public static String millis2String(final long millis, final DateFormat format) {
        return date2String(new Date(millis), format);
    }

    
    public static String date2String(final Date date, final DateFormat format) {
        if (format != null) {
            return format.format(date);
        } else {
            return EMPTY;
        }
    }

    
    public static long string2Millis(final String time, final DateFormat format) {
        try {
            if (format != null) {
                return format.parse(time).getTime();
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return -1;
    }

    
    public static Date string2Date(final String time, final DateFormat format) {
        try {
            if (format != null) {
                return format.parse(time);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    
    public static long date2Millis(final Date date) {
        return date != null ? date.getTime() : -1;
    }

    
    public static Date millis2Date(final long millis) {
        return new Date(millis);
    }

    
    public static String translateDateFormat(final String time, final DateFormat oldFormat, final DateFormat newFormat) {
        if (StringUtils.isSpace(time)) {
            return EMPTY;
        }

        Date date = string2Date(time, oldFormat); //String -> Date
        return date != null ? date2String(date, newFormat) : EMPTY; //Date -> String
    }

    
    public static String translateDateFormat(final String time, final String oldFormatType, final String newFormatType) {
        return translateDateFormat(time, new SimpleDateFormat(oldFormatType), new SimpleDateFormat(newFormatType));
    }

    
    public static boolean isDateFormatRight(final String time, final DateFormat format) {
        
        if (StringUtils.isSpace(time) || format == null) {
            return false;
        }

        try {
            Date date = format.parse(time);
            String s = format.format(date);
            return time.equals(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    
    @Nullable
    public static String convertTimeToFileName(String dateTime, String suffix) {
        if (StringUtils.isSpace(dateTime)) {
            return null;
        }

        Pattern p = Pattern.compile("[^\\d]+");
        Matcher m = p.matcher(dateTime);
        return m.replaceAll("").trim() + suffix;
    }

    

    
    public static Date getStartOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    
    public static Date getStartOfDay(Date date, int dayAfter) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, dayAfter);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    
    public static Date getEndOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    
    public static Date getEndOfDay(Date date, int dayAfter) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, dayAfter);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }


    
    public static String nDaysBeforeToday(int day, boolean isNeedHMS) {
        return nDaysAfterToday(-day, isNeedHMS);
    }

    
    public static String nDaysAfterToday(int day, boolean isNeedHMS) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTimeInMillis(System.currentTimeMillis());
        rightNow.add(Calendar.DAY_OF_MONTH, day);
        if (isNeedHMS) {
            return DateUtils.date2String(rightNow.getTime(), yyyyMMddHHmmss.get());
        } else {
            return DateUtils.date2String(rightNow.getTime(), yyyyMMdd.get());
        }
    }

    
    public static Date nDaysBeforeToday(int day) {
        return nDaysAfterToday(-day);
    }

    
    public static Date nDaysAfterToday(int day) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTimeInMillis(System.currentTimeMillis());
        rightNow.add(Calendar.DAY_OF_MONTH, day);
        return rightNow.getTime();
    }

    
    public static Date nMonthsBeforeToday(int month) {
        return nMonthsAfterToday(-month);
    }

    
    public static Date nMonthsAfterToday(int month) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTimeInMillis(System.currentTimeMillis());
        rightNow.add(Calendar.MONTH, month);
        return rightNow.getTime();
    }

    
    public static long getTimeSpan(final String time0,
                                   final String time1,
                                   final DateFormat format,
                                   @TimeConstants.Unit final int unit) {
        return millis2TimeSpan(
                Math.abs(string2Millis(time0, format) - string2Millis(time1, format)), unit
        );
    }

    
    public static long getTimeSpan(final Date date0,
                                   final Date date1,
                                   @TimeConstants.Unit final int unit) {
        return millis2TimeSpan(Math.abs(date2Millis(date0) - date2Millis(date1)), unit);
    }

    
    public static long getTimeSpan(final long millis0,
                                   final long millis1,
                                   @TimeConstants.Unit final int unit) {
        return millis2TimeSpan(Math.abs(millis0 - millis1), unit);
    }

    
    public static String getFitTimeSpan(final String time0,
                                        final String time1,
                                        final DateFormat format,
                                        final int precision) {
        long delta = string2Millis(time0, format) - string2Millis(time1, format);
        return millis2FitTimeSpan(Math.abs(delta), precision);
    }

    
    public static String getFitTimeSpan(final Date date0, final Date date1, final int precision) {
        return millis2FitTimeSpan(Math.abs(date2Millis(date0) - date2Millis(date1)), precision);
    }

    
    public static String getFitTimeSpan(final long millis0,
                                        final long millis1,
                                        final int precision) {
        return millis2FitTimeSpan(Math.abs(millis0 - millis1), precision);
    }

    
    public static long getTimeSpanByNow(final String time,
                                        final DateFormat format,
                                        @TimeConstants.Unit final int unit) {
        return getTimeSpan(getNowString(format), time, format, unit);
    }

    
    public static long getTimeSpanByNow(final Date date, @TimeConstants.Unit final int unit) {
        return getTimeSpan(new Date(), date, unit);
    }

    
    public static long getTimeSpanByNow(final long millis, @TimeConstants.Unit final int unit) {
        return getTimeSpan(System.currentTimeMillis(), millis, unit);
    }

    
    public static String getFitTimeSpanByNow(final String time,
                                             final DateFormat format,
                                             final int precision) {
        return getFitTimeSpan(getNowString(format), time, format, precision);
    }

    
    public static String getFitTimeSpanByNow(final Date date, final int precision) {
        return getFitTimeSpan(getNowDate(), date, precision);
    }

    
    public static String getFitTimeSpanByNow(final long millis, final int precision) {
        return getFitTimeSpan(System.currentTimeMillis(), millis, precision);
    }

    
    public static String getFriendlyTimeSpanByNow(final String time, final DateFormat format) {
        return getFriendlyTimeSpanByNow(string2Millis(time, format));
    }

    
    public static String getFriendlyTimeSpanByNow(final Date date) {
        return getFriendlyTimeSpanByNow(date.getTime());
    }

    
    public static String getFriendlyTimeSpanByNow(final long millis) {
        long now = System.currentTimeMillis();
        long span = now - millis;
        if (span < 0) {
            return String.format("%tc", millis);
        }
        if (span < 1000) {
            return "Just now";
        } else if (span < MIN) {
            return String.format(Locale.getDefault(), "%d seconds ago", span / SEC);
        } else if (span < HOUR) {
            return String.format(Locale.getDefault(), "%d minutes ago", span / MIN);
        }
        
        long wee = getWeeOfToday();
        if (millis >= wee) {
            return String.format("Today %tR", millis);
        } else if (millis >= wee - DAY) {
            return String.format("Yesterday %tR", millis);
        } else {
            return String.format("%tF", millis);
        }
    }

    private static long getWeeOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    
    private static final int YEAR_S = 365 * 24 * 60 * 60;
    
    private static final int MONTH_S = 30 * 24 * 60 * 60;
    
    private static final int DAY_S = 24 * 60 * 60;
    
    private static final int HOUR_S = 60 * 60;
    
    private static final int MINUTE_S = 60;

    
    public static String getFuzzyTimeDescriptionByNow(final String time, final DateFormat format) {
        return getFuzzyTimeDescriptionByNow(string2Millis(time, format));
    }

    
    public static String getFuzzyTimeDescriptionByNow(final Date date) {
        return getFuzzyTimeDescriptionByNow(date.getTime());
    }

    
    public static String getFuzzyTimeDescriptionByNow(final long timestamp) {
        long currentTime = System.currentTimeMillis();
        
        long timeGap = (currentTime - timestamp) / 1000;
        String timeStr;
        long span;
        if ((span = Math.round((float) timeGap / YEAR_S)) > 0) {
            timeStr = span + " years ago";
        } else if ((span = Math.round((float) timeGap / MONTH_S)) > 0) {
            timeStr = span + " months ago";
        } else if ((span = Math.round((float) timeGap / DAY_S)) > 0) {
            timeStr = span + " days ago";
        } else if ((span = Math.round((float) timeGap / HOUR_S)) > 0) {
            timeStr = span + " hours ago";
        } else if ((span = Math.round((float) timeGap / MINUTE_S)) > 0) {
            timeStr = span + " minutes ago";
        } else {
            timeStr = "Just now";
        }
        return timeStr;
    }

    private static long timeSpan2Millis(final long timeSpan, @TimeConstants.Unit final int unit) {
        return timeSpan * unit;
    }

    private static long millis2TimeSpan(final long millis, @TimeConstants.Unit final int unit) {
        return millis / unit;
    }

    
    private static String millis2FitTimeSpan(long millis, int precision) {
        if (millis < 0 || precision <= 0) {
            return null;
        }
        precision = Math.min(precision, 5);
        String[] units = {" days ", " hours ", " minutes ", " seconds ", " milliseconds "};
        if (millis == 0) {
            return 0 + units[precision - 1];
        }
        StringBuilder sb = new StringBuilder();
        int[] unitLen = {DAY, HOUR, MIN, SEC, 1};
        for (int i = 0; i < precision; i++) {
            if (millis >= unitLen[i]) {
                long mode = millis / unitLen[i];
                millis -= mode * unitLen[i];
                sb.append(mode).append(units[i]);
            }
        }
        return sb.toString();
    }

    
    public static int getAgeByBirthDay(final String birthDay, final DateFormat format) throws IllegalArgumentException {
        return getAgeByBirthDay(DateUtils.string2Date(birthDay, format));
    }

    
    public static int getAgeByBirthDay(Date birthDay) throws IllegalArgumentException {
        Calendar cal = Calendar.getInstance();

        if (cal.before(birthDay)) {
            throw new IllegalArgumentException("The birthDay is before Now.It's unbelievable!");
        }
        int yearNow = cal.get(Calendar.YEAR);
        int monthNow = cal.get(Calendar.MONTH);
        int dayNow = cal.get(Calendar.DAY_OF_MONTH);

        cal.setTime(birthDay);
        int yearBirth = cal.get(Calendar.YEAR);
        int monthBirth = cal.get(Calendar.MONTH);
        int dayBirth = cal.get(Calendar.DAY_OF_MONTH);

        int age = yearNow - yearBirth;

        if (monthNow <= monthBirth) {
            if (monthNow == monthBirth) {
                if (dayNow < dayBirth) {
                    age--;
                }
            } else {
                age--;
            }
        }
        return age;
    }

    

    
    public static long getNowMills() {
        return System.currentTimeMillis();
    }

    
    public static String getNowString(final DateFormat format) {
        return millis2String(System.currentTimeMillis(), format);
    }

    
    public static Date getNowDate() {
        return new Date();
    }

    
    public static boolean isToday(final String time, final DateFormat format) {
        return isToday(string2Millis(time, format));
    }

    
    public static boolean isToday(final Date date) {
        return isToday(date.getTime());
    }

    
    public static boolean isToday(final long millis) {
        long wee = getWeeOfToday();
        return millis >= wee && millis < wee + DAY;
    }

    
    public static int getWeekIndex(final String time, final DateFormat format) {
        return getWeekIndex(string2Date(time, format));
    }

    
    public static int getYear(final Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.get(Calendar.YEAR);
    }

    
    public static int getMonth(final Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.get(Calendar.MONTH) + 1;
    }

    
    public static int getDay(final Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    
    public static int getWeekIndex(final Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.DAY_OF_WEEK);
    }

    
    public static int getWeekIndex(final long millis) {
        return getWeekIndex(millis2Date(millis));
    }


    

    private static final String[] CHINESE_ZODIAC =
            {"Monkey", "Rooster", "Dog", "Pig", "Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat"};

    
    public static String getChineseZodiac(final String time, final DateFormat format) {
        return getChineseZodiac(string2Date(time, format));
    }

    
    public static String getChineseZodiac(final Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return CHINESE_ZODIAC[cal.get(Calendar.YEAR) % 12];
    }

    
    public static String getChineseZodiac(final long millis) {
        return getChineseZodiac(millis2Date(millis));
    }

    
    public static String getChineseZodiac(final int year) {
        return CHINESE_ZODIAC[year % 12];
    }

    private static final int[] ZODIAC_FLAGS = {20, 19, 21, 21, 21, 22, 23, 23, 23, 24, 23, 22};
    private static final String[] ZODIAC = {
            "Aquarius", "Pisces", "Aries", "Taurus", "Gemini", "Cancer",
            "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn"
    };

    
    public static String getZodiac(final String time, final DateFormat format) {
        return getZodiac(string2Date(time, format));
    }

    
    public static String getZodiac(final Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return getZodiac(month, day);
    }

    
    public static String getZodiac(final long millis) {
        return getZodiac(millis2Date(millis));
    }

    
    public static String getZodiac(final int month, final int day) {
        return ZODIAC[day >= ZODIAC_FLAGS[month - 1]
                ? month - 1
                : (month + 10) % 12];
    }

}
