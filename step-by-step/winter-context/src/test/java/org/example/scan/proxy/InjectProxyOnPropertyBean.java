package org.example.scan.proxy;

import org.example.annotation.Autowired;
import org.example.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
