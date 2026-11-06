package com.redislab.cache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.cache.support.CacheJsonCodec;
import com.redislab.cache.support.RedisFailSafe;
import com.redislab.cache.support.RedisFailSafe.ReadResult;
import com.redislab.config.AppProperties;
import com.redislab.product.dto.ProductResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final CacheJsonCodec jsonCodec;
    private final RedisFailSafe failSafe;
    private final AppProperties appProperties;

    public ProductCacheService(
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

    public Optional<ProductResponse> get(long id) {
        String key = keyBuilder.product(id);
        ReadResult<String> raw = failSafe.read(key, () -> redisTemplate.opsForValue().get(key));
        if (raw.failed()) {
            return Optional.empty();
        }
        if (raw.value() == null) {
            log.info("CACHE_MISS key={}", key);
            return Optional.empty();
        }
        Optional<ProductResponse> parsed = jsonCodec.fromJson(raw.value(), ProductResponse.class);
        if (parsed.isEmpty()) {
            log.warn("CACHE_CORRUPT_ENTRY key={}", key);
            failSafe.invalidate(key, () -> redisTemplate.delete(key));
            return Optional.empty();
        }
        log.info("CACHE_HIT key={}", key);
        return parsed;
    }

    public void put(ProductResponse response) {
        String key = keyBuilder.product(response.id());
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
                        .set(key, json, appProperties.redis().ttl().product()));
    }

    public void evict(long id) {
        String key = keyBuilder.product(id);
        failSafe.invalidate(key, () -> redisTemplate.unlink(key));
    }
}
