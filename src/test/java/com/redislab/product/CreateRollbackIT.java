package com.redislab.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.ItTestcontainersConfig;
import com.redislab.support.it.IntegrationTestConfiguration;
import com.redislab.inventory.entity.Inventory;
import com.redislab.inventory.repository.InventoryRepository;
import com.redislab.product.dto.CreateProductRequest;
import com.redislab.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItTestcontainersConfig.class, IntegrationTestConfiguration.class})
class CreateRollbackIT {

    private static final String ADMIN_KEY = "test-admin-key-for-integration-tests";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ProductRepository productRepository;

    @SpyBean
    private InventoryRepository inventoryRepository;

    @Test
    void createProductRollsBackWhenInventorySaveFails() {
        long productsBefore = productRepository.count();
        doThrow(new DataIntegrityViolationException("forced"))
                .when(inventoryRepository)
                .save(any(Inventory.class));

        CreateProductRequest create =
                new CreateProductRequest("Rollback-Product", "desc", new BigDecimal("12.34"));
        var headers = com.redislab.support.it.TestDataFactory.adminHeaders(ADMIN_KEY);
        ResponseEntity<String> response = rest.exchange(
                "http://localhost:" + port + "/api/admin/products",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(productRepository.count()).isEqualTo(productsBefore);
    }
}
