package com.redislab.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redislab.TestcontainersConfig;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import java.math.BigDecimal;
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
class ProductRepositoryIT {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void rejectsNonPositivePrice() {
        Product product = new Product("Valid name", "desc", BigDecimal.ZERO);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void findByIdAndStatusReturnsActiveOnly() {
        Product product = productRepository.save(new Product("Mouse", "Wireless", new BigDecimal("799.00")));
        product.changeStatus(ProductStatus.INACTIVE);
        productRepository.save(product);

        assertThat(productRepository.findByIdAndStatus(product.getId(), ProductStatus.ACTIVE))
                .isEmpty();
        assertThat(productRepository.findById(product.getId())).isPresent();
    }

    @Test
    void contextLoadsWithFlywaySchema() {
        assertThat(productRepository.count()).isZero();
    }
}
