package com.xiaoyu.mvcframework.servlet;

import com.xiaoyu.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XYDispatcherServlet extends HttpServlet{


    private Properties configFile = new Properties(); // 初始化配置文件
    private List<String> classNames = new ArrayList<>();  //保存扫描到的所有的 class 名称，用于初始化实例
    private Map<String, Object> ioc = new HashMap<>();  // ioc 容器，存放的是 beanName - instance
//    private Map<String,Method> handlerMapping = new HashMap<>();

    private List<Handler> handlers = new ArrayList<>();  //保存 url - method 映射
    private Parameter parameter;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 有可能带有 contextPath
        String uri = req.getRequestURI();
        System.out.println("请求 uri:" + uri);
        String contextPath = req.getContextPath();
        System.out.println( "contextPath:" + contextPath);

        uri = uri.replace(contextPath, "").replaceAll("/+","/");

        /*if(!handlerMapping.containsKey(uri)){
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }*/

        Handler handler = null;

        if((handler = getHandler(uri)) == null){
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }

//        Method method = handlerMapping.get(uri);

        Object controller = handler.controller;
        Method method = handler.method;

        Parameter[] parameters = method.getParameters();
        // 获取参数名
        String[] parameterNames = getParameterNames(parameters);

        // 用来保存所有需要传入的参数值
        Object[] paramValues = new Object[parameters.length];

        Map<String,String[]> params = req.getParameterMap();
        for (Map.Entry<String,String[]> param:params.entrySet()) {
            // 处理参数数组，使用 Arrays 把数组转化成 String 时会有 "[]"  和 ", "
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s","");

            // 进行除了 request 和 response 之外的参数匹配
            for (int i =0; i < parameterNames.length; i++) {
                String name = parameterNames[i];
                // 如果参数名匹配成功
                if (name.equals(param.getKey().trim())){
                    // 对参数列表进行赋值
                    paramValues[i] = value;
                    break;
                }

            }
        }

        // 根据参数类型来匹配 request 和 response 对象进行赋值
        for (int i = 0; i < parameters.length; i++){

            System.out.println("参数类型：" + parameters[i].getType().getName());


            if (parameters[i].getType().getName().equals("javax.servlet.http.HttpServletRequest")){
                paramValues[i] = req;
            }
            if(parameters[i].getType().getName().equals("javax.servlet.http.HttpServletResponse")){
                paramValues[i] = resp;
            }
        }


        String name = req.getParameter("name");
        System.out.println("object:" + controller);
        System.out.println("method:" + method);

        // 通过反射的方式调用用户请求的方法，第一个参数是该方法所在类的实例，第二个参数是被调方法的参数列表
        try {
//            method.invoke(controller,name,resp);
            method.invoke(controller, paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    // 获取参数名
    private String[] getParameterNames(Parameter[] parameters) {
        String[] parameterNames = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (!param.isNamePresent()) {
                return null;
            }
            parameterNames[i] = param.getName();
        }
        return parameterNames;
    }

    // 通过 uri 匹配 Handler
    private Handler getHandler(String uri){
        for (Handler handler:handlers) {
            Matcher matcher = handler.pattern.matcher(uri);

            if(!matcher.matches()){
                continue;
            }
            // 如果匹配成功就返回这个 Handler
            return handler;
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("=========== DispatcherServlet 初始化 =================");

        // 加载配置文件
        String location = config.getInitParameter("configFile");
        System.out.println(location);
        doLoadConfig(location);

        // 扫描包下面的类
        doScanner(configFile.getProperty("basePackage"));

        // 实例化扫描到的类
        doInstance();

        // DI 对加了 Autowired 的字段赋值
        doAutowired();

        // 构造 HandlerMapping 把 url 和 method 进行关联
        initHandlerMapping();

        System.out.println("================ spring DispatcherServlet 初始化完成 =======================");

    }

    private void initHandlerMapping() {

        for (Map.Entry<String ,Object> entry:ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(XYController.class)){
                continue;
            }

            String baseUrl = "";
            if(clazz.isAnnotationPresent(XYRequestMapping.class)){
                XYRequestMapping requestMapping = clazz.getAnnotation(XYRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            // 获取当前控制器中的所有公有函数对象
            Method[] methods = clazz.getMethods();
            for (Method method:methods) {
                // 如果没有加 XYRequestMapping 注解就跳过
                if (!method.isAnnotationPresent(XYRequestMapping.class)){continue;}

                XYRequestMapping requestMapping = method.getAnnotation(XYRequestMapping.class);
                String url = baseUrl +  requestMapping.value();

//                handlerMapping.put(url, method);
                handlers.add(new Handler(entry.getValue(), method, Pattern.compile(url))); // 使用 handler 存放 url - method 映射关系

                System.out.println("HandlerMapping:" + url + "," + method.getName());
            }


        }


    }

    private void doAutowired() {
        
        if(ioc.isEmpty()){
            return;
        }

        // 给 ioc 容器中所有加了 Autowired 注解的字段赋值
        for (Map.Entry<String,Object> entrySet:ioc.entrySet()) {

            Field[] fields = entrySet.getValue().getClass().getDeclaredFields();
            for (Field field:fields) {
                field.setAccessible(true);

                // 如果字段没有加 Autowired 注解
                if(!field.isAnnotationPresent(XYAutoWired.class)){
                    continue;
                }

                XYAutoWired autoWired = field.getAnnotation(XYAutoWired.class);
                // 判断该字段是否指定了注入的对象名称
                String beanName = autoWired.value();
                if("".equals(beanName.trim())){
                    beanName = field.getType().getName();
                    System.out.println("field type=" + field.getType().getName());
                }

                // 如果不能访问就强制设为可访问状态
                if (!field.isAccessible()){
                    field.setAccessible(true);
                }

                try {
                    System.out.println("entrySet.getValue=" + entrySet.getValue() );
                    System.out.println("ioc.get=" + ioc.get(beanName));
                    // 第一个参数是拥有那个要修改的字段的对象， 第二个参数是赋值给该字段的值
                    field.set(entrySet.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }


            }


        }

    }

    private void doInstance() {
        try {
            for (String className : this.classNames) {

                Class clazz = Class.forName(className);

//                clazz.newInstance();
                if (clazz.isAnnotationPresent(XYController.class)){
                    Object instance = clazz.newInstance();
                    String beanName = lowerFirstCase( clazz.getSimpleName() );

                    ioc.put(beanName, instance);

                } else if(clazz.isAnnotationPresent(XYService.class)){
                    XYService service = (XYService) clazz.getAnnotation(XYService.class);
                    String beanName = service.value();
                    if("".equals( beanName.trim() )){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    // 获取当前类实现的所有接口
                    Class<?>[] interfaces = clazz.getInterfaces();

                    // 将实现类赋值给接口
                    for (Class<?> z:interfaces) {
                        ioc.put(z.getName(), instance);
                        System.out.println("接口名称：" + z.getName());
                    }

                } else {
                    continue;
                }


            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }


    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    // 扫描指定的包下面所有的类
    private void doScanner(String basePackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));

        File dir = new File(url.getFile());

        // 遍历该文件夹下的所有文件
        for (File file:dir.listFiles()) {
            if(file.isDirectory()){
                doScanner(basePackage + "." + file.getName());
            } else{
                // 类名需要 包名 + 类名
                String className = basePackage + "." + file.getName().replace(".class", "");
                // 将扫描到的类保存下来
                this.classNames.add(className);
                System.out.println(className);
            }
        }


    }

    // 加载配置文件
    private void doLoadConfig(String location){

        InputStream in = this.getClass().getClassLoader().getResourceAsStream(location);

        try {
            // 加载配置文件
            configFile.load(in);
            String basePackage = configFile.getProperty("basePackage");
            System.out.println(basePackage);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }


    private class Handler{
        // protected 修饰的变量在外部类也可以访问
        protected Object controller;  // 方法对应的实例
        protected Method method;  // 请求到的方法
        protected Pattern pattern; // 方法对应的 RequestMapping URL

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
        }
    }

}
