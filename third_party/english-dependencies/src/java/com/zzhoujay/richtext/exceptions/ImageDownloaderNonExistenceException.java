// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.zzhoujay.richtext.exceptions;



public class ImageDownloaderNonExistenceException extends RuntimeException {

    public ImageDownloaderNonExistenceException() {
        super("ImageDownloader is null or not configured");
    }
}
