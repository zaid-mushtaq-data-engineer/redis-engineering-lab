package com.redislab.common.util;

import com.redislab.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private final AppProperties appProperties;

    public ClientIpResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String resolve(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (appProperties.rateLimit().trustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                ip = forwarded.split(",")[0].trim();
            }
        }
        return ip.replace(':', '_');
    }
}
