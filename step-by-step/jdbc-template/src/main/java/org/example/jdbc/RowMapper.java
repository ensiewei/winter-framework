package org.example.jdbc;

import jakarta.annotation.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {
    
    @Nullable
    T mapRow(ResultSet resultSet, int rowNum) throws SQLException;
    
}
