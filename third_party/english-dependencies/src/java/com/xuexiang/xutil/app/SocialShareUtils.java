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

package com.xuexiang.xutil.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.xuexiang.xutil.file.FileUtils;
import com.xuexiang.xutil.tip.ToastUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public final class SocialShareUtils {

    private SocialShareUtils() {
        throw new Error("Do not need instantiate!");
    }

    
    public enum ShareType {
        
        DEFAULT,

        
        WE_CHAT_CONTACTS,

        
        WE_CHAT_CIRCLE,

    }

    
    @NonNull
    private static ArrayList<Uri> getMediaUrisFromPaths(List<String> filePaths) {
        ArrayList<Uri> fileUris = new ArrayList<>();
        for (String filePath : filePaths) {
            Uri uri = PathUtils.getMediaContentUri(FileUtils.getFileByPath(filePath));
            if (uri != null) {
                fileUris.add(uri);
            }
        }
        return fileUris;
    }

    
    
    public static void shareMultiplePicture(Activity activity, String... filePaths) {
        shareMultiplePicture(activity, Arrays.asList(filePaths));
    }

    
    public static void shareMultiplePicture(Activity activity, List<String> filePaths) {
        if (!shareMultiplePictureForResult(activity, filePaths)) {
            ToastUtils.toast("No sharing application found!");
        }
    }

    
    public static void shareMultiplePictureToWeChatCircle(Activity activity, String description, String... filePaths) {
        shareMultiplePictureToWeChatCircle(activity, description, Arrays.asList(filePaths));
    }

    
    public static void shareMultiplePictureToWeChatCircle(Activity activity, String description, List<String> filePaths) {
        if (!shareMultiplePictureToWeChatCircleForResult(activity, description, filePaths)) {
            ToastUtils.toast("WeChat is not installed; sharing to WeChat is unavailable!");
        }
    }

    
    public static void shareMultiplePictureToWeChatContacts(Activity activity, String description, String... filePaths) {
        shareMultiplePictureToWeChatContacts(activity, description, Arrays.asList(filePaths));
    }

    
    public static void shareMultiplePictureToWeChatContacts(Activity activity, String description, List<String> filePaths) {
        if (!shareMultiplePictureToWeChatContactsForResult(activity, description, filePaths)) {
            ToastUtils.toast("WeChat is not installed; sharing to WeChat is unavailable!");
        }
    }

    //================================//


    
    public static boolean shareMultiplePictureForResult(Activity activity, String... filePaths) {
        return shareMultiplePictureForResult(activity, Arrays.asList(filePaths));
    }

    
    public static boolean shareMultiplePictureForResult(Activity activity, List<String> filePaths) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(IntentUtils.DocumentType.IMAGE);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, getMediaUrisFromPaths(filePaths));
        return ActivityUtils.startActivity(activity, Intent.createChooser(intent, "Share to"));
    }

    
    public static boolean shareMultiplePictureToWeChatCircleForResult(Activity activity, String description, String... filePaths) {
        return shareMultiplePictureToWeChatCircleForResult(activity, description, Arrays.asList(filePaths));
    }

    
    public static boolean shareMultiplePictureToWeChatCircleForResult(Activity activity, String description, List<String> filePaths) {
        Intent intent = new Intent();
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareToTimeLineUI");
        intent.setComponent(comp);
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(IntentUtils.DocumentType.IMAGE);

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, getMediaUrisFromPaths(filePaths));
        intent.putExtra("Kdescription", description);
        return ActivityUtils.startActivity(activity, Intent.createChooser(intent, "Share to"));
    }

    
    public static boolean shareMultiplePictureToWeChatContactsForResult(Activity activity, String description, String... filePaths) {
        return shareMultiplePictureToWeChatContactsForResult(activity, description, Arrays.asList(filePaths));
    }

    
    public static boolean shareMultiplePictureToWeChatContactsForResult(Activity activity, String description, List<String> filePaths) {
        Intent intent = new Intent();
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        intent.setComponent(comp);
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(IntentUtils.DocumentType.IMAGE);

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, getMediaUrisFromPaths(filePaths));
        intent.putExtra("Kdescription", description);
        return ActivityUtils.startActivity(activity, Intent.createChooser(intent, "Share to"));
    }

    

    
    public static void sharePicture(Activity activity, final Uri imgUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                sharePicture(activity, imgUri);
                break;
            case WE_CHAT_CIRCLE:
                sharePictureToWeChatCircle(activity, imgUri);
                break;
            case WE_CHAT_CONTACTS:
                sharePictureToWeChatContacts(activity, imgUri);
                break;
            default:
                break;
        }
    }

    
    public static void sharePictureToWeChatCircle(Activity activity, final Uri imgUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareToTimeLineUI");
        shareFile(activity, comp, imgUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static void sharePictureToWeChatContacts(Activity activity, final Uri imgUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        shareFile(activity, comp, imgUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static void sharePicture(Activity activity, final File picture) {
        shareFile(activity, null, picture, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static void sharePicture(Activity activity, final Uri pictureUri) {
        shareFile(activity, null, pictureUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    //=======================================//

    
    public static boolean sharePictureForResult(Activity activity, final Uri imgUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                return sharePictureForResult(activity, imgUri);
            case WE_CHAT_CIRCLE:
                return sharePictureToWeChatCircleForResult(activity, imgUri);
            case WE_CHAT_CONTACTS:
                return sharePictureToWeChatContactsForResult(activity, imgUri);
            default:
                break;
        }
        return false;
    }

    
    public static boolean sharePictureToWeChatCircleForResult(Activity activity, final Uri imgUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareToTimeLineUI");
        return shareFileForResult(activity, comp, imgUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static boolean sharePictureToWeChatContactsForResult(Activity activity, final Uri imgUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        return shareFileForResult(activity, comp, imgUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static boolean sharePictureForResult(Activity activity, final File picture) {
        return shareFileForResult(activity, null, picture, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    
    public static boolean sharePictureForResult(Activity activity, final Uri pictureUri) {
        return shareFileForResult(activity, null, pictureUri, IntentUtils.DocumentType.IMAGE, "Share image");
    }

    

    
    public static void shareVideo(Activity activity, final Uri videoUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                shareVideo(activity, videoUri);
                break;
            case WE_CHAT_CIRCLE:
                ToastUtils.toast("WeChat Moments only supports image sharing!");
                break;
            case WE_CHAT_CONTACTS:
                shareVideoToWeChatContacts(activity, videoUri);
                break;
            default:
                break;
        }
    }

    
    public static void shareVideoToWeChatContacts(Activity activity, final Uri videoUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        shareFile(activity, comp, videoUri, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    
    public static void shareVideo(Activity activity, final File videoFile) {
        shareFile(activity, null, videoFile, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    
    public static void shareVideo(Activity activity, final Uri videoUri) {
        shareFile(activity, null, videoUri, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    //============================//

    
    public static boolean shareVideoForResult(Activity activity, final Uri videoUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                return shareVideoForResult(activity, videoUri);
            case WE_CHAT_CIRCLE:
                break;
            case WE_CHAT_CONTACTS:
                return shareVideoToWeChatContactsForResult(activity, videoUri);
            default:
                break;
        }
        return false;
    }

    
    public static boolean shareVideoToWeChatContactsForResult(Activity activity, final Uri videoUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        return shareFileForResult(activity, comp, videoUri, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    
    public static boolean shareVideoForResult(Activity activity, final File videoFile) {
        return shareFileForResult(activity, null, videoFile, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    
    public static boolean shareVideoForResult(Activity activity, final Uri videoUri) {
        return shareFileForResult(activity, null, videoUri, IntentUtils.DocumentType.VIDEO, "Share video");
    }

    

    
    public static void shareFile(Activity activity, final Uri fileUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                shareFile(activity, fileUri);
                break;
            case WE_CHAT_CIRCLE:
                ToastUtils.toast("WeChat Moments only supports image sharing!");
                break;
            case WE_CHAT_CONTACTS:
                shareFileToWeChatContacts(activity, fileUri);
                break;
            default:
                break;
        }
    }

    
    public static void shareFileToWeChatContacts(Activity activity, final Uri fileUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        shareFile(activity, comp, fileUri, IntentUtils.DocumentType.ANY, "Share file");
    }


    
    public static void shareFile(Activity activity, final Uri fileUri) {
        shareFile(activity, null, fileUri, IntentUtils.DocumentType.ANY, "Share file");
    }

    
    public static void shareFile(Activity activity, ComponentName componentName, @NonNull final File fileToShare, final @IntentUtils.DocumentType String mimeTypeForFile, final String subjectTextToShare) {
        shareFile(activity, componentName, PathUtils.getUriForFile(fileToShare), mimeTypeForFile, subjectTextToShare);
    }


    
    public static void shareFile(Activity activity, ComponentName componentName, final Uri fileUri, @NonNull final @IntentUtils.DocumentType String mimeTypeForFile, @NonNull final String subjectTextToShare) {
        if (!shareFileForResult(activity, componentName, fileUri, mimeTypeForFile, subjectTextToShare)) {
            ToastUtils.toast("No sharing application found!");
        }
    }

    //============================//

    
    public static boolean shareFileForResult(Activity activity, final Uri fileUri, ShareType shareType) {
        switch (shareType) {
            case DEFAULT:
                return shareFileForResult(activity, fileUri);
            case WE_CHAT_CIRCLE:
                break;
            case WE_CHAT_CONTACTS:
                return shareFileToWeChatContactsForResult(activity, fileUri);
            default:
                break;
        }
        return false;
    }

    
    public static boolean shareFileToWeChatContactsForResult(Activity activity, final Uri fileUri) {
        ComponentName comp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI");
        return shareFileForResult(activity, comp, fileUri, IntentUtils.DocumentType.ANY, "Share file");
    }

    
    public static boolean shareFileForResult(Activity activity, final Uri fileUri) {
        return shareFileForResult(activity, null, fileUri, IntentUtils.DocumentType.ANY, "Share file");
    }

    
    public static boolean shareFileForResult(Activity activity, ComponentName componentName, @NonNull final File fileToShare, final @IntentUtils.DocumentType String mimeTypeForFile, final String subjectTextToShare) {
        return shareFileForResult(activity, componentName, PathUtils.getUriForFile(fileToShare), mimeTypeForFile, subjectTextToShare);
    }

    
    public static boolean shareFileForResult(Activity activity, ComponentName componentName, final Uri fileUri, @NonNull final @IntentUtils.DocumentType String mimeTypeForFile, @NonNull final String subjectTextToShare) {
        Intent intent = new Intent();
        if (componentName != null) {
            intent.setComponent(componentName);
        }
        intent.setAction(Intent.ACTION_SEND);
        intent.setType(mimeTypeForFile);
        intent.putExtra(Intent.EXTRA_SUBJECT, subjectTextToShare);
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        return ActivityUtils.startActivity(activity, Intent.createChooser(intent, "Share to"));
    }

}
