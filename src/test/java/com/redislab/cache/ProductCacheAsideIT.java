package com.redislab.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import com.redislab.support.it.StatisticsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProductCacheAsideIT extends IntegrationTestBase {

    @Autowired
    private StatisticsSupport stats;

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
        stats.clear();
    }

    @Test
    void secondGetUsesCacheWithoutExtraEntityLoads() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), "cache-aside", 0);
        String url = baseUrl() + "/api/products/" + productId;
        String key = redisTestSupport.keys().product(productId);

        stats.clear();
        ResponseEntity<String> first = rest.getForEntity(url, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redisTestSupport.hasKey(key)).isTrue();
        redisTestSupport.assertPlainJsonNoType(key);
        Long ttl = redisTestSupport.ttlSeconds(key);
        assertThat(ttl).isBetween(850L, 900L);
        long loadsAfterFirst = stats.entityLoadCount();

        stats.clear();
        ResponseEntity<String> second = rest.getForEntity(url, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stats.entityLoadCount()).isZero();
        assertThat(loadsAfterFirst).isGreaterThan(0);
    }

    @Test
    void corruptCacheValueFallsBackToMysqlAndEvictsKey() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), "corrupt", 0);
        String url = baseUrl() + "/api/products/" + productId;
        String key = redisTestSupport.keys().product(productId);

        rest.getForEntity(url, String.class);
        redisTestSupport.setCorrupt(key, "{not-valid-json");

        ResponseEntity<String> after = rest.getForEntity(url, String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).contains("Widget-corrupt");
        assertThat(redisTestSupport.get(key)).doesNotContain("not-valid-json");
    }

    @Test
    void notFoundIsNotCached() {
        String url = baseUrl() + "/api/products/999999";
        ResponseEntity<String> first = rest.getForEntity(url, String.class);
        ResponseEntity<String> second = rest.getForEntity(url, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().product(999999))).isFalse();
    }
}
