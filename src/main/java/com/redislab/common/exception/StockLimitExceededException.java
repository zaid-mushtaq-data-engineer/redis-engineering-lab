package com.redislab.common.exception;

public class StockLimitExceededException extends AppException {

    public StockLimitExceededException() {
        super(409, "STOCK_LIMIT_EXCEEDED", "Stock limit exceeded");
    }
}
