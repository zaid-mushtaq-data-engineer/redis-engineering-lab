package com.redislab.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import com.redislab.support.it.StatisticsSupport;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("slow")
class StampedeReproductionIT extends IntegrationTestBase {

    @Autowired
    private StatisticsSupport stats;

    @BeforeEach
    void setUp() {
        redisTestSupport.flushTestNamespace();
    }

    @Test
    void parallelCacheMissesHitDatabaseMoreThanOnce() throws Exception {
        long productId = testData.createActiveProductWithStock(port, adminHeaders(), UUID.randomUUID().toString(), 0);
        String url = baseUrl() + "/api/products/" + productId;
        redisTestSupport.unlink(redisTestSupport.keys().product(productId));

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    rest.getForEntity(url, String.class);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        stats.clear();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        long entityLoads = stats.entityLoadCount();
        assertThat(entityLoads)
                .as("Stage 2.1 baseline: parallel misses should cause multiple MySQL loads (observed=%d)", entityLoads)
                .isGreaterThan(1);
    }
}
