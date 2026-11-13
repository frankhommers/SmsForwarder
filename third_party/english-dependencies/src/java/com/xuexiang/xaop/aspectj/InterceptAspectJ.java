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
import com.xuexiang.xaop.annotation.Intercept;
import com.xuexiang.xaop.logger.XLogger;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/* Upstream explanatory comments omitted; see pinned source receipt.
 *     author : xuexiang
 */
@Aspect
public class InterceptAspectJ {

    @Pointcut("within(@com.xuexiang.xaop.annotation.Intercept *)")
    public void withinAnnotatedClass() {
    }

    @Pointcut("execution(!synthetic * *(..)) && withinAnnotatedClass()")
    public void methodInsideAnnotatedType() {
    }

    @Pointcut("execution(!synthetic *.new(..)) && withinAnnotatedClass()")
    public void constructorInsideAnnotatedType() {
    }

    @Pointcut("execution(@com.xuexiang.xaop.annotation.Intercept * *(..)) || methodInsideAnnotatedType()")
    public void method() {
    } 

    @Pointcut("execution(@com.xuexiang.xaop.annotation.Intercept *.new(..)) || constructorInsideAnnotatedType()")
    public void constructor() {
    } 

    @Around("(method() || constructor()) && @annotation(intercept)")
    public Object aroundJoinPoint(ProceedingJoinPoint joinPoint, Intercept intercept) throws Throwable {
        if (XAOP.getInterceptor() == null) {
            return joinPoint.proceed(); 
        }
        
        boolean result = proceedIntercept(intercept.value(), joinPoint);
        XLogger.d("Interception result:" + result + ", aspect" + (result ? "Intercepted!" : "Executing normally!"));
        return result ? null : joinPoint.proceed();
    }

    
    private boolean proceedIntercept(int[] types, JoinPoint joinPoint) throws Throwable {
        for (int type : types) {
            if (XAOP.getInterceptor().intercept(type, joinPoint)) { 
                return true;
            }
        }
        return false;
    }

}
