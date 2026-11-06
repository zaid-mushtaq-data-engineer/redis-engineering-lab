package com.redislab.cache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.cache.support.CacheJsonCodec;
import com.redislab.cache.support.RedisFailSafe;
import com.redislab.common.response.PageResponse;
import com.redislab.config.AppProperties;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.entity.ProductStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ProductListCacheServiceTest {

    private static final String KEY_PAGE0 = "redis-lab:dev:products:page:0:size:20";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> values;

    private ProductListCacheService cache;
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
        cache = new ProductListCacheService(
                redisTemplate, new RedisKeyBuilder(properties), codec, new RedisFailSafe(), properties);
    }

    @Test
    void getReturnsParsedPage() throws Exception {
        PageResponse<ProductResponse> stored = new PageResponse<>(
                List.of(new ProductResponse(1L, "Mouse", "d", new BigDecimal("1.00"), ProductStatus.ACTIVE)),
                0,
                20,
                1,
                1,
                true);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY_PAGE0)).thenReturn(codec.toJson(stored));

        assertThat(cache.get(0, 20)).contains(stored);
    }

    @Test
    void putSetsJsonWithListTtl() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        PageResponse<ProductResponse> response = new PageResponse<>(List.of(), 0, 20, 0, 0, true);

        cache.put(0, 20, response);

        verify(values).set(eq(KEY_PAGE0), any(String.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    void putSkipsEmptyOutOfRangePage() {
        PageResponse<ProductResponse> empty = new PageResponse<>(List.of(), 999, 20, 100, 5, true);

        cache.put(999, 20, empty);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void corruptJsonIsDeletedAndTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY_PAGE0)).thenReturn("{not-json");

        assertThat(cache.get(0, 20)).isEmpty();
        verify(redisTemplate).delete(KEY_PAGE0);
    }

    @Test
    void evictAllUsesScanCallback() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

        cache.evictAll();

        verify(redisTemplate).execute(any(RedisCallback.class));
    }
}
