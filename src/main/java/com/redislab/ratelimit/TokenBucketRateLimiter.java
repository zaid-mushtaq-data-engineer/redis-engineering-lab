package com.redislab.ratelimit;

import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    private static final int TOKENS_PER_REQUEST = 1;

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final AppProperties appProperties;
    private final DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(
            StringRedisTemplate redisTemplate, RedisKeyBuilder keyBuilder, AppProperties appProperties)
            throws IOException {
        this.redisTemplate = redisTemplate;
        this.keyBuilder = keyBuilder;
        this.appProperties = appProperties;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(loadScript());
        this.script.setResultType(List.class);
    }

    public Optional<RateLimitResult> tryConsume(String clientIp) {
        AppProperties.RateLimit config = appProperties.rateLimit();
        String key = keyBuilder.rateLimit(clientIp);
        try {
            List<?> raw = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(config.capacity()),
                    String.valueOf(config.refillTokensPerSecond()),
                    String.valueOf(TOKENS_PER_REQUEST),
                    String.valueOf(config.keyTtlAfterFull().getSeconds()));
            if (raw == null || raw.size() < 4) {
                log.warn("RATE_LIMIT_REDIS_FAILURE ip={} reason=invalid_script_result", clientIp);
                return Optional.empty();
            }
            int allowed = toInt(raw.get(0));
            int remaining = toInt(raw.get(1));
            int retryAfter = toInt(raw.get(2));
            long resetAt = toLong(raw.get(3));
            boolean permitted = allowed == 1;
            return Optional.of(new RateLimitResult(permitted, remaining, retryAfter, resetAt, config.capacity()));
        } catch (DataAccessException ex) {
            log.warn(
                    "RATE_LIMIT_REDIS_FAILURE ip={} exception={}",
                    clientIp,
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String loadScript() throws IOException {
        ClassPathResource resource = new ClassPathResource("redis/token_bucket.lua");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }
}
