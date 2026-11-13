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

package com.xuexiang.xhttp2.exception;

import android.net.ParseException;
import android.text.TextUtils;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

import org.apache.http.conn.ConnectTimeoutException;
import org.json.JSONException;

import java.io.InterruptedIOException;
import java.io.NotSerializableException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.HttpException;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class ApiException extends Exception {

    
    private int mCode;

    public ApiException(String message, int code) {
        super(message);
        mCode = code;
    }

    public ApiException(Throwable e, int code) {
        super(e);
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

    
    public String getDisplayMessage() {
        return getMessage();
    }

    
    public String getDetailMessage() {
        return "Code:" + mCode + ", Message:" + getMessage();
    }

    public static ApiException handleException(Throwable e) {
        ApiException ex;
        if (e instanceof HttpException) {
            HttpException httpException = (HttpException) e;
            if (!TextUtils.isEmpty(httpException.message())) {
                ex = new ApiException(httpException.message(), httpException.code());
            } else {
                ex = new ApiException(httpException.getMessage(), httpException.code());
            }
            return ex;
        } else if (e instanceof ServerException) {
            ServerException resultException = (ServerException) e;
            ex = new ApiException(resultException.getMessage(), resultException.getErrCode());
            return ex;
        } else if (e instanceof JsonParseException
                || e instanceof JSONException
                || e instanceof JsonSyntaxException
                || e instanceof JsonSerializer
                || e instanceof NotSerializableException
                || e instanceof ParseException) {
            ex = new ApiException("Parsing error", ERROR.PARSE_ERROR);
            return ex;
        } else if (e instanceof ClassCastException) {
            ex = new ApiException("Type conversion error", ERROR.CAST_ERROR);
            return ex;
        } else if (e instanceof ConnectException) {
            ex = new ApiException("Network connection error; please try again later", ERROR.NET_WORD_ERROR);
            return ex;
        } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
            ex = new ApiException("Certificate verification failed", ERROR.SSL_ERROR);
            return ex;
        } else if (e instanceof ConnectTimeoutException) {
            ex = new ApiException("Server request timed out; please try again later", ERROR.TIMEOUT_ERROR);
            return ex;
        } else if (e instanceof SocketTimeoutException) {
            ex = new ApiException("Network connection timed out; please try again later", ERROR.TIMEOUT_ERROR);
            return ex;
        } else if (e instanceof UnknownHostException) { 
            ex = new ApiException("Network unavailable; check network settings", ERROR.UN_KNOWN_HOST_ERROR);
            return ex;
        } else if (e instanceof NullPointerException) {
            ex = new ApiException("Null pointer error", ERROR.NULL_POINTER_ERROR);
            return ex;
        } else if (e instanceof OutOfMemoryError) {
            ex = new ApiException("Insufficient memory; close background applications", ERROR.OUT_OF_MEMORY_ERROR);
            return ex;
        } else if (e instanceof InterruptedIOException) {
            ex = new ApiException("Request cancelled", ERROR.REQUEST_CANCEL);
            return ex;
        } else {
            ex = new ApiException(e, ERROR.UNKNOWN);
            return ex;
        }
    }

    
    public static class ERROR {
        
        public static final int UNKNOWN = 5000;
        
        public static final int PARSE_ERROR = UNKNOWN + 1;
        
        public static final int NET_WORD_ERROR = PARSE_ERROR + 1;
        
        public static final int HTTP_ERROR = NET_WORD_ERROR + 1;

        
        public static final int SSL_ERROR = HTTP_ERROR + 1;

        
        public static final int TIMEOUT_ERROR = SSL_ERROR + 1;

        
        public static final int INVOKE_ERROR = TIMEOUT_ERROR + 1;
        
        public static final int CAST_ERROR = INVOKE_ERROR + 1;
        
        public static final int REQUEST_CANCEL = CAST_ERROR + 1;
        
        public static final int UN_KNOWN_HOST_ERROR = REQUEST_CANCEL + 1;
        
        public static final int NULL_POINTER_ERROR = UN_KNOWN_HOST_ERROR + 1;
        
        public static final int OUT_OF_MEMORY_ERROR = NULL_POINTER_ERROR + 1;
        
        public static final int DOWNLOAD_ERROR = OUT_OF_MEMORY_ERROR + 1;
        
        public static final int NET_METHOD_ANNOTATION_ERROR = OUT_OF_MEMORY_ERROR + 1;


    }
}
