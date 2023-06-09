package org.example.context;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.PropertyResolver;
import org.example.ResourceResolver;
import org.example.annotation.*;
import org.example.exception.*;
import org.example.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;

public class AnnotationConfigApplicationContext implements ConfigurableApplicationContext {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, BeanDefinition> beans;
    private final PropertyResolver propertyResolver;
    private final Set<String> creatingBeanNames;
    private final List<BeanPostProcessor> beanPostProcessors;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        ApplicationContextUtils.setApplicationContext(this);
        
        this.propertyResolver = propertyResolver;

        Set<String> beanClassNames = scanForClassNames(configClass);

        this.beans = createBeanDefinitions(beanClassNames);

        this.creatingBeanNames = new HashSet<>();

        this.beanPostProcessors = new ArrayList<>();
        
        this.beans.values().stream().filter(this::isConfiguration).sorted().forEach(this::createBeanAsEarlySingleton);
        
        this.beanPostProcessors.addAll(this.beans.values().stream().filter(this::isBeanPostProcessorDefinition).sorted().map(beanDefinition -> (BeanPostProcessor) createBeanAsEarlySingleton(beanDefinition)).toList());
        
        this.beans.values().stream().sorted().forEach(beanDefinition -> {
            if (beanDefinition.getInstance() == null) {
                createBeanAsEarlySingleton(beanDefinition);
            }
        });
        
        this.beans.values().forEach(this::injectBean);
        
        this.beans.values().forEach(this::initBean);
    }
    
    private Set<String> scanForClassNames(Class<?> configClass) {
        Set<String> beanClassNames = new HashSet<>();

        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        String[] scanPackages = scan == null || scan.value().length == 0 ? new String[] { configClass.getPackage().getName() } : scan.value();
        for (String scanPackage : scanPackages) {
            logger.atDebug().log("scan package: {}", scanPackage);

            ResourceResolver resourceResolver = new ResourceResolver(scanPackage);
            List<String> classList = resourceResolver.scan(resource -> {
                String name = resource.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });

            beanClassNames.addAll(classList);
        }

        Import importConfig = ClassUtils.findAnnotation(configClass, Import.class);
        if (importConfig != null) {
            for (Class<?> importClass : importConfig.value()) {
                beanClassNames.add(importClass.getName());
            }
        }

        return beanClassNames;
    }

    Map<String, BeanDefinition> createBeanDefinitions(Set<String> beanClassNames) {
        Map<String, BeanDefinition> beanDefinitions = new HashMap<>();
        for (String beanClassName : beanClassNames) {
            Class<?> beanClass;
            try {
                beanClass = Class.forName(beanClassName);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException();
            }
            if (beanClass.isAnnotation() || beanClass.isEnum() || beanClass.isInterface() || beanClass.isRecord()) {
                continue;
            }

            Component component = ClassUtils.findAnnotation(beanClass, Component.class);
            if (component != null) {
                logger.atDebug().log("found component: {}", beanClass.getName());

                int modifiers = beanClass.getModifiers();
                if (Modifier.isAbstract(modifiers)) {
                    throw new BeanDefinitionException("@Component class " + beanClass.getName() + " must not be abstract.");
                } else if (Modifier.isPrivate(modifiers)) {
                    throw new BeanDefinitionException("@Component class " + beanClass.getName() + " must not be private.");
                }
                
                BeanDefinition beanDefinition = createComponent(beanClass);
                addBeanDefinition(beanDefinition, beanDefinitions);
                logger.atDebug().log("define bean: {}", beanDefinition);

                Configuration configuration = ClassUtils.findAnnotation(beanClass, Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanDefinition.getName(), beanClass, beanDefinitions);
                }
            }
        }

        return beanDefinitions;
    }

    private void scanFactoryMethods(String factoryBeanName, Class<?> beanClass, Map<String, BeanDefinition> beanDefinitions) {
        for (Method method : beanClass.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean == null) {
                continue;
            }

            int modifiers = method.getModifiers();
            if (Modifier.isAbstract(modifiers)) {
                throw new BeanDefinitionException("@Bean method " + beanClass.getName() + "." + method.getName() + " must not be abstract.");
            }
            if (Modifier.isFinal(modifiers)) {
                throw new BeanDefinitionException("@Bean method " + beanClass.getName() + "." + method.getName() + " must not be final.");
            }
            if (Modifier.isPrivate(modifiers)) {
                throw new BeanDefinitionException("@Bean method " + beanClass.getName() + "." + method.getName() + " must not be private.");
            }

            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive()) {
                throw new BeanDefinitionException("@Bean method " + beanClass.getName() + "." + method.getName() + " must not return primitive type.");
            }
            if (returnType == void.class || returnType == Void.class) {
                throw new BeanDefinitionException("@Bean method " + beanClass.getName() + "." + method.getName() + " must not return void.");
            }

            method.setAccessible(true);
            BeanDefinition beanDefinition = new BeanDefinition(
                    ClassUtils.getBeanName(method),
                    returnType,
                    null,
                    null,
                    factoryBeanName,
                    method,
                    getOrder(method),
                    method.isAnnotationPresent(Primary.class),
                    bean.initMethod().isEmpty() ? null : bean.initMethod(),
                    bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                    null,
                    null
            );
            
            addBeanDefinition(beanDefinition, beanDefinitions);
            
            logger.atDebug().log("define bean: {}", beanDefinition);
        }
    }

    @Override
    public Object createBeanAsEarlySingleton(BeanDefinition beanDefinition) {
        if (beanDefinition.getInstance() != null) {
            return beanDefinition.getInstance();
        }
        
        logger.atDebug().log("Try create bean '{}' as early singleton: {}", beanDefinition.getName(), beanDefinition.getBeanClass().getName());

        if (!creatingBeanNames.add(beanDefinition.getName())) {
            throw new UnsatisfiedDependencyException();
        }

        Executable createFunction;
        if (beanDefinition.getFactoryName() == null) {
            createFunction = beanDefinition.getConstructor();
        } else {
            createFunction = beanDefinition.getFactoryMethod();
        }
        
        Object instance;
        Object[] args = getArgs(beanDefinition, createFunction);
        if (beanDefinition.getFactoryName() == null) {
            try {
                instance = beanDefinition.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", beanDefinition.getName(), beanDefinition.getBeanClass().getName()), e);
            }
        } else {
            Object factoryBean = getBean(beanDefinition.getFactoryName());
            try {
                instance = beanDefinition.getFactoryMethod().invoke(factoryBean, args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", beanDefinition.getName(), beanDefinition.getBeanClass().getName()), e);
            }
        }
        
        beanDefinition.setInstance(instance);

        logger.atDebug().log("bean initialized: {}", beanDefinition);

        for (BeanPostProcessor beanPostProcessor : this.beanPostProcessors) {
            Object processed = beanPostProcessor.postProcessBeforeInitialization(beanDefinition.getRequiredInstance(), beanDefinition.getName());
            if (processed == null) {
                throw new BeanCreationException(String.format("PostBeanProcessor returns null when process bean '%s' by %s", beanDefinition.getName(), beanPostProcessor));
            }
            if (processed != beanDefinition.getRequiredInstance()) {
                logger.atDebug().log("Bean '{}' was replaced by post processor {}.", beanDefinition.getName(), beanPostProcessor.getClass().getName());
                beanDefinition.setInstance(processed);
            }
        }

        return beanDefinition.getRequiredInstance();
    }
    
    private void injectBean(BeanDefinition beanDefinition) {
        try {
            injectProperties(beanDefinition, beanDefinition.getBeanClass(), getOriginalInstance(beanDefinition));
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }
    
    private void injectProperties(BeanDefinition beanDefinition, Class<?> clazz, Object instance) throws ReflectiveOperationException {
        for (Field field : clazz.getDeclaredFields()) {
            tryInjectProperties(beanDefinition, clazz, instance, field);
        }
        for (Method method : clazz.getDeclaredMethods()) {
            tryInjectProperties(beanDefinition, clazz, instance, method);
        }
        
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            injectProperties(beanDefinition, superclass, instance);
        }
    }
    
    private void tryInjectProperties(BeanDefinition beanDefinition, Class<?> clazz, Object instance, AccessibleObject accessibleObject) throws ReflectiveOperationException {
        Value value = accessibleObject.getAnnotation(Value.class);
        Autowired autowired = accessibleObject.getAnnotation(Autowired.class);
        
        if (value == null && autowired == null) {
            return;
        }
        
        Field field = null;
        Method method = null;
        if (accessibleObject instanceof Field f) {
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        } else if (accessibleObject instanceof Method m) {
            checkFieldOrMethod(m);
            if (m.getParameterCount() != 1) {
                throw new BeanDefinitionException(String.format("Cannot inject a non-setter method %s for bean '%s': %s",
                        m.getName(), beanDefinition.getName(), beanDefinition.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        } else {
            throw new BeanCreationException(String.format("unsupported accessibleObject: %s when inject for bean '%s': %s",
                    accessibleObject.getClass().getName(), beanDefinition.getName(), clazz.getName()));
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];
        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                    clazz.getSimpleName(), accessibleName, beanDefinition.getName(), beanDefinition.getBeanClass().getName()));
        }
        
        if (value != null) {
            Object propValue = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
            if (field != null) {
                logger.atDebug().log("Field injection: {}.{} = {}", beanDefinition.getBeanClass().getName(), accessibleName, propValue);
                field.set(instance, propValue);
            }
            
            if (method != null) {
                logger.atDebug().log("Method injection: {}.{} ({})", beanDefinition.getBeanClass().getName(), accessibleName, propValue);
                method.invoke(instance, propValue);
            }
        }
        
        if (autowired != null) {
            boolean required = autowired.value();
            String name = autowired.name();
            Object dependency = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);
            if (required && dependency == null) {
                throw new UnsatisfiedDependencyException(String.format("Dependency bean not found when inject %s.%s for bean '%s': %s",
                        clazz.getSimpleName(), accessibleName, beanDefinition.getName(), beanDefinition.getBeanClass().getName()));
            }
            if (dependency != null) {
                if (field != null) {
                    logger.atDebug().log("Field injection: {}.{} = {}", beanDefinition.getBeanClass().getName(), accessibleName, dependency);
                    field.set(instance, dependency);
                }
                
                if (method != null) {
                    logger.atDebug().log("Field injection: {}.{} = {}", beanDefinition.getBeanClass().getName(), accessibleName, dependency);
                    method.invoke(instance, dependency);
                }
                
            }
        }
    }

    private void initBean(BeanDefinition beanDefinition) {
        callMethodWithoutArgs(getOriginalInstance(beanDefinition), beanDefinition.getInitMethod(), beanDefinition.getInitMethodName());
    }
    
    private void callMethodWithoutArgs(Object instance, Method method, String methodName) {
        if (method != null) {
            if (method.getParameterCount() != 0) {
                throw new BeanCreationException(String.format("Method '%s' must not have argument", method.getName()));
            }
            try {
                method.invoke(instance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (methodName != null) {
            Method namedMethod = ClassUtils.getNamedMethod(instance.getClass(), methodName);
            namedMethod.setAccessible(true);
            if (namedMethod.getParameterCount() != 0) {
                throw new BeanCreationException(String.format("Method '%s' must not have argument", namedMethod.getName()));
            }
            try {
                namedMethod.invoke(instance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }
    
    private Object[] getArgs(Object instance, Executable function) {
        Parameter[] parameters = function.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Autowired autowired = parameter.getAnnotation(Autowired.class);
            Value value = parameter.getAnnotation(Value.class);

            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when call function '%s': %s.", instance.getClass().getName(), function.getName()));
            }
            if (value != null && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when call function '%s': %s.", instance.getClass().getName(), function.getName()));
            }

            Class<?> type = parameter.getType();
            if (value != null) {
                args[i] = this.propertyResolver.getRequiredProperty(value.value(), type);
            }

            if (autowired != null) {
                boolean required = autowired.value();
                String name = autowired.name();
                BeanDefinition dependency = name.isEmpty() ? findBeanDefinition(type) : findBeanDefinition(name, type);

                if (required && dependency == null) {
                    throw new BeanCreationException(String.format("Missing autowired bean with type '%s' when call function '%s': %s.", type.getName(),
                            instance.getClass().getName(), function.getName()));
                }

                if (dependency != null) {
                    if (isConfiguration(dependency)) {
                        throw new BeanCreationException(String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s at call function '%s': %s.",
                                dependency.getName(), dependency.getBeanClass().getName(), instance.getClass().getName(), function.getName()));
                    }
                    if (dependency.getInstance() == null) {
                        dependency.setInstance(createBeanAsEarlySingleton(dependency));
                    }
                    args[i] = dependency.getInstance();
                } else {
                    args[i] = null;
                }
            }
        }
        
        return args;
    }
    
    private boolean isConfiguration(BeanDefinition beanDefinition) {
        return ClassUtils.findAnnotation(beanDefinition.getBeanClass(), Configuration.class) != null;
    }
    
    private boolean isBeanPostProcessorDefinition(BeanDefinition beanDefinition) {
        return BeanPostProcessor.class.isAssignableFrom(beanDefinition.getBeanClass());
    }

    private int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order != null ? order.value(): Integer.MAX_VALUE;
    }

    private int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order != null ? order.value(): Integer.MAX_VALUE;
    }

    private Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            constructors = clazz.getDeclaredConstructors();
            if (constructors.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }
        if (constructors.length != 1) {
            throw new BeanDefinitionException("More than one public constructor found in class " + clazz.getName() + ".");
        }

        return constructors[0];
    }
    
    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition beanDefinition = this.beans.get(name);
        if (beanDefinition == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", name));
        }
        return (T) beanDefinition.getRequiredInstance();
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) {
        T bean = findBean(name, requiredType);
        if (bean == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        
        return bean;
    }

    @Override
    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(beanDefinition -> (T) beanDefinition.getRequiredInstance()).toList();
    }

    @Override
    public void close() {
        logger.atInfo().log("Closing {}...", this.getClass().getName());
        
        this.beans.values().forEach(beanDefinition -> callMethodWithoutArgs(getOriginalInstance(beanDefinition), beanDefinition.getDestroyMethod(), beanDefinition.getDestroyMethodName()));
        
        this.beans.clear();

        logger.atInfo().log("{} closed.", this.getClass().getName());
        
        ApplicationContextUtils.setApplicationContext(null);
    }

    private Object getOriginalInstance(BeanDefinition beanDefinition) {
        Object proxiedInstance = beanDefinition.getRequiredInstance();
        
        List<BeanPostProcessor> reversedBeanProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedBeanProcessors);
        for (BeanPostProcessor beanPostProcessor : reversedBeanProcessors) {
            Object restoredInstance = beanPostProcessor.postProcessOnSetProperty(proxiedInstance, beanDefinition.getName());
            if (restoredInstance != proxiedInstance) {
                logger.atDebug().log("BeanPostProcessor {} specified injection from {} to {}.",
                        beanPostProcessor.getClass().getSimpleName(), proxiedInstance.getClass().getSimpleName(), restoredInstance.getClass().getSimpleName());
                proxiedInstance = restoredInstance;
            }
        }
        
        return proxiedInstance;
    }
    
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition beanDefinition = findBeanDefinition(requiredType);
        if (beanDefinition == null) {
            return null;
        }
        
        return (T) beanDefinition.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition beanDefinition = findBeanDefinition(name, requiredType);
        if (beanDefinition == null) {
            return null;
        }

        return (T) beanDefinition.getRequiredInstance();
    }

    @Nullable
    @Override
    public BeanDefinition findBeanDefinition(String beanName) {
        return this.beans.get(beanName);
    }

    @Override
    public List<BeanDefinition> findBeanDefinitions(Class<?> requiredType) {
        return this.beans.values().stream().filter(beanDefinition -> requiredType.isAssignableFrom(beanDefinition.getBeanClass())).sorted().toList();
    }

    @Nullable
    @Override
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> beanDefinitions = findBeanDefinitions(type);
        if (beanDefinitions.isEmpty()) {
            return null;
        }
        if (beanDefinitions.size() == 1) {
            return beanDefinitions.get(0);
        }
        List<BeanDefinition> primaryDefinitions = beanDefinitions.stream().filter(BeanDefinition::isPrimary).toList();
        if (primaryDefinitions.size() == 1) {
            return primaryDefinitions.get(0);
        }
        if (primaryDefinitions.isEmpty()) {
            throw new RuntimeException(String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else {
            throw new RuntimeException(String.format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }

    @Nullable
    @Override
    public BeanDefinition findBeanDefinition(String name, Class<?> type) {
        BeanDefinition beanDefinition = findBeanDefinition(name);

        if (beanDefinition == null) {
            return null;
        }

        if (!type.isAssignableFrom(beanDefinition.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", type.getName(),
                    name, beanDefinition.getBeanClass().getName()));
        }

        return beanDefinition;
    }


    public void addBeanDefinition(BeanDefinition beanDefinition, Map<String, BeanDefinition> beanDefinitions) {
        if (beanDefinitions.put(beanDefinition.getName(), beanDefinition) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + beanDefinition.getName());
        }
    }

    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field field) {
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            }
            if (m instanceof Method) {
                logger.atWarn().log("Inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");
            }
        }
    }

    public BeanDefinition createComponent(Class<?> beanClass) {
        Constructor<?> constructor = getSuitableConstructor(beanClass);
        constructor.setAccessible(true);
        Method initMethod = ClassUtils.findAnnotationMethod(beanClass, PostConstruct.class);
        if (initMethod != null) {
            initMethod.setAccessible(true);
        }
        Method destroyMethod = ClassUtils.findAnnotationMethod(beanClass, PreDestroy.class);
        if (destroyMethod != null) {
            destroyMethod.setAccessible(true);
        }
        return new BeanDefinition(
                ClassUtils.getBeanName(beanClass),
                beanClass,
                null,
                constructor,
                null,
                null,
                getOrder(beanClass),
                beanClass.isAnnotationPresent(Primary.class),
                null,
                null,
                initMethod,
                destroyMethod
        );
    }

}
