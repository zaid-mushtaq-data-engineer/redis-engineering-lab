package com.redislab.redis.dto;

public record TtlSandboxKeyResponse(String key, long ttlSeconds) {}
