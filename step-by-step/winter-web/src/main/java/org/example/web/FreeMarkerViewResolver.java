package org.example.web;

import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.*;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.Objects;

public class FreeMarkerViewResolver implements ViewResolver {

    final Logger logger = LoggerFactory.getLogger(getClass());

    final String templatePath;
    final String templateEncoding;

    final ServletContext servletContext;

    Configuration configuration;

    public FreeMarkerViewResolver(String templatePath, String templateEncoding, ServletContext servletContext) {
        this.templatePath = templatePath;
        this.templateEncoding = templateEncoding;
        this.servletContext = servletContext;
    }

    @Override
    public void init() {
        logger.atInfo().log("init {}, set template path: {}", getClass().getSimpleName(), this.templatePath);
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setDefaultEncoding(this.templateEncoding);
        configuration.setTemplateLoader(new ServletTemplateLoader(this.servletContext, this.templatePath));
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        configuration.setAutoEscapingPolicy(Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY);
        configuration.setLocalizedLookup(false);

        DefaultObjectWrapper defaultObjectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_32);
        defaultObjectWrapper.setExposeFields(true);
        
        configuration.setObjectWrapper(defaultObjectWrapper);
        
        this.configuration = configuration;
    }

    @Override
    public void render(String viewName, Map<String, Object> model, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Template template;
        try {
            template = this.configuration.getTemplate(viewName);
        } catch (Exception e) {
            throw new ServerErrorException("View not found: " + viewName);
        }

        PrintWriter printWriter = resp.getWriter();
        try {
            template.process(model, printWriter);
        } catch (TemplateException e) {
            throw new ServerErrorException(e);
        }
        printWriter.flush();
    }
    
}


/**
 * copied from freemarker.cache.WebappTemplateLoader and modified to use
 * jakarta.servlet.ServletContext.
 
 * Because it is used old javax.servlet.ServletContext.
 */
class ServletTemplateLoader implements TemplateLoader {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final ServletContext servletContext;
    private final String subdirPath;

    public ServletTemplateLoader(ServletContext servletContext, String subdirPath) {
        Objects.requireNonNull(servletContext);
        Objects.requireNonNull(subdirPath);

        subdirPath = subdirPath.replace('\\', '/');
        if (!subdirPath.endsWith("/")) {
            subdirPath += "/";
        }
        if (!subdirPath.startsWith("/")) {
            subdirPath = "/" + subdirPath;
        }
        this.subdirPath = subdirPath;
        this.servletContext = servletContext;
    }

    @Override
    public Object findTemplateSource(String name) throws IOException {
        String fullPath = subdirPath + name;

        try {
            String realPath = servletContext.getRealPath(fullPath);
            logger.atDebug().log("load template {}: real path: {}", name, realPath);
            if (realPath != null) {
                File file = new File(realPath);
                if (file.canRead() && file.isFile()) {
                    return file;
                }
            }
        } catch (SecurityException e) {
            ;// ignore
        }
        return null;
    }

    @Override
    public long getLastModified(Object templateSource) {
        if (templateSource instanceof File) {
            return ((File) templateSource).lastModified();
        }
        return 0;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        if (templateSource instanceof File) {
            return new InputStreamReader(new FileInputStream((File) templateSource), encoding);
        }
        throw new IOException("File not found.");
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
    }

    public Boolean getURLConnectionUsesCaches() {
        return Boolean.FALSE;
    }
}

