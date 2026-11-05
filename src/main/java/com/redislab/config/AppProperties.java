package com.redislab.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank String environment,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Redis redis,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Pagination pagination,
        @Valid @NotNull Inventory inventory,
        @Valid @NotNull DevEndpoints devEndpoints,
        @Valid @NotNull Dev dev) {

    public record Admin(@NotBlank String apiKey) {}

    public record Redis(@NotBlank String project, @Valid @NotNull Ttl ttl) {}

    public record Ttl(
            @NotNull Duration product,
            @NotNull Duration inventory,
            @NotNull Duration productList) {}

    public record RateLimit(
            boolean enabled,
            @Positive int capacity,
            @Positive int refillTokensPerSecond,
            @NotNull Duration keyTtlAfterFull,
            boolean trustForwardedHeaders) {}

    public record Pagination(@Positive int defaultSize, @Positive int maxSize) {}

    public record Inventory(@Positive long maxStockQuantity) {}

    public record DevEndpoints(boolean enabled) {}

    public record Dev(boolean seedCatalog) {}
}
