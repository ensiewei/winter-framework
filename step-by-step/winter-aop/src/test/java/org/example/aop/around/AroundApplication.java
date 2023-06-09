package org.example.aop.around;

import org.example.annotation.Bean;
import org.example.annotation.ComponentScan;
import org.example.annotation.Configuration;
import org.example.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AroundApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
