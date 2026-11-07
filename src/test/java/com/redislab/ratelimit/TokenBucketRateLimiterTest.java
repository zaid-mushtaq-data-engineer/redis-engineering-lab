package com.redislab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.config.AppProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimiterTest {

    private static final String IP = "127.0.0.1";

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() throws IOException {
        AppProperties properties = appProperties();
        limiter = new TokenBucketRateLimiter(redisTemplate, new RedisKeyBuilder(properties), properties);
    }

    @Test
    void mapsAllowedScriptResult() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 9L, 0L, 1_700_000_000L));

        Optional<RateLimitResult> result = limiter.tryConsume(IP);

        assertThat(result).isPresent();
        assertThat(result.get().allowed()).isTrue();
        assertThat(result.get().remaining()).isEqualTo(9);
        assertThat(result.get().limit()).isEqualTo(10);
    }

    @Test
    void mapsRejectedScriptResult() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 0L, 2L, 1_700_000_010L));

        Optional<RateLimitResult> result = limiter.tryConsume(IP);

        assertThat(result).isPresent();
        assertThat(result.get().allowed()).isFalse();
        assertThat(result.get().retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void returnsEmptyOnRedisError() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(limiter.tryConsume(IP)).isEmpty();
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                "dev",
                new AppProperties.Admin("key"),
                new AppProperties.Redis(
                        "redis-lab",
                        new AppProperties.Ttl(
                                Duration.ofMinutes(15), Duration.ofSeconds(30), Duration.ofMinutes(15))),
                new AppProperties.RateLimit(true, 10, 1, Duration.ofSeconds(60), false),
                new AppProperties.Pagination(20, 50),
                new AppProperties.Inventory(1_000_000_000L),
                new AppProperties.DevEndpoints(false),
                new AppProperties.Dev(false));
    }
}
