package com.redislab.common.exception;

public class ProductNotFoundException extends AppException {

    public ProductNotFoundException(long id) {
        super(404, "PRODUCT_NOT_FOUND", "Product not found");
    }
}
