package com.redislab.common.exception;

public class ConcurrentModificationException extends AppException {

    public ConcurrentModificationException() {
        super(409, "CONCURRENT_MODIFICATION", "Resource was modified by another request");
    }
}
