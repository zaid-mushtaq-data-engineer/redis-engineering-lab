package com.redislab.ratelimit;

public record RateLimitResult(
        boolean allowed, int remaining, int retryAfterSeconds, long resetEpochSeconds, int limit) {}
