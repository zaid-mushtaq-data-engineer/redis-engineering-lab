package com.redislab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.ItTestcontainersConfig;
import com.redislab.cache.key.RedisKeyBuilder;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItTestcontainersConfig.class)
class RateLimitLuaIT {

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @BeforeEach
    void setUp() {
        String pattern = keyBuilder.product(0).replace("product:0", "*");
        try (var cursor = redisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                redisTemplate.unlink(cursor.next());
            }
        }
    }

    @Test
    void luaLimiterUnderConcurrencyDoesNotOverAllowBurst() throws Exception {
        String ip = "192.0.2.10";
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    Optional<RateLimitResult> result = rateLimiter.tryConsume(ip);
                    if (result.isPresent() && result.get().allowed()) {
                        allowed.incrementAndGet();
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
    }
}
