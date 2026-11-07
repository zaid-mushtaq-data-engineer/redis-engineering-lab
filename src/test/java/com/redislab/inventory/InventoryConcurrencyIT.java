package com.redislab.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("slow")
class InventoryConcurrencyIT extends IntegrationTestBase {

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void parallelDecrementsRespectStock() throws Exception {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 5);
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ResponseEntity<String> response = rest.exchange(
                            baseUrl() + "/api/admin/inventory/" + productId + "/decrement",
                            HttpMethod.POST,
                            adminJson(Map.of("quantity", 1)),
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
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(5);
        assertThat(conflict.get()).isEqualTo(95);

        ResponseEntity<String> inv = rest.exchange(
                baseUrl() + "/api/admin/inventory/" + productId,
                HttpMethod.GET,
                adminGet(),
                String.class);
        assertThat(inv.getBody()).contains("\"stockQuantity\":0");
    }

    @Test
    void staleVersionOnSetReturns409() {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 3);
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

        ResponseEntity<String> stale = rest.exchange(
                baseUrl() + "/api/admin/inventory/" + productId,
                HttpMethod.PUT,
                adminJson(Map.of("stockQuantity", 6, "version", version)),
                String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
