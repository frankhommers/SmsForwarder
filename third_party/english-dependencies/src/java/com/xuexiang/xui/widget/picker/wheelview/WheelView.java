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

package com.xuexiang.xui.widget.picker.wheelview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import com.xuexiang.xui.R;
import com.xuexiang.xui.logs.UILog;
import com.xuexiang.xui.widget.picker.wheelview.adapter.WheelAdapter;
import com.xuexiang.xui.widget.picker.wheelview.interfaces.IPickerViewItem;
import com.xuexiang.xui.widget.picker.wheelview.listener.LoopViewGestureListener;
import com.xuexiang.xui.widget.picker.wheelview.listener.OnItemSelectedListener;
import com.xuexiang.xui.widget.picker.wheelview.timer.InertiaTimerTask;
import com.xuexiang.xui.widget.picker.wheelview.timer.MessageHandler;
import com.xuexiang.xui.widget.picker.wheelview.timer.SmoothScrollTimerTask;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.inflationx.calligraphy3.HasTypeface;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class WheelView extends View implements HasTypeface {

    public enum ACTION { 
        CLICK, FLING, DAGGLE
    }

    public enum DividerType { 
        FILL, WRAP
    }

    
    private DividerType dividerType;

    private Context context;
    private Handler handler;
    private GestureDetector gestureDetector;
    private OnItemSelectedListener onItemSelectedListener;

    private boolean isOptions = false;
    private boolean isCenterLabel = true;

    private ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> mFuture;

    private TextPaint paintOuterText;
    private TextPaint paintCenterText;
    private Paint paintIndicator;

    private WheelAdapter adapter;

    
    private String label;
    
    private int textSize;
    private int maxTextWidth;
    private int maxTextHeight;
    private int textXOffset;
    private float itemHeight;


    
    private Typeface typeface = Typeface.MONOSPACE;
    private int textColorOut;
    private int textColorCenter;
    private int dividerColor;

    
    private float lineSpacingMultiplier = 1.6F;
    private boolean isLoop;

    
    private float firstLineY;
    
    private float secondLineY;
    
    private float centerY;

    
    private float totalScrollY;

    
    private int initPosition;

    
    private int selectedItem;
    private int preCurrentIndex;

    
    private int itemsVisible = 11;

    private int measuredHeight;
    private int measuredWidth;

    
    private int radius;

    private int mOffset = 0;
    private float previousY = 0;
    private long startTime = 0;

    
    private static final int VELOCITY_FLING = 5;
    private int widthMeasureSpec;

    private int mGravity = Gravity.CENTER;
    private int drawCenterContentStart = 0;
    private int drawOutContentStart = 0;
    private static final float SCALE_CONTENT = 0.8F;
    private float CENTER_CONTENT_OFFSET;

    private static final float DEFAULT_TEXT_TARGET_SKEWX = 0.5f;

    public WheelView(Context context) {
        this(context, null);
    }

    public WheelView(Context context, AttributeSet attrs) {
        super(context, attrs);

        
        textSize = getResources().getDimensionPixelSize(R.dimen.default_wheel_view_text_size);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        
        float density = dm.density;

        
        if (density < 1F) {
            CENTER_CONTENT_OFFSET = 2.4F;
        } else if (1F <= density && density < 1.5F) {
            CENTER_CONTENT_OFFSET = 3.6F;
        } else if (1.5F <= density && density < 2F) {
            CENTER_CONTENT_OFFSET = 4.5F;
        } else if (2F <= density && density < 3F) {
            CENTER_CONTENT_OFFSET = 6.0F;
        } else if (density >= 3F) {
            CENTER_CONTENT_OFFSET = density * 2.5F;
        }

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WheelView, 0, 0);
        mGravity = typedArray.getInt(R.styleable.WheelView_wv_gravity, Gravity.CENTER);
        textColorOut = typedArray.getColor(R.styleable.WheelView_wv_textColorOut, 0xFFA8A8A8);
        textColorCenter = typedArray.getColor(R.styleable.WheelView_wv_textColorCenter, 0xFF2A2A2A);
        dividerColor = typedArray.getColor(R.styleable.WheelView_wv_dividerColor, 0xFFD5D5D5);
        textSize = typedArray.getDimensionPixelSize(R.styleable.WheelView_wv_textSize, textSize);
        lineSpacingMultiplier = typedArray.getFloat(R.styleable.WheelView_wv_lineSpacingMultiplier, lineSpacingMultiplier);
        typedArray.recycle();

        judgeLineSpace();
        initLoopView(context);
    }

    
    private void judgeLineSpace() {
        if (lineSpacingMultiplier < 1.0f) {
            lineSpacingMultiplier = 1.0f;
        } else if (lineSpacingMultiplier > 4.0f) {
            lineSpacingMultiplier = 4.0f;
        }
    }

    private void initLoopView(Context context) {
        this.context = context;
        handler = new MessageHandler(this);
        gestureDetector = new GestureDetector(context, new LoopViewGestureListener(this));
        gestureDetector.setIsLongpressEnabled(false);
        isLoop = true;

        totalScrollY = 0;
        initPosition = -1;
        initPaints();
    }

    private void initPaints() {
        paintOuterText = new TextPaint();
        paintOuterText.setColor(textColorOut);
        paintOuterText.setAntiAlias(true);
        paintOuterText.setTypeface(typeface);
        paintOuterText.setTextSize(textSize);

        paintCenterText = new TextPaint();
        paintCenterText.setColor(textColorCenter);
        paintCenterText.setAntiAlias(true);
        paintCenterText.setTextScaleX(1.1F);
        paintCenterText.setTypeface(typeface);
        paintCenterText.setTextSize(textSize);

        paintIndicator = new Paint();
        paintIndicator.setColor(dividerColor);
        paintIndicator.setAntiAlias(true);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private void remeasure() {
        if (adapter == null) {
            return;
        }

        measureTextWidthHeight();

        
        int halfCircumference = (int) (itemHeight * (itemsVisible - 1));
        
        measuredHeight = (int) ((halfCircumference * 2) / Math.PI);
        
        radius = (int) (halfCircumference / Math.PI);
        
        measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        
        firstLineY = (measuredHeight - itemHeight) / 2.0F;
        secondLineY = (measuredHeight + itemHeight) / 2.0F;
        centerY = secondLineY - (itemHeight - maxTextHeight) / 2.0f - CENTER_CONTENT_OFFSET;

        
        if (initPosition == -1) {
            if (isLoop) {
                initPosition = (adapter.getItemsCount() + 1) / 2;
            } else {
                initPosition = 0;
            }
        }
        preCurrentIndex = initPosition;
    }

    
    private void measureTextWidthHeight() {
        Rect rect = new Rect();
        for (int i = 0; i < adapter.getItemsCount(); i++) {
            String s1 = getContentText(adapter.getItem(i));
            paintCenterText.getTextBounds(s1, 0, s1.length(), rect);

            int textWidth = rect.width();

            if (textWidth > maxTextWidth) {
                maxTextWidth = textWidth;
            }
            paintCenterText.getTextBounds("Wk", 0, 2, rect); 

            maxTextHeight = rect.height() + 2;

        }
        itemHeight = lineSpacingMultiplier * maxTextHeight;
    }

    public void smoothScroll(ACTION action) {
        cancelFuture();
        if (action == ACTION.FLING || action == ACTION.DAGGLE) {
            mOffset = (int) ((totalScrollY % itemHeight + itemHeight) % itemHeight);
            if ((float) mOffset > itemHeight / 2.0F) {
                mOffset = (int) (itemHeight - (float) mOffset);
            } else {
                mOffset = -mOffset;
            }
        }
        
        mFuture = mExecutor.scheduleWithFixedDelay(new SmoothScrollTimerTask(this, mOffset), 0, 10, TimeUnit.MILLISECONDS);
    }

    public final void scrollBy(float velocityY) {
        cancelFuture();
        mFuture = mExecutor.scheduleWithFixedDelay(new InertiaTimerTask(this, velocityY), 0, VELOCITY_FLING, TimeUnit.MILLISECONDS);
    }

    public void cancelFuture() {
        if (mFuture != null && !mFuture.isCancelled()) {
            mFuture.cancel(true);
            mFuture = null;
        }
    }

    
    public final void setCyclic(boolean cyclic) {
        isLoop = cyclic;
    }

    @Override
    public final void setTypeface(Typeface font) {
        typeface = font;
        paintOuterText.setTypeface(typeface);
        paintCenterText.setTypeface(typeface);
    }

    public final void setTextSize(float size) {
        if (size > 0.0F) {
            textSize = (int) (context.getResources().getDisplayMetrics().density * size);
            paintOuterText.setTextSize(textSize);
            paintCenterText.setTextSize(textSize);
        }
    }

    public final void setCurrentItem(int currentItem) {
        
        this.selectedItem = currentItem;
        this.initPosition = currentItem;
        totalScrollY = 0;
        invalidate();
    }

    public final void setOnItemSelectedListener(OnItemSelectedListener OnItemSelectedListener) {
        this.onItemSelectedListener = OnItemSelectedListener;
    }

    public final void setAdapter(WheelAdapter adapter) {
        this.adapter = adapter;
        boolean isNeedRelayout = measuredHeight <= 0;
        remeasure();
        if (isNeedRelayout) {
            requestLayout();
        } else {
            invalidate();
        }
    }

    public final WheelAdapter getAdapter() {
        return adapter;
    }

    public final int getCurrentItem() {
        // return selectedItem;
        if (adapter == null) {
            return 0;
        }
        if (isLoop && (selectedItem < 0 || selectedItem >= adapter.getItemsCount())) {
            return Math.max(0, Math.min(Math.abs(Math.abs(selectedItem) - adapter.getItemsCount()), adapter.getItemsCount() - 1));
        }
        return Math.max(0, Math.min(selectedItem, adapter.getItemsCount() - 1));
    }

    public final void onItemSelected() {
        if (onItemSelectedListener != null) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    onItemSelectedListener.onItemSelected(getCurrentItem());
                }
            }, 200L);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (adapter == null) {
            return;
        }
        
        initPosition = Math.min(Math.max(0, initPosition), adapter.getItemsCount() - 1);

        
        @SuppressLint("DrawAllocation")
        Object[] visibles = new Object[itemsVisible];
        
        
        int change = (int) (totalScrollY / itemHeight);

        try {
            
            preCurrentIndex = initPosition + change % adapter.getItemsCount();

        } catch (ArithmeticException e) {
            UILog.eTag("WheelView", "adapter.getItemsCount() == 0; linked data does not match");
        }
        if (!isLoop) {
            if (preCurrentIndex < 0) {
                preCurrentIndex = 0;
            }
            if (preCurrentIndex > adapter.getItemsCount() - 1) {
                preCurrentIndex = adapter.getItemsCount() - 1;
            }
        } else {
            if (preCurrentIndex < 0) {
                preCurrentIndex = adapter.getItemsCount() + preCurrentIndex;
            }
            if (preCurrentIndex > adapter.getItemsCount() - 1) {
                preCurrentIndex = preCurrentIndex - adapter.getItemsCount();
            }
        }
        
        float itemHeightOffset = (totalScrollY % itemHeight);

        
        int counter = 0;
        while (counter < itemsVisible) {
            int index = preCurrentIndex - (itemsVisible / 2 - counter);
            
            if (isLoop) {
                index = getLoopMappingIndex(index);
                visibles[counter] = adapter.getItem(index);
            } else if (index < 0) {
                visibles[counter] = "";
            } else if (index > adapter.getItemsCount() - 1) {
                visibles[counter] = "";
            } else {
                visibles[counter] = adapter.getItem(index);
            }

            counter++;

        }

        
        if (dividerType == DividerType.WRAP) {
            float startX;
            float endX;

            if (TextUtils.isEmpty(label)) {
                startX = (measuredWidth - maxTextWidth) / 2F - 12;
            } else {
                startX = (measuredWidth - maxTextWidth) / 4F - 12;
            }

            if (startX <= 0) {
                startX = 10;
            }
            endX = measuredWidth - startX;
            canvas.drawLine(startX, firstLineY, endX, firstLineY, paintIndicator);
            canvas.drawLine(startX, secondLineY, endX, secondLineY, paintIndicator);
        } else {
            canvas.drawLine(0.0F, firstLineY, measuredWidth, firstLineY, paintIndicator);
            canvas.drawLine(0.0F, secondLineY, measuredWidth, secondLineY, paintIndicator);
        }

        
        if (!TextUtils.isEmpty(label) && isCenterLabel) {
            
            int drawRightContentStart = measuredWidth - getTextWidth(paintCenterText, label);
            canvas.drawText(label, drawRightContentStart - CENTER_CONTENT_OFFSET, centerY, paintCenterText);
        }

        counter = 0;
        while (counter < itemsVisible) {
            canvas.save();
            
            
            double radian = ((itemHeight * counter - itemHeightOffset)) / radius;
            
            // angle [-90°,90°]
            float angle = (float) (90D - (radian / Math.PI) * 180D);

            
            if (angle >= 90F || angle <= -90F) {
                canvas.restore();
            } else {
                
                float offsetCoefficient = (float) Math.pow(Math.abs(angle) / 90f, 2.2);
                
                String contentText;

                
                if (!isCenterLabel && !TextUtils.isEmpty(label) && !TextUtils.isEmpty(getContentText(visibles[counter]))) {
                    contentText = getContentText(visibles[counter]) + label;
                } else {
                    contentText = getContentText(visibles[counter]);
                }

                reMeasureTextSize(contentText);
                
                measuredCenterContentStart(contentText);
                measuredOutContentStart(contentText);
                float translateY = (float) (radius - Math.cos(radian) * radius - (Math.sin(radian) * maxTextHeight) / 2D);
                
                canvas.translate(0.0F, translateY);
//                canvas.scale(1.0F, (float) Math.sin(radian));
                if (translateY <= firstLineY && maxTextHeight + translateY >= firstLineY) {
                    
                    canvas.save();
                    canvas.clipRect(0, 0, measuredWidth, firstLineY - translateY);
                    canvas.scale(1.0F, (float) Math.sin(radian) * SCALE_CONTENT);
                    canvas.drawText(contentText, drawOutContentStart, maxTextHeight, paintOuterText);
                    canvas.restore();
                    canvas.save();
                    canvas.clipRect(0, firstLineY - translateY, measuredWidth, (int) (itemHeight));
                    canvas.scale(1.0F, (float) Math.sin(radian) * 1.0F);
                    canvas.drawText(contentText, drawCenterContentStart, maxTextHeight - CENTER_CONTENT_OFFSET, paintCenterText);
                    canvas.restore();
                } else if (translateY <= secondLineY && maxTextHeight + translateY >= secondLineY) {
                    
                    canvas.save();
                    canvas.clipRect(0, 0, measuredWidth, secondLineY - translateY);
                    canvas.scale(1.0F, (float) Math.sin(radian) * 1.0F);
                    canvas.drawText(contentText, drawCenterContentStart, maxTextHeight - CENTER_CONTENT_OFFSET, paintCenterText);
                    canvas.restore();
                    canvas.save();
                    canvas.clipRect(0, secondLineY - translateY, measuredWidth, (int) (itemHeight));
                    canvas.scale(1.0F, (float) Math.sin(radian) * SCALE_CONTENT);
                    canvas.drawText(contentText, drawOutContentStart, maxTextHeight, paintOuterText);
                    canvas.restore();
                } else if (translateY >= firstLineY && maxTextHeight + translateY <= secondLineY) {
                    
                    // canvas.clipRect(0, 0, measuredWidth, maxTextHeight);
                    
                    float Y = maxTextHeight - CENTER_CONTENT_OFFSET;
                    canvas.drawText(contentText, drawCenterContentStart, Y, paintCenterText);

                    
                    selectedItem = preCurrentIndex - (itemsVisible / 2 - counter);

                } else {
                    
                    canvas.save();
                    canvas.clipRect(0, 0, measuredWidth, (int) (itemHeight));
                    canvas.scale(1.0F, (float) Math.sin(radian) * SCALE_CONTENT);
                    
                    paintOuterText.setTextSkewX((textXOffset == 0 ? 0 : (textXOffset > 0 ? 1 : -1)) * (angle > 0 ? -1 : 1) * DEFAULT_TEXT_TARGET_SKEWX * offsetCoefficient);
                    
                    paintOuterText.setAlpha((int) ((1 - offsetCoefficient) * 255));
                    
                    canvas.drawText(contentText, drawOutContentStart + textXOffset * offsetCoefficient, maxTextHeight, paintOuterText);
                    canvas.restore();
                }
                canvas.restore();
                paintCenterText.setTextSize(textSize);
            }
            counter++;
        }
    }

    /**
     * reset the size of the text Let it can fully display
     *
     * @param contentText item text content.
     */
    private void reMeasureTextSize(String contentText) {
        Rect rect = new Rect();
        paintCenterText.getTextBounds(contentText, 0, contentText.length(), rect);
        int width = rect.width();
        int size = textSize;
        while (width > measuredWidth) {
            size--;
            
            paintCenterText.setTextSize(size);
            paintCenterText.getTextBounds(contentText, 0, contentText.length(), rect);
            width = rect.width();
        }
        
        paintOuterText.setTextSize(size);
    }


    
    private int getLoopMappingIndex(int index) {
        if (index < 0) {
            index = index + adapter.getItemsCount();
            index = getLoopMappingIndex(index);
        } else if (index > adapter.getItemsCount() - 1) {
            index = index - adapter.getItemsCount();
            index = getLoopMappingIndex(index);
        }
        return index;
    }

    
    private String getContentText(Object item) {
        if (item == null) {
            return "";
        } else if (item instanceof IPickerViewItem) {
            return ((IPickerViewItem) item).getPickerViewText();
        } else if (item instanceof Integer) {
            
            return String.format(Locale.getDefault(), "%02d", item);
        }
        return item.toString();
    }

    private void measuredCenterContentStart(String content) {
        Rect rect = new Rect();
        paintCenterText.getTextBounds(content, 0, content.length(), rect);
        switch (mGravity) {
            case Gravity.CENTER:
                
                if (isOptions || label == null || "".equals(label) || !isCenterLabel) {
                    drawCenterContentStart = (int) ((measuredWidth - rect.width()) * 0.5);
                } else {
                    
                    drawCenterContentStart = (int) ((measuredWidth - rect.width()) * 0.25);
                }
                break;
            case Gravity.LEFT:
                drawCenterContentStart = 0;
                break;
            case Gravity.RIGHT:
                
                drawCenterContentStart = measuredWidth - rect.width() - (int) CENTER_CONTENT_OFFSET;
                break;
            default:
                break;
        }
    }

    private void measuredOutContentStart(String content) {
        Rect rect = new Rect();
        paintOuterText.getTextBounds(content, 0, content.length(), rect);
        switch (mGravity) {
            case Gravity.CENTER:
                if (isOptions || label == null || "".equals(label) || !isCenterLabel) {
                    drawOutContentStart = (int) ((measuredWidth - rect.width()) * 0.5);
                } else {
                    drawOutContentStart = (int) ((measuredWidth - rect.width()) * 0.25);
                }
                break;
            case Gravity.LEFT:
                drawOutContentStart = 0;
                break;
            case Gravity.RIGHT:
                drawOutContentStart = measuredWidth - rect.width() - (int) CENTER_CONTENT_OFFSET;
                break;
            default:
                break;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        this.widthMeasureSpec = widthMeasureSpec;
        remeasure();
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean eventConsumed = gestureDetector.onTouchEvent(event);
        boolean isIgnore = false;

        float top = -initPosition * itemHeight;
        float bottom = (adapter.getItemsCount() - 1 - initPosition) * itemHeight;
        float ratio = 0.25f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startTime = System.currentTimeMillis();
                cancelFuture();
                previousY = event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                float dy = previousY - event.getRawY();
                previousY = event.getRawY();
                totalScrollY = totalScrollY + dy;

                
                if (!isLoop) {
                    if ((totalScrollY - itemHeight * ratio < top && dy < 0)
                            || (totalScrollY + itemHeight * ratio > bottom && dy > 0)) {
                        
                        totalScrollY -= dy;
                        isIgnore = true;
                    } else {
                        isIgnore = false;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            default:
                if (!eventConsumed) {

                    

                    float y = event.getY();
                    double L = Math.acos((radius - y) / radius) * radius;
                    
                    int circlePosition = (int) ((L + itemHeight / 2) / itemHeight);
                    float extraOffset = (totalScrollY % itemHeight + itemHeight) % itemHeight;
                    
                    mOffset = (int) ((circlePosition - itemsVisible / 2) * itemHeight - extraOffset);

                    if ((System.currentTimeMillis() - startTime) > 120) {
                        
                        smoothScroll(ACTION.DAGGLE);
                    } else {
                        
                        smoothScroll(ACTION.CLICK);
                    }
                }
                break;
        }
        if (!isIgnore && event.getAction() != MotionEvent.ACTION_DOWN) {
            invalidate();
        }
        return true;
    }

    public int getItemsCount() {
        return adapter != null ? adapter.getItemsCount() : 0;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void isCenterLabel(boolean isCenterLabel) {
        this.isCenterLabel = isCenterLabel;
    }

    public void setGravity(int gravity) {
        this.mGravity = gravity;
    }

    public int getTextWidth(Paint paint, String str) { //calculate text width
        int iRet = 0;
        if (str != null && str.length() > 0) {
            int len = str.length();
            float[] widths = new float[len];
            paint.getTextWidths(str, widths);
            for (int j = 0; j < len; j++) {
                iRet += (int) Math.ceil(widths[j]);
            }
        }
        return iRet;
    }

    public void setIsOptions(boolean options) {
        isOptions = options;
    }

    public void setTextColorOut(int textColorOut) {

        this.textColorOut = textColorOut;
        paintOuterText.setColor(this.textColorOut);
    }

    public void setTextColorCenter(int textColorCenter) {
        this.textColorCenter = textColorCenter;
        paintCenterText.setColor(this.textColorCenter);
    }

    public void setTextXOffset(int textXOffset) {
        this.textXOffset = textXOffset;
        if (textXOffset != 0) {
            paintCenterText.setTextScaleX(1.0f);
        }
    }

    public void setDividerColor(int dividerColor) {
        this.dividerColor = dividerColor;
        paintIndicator.setColor(dividerColor);
    }

    public void setDividerType(DividerType dividerType) {
        this.dividerType = dividerType;
    }

    public void setLineSpacingMultiplier(float lineSpacingMultiplier) {
        if (lineSpacingMultiplier != 0) {
            this.lineSpacingMultiplier = lineSpacingMultiplier;
            judgeLineSpace();
        }
    }

    public boolean isLoop() {
        return isLoop;
    }

    public float getTotalScrollY() {
        return totalScrollY;
    }

    public void setTotalScrollY(float totalScrollY) {
        this.totalScrollY = totalScrollY;
    }

    public float getItemHeight() {
        return itemHeight;
    }

    public int getInitPosition() {
        return initPosition;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

}