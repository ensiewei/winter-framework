package org.example.jdbc.tx;

import org.example.annotation.Transactional;
import org.example.aop.AnnotationProxyBeanPostProcessor;

public class TransactionalBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Transactional> {
}
