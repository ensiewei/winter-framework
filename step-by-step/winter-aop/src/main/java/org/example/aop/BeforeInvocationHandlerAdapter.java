package org.example.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public abstract class BeforeInvocationHandlerAdapter implements InvocationHandler {
    
    public abstract void before(Object bean, Method method, Object[] args);
    
    @Override
    public Object invoke(Object bean, Method method, Object[] args) throws Throwable {
        before(bean, method, args);
        
        return method.invoke(bean, args);
    }
}
