package com.redislab.cache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.cache.support.CacheJsonCodec;
import com.redislab.cache.support.RedisFailSafe;
import com.redislab.cache.support.RedisFailSafe.ReadResult;
import com.redislab.config.AppProperties;
import com.redislab.inventory.dto.InventoryResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryCacheService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final CacheJsonCodec jsonCodec;
    private final RedisFailSafe failSafe;
    private final AppProperties appProperties;

    public InventoryCacheService(
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

    public Optional<InventoryResponse> get(long productId) {
        String key = keyBuilder.inventory(productId);
        ReadResult<String> raw = failSafe.read(key, () -> redisTemplate.opsForValue().get(key));
        if (raw.failed()) {
            return Optional.empty();
        }
        if (raw.value() == null) {
            log.info("CACHE_MISS key={}", key);
            return Optional.empty();
        }
        Optional<InventoryResponse> parsed = jsonCodec.fromJson(raw.value(), InventoryResponse.class);
        if (parsed.isEmpty()) {
            log.warn("CACHE_CORRUPT_ENTRY key={}", key);
            failSafe.invalidate(key, () -> redisTemplate.delete(key));
            return Optional.empty();
        }
        log.info("CACHE_HIT key={}", key);
        return parsed;
    }

    public void put(InventoryResponse response) {
        String key = keyBuilder.inventory(response.productId());
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
                        .set(key, json, appProperties.redis().ttl().inventory()));
    }

    public void evict(long productId) {
        String key = keyBuilder.inventory(productId);
        failSafe.invalidate(key, () -> redisTemplate.unlink(key));
    }
}
