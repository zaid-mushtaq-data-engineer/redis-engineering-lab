package com.redislab.ratelimit;

import com.redislab.common.exception.RateLimitExceededException;
import com.redislab.common.util.ClientIpResolver;
import com.redislab.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final AppProperties appProperties;
    private final ClientIpResolver clientIpResolver;
    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitInterceptor(
            AppProperties appProperties,
            ClientIpResolver clientIpResolver,
            TokenBucketRateLimiter rateLimiter) {
        this.appProperties = appProperties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!appProperties.rateLimit().enabled()) {
            return true;
        }
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }

        String clientIp = clientIpResolver.resolve(request);
        Optional<RateLimitResult> outcome = rateLimiter.tryConsume(clientIp);
        if (outcome.isEmpty()) {
            return true;
        }

        RateLimitResult result = outcome.get();
        RateLimitHeaders.apply(response, result);
        if (!result.allowed()) {
            log.info("RATE_LIMIT_REJECTED ip={}", clientIp);
            throw new RateLimitExceededException(result);
        }
        return true;
    }
}
