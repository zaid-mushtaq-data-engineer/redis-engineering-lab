package com.redislab.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record ProductResponse(
        Long id,
        String name,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
        ProductStatus status) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().setScale(2, RoundingMode.HALF_UP),
                product.getStatus());
    }
}
