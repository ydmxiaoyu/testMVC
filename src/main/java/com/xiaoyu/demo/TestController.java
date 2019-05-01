package com.xiaoyu.demo;

import com.xiaoyu.demo.service.DemoService;
import com.xiaoyu.mvcframework.annotation.XYAutoWired;
import com.xiaoyu.mvcframework.annotation.XYController;
import com.xiaoyu.mvcframework.annotation.XYRequestMapping;
import com.xiaoyu.mvcframework.annotation.XYRequestParameter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@XYController
@XYRequestMapping("/test")
public class TestController {

    @XYAutoWired
    private DemoService service;

    @XYRequestMapping("/method1")
    public void method1(@XYRequestParameter String name, HttpServletResponse response){
        response.setContentType("text/html; charset=utf-8");
        try {
            response.getWriter().write("欢迎你" + name);
        }catch (IOException e){
            e.printStackTrace();
        }


    }

}
