package com.redislab.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redislab.cache.service.InventoryCacheService;
import com.redislab.cache.service.ProductCacheService;
import com.redislab.cache.service.ProductListCacheService;
import com.redislab.common.exception.ConcurrentModificationException;
import com.redislab.common.exception.ProductNotFoundException;
import com.redislab.common.response.PageResponse;
import com.redislab.common.util.PageRequestValidator;
import com.redislab.inventory.repository.InventoryRepository;
import com.redislab.product.dto.CreateProductRequest;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.dto.UpdateProductRequest;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import com.redislab.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
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
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private PageRequestValidator pageRequestValidator;

    @Mock
    private ProductCacheService productCache;

    @Mock
    private ProductListCacheService productListCache;

    @Mock
    private InventoryCacheService inventoryCache;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        productService = new ProductServiceImpl(
                productRepository,
                inventoryRepository,
                pageRequestValidator,
                transactionTemplate,
                productCache,
                productListCache,
                inventoryCache);
    }

    @Test
    void getPublicProductThrowsWhenInactive() {
        when(productCache.get(1L)).thenReturn(Optional.empty());
        when(productRepository.findByIdAndStatus(1L, ProductStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getPublicProduct(1L)).isInstanceOf(ProductNotFoundException.class);
        verify(productCache, never()).put(any());
    }

    @Test
    void getPublicProductReturnsCacheHitWithoutDatabase() {
        ProductResponse cached =
                new ProductResponse(1L, "Mouse", "desc", new BigDecimal("10.00"), ProductStatus.ACTIVE);
        when(productCache.get(1L)).thenReturn(Optional.of(cached));

        assertThat(productService.getPublicProduct(1L)).isEqualTo(cached);
        verify(productRepository, never()).findByIdAndStatus(anyLong(), any());
    }

    @Test
    void updateProductRejectsStaleVersion() {
        Product product = new Product("Mouse", "desc", new BigDecimal("10.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "version", 2L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        UpdateProductRequest request =
                new UpdateProductRequest("Mouse", "desc", new BigDecimal("10.00"), 1L);

        assertThatThrownBy(() -> productService.updateProduct(1L, request))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void updateProductNoOpSkipsSave() {
        Product product = new Product("Mouse", "desc", new BigDecimal("10.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "version", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        UpdateProductRequest request =
                new UpdateProductRequest("Mouse", "desc", new BigDecimal("10.00"), 1L);

        var response = productService.updateProduct(1L, request);

        assertThat(response.version()).isEqualTo(1L);
        verify(productRepository, never()).save(any());
        verify(productCache, never()).evict(anyLong());
        verify(productListCache, never()).evictAll();
    }

    @Test
    void listPublicProductsReturnsCacheHitWithoutDatabase() {
        PageResponse<ProductResponse> cached = new PageResponse<>(List.of(), 0, 20, 0, 0, true);
        when(productListCache.get(0, 20)).thenReturn(Optional.of(cached));

        assertThat(productService.listPublicProducts(0, 20)).isEqualTo(cached);
        verify(productRepository, never()).findByStatus(any(), any());
    }

    @Test
    void createProductEvictsAllListPagesAfterCommit() {
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 42L);
            return p;
        });
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(new CreateProductRequest("New Item", "d", new BigDecimal("9.99")));

        verify(productListCache).evictAll();
    }

    @Test
    void updateProductEvictsCacheWhenValuesChange() {
        Product product = new Product("Mouse", "desc", new BigDecimal("10.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "version", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        UpdateProductRequest request =
                new UpdateProductRequest("New Mouse", "desc", new BigDecimal("10.00"), 1L);

        productService.updateProduct(1L, request);

        verify(productCache).evict(1L);
        verify(productListCache).evictAll();
    }

    @Test
    void activateProductIsIdempotent() {
        Product product = new Product("Mouse", "desc", new BigDecimal("10.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "version", 0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        var response = productService.activateProduct(1L);

        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        verify(productRepository, never()).save(any());
        verify(productCache, never()).evict(anyLong());
        verify(productListCache, never()).evictAll();
        verify(inventoryCache, never()).evict(anyLong());
    }

    @Test
    void deactivateProductEvictsInventoryWhenStatusChanges() {
        Product product = new Product("Mouse", "desc", new BigDecimal("10.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "version", 0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.deactivateProduct(1L);

        verify(inventoryCache).evict(1L);
    }
}
