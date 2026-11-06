package com.redislab.cache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.cache.support.CacheJsonCodec;
import com.redislab.cache.support.RedisFailSafe;
import com.redislab.config.AppProperties;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.entity.ProductStatus;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    private static final String KEY = "redis-lab:dev:product:1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> values;

    private ProductCacheService cache;
    private CacheJsonCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CacheJsonCodec(new ObjectMapper());
        AppProperties properties = new AppProperties(
                "dev",
                new AppProperties.Admin("test-key"),
                new AppProperties.Redis(
                        "redis-lab",
                        new AppProperties.Ttl(Duration.ofMinutes(15), Duration.ofSeconds(30), Duration.ofMinutes(15))),
                new AppProperties.RateLimit(false, 10, 1, Duration.ofSeconds(60), false),
                new AppProperties.Pagination(20, 50),
                new AppProperties.Inventory(1_000_000_000L),
                new AppProperties.DevEndpoints(false),
                new AppProperties.Dev(false));
        cache = new ProductCacheService(
                redisTemplate, new RedisKeyBuilder(properties), codec, new RedisFailSafe(), properties);
    }

    @Test
    void getReturnsParsedValue() throws Exception {
        ProductResponse stored =
                new ProductResponse(1L, "Mouse", "desc", new BigDecimal("10.00"), ProductStatus.ACTIVE);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(codec.toJson(stored));

        assertThat(cache.get(1L)).contains(stored);
    }

    @Test
    void putSetsJsonWithProductTtl() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        ProductResponse response =
                new ProductResponse(1L, "Mouse", "desc", new BigDecimal("10.00"), ProductStatus.ACTIVE);

        cache.put(response);

        verify(values).set(eq(KEY), org.mockito.ArgumentMatchers.contains("\"Mouse\""), eq(Duration.ofMinutes(15)));
    }

    @Test
    void evictUnlinksProductKey() {
        cache.evict(1L);

        verify(redisTemplate).unlink(KEY);
    }

    @Test
    void corruptJsonIsDeletedAndTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn("{not-json");

        assertThat(cache.get(1L)).isEmpty();
        verify(redisTemplate).delete(KEY);
    }
}
