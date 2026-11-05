package com.redislab.cache.key;

import com.redislab.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyBuilder {

    private final String prefix;

    public RedisKeyBuilder(AppProperties appProperties) {
        this.prefix = appProperties.redis().project() + ":" + appProperties.environment() + ":";
    }

    public String sandbox(String logicalKey) {
        return prefix + "sandbox:" + logicalKey;
    }

    public String product(long id) {
        return prefix + "product:" + id;
    }

    public String inventory(long productId) {
        return prefix + "inventory:" + productId;
    }

    public String productPage(int page, int size) {
        return prefix + "products:page:" + page + ":size:" + size;
    }

    public String productPagePattern() {
        return prefix + "products:page:*";
    }

    public String rateLimit(String clientIp) {
        return prefix + "rate-limit:" + clientIp;
    }
}
