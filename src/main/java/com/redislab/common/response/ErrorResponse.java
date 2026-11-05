package com.redislab.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(
            int status, String code, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }

    public static ErrorResponse of(int status, String code, String message, String path) {
        return of(status, code, message, path, null);
    }
}
