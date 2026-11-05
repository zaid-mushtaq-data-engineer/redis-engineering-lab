package com.redislab.common.exception;

public class InsufficientStockException extends AppException {

    public InsufficientStockException() {
        super(409, "INSUFFICIENT_STOCK", "Insufficient stock");
    }
}
