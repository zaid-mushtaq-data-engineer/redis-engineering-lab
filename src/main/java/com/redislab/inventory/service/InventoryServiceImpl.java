package com.redislab.inventory.service;

import com.redislab.cache.service.InventoryCacheService;
import com.redislab.common.exception.ConcurrentModificationException;
import com.redislab.common.exception.InsufficientStockException;
import com.redislab.common.exception.InventoryNotFoundException;
import com.redislab.common.exception.StockLimitExceededException;
import com.redislab.config.AppProperties;
import com.redislab.inventory.dto.AdminInventoryResponse;
import com.redislab.inventory.dto.InventoryResponse;
import com.redislab.inventory.dto.QuantityRequest;
import com.redislab.inventory.dto.SetInventoryRequest;
import com.redislab.inventory.entity.Inventory;
import com.redislab.inventory.repository.InventoryRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;
    private final InventoryCacheService inventoryCache;

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            AppProperties appProperties,
            TransactionTemplate transactionTemplate,
            InventoryCacheService inventoryCache) {
        this.inventoryRepository = inventoryRepository;
        this.appProperties = appProperties;
        this.transactionTemplate = transactionTemplate;
        this.inventoryCache = inventoryCache;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getPublicInventory(long productId) {
        return inventoryCache.get(productId).orElseGet(() -> loadAndCache(productId));
    }

    private InventoryResponse loadAndCache(long productId) {
        InventoryResponse response = inventoryRepository
                .findPublicByProductId(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        inventoryCache.put(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminInventoryResponse getAdminInventory(long productId) {
        return inventoryRepository
                .findByProductId(productId)
                .map(AdminInventoryResponse::from)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    @Override
    public AdminInventoryResponse setInventory(long productId, SetInventoryRequest request) {
        WriteOutcome outcome = transactionTemplate.execute(status -> {
            Inventory inventory = inventoryRepository
                    .findByProductId(productId)
                    .orElseThrow(() -> new InventoryNotFoundException(productId));
            if (!request.version().equals(inventory.getVersion())) {
                throw new ConcurrentModificationException();
            }
            if (inventory.getStockQuantity() == request.stockQuantity()) {
                return WriteOutcome.unchanged(AdminInventoryResponse.from(inventory));
            }
            inventory.setStockQuantity(request.stockQuantity());
            try {
                Inventory saved = inventoryRepository.save(inventory);
                return WriteOutcome.changed(AdminInventoryResponse.from(saved));
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConcurrentModificationException();
            }
        });
        if (outcome.changed()) {
            inventoryCache.evict(productId);
        }
        return outcome.response();
    }

    @Override
    public AdminInventoryResponse incrementInventory(long productId, QuantityRequest request) {
        AdminInventoryResponse response = transactionTemplate.execute(status -> {
            int max = (int) appProperties.inventory().maxStockQuantity();
            int updated = inventoryRepository.increment(productId, request.quantity(), max);
            if (updated == 0) {
                handleZeroRowUpdate(productId, true);
            }
            return reloadAdminInventory(productId);
        });
        inventoryCache.evict(productId);
        return response;
    }

    @Override
    public AdminInventoryResponse decrementInventory(long productId, QuantityRequest request) {
        AdminInventoryResponse response = transactionTemplate.execute(status -> {
            int updated = inventoryRepository.decrement(productId, request.quantity());
            if (updated == 0) {
                handleZeroRowUpdate(productId, false);
            }
            return reloadAdminInventory(productId);
        });
        inventoryCache.evict(productId);
        return response;
    }

    private AdminInventoryResponse reloadAdminInventory(long productId) {
        return AdminInventoryResponse.from(inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId)));
    }

    private void handleZeroRowUpdate(long productId, boolean increment) {
        if (!inventoryRepository.existsByProductId(productId)) {
            throw new InventoryNotFoundException(productId);
        }
        if (increment) {
            throw new StockLimitExceededException();
        }
        throw new InsufficientStockException();
    }

    private record WriteOutcome(AdminInventoryResponse response, boolean changed) {

        static WriteOutcome changed(AdminInventoryResponse response) {
            return new WriteOutcome(response, true);
        }

        static WriteOutcome unchanged(AdminInventoryResponse response) {
            return new WriteOutcome(response, false);
        }
    }
}
