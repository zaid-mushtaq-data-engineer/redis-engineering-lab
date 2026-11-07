package com.redislab.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redislab.cache.service.InventoryCacheService;
import com.redislab.common.exception.InsufficientStockException;
import com.redislab.common.exception.InventoryNotFoundException;
import com.redislab.common.exception.StockLimitExceededException;
import com.redislab.config.AppProperties;
import com.redislab.inventory.dto.InventoryResponse;
import com.redislab.inventory.dto.QuantityRequest;
import com.redislab.inventory.entity.Inventory;
import com.redislab.inventory.repository.InventoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private AppProperties appProperties;

    @Mock
    private InventoryCacheService inventoryCache;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        when(appProperties.inventory()).thenReturn(new AppProperties.Inventory(1_000_000_000L));
        PlatformTransactionManager transactionManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SimpleTransactionStatus());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        inventoryService = new InventoryServiceImpl(
                inventoryRepository, appProperties, transactionTemplate, inventoryCache);
    }

    @Test
    void getPublicInventoryReturnsCacheHitWithoutDatabase() {
        InventoryResponse cached = new InventoryResponse(1L, 4);
        when(inventoryCache.get(1L)).thenReturn(Optional.of(cached));

        assertThat(inventoryService.getPublicInventory(1L)).isEqualTo(cached);
        verify(inventoryRepository, never()).findPublicByProductId(anyLong());
    }

    @Test
    void decrementEvictsCacheAfterSuccess() {
        when(inventoryRepository.decrement(1L, 1)).thenReturn(1);
        Inventory inventory = new Inventory(1L, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(inventory, "version", 1L);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

        inventoryService.decrementInventory(1L, new QuantityRequest(1));

        verify(inventoryCache).evict(1L);
    }

    @Test
    void decrementThrowsWhenProductMissing() {
        when(inventoryRepository.decrement(99L, 1)).thenReturn(0);
        when(inventoryRepository.existsByProductId(99L)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.decrementInventory(99L, new QuantityRequest(1)))
                .isInstanceOf(InventoryNotFoundException.class);
        verify(inventoryCache, never()).evict(anyLong());
    }

    @Test
    void decrementThrowsWhenInsufficientStock() {
        when(inventoryRepository.decrement(1L, 5)).thenReturn(0);
        when(inventoryRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.decrementInventory(1L, new QuantityRequest(5)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void incrementThrowsWhenStockLimitExceeded() {
        when(inventoryRepository.increment(eq(1L), eq(10), anyInt())).thenReturn(0);
        when(inventoryRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.incrementInventory(1L, new QuantityRequest(10)))
                .isInstanceOf(StockLimitExceededException.class);
    }
}
