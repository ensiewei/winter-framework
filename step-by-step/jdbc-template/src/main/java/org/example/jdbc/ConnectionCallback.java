package org.example.jdbc;

import jakarta.annotation.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionCallback<T> {
    
     @Nullable
    T doConnection(Connection connection) throws SQLException;
    
}
