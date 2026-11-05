package com.redislab.product.service;

import com.redislab.common.exception.ConcurrentModificationException;
import com.redislab.common.exception.ProductNotFoundException;
import com.redislab.common.response.PageResponse;
import com.redislab.common.util.PageRequestValidator;
import com.redislab.inventory.entity.Inventory;
import com.redislab.inventory.repository.InventoryRepository;
import com.redislab.product.dto.AdminProductResponse;
import com.redislab.product.dto.CreateProductRequest;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.dto.ProductStatusResponse;
import com.redislab.product.dto.UpdateProductRequest;
import com.redislab.product.entity.Product;
import com.redislab.product.entity.ProductStatus;
import com.redislab.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PageRequestValidator pageRequestValidator;
    private final TransactionTemplate transactionTemplate;

    public ProductServiceImpl(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            PageRequestValidator pageRequestValidator,
            TransactionTemplate transactionTemplate) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.pageRequestValidator = pageRequestValidator;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(long id) {
        return productRepository
                .findByIdAndStatus(id, ProductStatus.ACTIVE)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listPublicProducts(int page, int size) {
        pageRequestValidator.validate(page, size);
        Page<Product> result = productRepository.findByStatus(
                ProductStatus.ACTIVE, PageRequest.of(page, size, LIST_SORT));
        return PageResponse.from(result.map(ProductResponse::from), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductResponse getAdminProduct(long id) {
        return productRepository
                .findById(id)
                .map(AdminProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> listAdminProducts(int page, int size) {
        pageRequestValidator.validate(page, size);
        Page<Product> result = productRepository.findAll(PageRequest.of(page, size, LIST_SORT));
        return PageResponse.from(result.map(AdminProductResponse::from), page, size);
    }

    @Override
    public AdminProductResponse createProduct(CreateProductRequest request) {
        return transactionTemplate.execute(status -> {
            String name = request.name().trim();
            Product product =
                    productRepository.save(new Product(name, request.description(), request.price()));
            inventoryRepository.save(new Inventory(product.getId(), 0));
            // Stage 5+: productListCache.evictAll()
            return AdminProductResponse.from(product);
        });
    }

    @Override
    public AdminProductResponse updateProduct(long id, UpdateProductRequest request) {
        return transactionTemplate.execute(status -> {
            Product product = productRepository
                    .findById(id)
                    .orElseThrow(() -> new ProductNotFoundException(id));
            if (!request.version().equals(product.getVersion())) {
                throw new ConcurrentModificationException();
            }
            String name = request.name().trim();
            String description = request.description();
            if (product.hasSameEditableValues(name, description, request.price())) {
                return AdminProductResponse.from(product);
            }
            product.rename(name, description, request.price());
            try {
                Product saved = productRepository.save(product);
                // Stage 5+: productCache.evict(id); productListCache.evictAll()
                return AdminProductResponse.from(saved);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConcurrentModificationException();
            }
        });
    }

    @Override
    public ProductStatusResponse activateProduct(long id) {
        return transactionTemplate.execute(status -> changeStatus(id, ProductStatus.ACTIVE));
    }

    @Override
    public ProductStatusResponse deactivateProduct(long id) {
        return transactionTemplate.execute(status -> changeStatus(id, ProductStatus.INACTIVE));
    }

    private ProductStatusResponse changeStatus(long id, ProductStatus target) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getStatus() == target) {
            return ProductStatusResponse.from(product);
        }
        product.changeStatus(target);
        Product saved = productRepository.save(product);
        // Stage 5–7: evict product, inventory, list caches when status changes
        return ProductStatusResponse.from(saved);
    }
}
