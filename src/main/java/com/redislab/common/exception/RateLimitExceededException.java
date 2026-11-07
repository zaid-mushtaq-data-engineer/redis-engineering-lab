package com.redislab.common.exception;

import com.redislab.ratelimit.RateLimitResult;

public class RateLimitExceededException extends AppException {

    private final RateLimitResult result;

    public RateLimitExceededException(RateLimitResult result) {
        super(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
        this.result = result;
    }

    public RateLimitResult getResult() {
        return result;
    }
}
