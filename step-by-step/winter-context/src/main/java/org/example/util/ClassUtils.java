package org.example.util;

import org.example.annotation.Bean;
import org.example.annotation.Component;
import org.example.exception.BeanDefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ClassUtils {

    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A a = target.getAnnotation(annoClass);

        for (Annotation annotation : target.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (!"java.lang.annotation".equals(type.getPackageName())) {
                A found = findAnnotation(type, annoClass);
                if (found != null) {
                    if (a != null) {
                        throw new BeanDefinitionException("Duplicate @" + annoClass.getSimpleName() + " found on class " + target.getSimpleName());
                    }
                    a = found;
                }
            }
        }

        return a;
    }
    public static <A extends Annotation> Method findAnnotationMethod(Class<?> clazz, Class<A> annoClass) {
        List<Method> methodList = Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annoClass))
                .peek(method -> {
                    if (method.getParameterCount() != 0) {
                        throw new BeanDefinitionException(
                                String.format("Method '%s' with @%s must not have argument: %s", method.getName(), annoClass.getSimpleName(), clazz.getName())
                        );
                    }
                })
                .toList();

        if (methodList.isEmpty()) {
            return null;
        }

        if (methodList.size() != 1) {
            throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s", annoClass.getSimpleName(), clazz.getName()));
        }

        return methodList.get(0);
    }

    public static String getBeanName(Class<?> beanClass) {
        String beanName = "";

        Component component = beanClass.getAnnotation(Component.class);
        if (component != null) {
            beanName = component.value();
        } else {
            for (Annotation annotation : beanClass.getAnnotations()) {
                if (findAnnotation(annotation.annotationType(), Component.class) != null) {
                    try {
                        beanName = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    } catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }

        if (beanName.isEmpty()) {
            beanName = beanClass.getSimpleName();
            beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);
        }

        return beanName;
    }

    public static String getBeanName(Method method) {
        String name = "";

        Bean bean = method.getAnnotation(Bean.class);
        if (bean != null) {
            name = bean.value();
        }

        if (name.isEmpty()) {
            name = method.getName();
        }

        return name;
    }
    
    public static Method getNamedMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new BeanDefinitionException(String.format("Method '%s' not found in class: %s", methodName, clazz.getName()));
        }
    }

}
