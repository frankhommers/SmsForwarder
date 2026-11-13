// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.zzhoujay.richtext.exceptions;

import android.annotation.TargetApi;
import android.os.Build;

/**
 * Created by zhou on 2017/4/4.
 */

public class ResetImageSourceException extends RuntimeException {

    private static final String MESSAGE = "ImageHolder source may only be changed during INIT";

    public ResetImageSourceException() {
        super(MESSAGE);
    }

    public ResetImageSourceException(Throwable cause) {
        super(MESSAGE, cause);
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ResetImageSourceException(Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(MESSAGE, cause, enableSuppression, writableStackTrace);
    }
}
