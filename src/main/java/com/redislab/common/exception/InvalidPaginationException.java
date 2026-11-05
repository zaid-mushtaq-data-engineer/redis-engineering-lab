package com.redislab.common.exception;

public class InvalidPaginationException extends AppException {

    public InvalidPaginationException() {
        super(400, "INVALID_PAGINATION", "Invalid pagination parameters");
    }
}
