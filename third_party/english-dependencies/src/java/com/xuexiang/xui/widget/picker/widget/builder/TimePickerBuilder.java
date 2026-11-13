// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Copyright (C) 2019 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xui.widget.picker.widget.builder;

import android.content.Context;
import android.view.ViewGroup;

import com.xuexiang.xui.utils.ThemeUtils;
import com.xuexiang.xui.widget.picker.wheelview.WheelView;
import com.xuexiang.xui.widget.picker.widget.TimePickerView;
import com.xuexiang.xui.widget.picker.widget.configure.PickerOptions;
import com.xuexiang.xui.widget.picker.widget.configure.TimePickerType;
import com.xuexiang.xui.widget.picker.widget.listener.CustomListener;
import com.xuexiang.xui.widget.picker.widget.listener.OnTimeSelectChangeListener;
import com.xuexiang.xui.widget.picker.widget.listener.OnTimeSelectListener;

import java.util.Calendar;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class TimePickerBuilder {

    private PickerOptions mPickerOptions;

    //Required
    public TimePickerBuilder(Context context, OnTimeSelectListener listener) {
        mPickerOptions = new PickerOptions(PickerOptions.TYPE_PICKER_TIME);
        mPickerOptions.context = context;
        mPickerOptions.timeSelectListener = listener;
        mPickerOptions.textColorConfirm = ThemeUtils.getMainThemeColor(context);
        mPickerOptions.textColorCancel = ThemeUtils.getMainThemeColor(context);
    }

    //Option
    public TimePickerBuilder setGravity(int gravity) {
        mPickerOptions.textGravity = gravity;
        return this;
    }

    
    public TimePickerBuilder setType(boolean... types) {
        if (types.length != 6) {
            throw new IllegalArgumentException("Visibility flags for year, month, day, hour, minute and second must have length 6!");
        }
        mPickerOptions.type = types;
        return this;
    }


    
    public TimePickerBuilder setType(TimePickerType type) {
        mPickerOptions.type = type.getType();
        return this;
    }

    public TimePickerBuilder setSubmitText(String textContentConfirm) {
        mPickerOptions.textContentConfirm = textContentConfirm;
        return this;
    }

    public TimePickerBuilder isDialog(boolean isDialog) {
        mPickerOptions.isDialog = isDialog;
        return this;
    }

    public TimePickerBuilder setCancelText(String textContentCancel) {
        mPickerOptions.textContentCancel = textContentCancel;
        return this;
    }

    public TimePickerBuilder setTitleText(String textContentTitle) {
        mPickerOptions.textContentTitle = textContentTitle;
        return this;
    }

    public TimePickerBuilder setSubmitColor(int textColorConfirm) {
        mPickerOptions.textColorConfirm = textColorConfirm;
        return this;
    }

    public TimePickerBuilder setCancelColor(int textColorCancel) {
        mPickerOptions.textColorCancel = textColorCancel;
        return this;
    }

    
    public TimePickerBuilder setDecorView(ViewGroup decorView) {
        mPickerOptions.decorView = decorView;
        return this;
    }

    public TimePickerBuilder setBgColor(int bgColorWheel) {
        mPickerOptions.bgColorWheel = bgColorWheel;
        return this;
    }

    public TimePickerBuilder setTitleBgColor(int bgColorTitle) {
        mPickerOptions.bgColorTitle = bgColorTitle;
        return this;
    }

    public TimePickerBuilder setTitleColor(int textColorTitle) {
        mPickerOptions.textColorTitle = textColorTitle;
        return this;
    }

    public TimePickerBuilder setSubCalSize(int textSizeSubmitCancel) {
        mPickerOptions.textSizeSubmitCancel = textSizeSubmitCancel;
        return this;
    }

    public TimePickerBuilder setTitleSize(int textSizeTitle) {
        mPickerOptions.textSizeTitle = textSizeTitle;
        return this;
    }

    public TimePickerBuilder setContentTextSize(int textSizeContent) {
        mPickerOptions.textSizeContent = textSizeContent;
        return this;
    }

    
    public TimePickerBuilder setDate(Calendar date) {
        mPickerOptions.date = date;
        return this;
    }

    public TimePickerBuilder setLayoutRes(int res, CustomListener customListener) {
        mPickerOptions.layoutRes = res;
        mPickerOptions.customListener = customListener;
        return this;
    }


    

    public TimePickerBuilder setRangDate(Calendar startDate, Calendar endDate) {
        mPickerOptions.startDate = startDate;
        mPickerOptions.endDate = endDate;
        return this;
    }


    
    public TimePickerBuilder setLineSpacingMultiplier(float lineSpacingMultiplier) {
        mPickerOptions.lineSpacingMultiplier = lineSpacingMultiplier;
        return this;
    }

    
    public TimePickerBuilder setDividerColor(int dividerColor) {
        mPickerOptions.dividerColor = dividerColor;
        return this;
    }

    
    public TimePickerBuilder setDividerType(WheelView.DividerType dividerType) {
        mPickerOptions.dividerType = dividerType;
        return this;
    }

    

    public TimePickerBuilder setBackgroundId(int backgroundId) {
        mPickerOptions.backgroundId = backgroundId;
        return this;
    }

    
    public TimePickerBuilder setTextColorCenter(int textColorCenter) {
        mPickerOptions.textColorCenter = textColorCenter;
        return this;
    }

    
    public TimePickerBuilder setTextColorOut(int textColorOut) {
        mPickerOptions.textColorOut = textColorOut;
        return this;
    }

    public TimePickerBuilder isCyclic(boolean cyclic) {
        mPickerOptions.cyclic = cyclic;
        return this;
    }

    public TimePickerBuilder setOutSideCancelable(boolean cancelable) {
        mPickerOptions.cancelable = cancelable;
        return this;
    }

    public TimePickerBuilder setLunarCalendar(boolean lunarCalendar) {
        mPickerOptions.isLunarCalendar = lunarCalendar;
        return this;
    }


    public TimePickerBuilder setLabel(String labelYear, String labelMonth, String labelDay, String labelHours, String labelMins, String labelSeconds) {
        mPickerOptions.labelYear = labelYear;
        mPickerOptions.labelMonth = labelMonth;
        mPickerOptions.labelDay = labelDay;
        mPickerOptions.labelHours = labelHours;
        mPickerOptions.labelMinutes = labelMins;
        mPickerOptions.labelSeconds = labelSeconds;
        return this;
    }

    
    public TimePickerBuilder setTextXOffset(int xOffsetYear, int xOffsetMonth, int xOffsetDay,
                                            int xOffsetHours, int xOffsetMinutes, int xOffsetSeconds) {
        mPickerOptions.xOffsetYear = xOffsetYear;
        mPickerOptions.xOffsetMonth = xOffsetMonth;
        mPickerOptions.xOffsetDay = xOffsetDay;
        mPickerOptions.xOffsetHours = xOffsetHours;
        mPickerOptions.xOffsetMinutes = xOffsetMinutes;
        mPickerOptions.xOffsetSeconds = xOffsetSeconds;
        return this;
    }

    public TimePickerBuilder isCenterLabel(boolean isCenterLabel) {
        mPickerOptions.isCenterLabel = isCenterLabel;
        return this;
    }

    
    public TimePickerBuilder setTimeSelectChangeListener(OnTimeSelectChangeListener listener) {
        mPickerOptions.timeSelectChangeListener = listener;
        return this;
    }

    public TimePickerView build() {
        return new TimePickerView(mPickerOptions);
    }
}
