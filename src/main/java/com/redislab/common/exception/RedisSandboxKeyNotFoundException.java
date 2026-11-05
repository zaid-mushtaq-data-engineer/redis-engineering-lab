package com.redislab.common.exception;

public class RedisSandboxKeyNotFoundException extends AppException {

    public RedisSandboxKeyNotFoundException(String key) {
        super(404, "REDIS_KEY_NOT_FOUND", "Redis key not found: " + key);
    }
}
