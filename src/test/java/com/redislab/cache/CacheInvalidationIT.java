package com.redislab.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CacheInvalidationIT extends IntegrationTestBase {

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void productUpdateEvictsProductAndListKeys() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 1);
        warmCaches(productId);

        String productKey = redisTestSupport.keys().product(productId);
        String listKey = redisTestSupport.keys().productPage(0, 20);
        assertThat(redisTestSupport.hasKey(productKey)).isTrue();
        assertThat(redisTestSupport.hasKey(listKey)).isTrue();

        ResponseEntity<String> adminGet = rest.exchange(
                baseUrl() + "/api/admin/products/" + productId,
                HttpMethod.GET,
                adminGet(),
                String.class);
        long version = testData.parseVersion(adminGet.getBody());

        Map<String, Object> update = Map.of(
                "name", "Renamed-" + productId,
                "description", "updated",
                "price", new BigDecimal("29.99"),
                "version", version);
        rest.exchange(
                baseUrl() + "/api/admin/products/" + productId,
                HttpMethod.PUT,
                adminJson(update),
                String.class);

        assertThat(redisTestSupport.hasKey(productKey)).isFalse();
        assertThat(redisTestSupport.hasKey(listKey)).isFalse();
    }

    @Test
    void deactivateEvictsProductListAndInventory() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 4);
        warmCaches(productId);
        String invKey = redisTestSupport.keys().inventory(productId);
        assertThat(redisTestSupport.hasKey(invKey)).isTrue();

        rest.exchange(
                baseUrl() + "/api/admin/products/" + productId + "/deactivate",
                HttpMethod.PATCH,
                adminGet(),
                String.class);

        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().product(productId))).isFalse();
        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().productPage(0, 20))).isFalse();
        assertThat(redisTestSupport.hasKey(invKey)).isFalse();
    }

    @Test
    void unchangedUpdateKeepsCaches() throws Exception {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 2);
        warmCaches(productId);
        String productKey = redisTestSupport.keys().product(productId);
        String before = redisTestSupport.get(productKey);

        ResponseEntity<String> adminGet = rest.exchange(
                baseUrl() + "/api/admin/products/" + productId,
                HttpMethod.GET,
                adminGet(),
                String.class);
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(adminGet.getBody());
        long version = node.get("version").asLong();
        Map<String, Object> body = Map.of(
                "name", node.get("name").asText(),
                "description", node.path("description").asText("integration"),
                "price", new BigDecimal(node.get("price").asText()),
                "version", version);

        rest.exchange(
                baseUrl() + "/api/admin/products/" + productId,
                HttpMethod.PUT,
                adminJson(body),
                String.class);

        assertThat(redisTestSupport.hasKey(productKey)).isTrue();
        assertThat(redisTestSupport.get(productKey)).isEqualTo(before);
    }

    @Test
    void inventoryWriteEvictsOnlyInventoryKey() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 2);
        warmCaches(productId);
        String productKey = redisTestSupport.keys().product(productId);
        String listKey = redisTestSupport.keys().productPage(0, 20);

        ResponseEntity<String> inv = rest.exchange(
                baseUrl() + "/api/admin/inventory/" + productId,
                HttpMethod.GET,
                adminGet(),
                String.class);
        long version = testData.parseVersion(inv.getBody());
        rest.exchange(
                baseUrl() + "/api/admin/inventory/" + productId,
                HttpMethod.PUT,
                adminJson(Map.of("stockQuantity", 5, "version", version)),
                String.class);

        assertThat(redisTestSupport.hasKey(productKey)).isTrue();
        assertThat(redisTestSupport.hasKey(listKey)).isTrue();
        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().inventory(productId))).isFalse();
    }

    private void warmCaches(long productId) {
        rest.getForEntity(baseUrl() + "/api/products/" + productId, String.class);
        rest.getForEntity(baseUrl() + "/api/products?page=0&size=20", String.class);
        rest.getForEntity(baseUrl() + "/api/inventory/" + productId, String.class);
    }
}
