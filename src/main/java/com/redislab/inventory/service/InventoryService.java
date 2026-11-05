package com.redislab.inventory.service;

import com.redislab.inventory.dto.AdminInventoryResponse;
import com.redislab.inventory.dto.InventoryResponse;
import com.redislab.inventory.dto.QuantityRequest;
import com.redislab.inventory.dto.SetInventoryRequest;

public interface InventoryService {

    InventoryResponse getPublicInventory(long productId);

    AdminInventoryResponse getAdminInventory(long productId);

    AdminInventoryResponse setInventory(long productId, SetInventoryRequest request);

    AdminInventoryResponse incrementInventory(long productId, QuantityRequest request);

    AdminInventoryResponse decrementInventory(long productId, QuantityRequest request);
}
