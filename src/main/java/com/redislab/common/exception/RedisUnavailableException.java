package com.redislab.common.exception;

public class RedisUnavailableException extends AppException {

    public RedisUnavailableException() {
        super(503, "REDIS_UNAVAILABLE", "Redis is unavailable");
    }
}
