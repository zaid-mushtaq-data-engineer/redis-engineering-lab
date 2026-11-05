package com.redislab.redis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSandboxKeyRequest(
        @NotBlank
                @Pattern(regexp = "[A-Za-z0-9:_-]{1,100}", message = "Key must match [A-Za-z0-9:_-]{1,100}")
                String key,
        @NotBlank @Size(max = 1000, message = "Value must be at most 1000 characters") String value,
        @Min(value = 1, message = "ttlSeconds must be between 1 and 86400")
                @Max(value = 86400, message = "ttlSeconds must be between 1 and 86400")
                Integer ttlSeconds) {}
