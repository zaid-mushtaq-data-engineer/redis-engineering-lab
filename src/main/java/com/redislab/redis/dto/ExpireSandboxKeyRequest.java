package com.redislab.redis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExpireSandboxKeyRequest(
        @NotNull @Min(1) @Max(86400) Integer seconds) {}
