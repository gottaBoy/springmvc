package com.mysting.springmvc.servlet;

import com.mysting.springmvc.annotation.MyAutowired;
import com.mysting.springmvc.annotation.MyController;
import com.mysting.springmvc.annotation.MyRequestMapping;
import com.mysting.springmvc.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // super.doPost(req, resp);
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            //如果匹配过程中出现异常，将异常信息打印出去
            resp.getWriter().write("500 Exception, Details:\r\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);
        if(handler == null) {
            resp.getWriter().write("404 Not Found!");
            return;
        }
        //获取方法的参数列表
        Class[] paramTypes = handler.getMethod().getParameterTypes();

        //保存所有需要自动赋值的参数值
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params = req.getParameterMap();
        for(Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "");
            //如果找到匹配的对象，则开始填充参数值
            if(!handler.getParamIndexMapping().containsKey(param.getKey())) continue;
            int index = handler.getParamIndexMapping().get(param.getKey());
            paramValues[index] = convert(paramTypes[index], value);
        }

        //设置方法中的request和response对象
        int reqIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;

        int respIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;

        handler.getMethod().invoke(handler.getController(), paramValues);
    }

    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()) return null;
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replace("/+", "/");
        for(Handler handler : handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(url);
            if(!matcher.matches()) continue;
            return handler;
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if(Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("===================");
        // 1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2.扫描所有相关联的类
        doScanner(contextConfig.getProperty("scanPackage"));

        // 3.初始化所有相关联的类，并且将其保存在IOC容器里面
        doInstance();

        // 4.执行依赖注入（把加了@Autowired注解的字段赋值）
        doAutowired();

        // Spring 和核心功能已经完成 IOC、DI
        // 5.构造HandlerMapping，将URL和Method进行关联
        initHandlerMapping();

        System.out.println("Spring MVC framework initialized");
    }

    private void doLoadConfig(String location) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String basePackage) {
        URL url =  this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for(File file: dir.listFiles()) {
            if(file.isDirectory()){
                doScanner(basePackage + "." + file.getName());
            } else {
                String className = basePackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
                System.out.println(className);
            }
        }
    }

    private void doInstance() {
        if(classNames.isEmpty()) return;
        for(String className: classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    MyService service = clazz.getAnnotation(MyService.class);
                    // 2.优先使用自定义命名
                    String beanName = service.value();
                    if("".equals(beanName.trim())) {
                        // 1.默认使用类名首字母小写
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 3.自动类型匹配（例如：将实现类赋值给接口）
                    Class<?> [] interfaces = clazz.getInterfaces();
                    for(Class<?> inter: interfaces) {
                        ioc.put(inter.getName(), instance);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //利用ASCII码的差值
    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doAutowired() {
        if(ioc.isEmpty()) return;
        for(Map.Entry<String, Object> entry: ioc.entrySet()) {
            // 注入的意思就是把所有的IOC容器中加了@Autowired注解的字段赋值
            // 包含私有字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field : fields) {
                // 判断是否加了@Autowired注解
                if(!field.isAnnotationPresent(MyAutowired.class)) continue;
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanName = autowired.value();
                if("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                // 如果这个字段是私有字段的话，那么要强制访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()) return;
        for(Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)) continue;
            String baseUrl = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for(Method method : methods) {
                if(!method.isAnnotationPresent(MyRequestMapping.class)) continue;
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String regex = requestMapping.value();
                regex = (baseUrl + regex).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                System.out.println("Mapping: " + regex + "," + method.getName());
            }
        }
    }
}
