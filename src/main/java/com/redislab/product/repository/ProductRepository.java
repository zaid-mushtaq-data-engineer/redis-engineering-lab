package com.redislab.product.repository;

import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
}
