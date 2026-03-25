package com.nem12;

import com.nem12.config.Nem12Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(Nem12Properties.class)
@SpringBootApplication
public class Nem12ParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nem12ParserApplication.class, args);
    }
}
