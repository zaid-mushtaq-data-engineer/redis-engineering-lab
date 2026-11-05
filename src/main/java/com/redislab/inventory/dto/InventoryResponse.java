package com.redislab.inventory.dto;

import com.redislab.inventory.repository.PublicInventoryView;

public record InventoryResponse(Long productId, Integer stockQuantity) {

    public static InventoryResponse from(PublicInventoryView view) {
        return new InventoryResponse(view.getProductId(), view.getStockQuantity());
    }
}
