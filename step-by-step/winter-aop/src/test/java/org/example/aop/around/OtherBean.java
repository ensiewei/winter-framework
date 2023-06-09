package org.example.aop.around;

import org.example.annotation.Autowired;
import org.example.annotation.Component;
import org.example.annotation.Order;

@Order(0)
@Component
public class OtherBean {

    public OriginBean origin;

    public OtherBean(@Autowired OriginBean origin) {
        this.origin = origin;
    }
}
