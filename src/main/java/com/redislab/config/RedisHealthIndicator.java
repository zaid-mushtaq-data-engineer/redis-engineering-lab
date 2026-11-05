package com.redislab.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redis")
@Profile("!test")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up().build();
            }
            return Health.status("DEGRADED").build();
        } catch (Exception ex) {
            return Health.status("DEGRADED").build();
        }
    }
}
