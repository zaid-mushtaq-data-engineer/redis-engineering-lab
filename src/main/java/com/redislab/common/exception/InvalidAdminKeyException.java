package com.redislab.common.exception;

public class InvalidAdminKeyException extends AppException {

    public InvalidAdminKeyException() {
        super(401, "INVALID_ADMIN_KEY", "Invalid or missing admin API key");
    }
}
