package com.redislab.product.service;

import com.redislab.common.response.PageResponse;
import com.redislab.product.dto.AdminProductResponse;
import com.redislab.product.dto.CreateProductRequest;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.dto.ProductStatusResponse;
import com.redislab.product.dto.UpdateProductRequest;

public interface ProductService {

    ProductResponse getPublicProduct(long id);

    PageResponse<ProductResponse> listPublicProducts(int page, int size);

    AdminProductResponse getAdminProduct(long id);

    PageResponse<AdminProductResponse> listAdminProducts(int page, int size);

    AdminProductResponse createProduct(CreateProductRequest request);

    AdminProductResponse updateProduct(long id, UpdateProductRequest request);

    ProductStatusResponse activateProduct(long id);

    ProductStatusResponse deactivateProduct(long id);
}
