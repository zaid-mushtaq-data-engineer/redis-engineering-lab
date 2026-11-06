package com.redislab.cache.support;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class RedisFailSafe {

    private static final Logger log = LoggerFactory.getLogger(RedisFailSafe.class);

    public <T> ReadResult<T> read(String key, Supplier<T> action) {
        try {
            return ReadResult.of(action.get());
        } catch (DataAccessException ex) {
            log.warn("CACHE_READ_FAILED key={} exception={}", key, ex.getClass().getSimpleName());
            return ReadResult.failure();
        }
    }

    public void write(String key, Runnable action) {
        run(key, "CACHE_WRITE_FAILED", action);
    }

    public void invalidate(String key, Runnable action) {
        run(key, "CACHE_INVALIDATE_FAILED", action);
    }

    private void run(String key, String event, Runnable action) {
        try {
            action.run();
        } catch (DataAccessException ex) {
            log.warn("{} key={} exception={}", event, key, ex.getClass().getSimpleName());
        }
    }

    public record ReadResult<T>(T value, boolean failed) {

        public static <T> ReadResult<T> of(T value) {
            return new ReadResult<>(value, false);
        }

        public static <T> ReadResult<T> failure() {
            return new ReadResult<>(null, true);
        }
    }
}
