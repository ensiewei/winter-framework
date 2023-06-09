package org.example;

import org.example.annotation.ComponentScan;
import org.example.annotation.Configuration;
import org.example.annotation.Import;
import org.example.jdbc.JdbcConfiguration;
import org.example.web.WebMvcConfiguration;

@ComponentScan
@Configuration
@Import({ JdbcConfiguration.class, WebMvcConfiguration.class })
public class BootConfiguration {

}
