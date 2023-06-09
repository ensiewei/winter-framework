package org.example.web.utils;

import jakarta.servlet.*;
import org.example.PropertyResolver;
import org.example.context.ApplicationContext;
import org.example.context.ApplicationContextUtils;
import org.example.util.ClassPathUtils;
import org.example.util.YamlUtils;
import org.example.web.DispatcherServlet;
import org.example.web.FilterRegistrationBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.*;

public class WebUtils {

    public static final String DEFAULT_PARAM_VALUE = "\0\t\0\t\0";
    static final Logger logger = LoggerFactory.getLogger(WebUtils.class);
    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";
    
    public static PropertyResolver createPropertyResolver() {
        Properties properties = new Properties();

        try {
            // try load application.yml
            Map<String, Object> yamlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            logger.atInfo().log("load config: {}", CONFIG_APP_YAML);
            for (String key : yamlMap.keySet()) {
                Object value = yamlMap.get(key);
                if (value instanceof String str) {
                    properties.put(key, str);
                }
            }
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                // try load application.properties
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, input -> {
                    logger.atInfo().log("load config: {}", CONFIG_APP_PROP);
                    properties.load(input);
                    return true;
                });
            }
        }

        return new PropertyResolver(properties);
    }
    
    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver propertyResolver) {
        DispatcherServlet dispatcherServlet = new DispatcherServlet(ApplicationContextUtils.getRequiredApplicationContext(), propertyResolver);
        logger.atInfo().log("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());
        ServletRegistration.Dynamic dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }
    
    public static void registerFilters(ServletContext servletContext) {
        ApplicationContext applicationContext = ApplicationContextUtils.getRequiredApplicationContext();
        for (FilterRegistrationBean filterRegistrationBean : applicationContext.getBeans(FilterRegistrationBean.class)) {
            List<String> urlPatterns = filterRegistrationBean.getUrlPatterns();
            if (urlPatterns == null || urlPatterns.isEmpty()) {
                throw new IllegalArgumentException("No url patterns for {}" + filterRegistrationBean.getClass().getName());
            }

            Filter filter = Objects.requireNonNull(filterRegistrationBean.getFilter(), "FilterRegistrationBean.getFilter() must not return null.");
            logger.atInfo().log("register filter '{}' {} for URLs: {}", filterRegistrationBean.getName(), filter.getClass().getName(), String.join(", ", urlPatterns));
            FilterRegistration.Dynamic filterReg = servletContext.addFilter(filterRegistrationBean.getName(), filter);
            filterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns.toArray(String[]::new));
        }
    }
    
}
