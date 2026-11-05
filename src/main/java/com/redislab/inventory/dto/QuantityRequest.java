package com.redislab.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuantityRequest(@NotNull @Min(1) @Max(1_000_000_000) Integer quantity) {}
