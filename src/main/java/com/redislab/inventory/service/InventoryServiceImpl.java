package com.redislab.inventory.service;

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

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            AppProperties appProperties,
            TransactionTemplate transactionTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.appProperties = appProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getPublicInventory(long productId) {
        return inventoryRepository
                .findPublicByProductId(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
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
        return transactionTemplate.execute(status -> {
            Inventory inventory = inventoryRepository
                    .findByProductId(productId)
                    .orElseThrow(() -> new InventoryNotFoundException(productId));
            if (!request.version().equals(inventory.getVersion())) {
                throw new ConcurrentModificationException();
            }
            if (inventory.getStockQuantity() == request.stockQuantity()) {
                return AdminInventoryResponse.from(inventory);
            }
            inventory.setStockQuantity(request.stockQuantity());
            try {
                Inventory saved = inventoryRepository.save(inventory);
                // Stage 7+: inventoryCache.evict(productId)
                return AdminInventoryResponse.from(saved);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConcurrentModificationException();
            }
        });
    }

    @Override
    public AdminInventoryResponse incrementInventory(long productId, QuantityRequest request) {
        return transactionTemplate.execute(status -> {
            int max = (int) appProperties.inventory().maxStockQuantity();
            int updated = inventoryRepository.increment(productId, request.quantity(), max);
            if (updated == 0) {
                handleZeroRowUpdate(productId, true);
            }
            return reloadAdminInventory(productId);
        });
    }

    @Override
    public AdminInventoryResponse decrementInventory(long productId, QuantityRequest request) {
        return transactionTemplate.execute(status -> {
            int updated = inventoryRepository.decrement(productId, request.quantity());
            if (updated == 0) {
                handleZeroRowUpdate(productId, false);
            }
            return reloadAdminInventory(productId);
        });
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
}
