package com.redislab.common.security;

import com.redislab.common.exception.InvalidAdminKeyException;
import com.redislab.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Admin-Key";

    private final byte[] expectedKeyBytes;

    public AdminKeyInterceptor(AppProperties appProperties) {
        this.expectedKeyBytes = appProperties.admin().apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided.getBytes(StandardCharsets.UTF_8), expectedKeyBytes)) {
            throw new InvalidAdminKeyException();
        }
        return true;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
