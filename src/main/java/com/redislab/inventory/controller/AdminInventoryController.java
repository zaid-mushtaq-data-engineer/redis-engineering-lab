package com.redislab.inventory.controller;

import com.redislab.inventory.dto.AdminInventoryResponse;
import com.redislab.inventory.dto.QuantityRequest;
import com.redislab.inventory.dto.SetInventoryRequest;
import com.redislab.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
@Validated
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public AdminInventoryResponse get(@PathVariable @Min(1) Long productId) {
        return inventoryService.getAdminInventory(productId);
    }

    @PutMapping("/{productId}")
    public AdminInventoryResponse set(
            @PathVariable @Min(1) Long productId, @Valid @RequestBody SetInventoryRequest request) {
        return inventoryService.setInventory(productId, request);
    }

    @PostMapping("/{productId}/increment")
    public AdminInventoryResponse increment(
            @PathVariable @Min(1) Long productId, @Valid @RequestBody QuantityRequest request) {
        return inventoryService.incrementInventory(productId, request);
    }

    @PostMapping("/{productId}/decrement")
    public AdminInventoryResponse decrement(
            @PathVariable @Min(1) Long productId, @Valid @RequestBody QuantityRequest request) {
        return inventoryService.decrementInventory(productId, request);
    }
}
