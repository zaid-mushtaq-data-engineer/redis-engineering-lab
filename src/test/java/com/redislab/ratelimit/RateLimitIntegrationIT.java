package com.redislab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redislab.support.it.IntegrationTestBase;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RateLimitIntegrationIT extends IntegrationTestBase {

    private long productId;

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
        productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 0);
    }

    @Test
    void burstThenRejectThenRefill() {
        String url = baseUrl() + "/api/products/" + productId;
        String rateKey = redisTestSupport.keys().rateLimit("127.0.0.1");

        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = rest.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst(RateLimitHeaders.REMAINING))
                    .isEqualTo(String.valueOf(9 - i));
        }

        ResponseEntity<String> rejected = rest.getForEntity(url, String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getHeaders().getFirst(RateLimitHeaders.RETRY_AFTER)).isNotBlank();

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(rest.getForEntity(url, String.class).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        assertThat(redisTestSupport.ttlSeconds(rateKey)).isGreaterThan(0);
    }

    @Test
    void sharedBucketAcrossPublicEndpoints() {
        rest.getForEntity(baseUrl() + "/api/products/" + productId, String.class);
        ResponseEntity<String> list = rest.getForEntity(baseUrl() + "/api/products?page=0&size=20", String.class);
        assertThat(list.getHeaders().getFirst(RateLimitHeaders.REMAINING)).isEqualTo("8");
    }

    @Test
    void parallelBurstAllowsRoughlyCapacity() throws Exception {
        String url = baseUrl() + "/api/products/" + productId;
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ResponseEntity<String> response = rest.getForEntity(url, String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        allowed.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        rejected.incrementAndGet();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(allowed.get()).isBetween(10, 15);
        assertThat(rejected.get()).isGreaterThan(0);
    }
}
