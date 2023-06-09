package org.example;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;

public class ResourceResolverTest {

    @Test
    public void scanClass() {
        ResourceResolver resourceResolver = new ResourceResolver("org.example");
        for (Integer integer : resourceResolver.scan(Record::hashCode)) {
            System.out.println(integer);
        }
    }

    @Test
    public void scanJar() {
        ResourceResolver jarResourceResolver = new ResourceResolver(PostConstruct.class.getPackageName());
        for (Integer integer : jarResourceResolver.scan(Record::hashCode)) {
            System.out.println(integer);
        }
    }

}
