package org.example.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.PropertyResolver;
import org.example.annotation.*;
import org.example.context.ApplicationContext;
import org.example.context.BeanDefinition;
import org.example.context.ConfigurableApplicationContext;
import org.example.exception.ErrorResponseException;
import org.example.exception.NestedRuntimeException;
import org.example.exception.ServerErrorException;
import org.example.exception.ServerWebInputException;
import org.example.web.utils.JsonUtils;
import org.example.web.utils.PathUtils;
import org.example.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    final Logger logger = LoggerFactory.getLogger(getClass());
    
    final ApplicationContext applicationContext;
    ViewResolver viewResolver;

    String resourcePath;
    String faviconPath;

    List<Dispatcher> getDispatchers = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();

    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
        this.viewResolver = applicationContext.getBean(ViewResolver.class);
        this.resourcePath = propertyResolver.getProperty("${winter.web.static-path:/static/}");
        this.faviconPath = propertyResolver.getProperty("${winter.web.favicon-path:/favicon.ico}");
        if (!this.resourcePath.endsWith("/")) {
            this.resourcePath = this.resourcePath + "/";
        }
    }

    @Override
    public void init() throws ServletException {
        logger.atInfo().log("init {}.", getClass().getName());
        for (BeanDefinition beanDefinition : ((ConfigurableApplicationContext) this.applicationContext).findBeanDefinitions(Object.class)) {
            Class<?> beanClass = beanDefinition.getBeanClass();
            Object instance = beanDefinition.getRequiredInstance();
            
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);
            
            if (controller != null && restController != null) {
                throw new ServletException("Found @Controller and @RestController on class: " + beanClass.getName());
            }
            
            if (controller != null) {
                addController(false, beanDefinition.getName(), instance);
            }
            
            if (restController != null) {
                addController(true, beanDefinition.getName(), instance);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURI();
        
        if (url.startsWith(this.resourcePath) || url.startsWith(this.faviconPath)) {
            doResource(url, req, resp);
        } else {
            doService(req, resp, this.getDispatchers);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doService(req, resp, this.postDispatchers);
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }
    
    void doResource(String url, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ServletContext servletContext = req.getServletContext();
        try (InputStream inputStream = servletContext.getResourceAsStream(url)) {
            if (inputStream == null) {
                resp.sendError(404, "Not Found.");
            } else {
                // guess content type
                String file = url;
                int i = file.lastIndexOf("/");
                if (i >= 0) {
                    file = file.substring(i + 1);
                }

                String mimeType = servletContext.getMimeType(file);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
                resp.setContentType(mimeType);
                
                ServletOutputStream outputStream = resp.getOutputStream();
                inputStream.transferTo(outputStream);
                outputStream.flush();
            }
        }
    }
    
    void doService(HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws IOException, ServletException {
        String url = req.getRequestURI();
        
        try {
            doService(url, req, resp, dispatchers);
        } catch (ErrorResponseException e) {
            logger.atWarn().log("process request failed with status " + e.statusCode + " : " + url, e);
            if (!resp.isCommitted()) {
                resp.resetBuffer();
                resp.sendError(e.statusCode);
            }
        } catch (RuntimeException | ServletException | IOException e) {
            logger.atWarn().log("process request failed: " + url, e);
            throw e;
        } catch (Exception e) {
            logger.atWarn().log("process request failed: " + url, e);
            throw new NestedRuntimeException(e);
        }
    }
    
    void addController(boolean isRest, String name, Object instance) throws ServletException {
        logger.atInfo().log("add {} controller '{}': {}", isRest ? "REST" : "MVC", name, instance.getClass().getName());
        
        addMethods(isRest, instance, instance.getClass());
    }
    
    void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws Exception {
        for (Dispatcher dispatcher : dispatchers) {
            Result result = dispatcher.process(url, req, resp);
            if (result.processed()) {
                Object r = result.returnObject();
                
                if (dispatcher.isRest) {
                    
                    if (!resp.isCommitted()) {
                        resp.setContentType("application/json");
                    }
                    
                    if (dispatcher.isResponseBody) {
                        if (r instanceof String s) {
                            PrintWriter printWriter = resp.getWriter();
                            printWriter.write(s);
                            printWriter.flush();
                        } else if (r instanceof byte[] data) {
                            ServletOutputStream outputStream = resp.getOutputStream();
                            outputStream.write(data);
                            outputStream.flush();
                        } else {
                            throw new ServletException("Unable to process REST result when handle url: " + url);
                        }
                    } else if (!dispatcher.isVoid) {
                        PrintWriter printWriter = resp.getWriter();
                        JsonUtils.writeJson(printWriter, r);
                        printWriter.flush();
                    }
                    
                } else {
                    
                    if (!resp.isCommitted()) {
                        resp.setContentType("text/html");
                    }
                    
                    if (r instanceof String s) {
                        if (dispatcher.isResponseBody) {
                            PrintWriter printWriter = resp.getWriter();
                            printWriter.write(s);
                            printWriter.flush();
                        } else if (s.startsWith("redirect:")) {
                            resp.sendRedirect(s.substring(9));
                        } else {
                            throw new ServletException("Unable to process String result when handle url: " + url);
                        }
                    } else if (r instanceof byte[] data) {
                        if (dispatcher.isResponseBody) {
                            ServletOutputStream outputStream = resp.getOutputStream();
                            outputStream.write(data);
                            outputStream.flush();
                        } else {
                            throw new ServletException("Unable to process byte[] result when handle url: " + url);
                        }
                    } else if (r instanceof ModelAndView mv) {
                        String viewName = mv.getViewName();
                        if (viewName.startsWith("redirect:")) {
                            resp.sendRedirect(viewName.substring(9));
                        } else {
                            this.viewResolver.render(viewName, mv.getModel(), req, resp);
                        }
                    } else if (!dispatcher.isVoid && r != null) {
                        throw new ServletException("Unable to process " + r.getClass().getName() + " result when handle url: " + url);
                    }
                    
                }

                return;
            }
        }
        
        resp.sendError(404, "Not Found.");
    }
    
    void addMethods(boolean isRest, Object instance, Class<?> clazz) throws ServletException {
        for (Method m : clazz.getDeclaredMethods()) {
            GetMapping getMapping = m.getAnnotation(GetMapping.class);
            if (getMapping != null) {
                checkMethod(m);
                this.getDispatchers.add(new Dispatcher(isRest, instance, m, getMapping.value()));
            }

            PostMapping postMapping = m.getAnnotation(PostMapping.class);
            if (postMapping != null) {
                checkMethod(m);
                this.postDispatchers.add(new Dispatcher(isRest, instance, m, postMapping.value()));
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            addMethods(isRest, instance, superclass);
        }
    }

    void checkMethod(Method m) throws ServletException {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new ServletException("Cannot do URL mapping to static method: " + m);
        }
        m.setAccessible(true);
    }
    
    static class Dispatcher {
        final Logger logger = LoggerFactory.getLogger(getClass());
        
        boolean isRest;
        boolean isResponseBody;
        boolean isVoid;
        Pattern urlPattern;
        Object controller;
        Method handlerMethod;
        Param[] methodParameters;

        public Dispatcher(boolean isRest, Object controller, Method method, String urlPattern) throws ServletException {
            this.isRest = isRest;
            this.isResponseBody = method.isAnnotationPresent(ResponseBody.class);
            this.isVoid = method.getReturnType() == void.class;
            this.urlPattern = PathUtils.compile(urlPattern);
            this.controller = controller;
            this.handlerMethod = method;
            Parameter[] parameters = method.getParameters();
            this.methodParameters = new Param[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                this.methodParameters[i] = new Param(method, parameters[i]);
            }
            logger.atDebug().log("mapping {} to handler {}.{}", urlPattern, controller.getClass().getSimpleName(), method.getName());
            if (logger.isDebugEnabled()) {
                for (var p : this.methodParameters) {
                    logger.debug("> parameter: {}", p);
                }
            }
        }
        
        Result process(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
            Matcher matcher = this.urlPattern.matcher(url);
            if (matcher.matches()) {
                Object[] arguments = new Object[this.methodParameters.length];
                for (int i = 0; i < arguments.length; i++) {
                    Param param = this.methodParameters[i];
                    final Class<?> classType = param.classType;
                    arguments[i] = switch (param.paramType) {
                        case PATH_VARIABLE -> {
                            try {
                                String s = matcher.group(param.name);
                                yield convertToType(classType, s);
                            } catch (IllegalArgumentException e) {
                                throw new ServerWebInputException("Path variable '" + param.name + "' not found.");
                            }
                        }
                        case REQUEST_PARAM -> {
                            String s = getOrDefault(request, param.name, param.defaultValue);
                            yield convertToType(classType, s);
                        }
                        case REQUEST_BODY -> {
                            BufferedReader reader = request.getReader();
                            yield JsonUtils.readJson(reader, classType);
                        }
                        case SERVLET_PARAM -> {
                            if (classType == HttpServletRequest.class) {
                                yield request;
                            } else if (classType == HttpServletResponse.class) {
                                yield response;
                            } else if (classType == HttpSession.class) {
                                yield request.getSession();
                            } else if (classType == ServletContext.class) {
                                yield request.getServletContext();
                            } else {
                                throw new ServerErrorException("Could not determine argument type: " + classType);
                            }
                        }
                    };
                }
                
                Object result;
                try {
                    result = this.handlerMethod.invoke(this.controller, arguments);
                } catch (InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof Exception ex) {
                        throw ex;
                    }
                    throw e;
                } catch (ReflectiveOperationException e) {
                    throw new ServerErrorException(e);
                }
                
                return new Result(true, result);
            }
            
            return new Result(false, null);
        }

        Object convertToType(Class<?> classType, String s) {
            if (classType == String.class) {
                return s;
            } else if (classType == boolean.class || classType == Boolean.class) {
                return Boolean.valueOf(s);
            } else if (classType == int.class || classType == Integer.class) {
                return Integer.valueOf(s);
            } else if (classType == long.class || classType == Long.class) {
                return Long.valueOf(s);
            } else if (classType == byte.class || classType == Byte.class) {
                return Byte.valueOf(s);
            } else if (classType == short.class || classType == Short.class) {
                return Short.valueOf(s);
            } else if (classType == float.class || classType == Float.class) {
                return Float.valueOf(s);
            } else if (classType == double.class || classType == Double.class) {
                return Double.valueOf(s);
            } else {
                throw new ServerErrorException("Could not determine argument type: " + classType);
            }
        }

        String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
            String s = request.getParameter(name);
            if (s == null) {
                if (WebUtils.DEFAULT_PARAM_VALUE.equals(defaultValue)) {
                    throw new ServerWebInputException("Request parameter '" + name + "' not found.");
                }
                return defaultValue;
            }
            return s;
        }
        
    }
    
    static class Param {
        String name;
        final ParamType paramType;
        final Class<?> classType;
        String defaultValue;
        
        public Param(Method method, Parameter parameter) throws ServletException {
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            
            int total = (pathVariable == null ? 0 : 1) + (requestParam == null ? 0 : 1) + (requestBody == null ? 0 : 1);
            if (total > 1) {
                throw new ServletException("Annotation @PathVariable, @RequestParam and @RequestBody cannot be combined at method: " + method);
            }
            
            this.classType = parameter.getType();
            
            if (pathVariable != null) {
                this.name = pathVariable.value();
                this.paramType = ParamType.PATH_VARIABLE;
            } else if (requestParam != null) {
                this.name = requestParam.value();
                this.defaultValue = requestParam.defaultValue();
                this.paramType = ParamType.REQUEST_PARAM;
            } else if (requestBody != null) {
                this.paramType = ParamType.REQUEST_BODY;
            } else {
                this.paramType = ParamType.SERVLET_PARAM;
                if (this.classType != HttpServletRequest.class && this.classType != HttpServletResponse.class && this.classType != HttpSession.class
                        && this.classType != ServletContext.class) {
                    throw new ServerErrorException("(Missing annotation?) Unsupported argument type: " + classType + " at method: " + method);
                }
            }
        }

        @Override
        public String toString() {
            return "Param [name=" + name + ", paramType=" + paramType + ", classType=" + classType + ", defaultValue=" + defaultValue + "]";
        }
    }
    
    enum ParamType {
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_BODY, SERVLET_PARAM,
    }
    
    record Result(boolean processed, Object returnObject) {
        
    }
    
}
