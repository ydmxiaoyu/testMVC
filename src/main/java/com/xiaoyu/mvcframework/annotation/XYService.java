package com.xiaoyu.mvcframework.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XYService {

    String value() default "";
}
