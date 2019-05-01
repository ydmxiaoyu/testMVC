package com.xiaoyu.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})         //声明这个注解只能在类上使用
@Retention(RetentionPolicy.RUNTIME)  // 声明在运行时使用
@Documented
public @interface XYRequestMapping {

    String value() default "";

}
