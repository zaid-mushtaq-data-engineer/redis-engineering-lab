package com.redislab.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InventoryCacheIT extends IntegrationTestBase {

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void inventoryCachedWithShortTtl() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 7);
        String url = baseUrl() + "/api/inventory/" + productId;
        String key = redisTestSupport.keys().inventory(productId);

        rest.getForEntity(url, String.class);
        assertThat(redisTestSupport.hasKey(key)).isTrue();
        Long ttl = redisTestSupport.ttlSeconds(key);
        assertThat(ttl).isBetween(1L, 30L);
    }

    @Test
    void inactiveProductInventoryNotCached() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 3);
        rest.exchange(
                baseUrl() + "/api/admin/products/" + productId + "/deactivate",
                HttpMethod.PATCH,
                adminGet(),
                String.class);

        String url = baseUrl() + "/api/inventory/" + productId;
        ResponseEntity<String> response = rest.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().inventory(productId))).isFalse();
    }
}
