package org.example.scan.primary;

import org.example.annotation.Bean;
import org.example.annotation.Configuration;
import org.example.annotation.Primary;

@Configuration
public class PrimaryConfiguration {

    @Primary
    @Bean
    DogBean husky() {
        return new DogBean("Husky");
    }

    @Bean
    DogBean teddy() {
        return new DogBean("Teddy");
    }
}
