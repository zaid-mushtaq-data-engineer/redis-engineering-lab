package com.redislab.config;

import com.redislab.common.security.AdminKeyInterceptor;
import com.redislab.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminKeyInterceptor adminKeyInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(AdminKeyInterceptor adminKeyInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.adminKeyInterceptor = adminKeyInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/products", "/api/products/**", "/api/inventory/**");
        registry.addInterceptor(adminKeyInterceptor).addPathPatterns("/api/admin/**", "/api/dev/**");
    }
}
