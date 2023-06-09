package org.example;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

public class PropertyResolver {

    private final Map<String, String> properties = new HashMap<>();
    private final Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    public PropertyResolver(Properties props) {
        this.properties.putAll(System.getenv());

        for (String propertyName : props.stringPropertyNames()) {
            this.properties.put(propertyName, props.getProperty(propertyName));
        }

        // register converters:
        converters.put(String.class, s -> s);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Boolean.class, Boolean::valueOf);

        converters.put(byte.class, Byte::parseByte);
        converters.put(Byte.class, Byte::valueOf);

        converters.put(short.class, Short::parseShort);
        converters.put(Short.class, Short::valueOf);

        converters.put(int.class, Integer::parseInt);
        converters.put(Integer.class, Integer::valueOf);

        converters.put(long.class, Long::parseLong);
        converters.put(Long.class, Long::valueOf);

        converters.put(float.class, Float::parseFloat);
        converters.put(Float.class, Float::valueOf);

        converters.put(double.class, Double::parseDouble);
        converters.put(Double.class, Double::valueOf);

        converters.put(LocalDate.class, LocalDate::parse);
        converters.put(LocalTime.class, LocalTime::parse);
        converters.put(LocalDateTime.class, LocalDateTime::parse);
        converters.put(ZonedDateTime.class, ZonedDateTime::parse);
        converters.put(Duration.class, Duration::parse);
        converters.put(ZoneId.class, ZoneId::of);

    }

    public <T> void registerConverter(Class<T> clazz, Function<String, Object> function) {
        converters.put(clazz, function);
    }

    public String getProperty(String propertyName) {
        String value;

        PropertyExpr propertyExpr = parsePropertyExpr(propertyName);
        if (propertyExpr == null) {
            value = this.properties.get(propertyName);
        } else {
            if (propertyExpr.defaultValue() == null) {
                value = getRequiredProperty(propertyExpr.propertyName());
            } else {
                value = getProperty(propertyExpr.propertyName(), propertyExpr.defaultValue());
            }
        }

        return parseValue(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String propertyName, Class<T> propertyClass) {
        String value = getProperty(propertyName);

        Function<String, Object> function = this.converters.get(propertyClass);
        if (function == null) {
            throw new IllegalArgumentException("Unsupported value type: " + propertyClass.getName());
        }

        return (T) function.apply(value);
    }

    public String getRequiredProperty(String propertyName) {
        String value = getProperty(propertyName);
        return Objects.requireNonNull(value, "Property '" + propertyName + "' not found.");
    }

    public <T> T getRequiredProperty(String propertyName, Class<T> propertyClass) {
        T t = getProperty(propertyName, propertyClass);
        return Objects.requireNonNull(t, "Property '" + propertyName + "' not found.");
    }

    public String getProperty(String propertyName, String defaultValue) {
        String propertyValue = getProperty(propertyName);
        return propertyValue == null ? parseValue(defaultValue) : propertyValue;
    }

    public String parseValue(String value) {
        PropertyExpr propertyExpr = parsePropertyExpr(value);
        if (propertyExpr == null) {
            return value;
        }

        return getProperty(value);
    }

    public PropertyExpr parsePropertyExpr(String propertyName) {
        if (propertyName == null) {
            return null;
        }

        if (propertyName.startsWith("${") && propertyName.endsWith("}")) {
            int i = propertyName.indexOf(":");
            if (i == -1) {
                return new PropertyExpr(propertyName.substring(2, propertyName.length() - 1), null);
            } else {
                return new PropertyExpr(propertyName.substring(2, i), propertyName.substring(i + 1, propertyName.length() - 1));
            }
        }

        return null;
    }


    record PropertyExpr(String propertyName, String defaultValue) {

    }

}
