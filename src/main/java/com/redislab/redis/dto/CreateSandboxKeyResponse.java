package com.redislab.redis.dto;

public record CreateSandboxKeyResponse(String key, String storedAs, Integer ttlSeconds) {}
