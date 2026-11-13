// Modified for English diagnostics and labels by SmsForwarder Private contributors.
/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xui.widget.textview.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class AlignMiddleImageSpan extends ImageSpan {

    
    public static final int ALIGN_MIDDLE = -100;

    
    private float mFontWidthMultiple = -1f;

    
    private boolean mAvoidSuperChangeFontMetrics = false;

    @SuppressWarnings("FieldCanBeLocal")
    private int mWidth;

    
    public AlignMiddleImageSpan(Drawable d, int verticalAlignment) {
        super(d, verticalAlignment);
    }

    
    public AlignMiddleImageSpan(Drawable d, int verticalAlignment, float fontWidthMultiple) {
        this(d, verticalAlignment);
        if (fontWidthMultiple >= 0) {
            mFontWidthMultiple = fontWidthMultiple;
        }
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (mAvoidSuperChangeFontMetrics) {
            Drawable d = getDrawable();
            Rect rect = d.getBounds();
            mWidth = rect.right;
        } else {
            mWidth = super.getSize(paint, text, start, end, fm);
        }
        if (mFontWidthMultiple > 0) {
            mWidth = (int) (paint.measureText("M") * mFontWidthMultiple);
        }
        return mWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (mVerticalAlignment == ALIGN_MIDDLE) {
            Drawable d = getDrawable();
            canvas.save();




//            canvas.translate(x, transY);
//            d.draw(canvas);
//            canvas.restore();

            Paint.FontMetricsInt fontMetricsInt = paint.getFontMetricsInt();
            int fontTop = y + fontMetricsInt.top;
            int fontMetricsHeight = fontMetricsInt.bottom - fontMetricsInt.top;
            int iconHeight = d.getBounds().bottom - d.getBounds().top;
            int iconTop = fontTop + (fontMetricsHeight - iconHeight) / 2;
            canvas.translate(x, iconTop);
            d.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }
    }

    
    public void setAvoidSuperChangeFontMetrics(boolean avoidSuperChangeFontMetrics) {
        mAvoidSuperChangeFontMetrics = avoidSuperChangeFontMetrics;
    }
}
