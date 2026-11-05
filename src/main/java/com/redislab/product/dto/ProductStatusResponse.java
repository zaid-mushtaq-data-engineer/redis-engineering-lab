package com.redislab.product.dto;

import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;

public record ProductStatusResponse(Long id, ProductStatus status, Long version) {

    public static ProductStatusResponse from(Product product) {
        return new ProductStatusResponse(product.getId(), product.getStatus(), product.getVersion());
    }
}
