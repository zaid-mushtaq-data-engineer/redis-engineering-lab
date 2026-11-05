package com.redislab.inventory.repository;

import com.redislab.inventory.entity.Inventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query(
            """
            SELECT i.productId AS productId, i.stockQuantity AS stockQuantity
            FROM Inventory i
            JOIN Product p ON p.id = i.productId
            WHERE i.productId = :productId AND p.status = com.redislab.product.entity.ProductStatus.ACTIVE
            """)
    Optional<PublicInventoryView> findPublicByProductId(@Param("productId") long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE inventory
                       SET stock_quantity = stock_quantity + :q, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE product_id = :productId AND stock_quantity <= :max - :q
                    """,
            nativeQuery = true)
    int increment(@Param("productId") long productId, @Param("q") int q, @Param("max") int max);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE inventory
                       SET stock_quantity = stock_quantity - :q, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE product_id = :productId AND stock_quantity >= :q
                    """,
            nativeQuery = true)
    int decrement(@Param("productId") long productId, @Param("q") int q);
}
