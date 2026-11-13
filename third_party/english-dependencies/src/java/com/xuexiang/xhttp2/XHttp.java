// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.xuexiang.xhttp2;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.xuexiang.xhttp2.cache.RxCache;
import com.xuexiang.xhttp2.cache.converter.IDiskConverter;
import com.xuexiang.xhttp2.cache.converter.SerializableDiskConverter;
import com.xuexiang.xhttp2.cache.model.CacheMode;
import com.xuexiang.xhttp2.cookie.CookieManager;
import com.xuexiang.xhttp2.https.DefaultHostnameVerifier;
import com.xuexiang.xhttp2.https.HttpsUtils;
import com.xuexiang.xhttp2.interceptor.HttpLoggingInterceptor;
import com.xuexiang.xhttp2.logs.HttpLog;
import com.xuexiang.xhttp2.model.HttpHeaders;
import com.xuexiang.xhttp2.model.HttpParams;
import com.xuexiang.xhttp2.request.*;
import com.xuexiang.xhttp2.utils.RxSchedulers;
import com.xuexiang.xhttp2.utils.Utils;

import java.io.File;
import java.io.InputStream;
import java.net.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;

import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public final class XHttp {
    private volatile static XHttp sInstance = null;
    private static Application sContext;

    
    public static final int DEFAULT_TIMEOUT_MILLISECONDS = 15000;
    
    public static final int DEFAULT_RETRY_COUNT = 0;
    
    public static final int DEFAULT_RETRY_INCREASE_DELAY = 0;
    
    public static final int DEFAULT_RETRY_DELAY = 500;
    
    public static final long DEFAULT_CACHE_NEVER_EXPIRE = -1;

    
    
    private String mBaseUrl;
    
    private String mSubUrl = "";
    
    
    private Cache mCache = null;
    
    private CacheMode mCacheMode = CacheMode.NO_CACHE;
    
    private long mCacheTime = DEFAULT_CACHE_NEVER_EXPIRE;
    
    private File mCacheDirectory;
    
    private long mCacheMaxSize;
    
    
    private int mRetryCount = DEFAULT_RETRY_COUNT;
    
    private int mRetryDelay = DEFAULT_RETRY_DELAY;
    
    private int mRetryIncreaseDelay = DEFAULT_RETRY_INCREASE_DELAY;
    
    
    private HttpHeaders mCommonHeaders;
    
    private HttpParams mCommonParams;
    //======Builder=====//
    
    private OkHttpClient.Builder mOkHttpClientBuilder;
    
    private Retrofit.Builder mRetrofitBuilder;
    
    private RxCache.Builder mRxCacheBuilder;
    
    private CookieManager mCookieJar;

    
    private boolean mStrictMode;

    

    
    private XHttp() {
        mOkHttpClientBuilder = new OkHttpClient.Builder();
        mOkHttpClientBuilder.hostnameVerifier(new DefaultHostnameVerifier());
        mOkHttpClientBuilder.connectTimeout(DEFAULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        mOkHttpClientBuilder.readTimeout(DEFAULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        mOkHttpClientBuilder.writeTimeout(DEFAULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        mRetrofitBuilder = new Retrofit.Builder();
        mRxCacheBuilder = new RxCache.Builder().init(sContext)
                
                .diskConverter(new SerializableDiskConverter());
    }

    
    public static XHttp getInstance() {
        testInitialize();
        if (sInstance == null) {
            synchronized (XHttp.class) {
                if (sInstance == null) {
                    sInstance = new XHttp();
                }
            }
        }
        return sInstance;
    }

    
    public static void init(Application app) {
        sContext = app;
    }

    
    public static Context getContext() {
        testInitialize();
        return sContext;
    }

    private static void testInitialize() {
        if (sContext == null) {
            throw new ExceptionInInitializerError("Call XHttp.init() in Application before using this library!");
        }
    }

    

    public static OkHttpClient getOkHttpClient() {
        return getInstance().mOkHttpClientBuilder.build();
    }

    public static RxCache getRxCache() {
        return getInstance().mRxCacheBuilder.build();
    }

    
    public static OkHttpClient.Builder getOkHttpClientBuilder() {
        return getInstance().mOkHttpClientBuilder;
    }

    
    public static Retrofit.Builder getRetrofitBuilder() {
        return getInstance().mRetrofitBuilder;
    }

    
    public static RxCache.Builder getRxCacheBuilder() {
        return getInstance().mRxCacheBuilder;
    }

    

    
    public XHttp debug(HttpLoggingInterceptor loggingInterceptor) {
        if (loggingInterceptor != null) {
            mOkHttpClientBuilder.addInterceptor(loggingInterceptor);
            HttpLog.debug(true);
        } else {
            HttpLog.debug(false);
        }
        return this;
    }

    
    public XHttp debug(boolean isDebug) {
        if (isDebug) {
            debug(new HttpLoggingInterceptor(HttpLog.DEFAULT_LOG_TAG, true)
                    .setLevel(HttpLoggingInterceptor.Level.BODY));
        }
        HttpLog.debug(isDebug);
        return this;
    }

    
    public XHttp debug(String tag) {
        if (!TextUtils.isEmpty(tag)) {
            debug(new HttpLoggingInterceptor(tag, true).setLevel(HttpLoggingInterceptor.Level.BODY));
            HttpLog.debug(tag);
        } else {
            HttpLog.debug(false);
        }
        return this;
    }

    
    public XHttp setStrictMode(boolean strictMode) {
        mStrictMode = strictMode;
        return this;
    }

    
    public boolean isInStrictMode() {
        return mStrictMode;
    }

    

    
    public XHttp setBaseUrl(String baseUrl) {
        mBaseUrl = Utils.checkNotNull(baseUrl, "mBaseUrl == null");
        return this;
    }

    
    public XHttp setSubUrl(String subUrl) {
        mSubUrl = Utils.checkNotNull(subUrl, "mSubUrl == null");
        return this;
    }

    
    public static String getBaseUrl() {
        return getInstance().mBaseUrl;
    }

    
    public static String getSubUrl() {
        return getInstance().mSubUrl;
    }

    

    
    public XHttp setReadTimeOut(long readTimeOut) {
        mOkHttpClientBuilder.readTimeout(readTimeOut, TimeUnit.MILLISECONDS);
        return this;
    }

    
    public XHttp setWriteTimeOut(long writeTimeout) {
        mOkHttpClientBuilder.writeTimeout(writeTimeout, TimeUnit.MILLISECONDS);
        return this;
    }

    
    public XHttp setConnectTimeout(long connectTimeout) {
        mOkHttpClientBuilder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        return this;
    }

    
    public XHttp setTimeout(long timeout) {
        mOkHttpClientBuilder.readTimeout(timeout, TimeUnit.MILLISECONDS);
        mOkHttpClientBuilder.writeTimeout(timeout, TimeUnit.MILLISECONDS);
        mOkHttpClientBuilder.connectTimeout(timeout, TimeUnit.MILLISECONDS);
        return this;
    }

    
    public XHttp setRetryCount(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("mRetryCount must >= 0");
        }
        mRetryCount = retryCount;
        return this;
    }

    
    public static int getRetryCount() {
        return getInstance().mRetryCount;
    }

    
    public XHttp setRetryDelay(int retryDelay) {
        if (retryDelay < 0) {
            throw new IllegalArgumentException("mRetryDelay must > 0");
        }
        mRetryDelay = retryDelay;
        return this;
    }

    
    public static int getRetryDelay() {
        return getInstance().mRetryDelay;
    }

    
    public XHttp setRetryIncreaseDelay(int retryIncreaseDelay) {
        if (retryIncreaseDelay < 0) {
            throw new IllegalArgumentException("mRetryIncreaseDelay must >= 0");
        }
        mRetryIncreaseDelay = retryIncreaseDelay;
        return this;
    }

    
    public static int getRetryIncreaseDelay() {
        return getInstance().mRetryIncreaseDelay;
    }

    

    
    public XHttp setCacheMode(CacheMode cacheMode) {
        mCacheMode = cacheMode;
        return this;
    }

    
    public static CacheMode getCacheMode() {
        return getInstance().mCacheMode;
    }

    
    public XHttp setIsDiskCache(boolean isDiskCache) {
        mRxCacheBuilder.isDiskCache(isDiskCache);
        return this;
    }

    
    public XHttp setMemoryMaxSize(int memoryMaxSize) {
        mRxCacheBuilder.memoryMaxSize(memoryMaxSize);
        return this;
    }

    
    public XHttp setCacheTime(long cacheTime) {
        if (cacheTime <= -1) {
            cacheTime = DEFAULT_CACHE_NEVER_EXPIRE;
        }
        mCacheTime = cacheTime;
        return this;
    }

    
    public static long getCacheTime() {
        return getInstance().mCacheTime;
    }

    
    public XHttp setCacheMaxSize(long maxSize) {
        mCacheMaxSize = maxSize;
        return this;
    }

    
    public static long getCacheMaxSize() {
        return getInstance().mCacheMaxSize;
    }

    
    public XHttp setCacheVersion(int cacheVersion) {
        if (cacheVersion < 0) {
            throw new IllegalArgumentException("cache version must > 0");
        }
        mRxCacheBuilder.appVersion(cacheVersion);
        return this;
    }

    
    public XHttp setCacheDirectory(File directory) {
        mCacheDirectory = Utils.checkNotNull(directory, "directory == null");
        mRxCacheBuilder.diskDir(directory);
        return this;
    }

    
    public static File getCacheDirectory() {
        return getInstance().mCacheDirectory;
    }

    
    public XHttp setCacheDiskConverter(IDiskConverter converter) {
        mRxCacheBuilder.diskConverter(Utils.checkNotNull(converter, "converter == null"));
        return this;
    }

    
    public XHttp setHttpCache(Cache cache) {
        mCache = cache;
        return this;
    }

    
    public static Cache getHttpCache() {
        return getInstance().mCache;
    }

    

    
    public XHttp addCommonParams(HttpParams commonParams) {
        if (mCommonParams == null) {
            mCommonParams = new HttpParams();
        }
        mCommonParams.put(commonParams);
        return this;
    }

    
    public XHttp addCommonHeaders(HttpHeaders commonHeaders) {
        if (mCommonHeaders == null) {
            mCommonHeaders = new HttpHeaders();
        }
        mCommonHeaders.put(commonHeaders);
        return this;
    }

    
    public static HttpParams getCommonParams() {
        return getInstance().mCommonParams;
    }

    
    public static HttpHeaders getCommonHeaders() {
        return getInstance().mCommonHeaders;
    }

    

    
    public XHttp addInterceptor(Interceptor interceptor) {
        mOkHttpClientBuilder.addInterceptor(Utils.checkNotNull(interceptor, "interceptor == null"));
        return this;
    }

    
    public XHttp addNetworkInterceptor(Interceptor interceptor) {
        mOkHttpClientBuilder.addNetworkInterceptor(Utils.checkNotNull(interceptor, "interceptor == null"));
        return this;
    }

    
    public XHttp setOkproxy(Proxy proxy) {
        mOkHttpClientBuilder.proxy(Utils.checkNotNull(proxy, "mProxy == null"));
        return this;
    }

    
    public XHttp setOkconnectionPool(ConnectionPool connectionPool) {
        mOkHttpClientBuilder.connectionPool(Utils.checkNotNull(connectionPool, "connectionPool == null"));
        return this;
    }

    

    
    public XHttp setOkclient(OkHttpClient client) {
        mRetrofitBuilder.client(Utils.checkNotNull(client, "client == null"));
        return this;
    }

    
    public XHttp addConverterFactory(Converter.Factory factory) {
        mRetrofitBuilder.addConverterFactory(Utils.checkNotNull(factory, "factory == null"));
        return this;
    }

    
    public XHttp addCallAdapterFactory(CallAdapter.Factory factory) {
        mRetrofitBuilder.addCallAdapterFactory(Utils.checkNotNull(factory, "factory == null"));
        return this;
    }

    
    public XHttp setCallbackExecutor(Executor executor) {
        mRetrofitBuilder.callbackExecutor(Utils.checkNotNull(executor, "executor == null"));
        return this;
    }

    
    public XHttp setCallFactory(okhttp3.Call.Factory factory) {
        mRetrofitBuilder.callFactory(Utils.checkNotNull(factory, "factory == null"));
        return this;
    }

    

    
    public XHttp setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        mOkHttpClientBuilder.hostnameVerifier(hostnameVerifier);
        return this;
    }

    
    public XHttp setCertificates(InputStream... certificates) {
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(null, null, certificates);
        mOkHttpClientBuilder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        return this;
    }

    
    public XHttp setCertificates(InputStream bksFile, String password, InputStream... certificates) {
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(bksFile, password, certificates);
        mOkHttpClientBuilder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        return this;
    }

    //==================cookie=====================//

    
    public XHttp setCookieStore(CookieManager cookieManager) {
        mCookieJar = cookieManager;
        mOkHttpClientBuilder.cookieJar(mCookieJar);
        return this;
    }

    
    public static CookieManager getCookieJar() {
        return getInstance().mCookieJar;
    }

    

    
    public static GetRequest get(String url) {
        return new GetRequest(url);
    }

    
    public static PostRequest post(String url) {
        return new PostRequest(url);
    }

    
    public static DeleteRequest delete(String url) {
        return new DeleteRequest(url);
    }

    
    public static PutRequest put(String url) {
        return new PutRequest(url);
    }

    
    public static PatchRequest patch(String url) {
        return new PatchRequest(url);
    }

    
    public static CustomRequest custom() {
        return new CustomRequest()
                .addConverterFactory(GsonConverterFactory.create(new Gson()));
    }

    
    public static <T> T custom(final Class<T> service) {
        return new CustomRequest()
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .build()
                .create(service);
    }

    
    public static DownloadRequest downLoad(String url) {
        return new DownloadRequest(url);
    }

    

    
    @SuppressLint("CheckResult")
    public static void clearCache() {
        getRxCache().clear().compose(RxSchedulers.<Boolean>_io_main())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(@NonNull Boolean aBoolean) throws Exception {
                        HttpLog.i("clearCache success!!!");
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        HttpLog.i("clearCache err!!!");
                    }
                });
    }

    
    @SuppressLint("CheckResult")
    public static void removeCache(String key) {
        getRxCache().remove(key).compose(RxSchedulers.<Boolean>_io_main()).subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(@NonNull Boolean aBoolean) throws Exception {
                HttpLog.i("removeCache success!!!");
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                HttpLog.i("removeCache err!!!");
            }
        });
    }

}


