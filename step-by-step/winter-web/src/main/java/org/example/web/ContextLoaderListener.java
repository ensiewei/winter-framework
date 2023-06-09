package org.example.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.example.PropertyResolver;
import org.example.context.AnnotationConfigApplicationContext;
import org.example.context.ApplicationContext;
import org.example.exception.NestedRuntimeException;
import org.example.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextLoaderListener implements ServletContextListener {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.atInfo().log("init {}.", getClass().getName());
        
        ServletContext servletContext = sce.getServletContext();
        WebMvcConfiguration.setServletContext(servletContext);
        
        PropertyResolver propertyResolver = WebUtils.createPropertyResolver();
        
        String encoding = propertyResolver.getProperty("${winter.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);
        
        ApplicationContext applicationContext = createApplicationContext(servletContext.getInitParameter("configuration"), propertyResolver);
        servletContext.setAttribute("applicationContext", applicationContext);
        
        WebUtils.registerFilters(servletContext);
        
        WebUtils.registerDispatcherServlet(servletContext, propertyResolver);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (sce.getServletContext().getAttribute("applicationContext") instanceof ApplicationContext applicationContext) {
            applicationContext.close();
        }
    }

    public ApplicationContext createApplicationContext(String configClassName, PropertyResolver propertyResolver) {
        logger.atInfo().log("init ApplicationContext by configuration: {}", configClassName);
        
        if (configClassName == null || configClassName.isEmpty()) {
            throw new NestedRuntimeException("Cannot init ApplicationContext for missing init param name: configuration");
        }
        Class<?> configClass;
        try {
            configClass = Class.forName(configClassName);
        } catch (ClassNotFoundException e) {
            throw new NestedRuntimeException("Could not load class from init param 'configuration': " + configClassName);
        }
        
        return new AnnotationConfigApplicationContext(configClass, propertyResolver);
    }
    
}
