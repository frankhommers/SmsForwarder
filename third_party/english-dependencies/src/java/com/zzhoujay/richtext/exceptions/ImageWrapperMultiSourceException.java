// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.zzhoujay.richtext.exceptions;

/**
 * Created by zhou on 2017/4/4.
 * ImageWrapperMultiSourceException
 */

public class ImageWrapperMultiSourceException extends IllegalArgumentException {

    private static final String MESSAGE = "Exactly one of GifDrawable and Bitmap must be null";

    public ImageWrapperMultiSourceException() {
        super(MESSAGE);
    }

    @SuppressWarnings("unused")
    public ImageWrapperMultiSourceException(Throwable cause) {
        super(MESSAGE, cause);
    }

}
