package com.redislab.inventory.controller;

import com.redislab.inventory.dto.InventoryResponse;
import com.redislab.inventory.service.InventoryService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(@PathVariable @Min(1) Long productId) {
        return inventoryService.getPublicInventory(productId);
    }
}
