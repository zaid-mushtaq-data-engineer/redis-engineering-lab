package com.redislab.support.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.cache.key.RedisKeyBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
public class RedisTestSupport {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;

    public RedisTestSupport(StringRedisTemplate redisTemplate, RedisKeyBuilder keyBuilder) {
        this.redisTemplate = redisTemplate;
        this.keyBuilder = keyBuilder;
    }

    public void flushTestNamespace() {
        String pattern = keyBuilder.product(0).replace("product:0", "*");
        List<String> keys = scan(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.unlink(keys);
        }
    }

    public boolean hasKey(String redisKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    public Long ttlSeconds(String redisKey) {
        return redisTemplate.getExpire(redisKey);
    }

    public String get(String redisKey) {
        return redisTemplate.opsForValue().get(redisKey);
    }

    public void setCorrupt(String redisKey, String corruptJson) {
        redisTemplate.opsForValue().set(redisKey, corruptJson);
    }

    public void assertPlainJsonNoType(String redisKey) {
        String value = get(redisKey);
        assertThat(value).isNotNull();
        assertThat(value).doesNotContain("@class");
    }

    private List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    public RedisKeyBuilder keys() {
        return keyBuilder;
    }

    public void unlink(String redisKey) {
        redisTemplate.unlink(redisKey);
    }
}
