package org.example.scan.nested;

import org.example.annotation.Component;

@Component
public class OuterBean {

    @Component
    public static class NestedBean {

    }
}
