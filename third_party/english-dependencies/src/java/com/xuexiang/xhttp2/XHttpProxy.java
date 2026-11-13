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

package com.xuexiang.xhttp2;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xuexiang.xhttp2.annotation.NetMethod;
import com.xuexiang.xhttp2.annotation.ThreadType;
import com.xuexiang.xhttp2.cache.RxCache;
import com.xuexiang.xhttp2.cache.model.CacheMode;
import com.xuexiang.xhttp2.exception.ApiException;
import com.xuexiang.xhttp2.request.BaseBodyRequest;
import com.xuexiang.xhttp2.request.BaseRequest;
import com.xuexiang.xhttp2.utils.HttpUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import static com.xuexiang.xhttp2.annotation.NetMethod.*;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class XHttpProxy implements InvocationHandler {

    
    public static final int JSON_OBJECT_METHOD_PARAM_NUMBER = 1;

    
    public static <T> T proxy(Class<T> cls) {
        return new XHttpProxy().create(cls);
    }

    
    public static <T> T proxy(Class<T> cls, @ThreadType String threadType) {
        return new XHttpProxy(threadType).create(cls);
    }

    
    private String mThreadType;

    
    public XHttpProxy() {
        this(ThreadType.TO_MAIN);
    }

    
    public XHttpProxy(@ThreadType String threadType) {
        mThreadType = threadType;
    }


    public <T> T create(Class<T> cls) {
        return (T) Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, this);
    }

    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {
        NetMethod netMethod = method.getAnnotation(NetMethod.class);
        if (netMethod == null) {
            throw new ApiException(method.getName() + "Method has no NetMethod annotation", ApiException.ERROR.NET_METHOD_ANNOTATION_ERROR);
        }
        if (netMethod.paramType() == NetMethod.JSON_OBJECT) {
            if (method.getGenericParameterTypes().length != JSON_OBJECT_METHOD_PARAM_NUMBER) {
                throw new ApiException(method.getName() + "A JSON_OBJECT NetMethod must have exactly one parameter", ApiException.ERROR.NET_METHOD_ANNOTATION_ERROR);
            }
        } else {
            if (netMethod.parameterNames().length != method.getGenericParameterTypes().length) {
                throw new ApiException(method.getName() + "NetMethod annotation parameter count does not match method", ApiException.ERROR.NET_METHOD_ANNOTATION_ERROR);
            }
        }

        BaseRequest request = getHttpRequest(method, args, netMethod);
        if (request instanceof BaseBodyRequest) {
            if (netMethod.paramType() == NetMethod.JSON) {
                ((BaseBodyRequest) request).upJson(HttpUtils.toJson(getParamsMap(method, args, netMethod)));
            } else if (netMethod.paramType() == NetMethod.JSON_OBJECT) {
                
                ((BaseBodyRequest) request).upJson(HttpUtils.toJson(args[0]));
            } else {
                request.params(getParamsMap(method, args, netMethod));
            }
        } else {
            if (netMethod.paramType() == NetMethod.URL_GET) {
                if (args.length > 0) {
                    request.url(netMethod.url() + "/" + args[0]);
                    request.params(getParamsMap(method, args, netMethod, 1));
                }
            } else {
                request.params(getParamsMap(method, args, netMethod));
            }
        }
        return request.execute(getReturnType(method));
    }

    
    private Type getReturnType(Method method) throws ApiException {
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType) {
            type = ((ParameterizedType) type).getActualTypeArguments()[0];
        } else {
            throw new ApiException("Interface method:" + method.getName() + " must return the generic type Observable<T>", ApiException.ERROR.NET_METHOD_ANNOTATION_ERROR);
        }
        return type;
    }

    
    private BaseRequest getHttpRequest(Method method, Object[] args, NetMethod apiMethod) {
        String baseUrl = apiMethod.baseUrl();
        String url = apiMethod.url();
        long timeout = apiMethod.timeout();
        CacheMode cacheMode = apiMethod.cacheMode();

        BaseRequest request;
        switch (apiMethod.action()) {
            case POST:
                request = XHttp.post(url);
                break;
            case GET:
                request = XHttp.get(url);
                break;
            case PUT:
                request = XHttp.put(url);
                break;
            case PATCH:
                request = XHttp.patch(url);
                break;
            default:
                request = XHttp.delete(url);
                break;
        }
        if (!TextUtils.isEmpty(baseUrl)) {
            request.baseUrl(baseUrl);
        }
        if (!CacheMode.NO_CACHE.equals(cacheMode)) {
            request.cacheMode(cacheMode).cacheKey(getCacheKey(method, args, apiMethod));
            long cacheTime = apiMethod.cacheTime();
            if (cacheTime != NetMethod.NOT_SET_CACHE_TIME) {
                request.cacheTime(cacheTime);
            }
        }
        
        if (timeout <= 0) {
            timeout = XHttp.DEFAULT_TIMEOUT_MILLISECONDS;
        }
        return request
                .threadType(mThreadType)
                .keepJson(apiMethod.keepJson())
                .accessToken(apiMethod.accessToken())
                .timeOut(timeout);
    }


    
    @NonNull
    private Map<String, Object> getParamsMap(Method method, Object[] args, NetMethod apiMethod) {
        return getParamsMap(method, args, apiMethod, 0);
    }

    
    @NonNull
    private Map<String, Object> getParamsMap(Method method, Object[] args, NetMethod apiMethod, int index) {
        Map<String, Object> params = new TreeMap<>();
        Type[] parameters = method.getGenericParameterTypes();
        for (int i = index; i < parameters.length; i++) {
            params.put(apiMethod.parameterNames()[i], args[i]);
        }
        return params;
    }

    
    @NonNull
    private String getCacheKey(Method method, Object[] args, NetMethod apiMethod) {
        return RxCache.getICacheKeyCreator().getCacheKey(method, args, apiMethod);
    }

}
