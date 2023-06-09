package org.example.web;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import org.example.PropertyResolver;
import org.example.context.AnnotationConfigApplicationContext;
import org.example.context.ApplicationContext;
import org.example.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ContextLoaderInitializer implements ServletContainerInitializer {

    final Logger logger = LoggerFactory.getLogger(getClass());
    final Class<?> configClass;
    final PropertyResolver propertyResolver;

    public ContextLoaderInitializer(Class<?> configClass, PropertyResolver propertyResolver) {
        this.configClass = configClass;
        this.propertyResolver = propertyResolver;
    }

    @Override
    public void onStartup(Set<Class<?>> set, ServletContext servletContext) {
        logger.info("Servlet container start. ServletContext = {}", servletContext);

        String encoding = propertyResolver.getProperty("${winter.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);
        
        WebMvcConfiguration.setServletContext(servletContext);

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(this.configClass, this.propertyResolver);
        logger.info("Application context created: {}", applicationContext);

        WebUtils.registerFilters(servletContext);
        
        WebUtils.registerDispatcherServlet(servletContext, this.propertyResolver);

    }
}
