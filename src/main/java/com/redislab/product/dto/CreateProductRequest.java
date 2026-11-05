package com.redislab.product.dto;

import com.redislab.product.entity.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record CreateProductRequest(
        @NotBlank(message = "Name is required")
                @Size(min = 3, max = 100, message = "Name must be 3–100 characters")
                String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false, message = "Price must be greater than zero")
                @DecimalMax("99999999.99")
                @Digits(integer = 8, fraction = 2)
                BigDecimal price) {}
