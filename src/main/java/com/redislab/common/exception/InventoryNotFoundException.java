package com.redislab.common.exception;

public class InventoryNotFoundException extends AppException {

    public InventoryNotFoundException(long productId) {
        super(404, "INVENTORY_NOT_FOUND", "Inventory not found");
    }
}
