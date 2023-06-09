package org.example.boot;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.example.PropertyResolver;
import org.example.util.ClassPathUtils;
import org.example.web.ContextLoaderInitializer;
import org.example.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.Set;

public class WinterApplication {

    final Logger logger = LoggerFactory.getLogger(getClass());
    
    public static void run(String webDir, String baseDir, Class<?> configClass, String... args) throws Exception {
        new WinterApplication().start(webDir, baseDir, configClass);
    }
    
    public void start(String webDir, String baseDir, Class<?> configClass) throws Exception {
        printBanner();

        final long startTime = System.currentTimeMillis();
        final int javaVersion = Runtime.version().feature();
        final long pid = ManagementFactory.getRuntimeMXBean().getPid();
        final String user = System.getProperty("user.name");
        final String pwd = Paths.get("").toAbsolutePath().toString();
        logger.info("Starting {} using Java {} with PID {} (started by {} in {})", configClass.getSimpleName(), javaVersion, pid, user, pwd);

        PropertyResolver propertyResolver = WebUtils.createPropertyResolver();
        Server server = startTomcat(webDir, baseDir, configClass, propertyResolver);

        final long endTime = System.currentTimeMillis();
        final String appTime = String.format("%.3f", (endTime - startTime) / 1000.0);
        final String jvmTime = String.format("%.3f", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        logger.info("Started {} in {} seconds (process running for {})", configClass.getSimpleName(), appTime, jvmTime);
        
        server.await();
    }
    
    protected Server startTomcat(String webDir, String baseDir, Class<?> configClass, PropertyResolver propertyResolver) throws LifecycleException {
        int port = propertyResolver.getProperty("${server.port:8080}", int.class);
        logger.atInfo().log("starting Tomcat at port {}...", port);

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector().setThrowOnFailure(true);
        Context context = tomcat.addWebapp("", new File(webDir).getAbsolutePath());
        WebResourceRoot resource = new StandardRoot(context);
        resource.addPreResources(new DirResourceSet(resource, "/WEB-INF/classes", new File(baseDir).getAbsolutePath(), "/"));
        context.setResources(resource);
        context.addServletContainerInitializer(new ContextLoaderInitializer(configClass, propertyResolver), Set.of());
        
        tomcat.start();
        logger.atInfo().log("Tomcat started at port {}...", port);
        
        return tomcat.getServer();
    }
    
    protected void printBanner() {
        String banner = ClassPathUtils.readString("/banner.txt");
        banner.lines().forEach(System.out::println);
    }
    
}
