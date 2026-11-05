package com.redislab.inventory.dto;

import com.redislab.inventory.entity.Inventory;

public record AdminInventoryResponse(Long productId, Integer stockQuantity, Long version) {

    public static AdminInventoryResponse from(Inventory inventory) {
        return new AdminInventoryResponse(
                inventory.getProductId(), inventory.getStockQuantity(), inventory.getVersion());
    }
}
