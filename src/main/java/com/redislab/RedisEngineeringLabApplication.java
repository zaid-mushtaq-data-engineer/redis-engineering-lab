package com.redislab;

import com.redislab.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class RedisEngineeringLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisEngineeringLabApplication.class, args);
    }
}
