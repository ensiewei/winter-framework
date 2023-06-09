package org.example.jdbc.with.tx;

import org.example.annotation.ComponentScan;
import org.example.annotation.Configuration;
import org.example.annotation.Import;
import org.example.jdbc.JdbcConfiguration;

@ComponentScan
@Configuration
@Import(JdbcConfiguration.class)
public class JdbcWithTxApplication {

}
