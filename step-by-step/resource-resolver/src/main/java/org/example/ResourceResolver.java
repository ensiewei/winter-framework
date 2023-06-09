package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class ResourceResolver {

    Logger logger = LoggerFactory.getLogger(getClass());

    private String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public <R> List<R> scan(Function<Resource, R> mapper) {
        basePackage = basePackage.replace(".", "/");
        List<R> collector = new ArrayList<>();
        try {
            scan0(basePackage, collector, mapper);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return collector;
    }

    <R> void scan0(String basePackage, List<R> collector, Function<Resource, R> mapper) throws IOException, URISyntaxException {
        logger.atDebug().log("scan basePackage: {}", basePackage);
        Enumeration<URL> enumeration = getClassLoader().getResources(basePackage);
        while (enumeration.hasMoreElements()) {
            URL url = enumeration.nextElement();
            URI uri = url.toURI();
            String uriStr = removeTrailingSlash(uri.toString());
            String uriBasePath = uriStr.substring(0, uriStr.length() - basePackage.length());
            if (uriBasePath.startsWith("file:")) {
                scanFile(false, uriBasePath.substring(5), Paths.get(uri), collector, mapper);
            } else if (uriBasePath.startsWith("jar:")) {
                scanFile(true, uriBasePath, jarUriToPath(basePackage, uri), collector, mapper);
            } else {
                throw new UnsupportedOperationException("unsupported file: " + uriBasePath);
            }
        }
    }

    <R> void scanFile(boolean isJar, String base, Path root, List<R> collector, Function<Resource, R> mapper) throws IOException {
        String baseDir = removeTrailingSlash(base);
        try (Stream<Path> pathStream = Files.walk(root)) {
            pathStream.filter(Files::isRegularFile).forEach(file -> {
                Resource resource;
                if (isJar) {
                    resource = new Resource(baseDir, removeLeadingSlash(file.toString()));
                } else {
                    String path = file.toString();
                    String name = removeLeadingSlash(path.substring(baseDir.length())); // todo invalid name have occurred when inputting invalid baseDir(include space and chinese character)
                    resource = new Resource("file:" + path, name);
                }
                logger.atDebug().log("found resource: {}", resource);
                R r = mapper.apply(resource);
                if (r != null) {
                    collector.add(r);
                }
            });
        }
    }

    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        try {
            return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(jarUri).getPath(basePackagePath);
        }
    }

    ClassLoader getClassLoader() {
        ClassLoader classLoader;
        classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        return classLoader;
    }

    String removeTrailingSlash(String str) {
        if (str.endsWith("/") || str.endsWith("\\")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    String removeLeadingSlash(String str) {
        if (str.startsWith("/") || str.startsWith("\\")) {
            str = str.substring(1);
        }
        return str;
    }

}
