package app.aiyun.mvcframework.servlet;

import app.aiyun.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BobbyDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       try {
           doDispatch(req, resp);
       }
       catch (Exception ex){
           ex.printStackTrace();
           resp.getWriter().write("500 Exection,Detail : " + Arrays.toString(ex.getStackTrace()));
       }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //1. 根据请求的Url获取对应的Handler
        Handler handler = getHandler(req);
        if (handler == null){
            resp.getWriter().write("404, not found");
            return;
        }
        //2. 生成对应Handler要执行方法的实参数组
        Object[] params = this.getParameters(req, resp, handler);

        //3. 执行Handler对应的方法
        Object returnValue = handler.method.invoke(handler.controller,params);
        if(returnValue == null || returnValue instanceof Void){ return; }
        resp.getWriter().write(returnValue.toString());

    }

    private Object[] getParameters(HttpServletRequest request, HttpServletResponse response, Handler handler){

        Map<String, String[]> requestParameterMap = request.getParameterMap();
        Parameter[] parameters = handler.getMethod().getParameters();

        Object[] params = new Object[parameters.length];

        for(int i =0; i < parameters.length; i++){
            Parameter param = parameters[i];
            //一个参数可以有多个注解
            Annotation[] annotations = param.getAnnotations();
            for (Annotation annotation : annotations){
                if(annotation instanceof BobbyRequestParam){
                    String paramName = ((BobbyRequestParam) annotation).value().trim();
                    if(!"".equals(paramName) && requestParameterMap.containsKey(paramName)){
                        String value = Arrays.toString(requestParameterMap.get(paramName)).replaceAll("\\[|\\]","")
                                .replaceAll("\\s",",");
                        params[i] =  convert(param.getType(), value);
                    }
                }
            }
            //HttpServletRequest 或HttpServletResponse参数，没有注解
            if(param.getType() == HttpServletRequest.class){
                params[i] = request;
            }

            if(param.getType() == HttpServletResponse.class){
                params[i] = response;
            }
        }

        return params;

    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        //如果是int
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        else if(Double.class == type){
            return Double.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }

    private Handler getHandler(HttpServletRequest req){
        if(this.handlerMapping.isEmpty()) {return null;}
        //绝对路径
        String url = req.getRequestURI();
        //转成相对路径
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        for(Handler handler : this.handlerMapping){
            Pattern pattern = handler.getUrlPattern();
            Matcher matcher = pattern.matcher(url);
            if(matcher.matches()){
                return handler;
            }

        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1. 加载配置文件
        loadConfiguration(config.getInitParameter("contextConfigLocation"));

        //2. 扫包，获取包下所的类
        String packageName = contextConfig.getProperty("scanPackage");
        scanePackage(packageName);

        //3. 初始化扫描到到类，并保存到IOC容器中
        try {
            doInstance();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }

        //4. DI 依赖注入，将对象中所有Autowired注解成员变量赋值
        try {
            doAutowired();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }

        //5. URL与类的方法做映射关系
        initHandlerMapping();

    }

    //初始化url和Method的一对一对应关系
    private void initHandlerMapping() {
        if(ioc.isEmpty()){ return; }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(BobbyController.class)){continue;}

            //保存写在类上面的@GPRequestMapping("/demo")
            String baseUrl = "";
            if(clazz.isAnnotationPresent(BobbyRequestMapping.class)){
                BobbyRequestMapping requestMapping = clazz.getAnnotation(BobbyRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //默认获取所有的public方法
            for (Method method : clazz.getMethods()) {
                if(!method.isAnnotationPresent(BobbyRequestMapping.class)){continue;}

                BobbyRequestMapping requestMapping = method.getAnnotation(BobbyRequestMapping.class);
                //优化
                // //demo///query
                String regex = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                this.handlerMapping.add(new Handler(pattern,entry.getValue(),method));
            }
        }
    }

    //依赖注入，在IOC容器中查找所有包含@Autowired注解成员变量的类
    //并将@Autowired注解的成员变量赋值
    private void doAutowired() throws Exception {
        if(ioc.isEmpty()) {return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field : fields){
                if (!field.isAnnotationPresent(BobbyAutowired.class)) {continue;}
                BobbyAutowired autowired = field.getAnnotation(BobbyAutowired.class);
                //取自定义名称
                String beanName = autowired.value().trim();
                //如果没有自定义名称，则取字段名称到IOC容器中找
                if ("".equals(beanName)){
                    beanName = field.getName();
                    //如果字段名称在IOC中没有，则用此成员变量到接口类型全名到IOC容器中找
                    if (!ioc.containsKey(beanName)){
                        beanName = field.getType().getName();
                    }
                }

                if (!ioc.containsKey(beanName)){
                    throw new Exception("The " + beanName + "is not existed");
                }

                Object instance = ioc.get(beanName);
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), instance);
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }

            }

        }
    }

    /*
    * 将扫描到的类实例化，并放到IOC容器中，以便后面使用
    * */
    private void doInstance() throws Exception {
        if(classNames.size() == 0) {return;}

        for(String className : classNames){
            Class<?> clazz = Class.forName(className);
            // 如果是Controller
            if (clazz.isAnnotationPresent(BobbyController.class)){
                Object instance = clazz.newInstance();
                String beanName = initialToLowerCase(clazz.getSimpleName());
                ioc.put(beanName, instance);
            }//如果是service, 如果Service有自定义名称，则用自定义名称作为Key保存到IOC中
            //如果没有自定义名称，则默认将类名称首字母小写作为Key保存到IOC中
            //同时将类所实现到接口全名作为key保存到IOC中；
            else  if (clazz.isAnnotationPresent(BobbyService.class)){
              BobbyService service = clazz.getAnnotation(BobbyService.class);
              //先获取自定义Servicename，如果没有自定义name则默认取类名首字母小写
              String beanName = service.value();
              if ("".equals(beanName.trim())){
                  beanName = initialToLowerCase(clazz.getSimpleName());
              }
              Object instance = clazz.newInstance();
              ioc.put(beanName, instance);

              //将当前类实现的接口类型全名称也添加到IOC容器中，这样@Autowired 注释的成员变量
              //根据自定义的名称或者默认的类名首字母小写在IOC容器中没有找到对于的值时，
              //也可以尝试根据接口的类型全名称去IOC容器中查找
              Class<?>[] interfaces = clazz.getInterfaces();
                for (Class<?> c:
                     interfaces) {
                    beanName = c.getName();
                    if (!ioc.containsKey(beanName)){
                        ioc.put(beanName, instance);
                    }
                }

            }
        }
    }

    /*
    * 字符串首字母转小写
    * */
    private String initialToLowerCase(String str){
        char[] array = str.toCharArray();
        if (Character.isLowerCase(array[0])) {return  str;}

        array[0] = Character.toLowerCase(array[0]);
        return String.valueOf(array);

    }

    /*
    * 扫描得到所有的类
    * */
    private void scanePackage(String packageName) {

        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        boolean flag = classPath.exists();
        for (File file : classPath.listFiles()) {
            if(file.isDirectory()){
                scanePackage(packageName + "." + file.getName());
            }else{
                if(!file.getName().endsWith(".class")){ continue;}
                String className = (packageName + "." + file.getName().replace(".class",""));
                classNames.add(className);
            }
        }
    }

    /*
    * 加载配置文件，根据资源文件的名称获取资源文件的配置内容
    *
    * */
    private void loadConfiguration(String configLocation) {

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(configLocation);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != inputStream){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    //保存一个url和一个Method的关系
    public class Handler {
        private Pattern urlPattern;  //正则URL可以是一个正则表达式
        private Method method;
        private Object controller;

        public Pattern getUrlPattern() {
            return urlPattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }


        public Handler(Pattern urlPattern, Object controller, Method method) {
            this.urlPattern = urlPattern;
            this.method = method;
            this.controller = controller;
        }

    }

}
