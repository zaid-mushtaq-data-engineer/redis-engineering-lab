package com.redislab.cache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.cache.support.CacheJsonCodec;
import com.redislab.cache.support.RedisFailSafe;
import com.redislab.cache.support.RedisFailSafe.ReadResult;
import com.redislab.common.response.PageResponse;
import com.redislab.config.AppProperties;
import com.redislab.product.dto.ProductResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductListCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductListCacheService.class);

    private static final TypeReference<PageResponse<ProductResponse>> PAGE_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final CacheJsonCodec jsonCodec;
    private final RedisFailSafe failSafe;
    private final AppProperties appProperties;

    public ProductListCacheService(
            StringRedisTemplate redisTemplate,
            RedisKeyBuilder keyBuilder,
            CacheJsonCodec jsonCodec,
            RedisFailSafe failSafe,
            AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.keyBuilder = keyBuilder;
        this.jsonCodec = jsonCodec;
        this.failSafe = failSafe;
        this.appProperties = appProperties;
    }

    public Optional<PageResponse<ProductResponse>> get(int page, int size) {
        String key = keyBuilder.productPage(page, size);
        ReadResult<String> raw = failSafe.read(key, () -> redisTemplate.opsForValue().get(key));
        if (raw.failed()) {
            return Optional.empty();
        }
        if (raw.value() == null) {
            log.info("CACHE_MISS key={}", key);
            return Optional.empty();
        }
        Optional<PageResponse<ProductResponse>> parsed = jsonCodec.fromJson(raw.value(), PAGE_TYPE);
        if (parsed.isEmpty()) {
            log.warn("CACHE_CORRUPT_ENTRY key={}", key);
            failSafe.invalidate(key, () -> redisTemplate.delete(key));
            return Optional.empty();
        }
        log.info("CACHE_HIT key={}", key);
        return parsed;
    }

    public void put(int page, int size, PageResponse<ProductResponse> response) {
        if (response.content().isEmpty() && page > 0) {
            return;
        }
        String key = keyBuilder.productPage(page, size);
        String json;
        try {
            json = jsonCodec.toJson(response);
        } catch (JsonProcessingException ex) {
            log.warn("CACHE_WRITE_FAILED key={} exception={}", key, ex.getClass().getSimpleName());
            return;
        }
        failSafe.write(
                key,
                () -> redisTemplate
                        .opsForValue()
                        .set(key, json, appProperties.redis().ttl().productList()));
    }

    public void evictAll() {
        String pattern = keyBuilder.productPagePattern();
        failSafe.invalidate(pattern, () -> {
            ScanOptions options =
                    ScanOptions.scanOptions().match(pattern).count(100).build();
            Long removed = redisTemplate.execute((RedisCallback<Long>) connection -> {
                long count = 0;
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        connection.unlink(cursor.next());
                        count++;
                    }
                }
                return count;
            });
            log.info("CACHE_INVALIDATE_LIST pattern={} keysRemoved={}", pattern, removed != null ? removed : 0);
        });
    }
}
