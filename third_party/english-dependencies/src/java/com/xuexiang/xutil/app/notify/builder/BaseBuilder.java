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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.app.notify.NotificationUtils;
import com.xuexiang.xutil.resource.ResUtils;

import java.util.ArrayList;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
public class BaseBuilder {
    private static final String DEFAULT_CHANNEL_ID_PREFIX = "xutil_channel_id_";
    private static final String DEFAULT_CHANNEL_NAME_PREFIX = "xutil_channel_name_";

    private NotificationCompat.Builder mBuilder;

    
    
    private int mSmallIcon;
    
    protected CharSequence mContentTitle;
    
    protected CharSequence mContentText;

    
    protected CharSequence mSummaryText;

    private boolean mHeadUp;

    
    
    private PendingIntent mContentIntent;
    
    private PendingIntent mDeleteIntent;
    /**
     *
     */
    private PendingIntent mFullscreenIntent;

    
    
    private int mId;
    
    private String mChannelId;
    
    private String mChannelName;
    
    private int mBigIcon;
    
    private CharSequence mTicker = "You have a new message";

    private CharSequence mSubText;

    
    private long mWhen;

    
    private boolean mIsShowWhen = true;

    
    private List<BtnActionBean> mBtnActionBeans;

    
    private int mPriority = NotificationCompat.PRIORITY_DEFAULT;

    
    private int mDefaults = NotificationCompat.DEFAULT_LIGHTS;
    
    private boolean mIsSound = true;
    
    private boolean mIsVibrate = true;
    
    private boolean mLights = true;

    
    private Uri mSoundUri;

    private long[] mVibratePatten;

    
    private boolean mIsOnGoing = false;

    
    private boolean mIsForeGroundService = false;

    
    private int mVisibility = NotificationCompat.VISIBILITY_SECRET;

    
    private NotificationCompat.Style mStyle;

    
    private boolean mIsPolling = false;


    public <T extends BaseBuilder> T setBaseInfo(int icon, CharSequence contentTitle, CharSequence contentText) {
        mSmallIcon = icon;
        mContentTitle = contentTitle;
        mContentText = contentText;
        return (T) this;
    }

    public <T extends BaseBuilder> T setSmallIcon(int smallIcon) {
        mSmallIcon = smallIcon;
        return (T) this;
    }

    public <T extends BaseBuilder> T setContentTitle(CharSequence contentTitle) {
        mContentTitle = contentTitle;
        return (T) this;
    }

    public <T extends BaseBuilder> T setContentText(CharSequence contentText) {
        mContentText = contentText;
        return (T) this;
    }

    public <T extends BaseBuilder> T setSummaryText(CharSequence summaryText) {
        mSummaryText = summaryText;
        return (T) this;
    }

    public <T extends BaseBuilder> T setId(int id) {
        mId = id;
        return (T) this;
    }

    public <T extends BaseBuilder> T setChannelId(String channelId) {
        mChannelId = channelId;
        return (T) this;
    }

    public <T extends BaseBuilder> T setChannelName(String channelName) {
        mChannelName = channelName;
        return (T) this;
    }

    public <T extends BaseBuilder> T setPriority(int priority) {
        mPriority = priority;
        return (T) this;
    }

    public <T extends BaseBuilder> T setContentIntent(PendingIntent contentIntent) {
        mContentIntent = contentIntent;
        return (T) this;
    }

    public <T extends BaseBuilder> T setDeleteIntent(PendingIntent deleteIntent) {
        mDeleteIntent = deleteIntent;
        return (T) this;
    }

    public <T extends BaseBuilder> T setFullScreenIntent(PendingIntent fullscreenIntent) {
        mFullscreenIntent = fullscreenIntent;
        return (T) this;
    }

    public <T extends BaseBuilder> T setBigIcon(int bigIcon) {
        mBigIcon = bigIcon;
        return (T) this;
    }

    public <T extends BaseBuilder> T setHeadUp(boolean headUp) {
        mHeadUp = headUp;
        return (T) this;
    }

    public <T extends BaseBuilder> T setTicker(CharSequence ticker) {
        mTicker = ticker;
        return (T) this;
    }

    public <T extends BaseBuilder> T setSubtext(CharSequence subText) {
        mSubText = subText;
        return (T) this;
    }

    public <T extends BaseBuilder> T setWhen(long when) {
        mWhen = when;
        return (T) this;
    }

    public <T extends BaseBuilder> T setIsShowWhen(boolean isShowWhen) {
        mIsShowWhen = isShowWhen;
        return (T) this;
    }

    
    public <T extends BaseBuilder> T addAction(int icon, CharSequence text, PendingIntent pendingIntent) {
        if (mBtnActionBeans == null) {
            mBtnActionBeans = new ArrayList<>();
        }
        if (mBtnActionBeans.size() > 5) {
            throw new RuntimeException("5 buttons at most!");
        }
        mBtnActionBeans.add(new BtnActionBean(icon, text, pendingIntent));
        return (T) this;
    }

    
    public <T extends BaseBuilder> T setDisplayForm(boolean sound, boolean vibrate, boolean lights) {
        mIsSound = sound;
        mIsVibrate = vibrate;
        mLights = lights;
        return (T) this;
    }

    public <T extends BaseBuilder> T setSoundUri(Uri soundUri) {
        mSoundUri = soundUri;
        return (T) this;
    }

    public <T extends BaseBuilder> T setVibratePatten(long[] vibratePatten) {
        mVibratePatten = vibratePatten;
        return (T) this;
    }

    public <T extends BaseBuilder> T setIsOnGoing(boolean isOnGoing) {
        mIsOnGoing = isOnGoing;
        return (T) this;
    }

    public <T extends BaseBuilder> T setForegroundService() {
        mIsForeGroundService = true;
        mIsOnGoing = true;
        return (T) this;
    }

    public <T extends BaseBuilder> T setVisibility(int visibility) {
        mVisibility = visibility;
        return (T) this;
    }

    public <T extends BaseBuilder> T setStyle(NotificationCompat.Style style) {
        mStyle = style;
        return (T) this;
    }

    public <T extends BaseBuilder> T setIsPolling(boolean isPolling) {
        mIsPolling = isPolling;
        return (T) this;
    }

    
    public void build() {
        beforeBuild();

        if (mChannelId == null) {
            mChannelId = DEFAULT_CHANNEL_ID_PREFIX + mId;
        }
        if (mChannelName == null) {
            mChannelName = DEFAULT_CHANNEL_NAME_PREFIX + mId;
        }
        mBuilder = new NotificationCompat.Builder(XUtil.getContext(), mChannelId);

        if (mSmallIcon > 0) {
            
            mBuilder.setSmallIcon(mSmallIcon);
        }
        if (mBigIcon > 0) {
            mBuilder.setLargeIcon(BitmapFactory.decodeResource(ResUtils.getResources(), mBigIcon));
        }
        
        mBuilder.setTicker(mTicker);

        
        mBuilder.setContentTitle(mContentTitle);

        
        if (!TextUtils.isEmpty(mContentText)) {
            mBuilder.setContentText(mContentText);
        }

        if (!TextUtils.isEmpty(mSubText)) {
            mBuilder.setContentText(mSubText);
        }

        if (mWhen > 0) {
            mBuilder.setWhen(mWhen);
        } else {
            mBuilder.setWhen(System.currentTimeMillis());
        }

        mBuilder.setShowWhen(mIsShowWhen);

        
        
        mBuilder.setContentIntent(mContentIntent);
        mBuilder.setDeleteIntent(mDeleteIntent);
        mBuilder.setFullScreenIntent(mFullscreenIntent, true);

        
        mBuilder.setAutoCancel(true);

        
        mBuilder.setOngoing(mIsOnGoing);
        
        mBuilder.setPriority(mPriority);

        if (mIsSound) {
            mDefaults |= Notification.DEFAULT_SOUND;
            if (mSoundUri != null) {
                mBuilder.setSound(mSoundUri);
            }
        }
        if (mIsVibrate) {
            mDefaults |= Notification.DEFAULT_VIBRATE;
            if (mVibratePatten != null) {
                mBuilder.setVibrate(mVibratePatten);
            }
        }
        if (mLights) {
            mDefaults |= Notification.DEFAULT_LIGHTS;
        }
        mBuilder.setDefaults(mDefaults);

        
        if (mBtnActionBeans != null && mBtnActionBeans.size() > 0) {
            for (BtnActionBean bean : mBtnActionBeans) {
                mBuilder.addAction(bean.icon, bean.text, bean.pendingIntent);
            }
        }

        //HeadUp
        if (mHeadUp) {
            mBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
        } else {
            mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS);
        }

        mBuilder.setVisibility(mVisibility);

        if (mStyle != null) {
            mBuilder.setStyle(mStyle);
        }

        afterBuild();
    }


    protected void beforeBuild() {

    }

    protected void afterBuild() {

    }

    
    public void show() {
        build();
        Notification notification = mBuilder.build();
        if (mIsForeGroundService) {
            notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
        }
        if (mIsPolling) {
            notification.flags |= Notification.FLAG_INSISTENT;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(mChannelId, mChannelName, NotificationManager.IMPORTANCE_HIGH);
            NotificationUtils.getManager().createNotificationChannel(channel);
        }

        NotificationUtils.notify(mId, notification);
    }


    public NotificationCompat.Builder getBuilder() {
        return mBuilder;
    }


    
    public static class BtnActionBean {
        public final int icon;
        public final CharSequence text;
        public final PendingIntent pendingIntent;

        public BtnActionBean(int icon, CharSequence text, PendingIntent pendingIntent) {
            this.icon = icon;
            this.text = text;
            this.pendingIntent = pendingIntent;
        }
    }
}
