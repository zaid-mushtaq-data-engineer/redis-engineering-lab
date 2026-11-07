package com.redislab.support.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import jakarta.persistence.EntityManagerFactory;

@TestConfiguration
@Profile("it")
public class IntegrationTestConfiguration {

    @Bean
    TestDataFactory testDataFactory(TestRestTemplate rest, ObjectMapper objectMapper) {
        return new TestDataFactory(rest, objectMapper);
    }

    @Bean
    RedisTestSupport redisTestSupport(
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            com.redislab.cache.key.RedisKeyBuilder keyBuilder) {
        return new RedisTestSupport(redisTemplate, keyBuilder);
    }

    @Bean
    StatisticsSupport statisticsSupport(EntityManagerFactory entityManagerFactory) {
        return new StatisticsSupport(entityManagerFactory);
    }
}
