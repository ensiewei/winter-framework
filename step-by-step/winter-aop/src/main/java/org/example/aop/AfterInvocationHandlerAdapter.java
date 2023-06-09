package org.example.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public abstract class AfterInvocationHandlerAdapter implements InvocationHandler {

    public abstract Object after(Object bean, Object returnValue, Method method, Object[] args);
    
    @Override
    public Object invoke(Object bean, Method method, Object[] args) throws Throwable {
        Object returnValue = method.invoke(bean, args);
        
        return after(bean, returnValue, method, args);
    }
}
