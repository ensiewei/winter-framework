package org.example.jdbc;

import org.example.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BeanRowMapper<T> implements RowMapper<T> {

    final private Logger logger = LoggerFactory.getLogger(getClass());
    
    private final Class<T> clazz;
    private final Constructor<T> constructor;
    Map<String, Field> fields;
    Map<String, Method> methods;
    

    public BeanRowMapper(Class<T> clazz) {
        this.clazz = clazz;
        
        try {
            this.constructor = clazz.getConstructor();
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("No public default constructor found for class %s when build BeanRowMapper.", clazz.getName()), e);
        }
        
        this.fields = new HashMap<>();
        for (Field field : clazz.getFields()) {
            String name = field.getName();
            this.fields.put(name, field);
            logger.atDebug().log("Add row mapping: {} to field {}", name, name);
        }
        
        this.methods = new HashMap<>();
        for (Method method : clazz.getMethods()) {
            Parameter[] ps = method.getParameters();
            if (ps.length == 1) {
                String name = method.getName();
                if (name.length() > 4 && name.startsWith("set")) {
                    String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    this.methods.put(prop, method);
                    logger.atDebug().log("Add row mapping: {} to {}({})", prop, name, ps[0].getType().getSimpleName());
                }
            }
        }


    }

    @Override
    public T mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        T bean;

        try {
            bean = this.constructor.newInstance();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String label = metaData.getColumnLabel(i);
                Method method = this.methods.get(label);
                if (method != null) {
                    method.invoke(bean, resultSet.getObject(label));
                } else {
                    Field field = this.fields.get(label);
                    if (field != null) {
                        field.set(bean, resultSet.getObject(label));
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("Could not map result set to class %s", this.clazz.getName()), e);
        }

        logger.atDebug().log(String.format("map result set to class %s as %s", this.clazz.getName(), bean));
        
        return bean;
    }
    
}
