package com.redislab.redis;

import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.common.exception.RedisUnavailableException;
import com.redislab.redis.dto.CreateSandboxKeyRequest;
import com.redislab.redis.dto.JdkSerializationDemoResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-endpoints.enabled", havingValue = "true")
public class RedisSerializationDemoService {

    private static final String JDK_DEMO_KEY = "jdk-demo";
    private static final String STRING_DEMO_KEY = "string-demo";

    private final RedisTemplate<String, Object> jdkSerializationRedisTemplate;
    private final RedisSandboxService sandboxService;
    private final RedisKeyBuilder keyBuilder;

    public RedisSerializationDemoService(
            RedisTemplate<String, Object> jdkSerializationRedisTemplate,
            RedisSandboxService sandboxService,
            RedisKeyBuilder keyBuilder) {
        this.jdkSerializationRedisTemplate = jdkSerializationRedisTemplate;
        this.sandboxService = sandboxService;
        this.keyBuilder = keyBuilder;
    }

    public JdkSerializationDemoResponse runDemo() {
        String jdkStoredAs = keyBuilder.sandbox(JDK_DEMO_KEY);
        try {
            jdkSerializationRedisTemplate.opsForValue().set(jdkStoredAs, "hello-from-jdk");
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }

        sandboxService.create(new CreateSandboxKeyRequest(STRING_DEMO_KEY, "hello-from-string", null));

        return new JdkSerializationDemoResponse(
                JDK_DEMO_KEY,
                jdkStoredAs,
                keyBuilder.sandbox(STRING_DEMO_KEY),
                "Run redis-cli GET on both keys: jdk-demo shows binary (\\xac\\xed...); string-demo shows plain text.");
    }
}
