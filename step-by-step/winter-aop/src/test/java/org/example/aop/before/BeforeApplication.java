package org.example.aop.before;

import org.example.annotation.Bean;
import org.example.annotation.ComponentScan;
import org.example.annotation.Configuration;
import org.example.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class BeforeApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
