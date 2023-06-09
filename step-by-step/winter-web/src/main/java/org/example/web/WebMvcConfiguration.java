package org.example.web;

import jakarta.servlet.ServletContext;
import org.example.annotation.Autowired;
import org.example.annotation.Bean;
import org.example.annotation.Configuration;
import org.example.annotation.Value;

import java.util.Objects;

@Configuration
public class WebMvcConfiguration {
    private static ServletContext servletContext = null;

    /**
     * Set by web listener.
     */
    static void setServletContext(ServletContext ctx) {
        servletContext = ctx;
    }

    @Bean(initMethod = "init")
    ViewResolver viewResolver( //
                               @Autowired ServletContext servletContext, //
                               @Value("${winter.web.freemarker.template-path:/WEB-INF/templates}") String templatePath, //
                               @Value("${winter.web.freemarker.template-encoding:UTF-8}") String templateEncoding) {
        return new FreeMarkerViewResolver(templatePath, templateEncoding, servletContext);
    }

    @Bean
    ServletContext servletContext() {
        return Objects.requireNonNull(servletContext, "ServletContext is not set.");
    }
}
