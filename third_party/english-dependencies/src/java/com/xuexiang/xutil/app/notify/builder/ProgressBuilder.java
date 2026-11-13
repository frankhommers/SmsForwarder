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

package com.xuexiang.xutil.app.notify.builder;

import androidx.core.app.NotificationCompat;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public class ProgressBuilder extends BaseBuilder {

    private final static String DEFAULT_FORMAT = "Progress:%d/%d";
    
    private int mMax = 0;
    
    private int mProgress = 0;
    
    private boolean mIndeterminate = false;

    
    private String mFormat = DEFAULT_FORMAT;

    public ProgressBuilder setProgress(int max, int progress) {
        mMax = max;
        mProgress = progress;
        setContentText(String.format(DEFAULT_FORMAT, mProgress, mMax));
        return this;
    }

    public ProgressBuilder setMaxProgress(int max) {
        mMax = max;
        return this;
    }

    public ProgressBuilder setIndeterminate(boolean indeterminate) {
        mIndeterminate = indeterminate;
        if (mIndeterminate) {
            mMax = 0;
            mProgress = 0;
            setContentText(null);
        }
        return this;
    }

    public ProgressBuilder setFormat(String format) {
        mFormat = format;
        return this;
    }

    
    public void updateProgress(int progress, String format, Object... args) {
        mProgress = progress;
        mFormat = format;
        setContentText(String.format(mFormat, args));
    }

    
    public void updateProgress(int progress) {
        mProgress = progress;
        setContentText(String.format(DEFAULT_FORMAT, mProgress, mMax));
    }

    @Override
    protected void afterBuild() {
        updateProgress();
        getBuilder().setDefaults(0);
        getBuilder().setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private void updateProgress() {
        getBuilder().setProgress(mMax, mProgress, mIndeterminate);
    }


}
