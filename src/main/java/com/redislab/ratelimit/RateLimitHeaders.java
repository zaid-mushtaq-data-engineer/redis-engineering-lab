package com.redislab.ratelimit;

import jakarta.servlet.http.HttpServletResponse;

public final class RateLimitHeaders {

    public static final String LIMIT = "X-RateLimit-Limit";
    public static final String REMAINING = "X-RateLimit-Remaining";
    public static final String RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = "Retry-After";

    private RateLimitHeaders() {}

    public static void apply(HttpServletResponse response, RateLimitResult result) {
        response.setHeader(LIMIT, String.valueOf(result.limit()));
        response.setHeader(REMAINING, String.valueOf(result.remaining()));
        response.setHeader(RESET, String.valueOf(result.resetEpochSeconds()));
    }
}
