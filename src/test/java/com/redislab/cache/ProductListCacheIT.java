package com.redislab.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProductListCacheIT extends IntegrationTestBase {

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void listMissThenHitAndInvalidSize() {
        testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 0);
        String listUrl = baseUrl() + "/api/products?page=0&size=20";
        String key = redisTestSupport.keys().productPage(0, 20);

        ResponseEntity<String> miss = rest.getForEntity(listUrl, String.class);
        assertThat(miss.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redisTestSupport.hasKey(key)).isTrue();

        ResponseEntity<String> hit = rest.getForEntity(listUrl, String.class);
        assertThat(hit.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> badSize = rest.getForEntity(baseUrl() + "/api/products?size=51", String.class);
        assertThat(badSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void emptyPageBeyondEndIsNotCached() {
        String url = baseUrl() + "/api/products?page=999&size=20";
        ResponseEntity<String> response = rest.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redisTestSupport.hasKey(redisTestSupport.keys().productPage(999, 20))).isFalse();
    }
}
