package com.redislab.redis.dto;

public record JdkSerializationDemoResponse(
        String key,
        String storedAs,
        String compareWith,
        String observation) {}
