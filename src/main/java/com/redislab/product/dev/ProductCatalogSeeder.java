package com.redislab.product.dev;

import com.redislab.inventory.entity.Inventory;
import com.redislab.inventory.repository.InventoryRepository;
import com.redislab.product.entity.Product;
import com.redislab.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev.seed-catalog", havingValue = "true")
public class ProductCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogSeeder.class);
    private static final int TARGET_COUNT = 10_000;
    private static final int BATCH_SIZE = 500;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final TransactionTemplate transactionTemplate;

    public ProductCatalogSeeder(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            TransactionTemplate transactionTemplate) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() >= TARGET_COUNT) {
            log.info("Product catalog already has {} products; skipping seed", productRepository.count());
            return;
        }

        log.info("Seeding {} products (this may take a minute)...", TARGET_COUNT);
        long start = System.currentTimeMillis();

        for (int batchStart = 0; batchStart < TARGET_COUNT; batchStart += BATCH_SIZE) {
            final int from = batchStart;
            final int to = Math.min(batchStart + BATCH_SIZE, TARGET_COUNT);
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = from; i < to; i++) {
                    Product product = productRepository.save(new Product(
                            "Catalog product " + i,
                            "Seeded for performance experiments",
                            new BigDecimal("99.99")));
                    inventoryRepository.save(new Inventory(product.getId(), 0));
                }
            });
        }

        log.info(
                "Seeded {} products in {} ms",
                productRepository.count(),
                System.currentTimeMillis() - start);
    }
}
