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

package com.xuexiang.xhttp2.utils;

import com.xuexiang.xhttp2.model.ApiResult;
import com.xuexiang.xhttp2.reflect.TypeBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public final class TypeUtils {

    private TypeUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    
    public static <T> Type findNeedClass(Class<T> cls) {
        
        Type genType = cls.getGenericSuperclass();
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        Type type = params[0];
        Type finalNeedType;
        if (params.length > 1) {
            if (!(type instanceof ParameterizedType)) {
                throw new IllegalStateException("Generic type parameter is missing");
            }
            finalNeedType = ((ParameterizedType) type).getActualTypeArguments()[0];
            //Type rawType = ((ParameterizedType) type).getRawType();
        } else {
            finalNeedType = type;
        }
        return finalNeedType;
    }

    
    public static <T> Type findRawType(Class<T> cls) {
        Type genType = cls.getGenericSuperclass();
        return getGenericType((ParameterizedType) genType, 0);
    }

    private static Type getGenericType(ParameterizedType parameterizedType, int i) {
        Type genericType = parameterizedType.getActualTypeArguments()[i];
        if (genericType instanceof ParameterizedType) { 
            return ((ParameterizedType) genericType).getRawType();
        } else if (genericType instanceof GenericArrayType) { 
            return ((GenericArrayType) genericType).getGenericComponentType();
        } else if (genericType instanceof TypeVariable) { 
            return getClass(((TypeVariable) genericType).getBounds()[0], 0);
        } else {
            return genericType;
        }
    }

    public static Class getClass(Type type, int i) {
        if (type instanceof ParameterizedType) { 
            return getGenericClass((ParameterizedType) type, i);
        } else if (type instanceof TypeVariable) {
            return getClass(((TypeVariable) type).getBounds()[0], 0); 
        } else {
            return (Class) type;
        }
    }

    private static Class getGenericClass(ParameterizedType parameterizedType, int i) {
        Type genericClass = parameterizedType.getActualTypeArguments()[i];
        if (genericClass instanceof ParameterizedType) { 
            return (Class) ((ParameterizedType) genericClass).getRawType();
        } else if (genericClass instanceof GenericArrayType) { 
            return (Class) ((GenericArrayType) genericClass).getGenericComponentType();
        } else if (genericClass instanceof TypeVariable) { 
            return getClass(((TypeVariable) genericClass).getBounds()[0], 0);
        } else {
            return (Class) genericClass;
        }
    }

    public static Type getParameterizedType(Type type, int i) {
        if (type instanceof ParameterizedType) { 
            return ((ParameterizedType) type).getActualTypeArguments()[i];
        } else if (type instanceof TypeVariable) {
            return getType(((TypeVariable) type).getBounds()[0], 0); 
        } else {
            return type;
        }
    }

    private static Type getType(Type type, int i) {
        if (type instanceof ParameterizedType) { 
            return getGenericType((ParameterizedType) type, i);
        } else if (type instanceof TypeVariable) {
            return getType(((TypeVariable) type).getBounds()[0], 0); 
        } else {
            return type;
        }
    }

    /**
     * find the type by interfaces
     *
     * @param cls
     * @param <R>
     * @return
     */
    public static <R> Type findNeedType(Class<R> cls) {
        List<Type> typeList = TypeUtils.getAllTypes(cls);
        if (typeList == null || typeList.isEmpty()) {
            return ResponseBody.class;
        }
        return typeList.get(0);
    }

    
    public static <T> List<Type> getAllTypes(Class<T> cls) {
        Type genType = cls.getGenericSuperclass();
        List<Type> needTypes = null;
        // if Type is T
        if (genType instanceof ParameterizedType) {
            needTypes = new ArrayList<>();
            Type[] parentTypes = ((ParameterizedType) genType).getActualTypeArguments();
            for (Type childType : parentTypes) {
                needTypes.add(childType);
                if (childType instanceof ParameterizedType) {
                    Type[] childTypes = ((ParameterizedType) childType).getActualTypeArguments();
                    Collections.addAll(needTypes, childTypes);
                }
            }
        }
        return needTypes;
    }

    
    public static Type getApiResultType(Type type) {
        return TypeBuilder
                .newInstance(ApiResult.class)
                .addTypeParam(type)
                .build();
    }


    
    public static Type getListType(Type type) {
        return TypeBuilder
                .newInstance(List.class)
                .addTypeParam(type)
                .build();
    }


    
    public static <T> T newInstance(Class<T> clazz, Object... args)
            throws Exception {
        Class<?>[] argsClass = getClasses(args);
        Constructor<?> cons = clazz.getDeclaredConstructor(argsClass);
        cons.setAccessible(true);
        return (T) cons.newInstance(args);
    }

    
    private static Class<?>[] getClasses(Object... args) {
        Class<?>[] argsClass;
        if (args != null && args.length > 0) {
            argsClass = new Class[args.length];
            for (int i = 0, j = args.length; i < j; i++) {
                argsClass[i] = args[i].getClass();
            }
        } else {
            argsClass = new Class[0];
        }
        return argsClass;
    }


}
