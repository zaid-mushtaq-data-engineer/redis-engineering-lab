package com.redislab;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@Import(TestcontainersConfig.class)
public class ItTestcontainersConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2"))
                .withExposedPorts(6379)
                .withCommand(
                        "redis-server",
                        "--maxmemory",
                        "128mb",
                        "--maxmemory-policy",
                        "noeviction",
                        "--save",
                        "",
                        "--appendonly",
                        "no");
    }
}
