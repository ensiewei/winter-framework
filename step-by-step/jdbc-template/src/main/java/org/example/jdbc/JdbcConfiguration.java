package org.example.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.annotation.Autowired;
import org.example.annotation.Bean;
import org.example.annotation.Configuration;
import org.example.annotation.Value;
import org.example.jdbc.tx.DataSourceTransactionManager;
import org.example.jdbc.tx.PlatformTransactionManager;
import org.example.jdbc.tx.TransactionalBeanPostProcessor;

import javax.sql.DataSource;

@Configuration
public class JdbcConfiguration {
    
    @Bean(destroyMethod = "close")
    DataSource dataSource(
            @Value("${winter.datasource.url}") String url, //
            @Value("${winter.datasource.username}") String username, //
            @Value("${winter.datasource.password}") String password, //
            @Value("${winter.datasource.driver-class-name:}") String driver, //
            @Value("${winter.datasource.maximum-pool-size:20}") int maximumPoolSize, //
            @Value("${winter.datasource.minimum-pool-size:1}") int minimumPoolSize, //
            @Value("${winter.datasource.connection-timeout:30000}") int connTimeout //
    ) {
        
        HikariConfig config = new HikariConfig();
        config.setAutoCommit(false);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        if (driver != null) {
            config.setDriverClassName(driver);
        }
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumPoolSize);
        config.setConnectionTimeout(connTimeout);
        
        return new HikariDataSource(config);
    }
    
    @Bean
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    @Bean
    TransactionalBeanPostProcessor transactionalBeanPostProcessor() {
        return new TransactionalBeanPostProcessor();
    }
    
    @Bean
    PlatformTransactionManager platformTransactionManager(@Autowired DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
}
