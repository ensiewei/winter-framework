package org.example.jdbc.without.tx;

import org.example.annotation.*;
import org.example.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

@ComponentScan
@Configuration
public class JdbcWithoutTxApplication {

    @Bean(destroyMethod = "close")
    DataSource dataSource(
            // properties:
            @Value("${winter.datasource.url}") String url, //
            @Value("${winter.datasource.username}") String username, //
            @Value("${winter.datasource.password}") String password, //
            @Value("${winter.datasource.driver-class-name:}") String driver, //
            @Value("${winter.datasource.maximum-pool-size:20}") int maximumPoolSize, //
            @Value("${winter.datasource.minimum-pool-size:1}") int minimumPoolSize, //
            @Value("${winter.datasource.connection-timeout:30000}") int connTimeout //
    ) {
        var config = new HikariConfig();
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
}
