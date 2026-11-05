package com.redislab.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redislab.common.exception.ConcurrentModificationException;
import com.redislab.common.exception.ProductNotFoundException;
import com.redislab.common.util.PageRequestValidator;
import com.redislab.inventory.repository.InventoryRepository;
import com.redislab.product.dto.UpdateProductRequest;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import com.redislab.product.repository.ProductRepository;
import java.math.BigDecimal;
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

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        productService = new ProductServiceImpl(
                productRepository, inventoryRepository, pageRequestValidator, transactionTemplate);
    }

    @Test
    void getPublicProductThrowsWhenInactive() {
        when(productRepository.findByIdAndStatus(1L, ProductStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getPublicProduct(1L)).isInstanceOf(ProductNotFoundException.class);
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
    }
}
