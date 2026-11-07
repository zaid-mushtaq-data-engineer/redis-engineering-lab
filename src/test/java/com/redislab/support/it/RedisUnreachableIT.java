package com.redislab.support.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.TestcontainersConfig;
import com.redislab.ratelimit.RateLimitHeaders;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
@Isolated
class RedisUnreachableIT {

    private static final String ADMIN_KEY = "test-admin-key-for-integration-tests";

    @DynamicPropertySource
    static void redisDown(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6399);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void publicReadSucceedsWithoutRateLimitHeaders() {
        long start = System.currentTimeMillis();
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/api/products?page=0&size=20", String.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(RateLimitHeaders.LIMIT)).isNull();
        assertThat(elapsed).isLessThan(2000);
    }

    @Test
    void adminWriteStillSucceeds() {
        var headers = TestDataFactory.adminHeaders(ADMIN_KEY);
        Map<String, Object> body = Map.of(
                "name", "Redis-down-widget",
                "description", "d",
                "price", new BigDecimal("11.11"));
        ResponseEntity<String> response = rest.exchange(
                "http://localhost:" + port + "/api/admin/products",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
