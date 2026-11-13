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

package com.xuexiang.xaop.aspectj;

import com.xuexiang.xaop.XAOP;
import com.xuexiang.xaop.annotation.Permission;
import com.xuexiang.xaop.logger.XLogger;
import com.xuexiang.xaop.util.PermissionUtils;
import com.xuexiang.xaop.util.Utils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
@Aspect
public class PermissionAspectJ {
    @Pointcut("within(@com.xuexiang.xaop.annotation.Permission *)")
    public void withinAnnotatedClass() {
    }

    @Pointcut("execution(!synthetic * *(..)) && withinAnnotatedClass()")
    public void methodInsideAnnotatedType() {
    }

    @Pointcut("execution(@com.xuexiang.xaop.annotation.Permission * *(..)) || methodInsideAnnotatedType()")
    public void method() {
    }  

    @Around("method() && @annotation(permission)")
    public void aroundJoinPoint(final ProceedingJoinPoint joinPoint, Permission permission) throws Throwable {
        PermissionUtils.permission(permission.value())
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(List<String> permissionsGranted) {
                        try {
                            joinPoint.proceed();
                        } catch (Throwable e) {
                            e.printStackTrace();
                            XLogger.e(e);
                        }
                    }

                    @Override
                    public void onDenied(List<String> permissionsDeniedForever, List<String> permissionsDenied) {
                        XLogger.e("Permission request denied:" + Utils.listToString(permissionsDenied));
                        if (XAOP.getOnPermissionDeniedListener() != null) {
                            XAOP.getOnPermissionDeniedListener().onDenied(permissionsDenied);
                        }
                    }
                })
                .request();
    }



}


