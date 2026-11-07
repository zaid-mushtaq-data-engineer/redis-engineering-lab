package com.redislab.product.service;

import com.redislab.cache.service.InventoryCacheService;
import com.redislab.cache.service.ProductCacheService;
import com.redislab.cache.service.ProductListCacheService;
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
    private final ProductCacheService productCache;
    private final ProductListCacheService productListCache;
    private final InventoryCacheService inventoryCache;

    public ProductServiceImpl(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            PageRequestValidator pageRequestValidator,
            TransactionTemplate transactionTemplate,
            ProductCacheService productCache,
            ProductListCacheService productListCache,
            InventoryCacheService inventoryCache) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.pageRequestValidator = pageRequestValidator;
        this.transactionTemplate = transactionTemplate;
        this.productCache = productCache;
        this.productListCache = productListCache;
        this.inventoryCache = inventoryCache;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(long id) {
        return productCache.get(id).orElseGet(() -> loadAndCache(id));
    }

    private ProductResponse loadAndCache(long id) {
        Product product = productRepository
                .findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(id));
        ProductResponse response = ProductResponse.from(product);
        productCache.put(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listPublicProducts(int page, int size) {
        pageRequestValidator.validate(page, size);
        return productListCache
                .get(page, size)
                .orElseGet(() -> loadAndCacheList(page, size));
    }

    private PageResponse<ProductResponse> loadAndCacheList(int page, int size) {
        Page<Product> result = productRepository.findByStatus(
                ProductStatus.ACTIVE, PageRequest.of(page, size, LIST_SORT));
        PageResponse<ProductResponse> response =
                PageResponse.from(result.map(ProductResponse::from), page, size);
        productListCache.put(page, size, response);
        return response;
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
        AdminProductResponse created = transactionTemplate.execute(status -> {
            String name = request.name().trim();
            Product product =
                    productRepository.save(new Product(name, request.description(), request.price()));
            inventoryRepository.save(new Inventory(product.getId(), 0));
            return AdminProductResponse.from(product);
        });
        productListCache.evictAll();
        return created;
    }

    @Override
    public AdminProductResponse updateProduct(long id, UpdateProductRequest request) {
        WriteOutcome<AdminProductResponse> outcome = transactionTemplate.execute(status -> {
            Product product = productRepository
                    .findById(id)
                    .orElseThrow(() -> new ProductNotFoundException(id));
            if (!request.version().equals(product.getVersion())) {
                throw new ConcurrentModificationException();
            }
            String name = request.name().trim();
            String description = request.description();
            if (product.hasSameEditableValues(name, description, request.price())) {
                return WriteOutcome.unchanged(AdminProductResponse.from(product));
            }
            product.rename(name, description, request.price());
            try {
                Product saved = productRepository.save(product);
                return WriteOutcome.changed(AdminProductResponse.from(saved));
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConcurrentModificationException();
            }
        });
        if (outcome.changed()) {
            invalidateCatalogCaches(id);
        }
        return outcome.response();
    }

    @Override
    public ProductStatusResponse activateProduct(long id) {
        return changeStatusAfterCommit(id, ProductStatus.ACTIVE);
    }

    @Override
    public ProductStatusResponse deactivateProduct(long id) {
        return changeStatusAfterCommit(id, ProductStatus.INACTIVE);
    }

    private ProductStatusResponse changeStatusAfterCommit(long id, ProductStatus target) {
        WriteOutcome<ProductStatusResponse> outcome =
                transactionTemplate.execute(status -> changeStatus(id, target));
        if (outcome.changed()) {
            invalidateCatalogCaches(id);
            inventoryCache.evict(id);
        }
        return outcome.response();
    }

    private void invalidateCatalogCaches(long productId) {
        productCache.evict(productId);
        productListCache.evictAll();
    }

    private WriteOutcome<ProductStatusResponse> changeStatus(long id, ProductStatus target) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getStatus() == target) {
            return WriteOutcome.unchanged(ProductStatusResponse.from(product));
        }
        product.changeStatus(target);
        Product saved = productRepository.save(product);
        return WriteOutcome.changed(ProductStatusResponse.from(saved));
    }

    private record WriteOutcome<T>(T response, boolean changed) {

        static <T> WriteOutcome<T> changed(T response) {
            return new WriteOutcome<>(response, true);
        }

        static <T> WriteOutcome<T> unchanged(T response) {
            return new WriteOutcome<>(response, false);
        }
    }
}
