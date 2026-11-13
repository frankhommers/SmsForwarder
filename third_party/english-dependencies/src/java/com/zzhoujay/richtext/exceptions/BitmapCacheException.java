// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.zzhoujay.richtext.exceptions;

/**
 * Created by zhou on 2017/10/7.
 * BitmapCacheException
 */

@SuppressWarnings("unused")
public class BitmapCacheException extends RuntimeException {

    private static final String MESSAGE = "Bitmap cache error";

    public BitmapCacheException() {
        super(MESSAGE);
    }

    public BitmapCacheException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
