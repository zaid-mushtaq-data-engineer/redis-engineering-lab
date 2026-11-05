package com.redislab.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redislab.TestcontainersConfig;
import com.redislab.inventory.entity.Inventory;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import com.redislab.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
@Transactional
class InventoryRepositoryIT {

    private static final int MAX_STOCK = 1_000_000_000;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = productRepository.save(new Product("Keyboard", "Mechanical", new BigDecimal("1499.00")));
    }

    @Test
    void rejectsNegativeStockQuantity() {
        Inventory inventory = new Inventory(product.getId(), -1);

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(inventory))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void rejectsDuplicateProductId() {
        inventoryRepository.save(new Inventory(product.getId(), 0));

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(new Inventory(product.getId(), 1)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void decrementBelowZeroUpdatesZeroRows() {
        inventoryRepository.save(new Inventory(product.getId(), 5));

        int updated = inventoryRepository.decrement(product.getId(), 10);

        assertThat(updated).isZero();
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inventory.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void incrementAndDecrementBumpVersionAndQuantity() {
        inventoryRepository.save(new Inventory(product.getId(), 10));

        int incremented = inventoryRepository.increment(product.getId(), 5, MAX_STOCK);
        assertThat(incremented).isEqualTo(1);

        Inventory afterIncrement = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(afterIncrement.getStockQuantity()).isEqualTo(15);
        assertThat(afterIncrement.getVersion()).isEqualTo(1L);

        int decremented = inventoryRepository.decrement(product.getId(), 3);
        assertThat(decremented).isEqualTo(1);

        Inventory afterDecrement = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(afterDecrement.getStockQuantity()).isEqualTo(12);
        assertThat(afterDecrement.getVersion()).isEqualTo(2L);
    }

    @Test
    void incrementRespectsMaxStockCap() {
        inventoryRepository.save(new Inventory(product.getId(), MAX_STOCK - 1));

        int updated = inventoryRepository.increment(product.getId(), 2, MAX_STOCK);

        assertThat(updated).isZero();
        assertThat(inventoryRepository.findByProductId(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(MAX_STOCK - 1);
    }

    @Test
    void findPublicByProductIdRequiresActiveProduct() {
        inventoryRepository.save(new Inventory(product.getId(), 7));

        assertThat(inventoryRepository.findPublicByProductId(product.getId())).isPresent();

        product.changeStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);

        assertThat(inventoryRepository.findPublicByProductId(product.getId())).isEmpty();
    }
}
