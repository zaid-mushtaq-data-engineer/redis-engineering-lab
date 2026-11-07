package com.redislab.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OptimisticLockingIT extends IntegrationTestBase {

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void parallelPutWithSameVersionYieldsOneSuccessOneConflict() throws Exception {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 0);
        ResponseEntity<String> adminGet = rest.exchange(
                baseUrl() + "/api/admin/products/" + productId,
                HttpMethod.GET,
                adminGet(),
                String.class);
        long version = testData.parseVersion(adminGet.getBody());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        Runnable update = () -> {
            try {
                start.await();
                Map<String, Object> body = Map.of(
                        "name", "Concurrent-" + UUID.randomUUID(),
                        "description", "x",
                        "price", new BigDecimal("21.00"),
                        "version", version);
                ResponseEntity<String> response = rest.exchange(
                        baseUrl() + "/api/admin/products/" + productId,
                        HttpMethod.PUT,
                        adminJson(body),
                        String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    ok.incrementAndGet();
                } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    conflict.incrementAndGet();
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                done.countDown();
            }
        };
        pool.submit(update);
        pool.submit(update);
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
    }
}
