package org.example.jdbc;

import org.example.exception.DataAccessException;
import org.example.jdbc.tx.TransactionalUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {
    
    private final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) {
        if (clazz == String.class) {
            return (T) queryForObject(sql, StringRowMapper.instance, args);
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) queryForObject(sql, BooleanRowMapper.instance, args);
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            return (T) queryForObject(sql, NumberRowMapper.instance, args);
        }
        
        return queryForObject(sql, new BeanRowMapper<>(clazz), args);
    }
    
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
        return execute(
                preparedStatementCreator(sql, args),
                preparedStatement -> {
                    T t = null;
                    
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            if (t == null) {
                                t = rowMapper.mapRow(resultSet, resultSet.getRow());
                            } else {
                                throw new DataAccessException("Multiple rows found.");
                            }
                        }
                    }
                    
                    if (t == null) {
                        throw new DataAccessException("Empty result set.");
                    }
                    
                    return t;
                }
        );
    }
    
    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws DataAccessException {
        return queryForList(sql, new BeanRowMapper<>(clazz), args);
    }
    
    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        return execute(
                preparedStatementCreator(sql, args),
                preparedStatement -> {
                    List<T> list = new ArrayList<>();
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            list.add(rowMapper.mapRow(resultSet, resultSet.getRow()));
                        }
                    }
                    
                    return list;
                }
        );
    }
    
    public Number updateAndReturnGeneratedKey(String sql, Object... args) throws DataAccessException {
        return execute(
                connection -> {
                    PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    bindArgs(preparedStatement, args);
                    return preparedStatement;
                },
                preparedStatement -> {
                    int n = preparedStatement.executeUpdate();
                    if (n == 0) {
                        throw new DataAccessException("0 rows inserted.");
                    }
                    if (n > 1) {
                        throw new DataAccessException("Multiple rows inserted.");
                    }
                    
                    try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                        if (resultSet.next()) {
                            return (Number) resultSet.getObject(1);
                        }
                    }

                    throw new DataAccessException("Should not reach here.");
                }
        );
    }
    
    public int update(String sql, Object ... args) throws DataAccessException {
        return execute(preparedStatementCreator(sql, args), PreparedStatement::executeUpdate);
    }
    
    public <T>T execute(PreparedStatementCreator preparedStatementCreator, PreparedStatementCallback<T> action) throws DataAccessException {
        return execute(connection -> {
            try (PreparedStatement preparedStatement = preparedStatementCreator.createPreparedStatement(connection)) {
                return action.doInPreparedStatement(preparedStatement);
            }
        });
    }
    
    public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
        Connection currentConnection = TransactionalUtils.getCurrentConnection();
        if (currentConnection != null) {
            try {
                return action.doConnection(currentConnection);
            } catch (SQLException e) {
                throw new DataAccessException(e);
            }
        }

        try (Connection newConnection = dataSource.getConnection()) {
            final boolean autoCommit = newConnection.getAutoCommit();
            if (!autoCommit) {
                newConnection.setAutoCommit(true);
            }
            T result = action.doConnection(newConnection);
            if (!autoCommit) {
                newConnection.setAutoCommit(false);
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }
    
    private PreparedStatementCreator preparedStatementCreator(String sql, Object ... args) {
        return connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            bindArgs(preparedStatement, args);
            
            return preparedStatement;
        };
    }
    
    private void bindArgs(PreparedStatement preparedStatement, Object ... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            preparedStatement.setObject(i + 1, args[i]);
        }
    }
    
}

class StringRowMapper implements RowMapper<String> {

    static StringRowMapper instance = new StringRowMapper();

    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString(1);
    }
}

class BooleanRowMapper implements RowMapper<Boolean> {

    static BooleanRowMapper instance = new BooleanRowMapper();

    @Override
    public Boolean mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getBoolean(1);
    }
}

class NumberRowMapper implements RowMapper<Number> {

    static NumberRowMapper instance = new NumberRowMapper();

    @Override
    public Number mapRow(ResultSet rs, int rowNum) throws SQLException {
        return (Number) rs.getObject(1);
    }
}
